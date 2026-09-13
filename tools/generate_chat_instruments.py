"""Generate RALLE's original chat instrument samples offline (NumPy + imageio-ffmpeg).

Run from the repository root. Existing xylophone assets are deliberately preserved.
No recordings, soundfonts, or third-party samples are used. Output is mono OGG
Vorbis at 44.1 kHz. The game only plays packaged assets; no synthesis runs in-game.
"""
from pathlib import Path
import json
import argparse
import subprocess
import sys
import wave

import numpy as np

sys.path.insert(0, str(Path("build/asset-tools").resolve()))
import imageio_ffmpeg

RATE = 44100
ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/ralle"
NOTE_STEMS = ["d", "e", "f_sharp_", "g", "a", "b", "c_sharp_", "d", "e", "f_sharp_"]
BANK_OCTAVES = {"acoustic_guitar": 3, "bass_guitar": 2, "piano": 4}


def note_names(octave):
    return [stem + str(octave + (i >= 6)) for i, stem in enumerate(NOTE_STEMS)]
SEMITONES = [0, 2, 4, 5, 7, 9, 11, 12, 14, 16]


def envelope(signal, attack=0.002):
    signal = signal - np.mean(signal)
    signal *= np.minimum(np.arange(len(signal)) / (RATE * attack), 1)
    tail = min(int(RATE * 0.035), len(signal))
    signal[-tail:] *= np.linspace(1, 0, tail)
    return signal


def balance(signal, target=0.16):
    # Match short-window energy between instruments, with headroom for overlapping hits.
    window = signal[:int(RATE * 0.12)]
    gain = min(target / max(np.sqrt(np.mean(window ** 2)), 1e-9),
               0.70 / max(np.max(np.abs(signal)), 1e-9))
    return signal * gain


def melodic(bank, frequency, seed):
    t = np.arange(int(RATE * (0.62 if bank == "acoustic_guitar" else 0.54))) / RATE
    rng = np.random.default_rng(seed)
    sound = np.zeros_like(t)
    if bank == "acoustic_guitar":
        # Plucked-string partials with a pick-position comb, fast upper-harmonic
        # loss, and a quiet wooden-body transient. Its lower register keeps the
        # attack distinct from the piano bank.
        for n in range(1, 29):
            if n * frequency >= RATE * 0.45:
                break
            amplitude = np.sin(n * np.pi * 0.23) / n ** 1.2
            sound += amplitude * np.cos(2 * np.pi * frequency * n * t) * np.exp(-t * (9 + n * 2.5))
        sound += 0.07 * np.sin(2 * np.pi * 195 * t) * np.exp(-t * 45)
        sound += 0.025 * rng.normal(size=len(t)) * np.exp(-t * 240)
    elif bank == "bass_guitar":
        # Round finger-plucked bass, D2 through F-sharp 3 (three octaves down).
        for n in range(1, 17):
            amplitude = np.sin(n * np.pi * 0.31) / n ** 1.15
            sound += amplitude * np.cos(2 * np.pi * frequency * n * t) * np.exp(-t * (6 + n * 1.5))
        sound += 0.018 * rng.normal(size=len(t)) * np.exp(-t * 180)
    else:
        # Hammer-struck piano string with mild inharmonicity and paired strings.
        for n in range(1, 19):
            partial = frequency * n * np.sqrt(1 + 0.00012 * n * n)
            if partial >= RATE * 0.45:
                break
            amplitude = (1 if n == 1 else 0.7) / n ** 1.5
            oscillation = (np.cos(2 * np.pi * partial * t)
                           + 0.35 * np.cos(2 * np.pi * partial * 1.0015 * t))
            sound += amplitude * oscillation * np.exp(-t * (7 + n * 1.8))
        sound += 0.04 * rng.normal(size=len(t)) * np.exp(-t * 330)
    return balance(envelope(sound), 0.24 if bank == "bass_guitar" else 0.18)


def percussion(name, seed):
    rng = np.random.default_rng(seed)
    duration = 0.85 if name == "crash" else 0.48
    t = np.arange(int(RATE * duration)) / RATE
    noise = rng.normal(size=len(t))
    bright_noise = noise - np.roll(noise, 1)
    if name == "crash":
        # Dense inharmonic metal modes plus bright noise; restrained, short crash.
        sound = np.zeros_like(t)
        for frequency in np.geomspace(470, 15500, 90):
            frequency *= rng.uniform(0.96, 1.04)
            sound += np.sin(2 * np.pi * frequency * t + rng.uniform(0, 2 * np.pi)) * np.exp(
                -t * (5 + frequency / 4500)) / 12
        sound += 0.35 * bright_noise * np.exp(-t * 8)
    elif name == "snare":
        sound = (0.7 * np.sin(2 * np.pi * 185 * t) * np.exp(-t * 17)
                 + 0.4 * np.sin(2 * np.pi * 315 * t) * np.exp(-t * 22)
                 + 0.6 * bright_noise * np.exp(-t * 16) + 0.5 * noise * np.exp(-t * 240))
    else:
        frequency = {"bass_drum": 55, "floor_tom": 85, "low_tom": 120, "high_tom": 170}[name]
        # Exponentially settling membrane pitch and quieter inharmonic modes.
        phase = 2 * np.pi * frequency * (t + 0.7 * 0.018 * (1 - np.exp(-t / 0.018)))
        sound = np.sin(phase) * np.exp(-t * 11)
        sound += 0.42 * np.sin(phase * 1.59) * np.exp(-t * 16)
        sound += 0.22 * np.sin(phase * 2.14) * np.exp(-t * 23)
        # Audible stick/beater contact and upper membrane modes, not just low thuds.
        sound += 0.22 * bright_noise * np.exp(-t * 200)
        sound += 0.12 * np.sin(2 * np.pi * 2400 * t) * np.exp(-t * 280)
    return balance(envelope(sound, attack=0.0003), 0.17 if name == "crash" else 0.20)


