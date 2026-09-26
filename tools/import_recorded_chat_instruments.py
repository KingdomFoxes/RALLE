"""Build the two recorded chat-sound banks from locally downloaded source files.

This tool is intentionally separate from generate_chat_instruments.py and only
writes piano_recorded/ and drums_recorded/. Download instructions and the
source-license record are in src/main/resources/licenses/ralle-sounds/README.txt.
Requires NumPy and imageio-ffmpeg; no downloads happen when this script runs.
"""
from pathlib import Path
import subprocess
import sys

import numpy as np

sys.path.insert(0, str(Path("build/asset-tools").resolve()))
import imageio_ffmpeg

RATE = 44100
ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/ralle"
SOURCES = Path("build/audio-sources")
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()


def decode(path):
    raw = subprocess.check_output([
        FFMPEG, "-v", "error", "-i", str(path), "-f", "f32le",
        "-ar", str(RATE), "-ac", "1", "pipe:1",
    ])
    return np.frombuffer(raw, dtype="<f4").copy()


def trim_and_shape(signal, duration, target_rms=0.12):
    peak = float(np.max(np.abs(signal)))
    if not peak:
        raise ValueError("source sample is silent")
    active = np.flatnonzero(np.abs(signal) > peak * 0.006)
    if not len(active):
        raise ValueError("could not locate source transient")
    # Leave 2 ms ahead of the first detected transient to preserve the attack.
    signal = signal[max(0, int(active[0]) - int(RATE * 0.002)):]
    signal = signal[:int(RATE * duration)].copy()
    if len(signal) < RATE // 10:
        raise ValueError("trimmed sample is unexpectedly short")
    tail = min(int(RATE * 0.03), len(signal) // 3)
    signal[-tail:] *= np.linspace(1, 0, tail, endpoint=True)
    measure = signal[:min(len(signal), int(RATE * 0.14))]
    gain = min(target_rms / max(float(np.sqrt(np.mean(measure ** 2))), 1e-9),
               0.82 / max(float(np.max(np.abs(signal))), 1e-9))
    return signal * gain


def pitch_shift(signal, semitones):
    if not semitones:
        return signal
    ratio = 2 ** (semitones / 12)
    output_length = max(1, round(len(signal) / ratio))
    source_positions = np.arange(output_length, dtype=np.float64) * ratio
    return np.interp(source_positions, np.arange(len(signal)), signal).astype(np.float32)


def encode(path, signal):
    path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([
        FFMPEG, "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1",
        "-i", "pipe:0", "-c:a", "libvorbis", "-q:a", "5", str(path),
    ], input=signal.astype("<f4").tobytes(), check=True)


def main():
    piano = {
        # output stem: Salamander/Tone.js sample, semitone shift from its root
        "d4": ("D#4v8.flac", -1), "e4": ("D#4v8.flac", 1),
        "f_sharp_4": ("F#4v8.flac", 0), "g4": ("F#4v8.flac", 1),
        "a4": ("A4v8.flac", 0), "b4": ("C5v8.flac", -1),
        "c_sharp_5": ("C5v8.flac", 1), "d5": ("D#5v8.flac", -1),
        "e5": ("D#5v8.flac", 1), "f_sharp_5": ("F#5v8.flac", 0),
    }
    piano_signals = {}
    for stem, (source_name, shift) in piano.items():
        source = decode(SOURCES / "salamander-v3" / source_name)
        source = pitch_shift(source, shift)
        shaped = trim_and_shape(source, 0.72, 0.13)
        piano_signals[stem] = shaped
        encode(ROOT / "sounds/ui/piano_recorded" / f"{stem}.ogg", shaped)
    d4 = piano_signals["d4"]
    d5 = piano_signals["d5"]
    motif = np.zeros(max(len(d4), int(0.08 * RATE) + len(d5)), dtype=np.float32)
    motif[:len(d4)] += d4 * 0.48
    motif[int(0.08 * RATE):int(0.08 * RATE) + len(d5)] += d5 * 0.48
    encode(ROOT / "sounds/ui/piano_recorded/copy_success.ogg", motif)

    drum_sources = {
        "bass_drum": "36-Pearl22Kick-3.flac",
        "floor_tom": "41-Pearl16FloorTom-3.flac",
        "low_tom": "43-Pearl16FloorTomEdge-3.flac",
        "high_tom": "45-Pearl12Tom-3.flac",
        "snare": "38-PearlSnare-3.flac",
        "crash": "49-SabianAA16Crash-3.flac",
    }
    for stem, source_name in drum_sources.items():
        duration = 1.0 if stem == "crash" else 0.58
        signal = trim_and_shape(decode(SOURCES / "avl-drumkits/Samples" / source_name),
                                duration, 0.105 if stem == "crash" else 0.12)
        encode(ROOT / "sounds/ui/drums_recorded" / f"{stem}.ogg", signal)
    print("Wrote 11 recorded Piano and 6 recorded Drums OGG files.")


if __name__ == "__main__":
    main()