def write(bank, name, signal):
    # Keep copy-motif overlaps below full scale without changing note timing.
    signal = signal * min(1.0, 0.88 / max(np.max(np.abs(signal)), 1e-9))
    path = ROOT / "sounds/ui" / bank / (name + ".ogg")
    path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-v", "error", "-y",
                    "-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", "pipe:0",
                    "-c:a", "libvorbis", "-q:a", "5", str(path)],
                   input=signal.astype("<f4").tobytes(), check=True)
    return {"sounds": [{"name": f"ralle:ui/{bank}/{name}"}]}


def make_previews():
    """Audition decoded packaged files at equal playback gain; never normalize each preview."""
    output = ROOT.parents[4] / "build/audio-previews"
    output.mkdir(parents=True, exist_ok=True)
    for bank in ["acoustic_guitar", "bass_guitar", "piano", "drums"]:
        names = (note_names(BANK_OCTAVES[bank]) if bank != "drums" else
                 ["bass_drum", "floor_tom", "low_tom", "high_tom", "snare"])
        names += ["copy_success" if bank != "drums" else "crash"]
        onsets = [0.2 + i * 0.5 for i in range(len(names) - 1)]
        onsets.append(onsets[-1] + 1.0)
        result = np.zeros(int((onsets[-1] + 1.3) * RATE))
        for name, onset in zip(names, onsets):
            path = ROOT / "sounds/ui" / bank / (name + ".ogg")
            decoded = subprocess.check_output([imageio_ffmpeg.get_ffmpeg_exe(), "-v", "error",
                "-i", str(path), "-f", "f32le", "-ar", str(RATE), "-ac", "1", "pipe:1"])
            signal = np.frombuffer(decoded, dtype="<f4")
            start = int(onset * RATE)
            result[start:start + len(signal)] += signal
        assert np.max(np.abs(result)) < 1, bank
        with wave.open(str(output / (bank + ".wav")), "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(RATE)
            wav.writeframes((result * 32767).astype("<i2").tobytes())
    print(f"Previews: {output}")


def main():
    manifest_path = ROOT / "sounds.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for bank in ["acoustic_guitar", "bass_guitar", "piano"]:
        notes = note_names(BANK_OCTAVES[bank])
        midi_base = 12 * (BANK_OCTAVES[bank] + 1) + 2
        # Remove only obsolete notes in this generated bank, never unrelated assets.
        prefix = f"ui.{bank}."
        for key in list(manifest):
            suffix = key.removeprefix(prefix)
            if key.startswith(prefix) and suffix not in notes + ["copy_success"]:
                del manifest[key]
                path = ROOT / "sounds/ui" / bank / (suffix + ".ogg")
                if path.parent.resolve() != (ROOT / "sounds/ui" / bank).resolve():
                    raise ValueError("Invalid generated sound path")
                path.unlink(missing_ok=True)
        signals = []
        for i, (name, semitones) in enumerate(zip(notes, SEMITONES)):
            frequency = 440 * 2 ** ((midi_base + semitones - 69) / 12)
            signal = melodic(bank, frequency, i)
            signals.append(signal)
            manifest[f"ui.{bank}.{name}"] = write(bank, name, signal)
        # Bake the exact 80 ms second onset into one asset, as for xylophone.
        delay = int(RATE * 0.08)
        motif = np.zeros(len(signals[0]) + delay)
        motif[:len(signals[0])] += signals[0]
        motif[delay:] += signals[7]
        manifest[f"ui.{bank}.copy_success"] = write(bank, "copy_success", motif)
    for i, name in enumerate(["bass_drum", "floor_tom", "low_tom", "high_tom", "snare", "crash"]):
        manifest[f"ui.drums.{name}"] = write("drums", name, percussion(name, i))
    # Preserve the compact existing manifest layout.
    entries = []
    for key, value in manifest.items():
        sounds = ", ".join("{ " + json.dumps(sound)[1:-1] + " }" for sound in value["sounds"])
        entries.append('  "' + key + '": {\n    "sounds": [' + sounds + ']\n  }')
    manifest_path.write_text("{\n" + ",\n".join(entries) + "\n}\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--previews", action="store_true", help="also write audition WAVs under build/audio-previews")
    args = parser.parse_args()
    main()
    if args.previews:
        make_previews()
