"""Rebuild RALLE's original processed-xylophone OGG assets.

Requires Python 3 and libsndfile for OGG Vorbis encoding. The synthesis itself
uses only the Python standard library and no recorded or third-party samples.
"""

from __future__ import annotations

import argparse
import ctypes
import ctypes.util
import math
import random
from pathlib import Path


SAMPLE_RATE = 48_000
HIT_SECONDS = 0.155
MOTIF_SECOND_ONSET_SECONDS = 0.080
NOTES = {
    "d5": 587.3295,
    "e5": 659.2551,
    "f_sharp_5": 739.9888,
    "g5": 783.9909,
    "a5": 880.0000,
    "b5": 987.7666,
    "c_sharp_6": 1108.7305,
    "d6": 1174.6591,
    "e6": 1318.5102,
    "f_sharp_6": 1479.9777,
}


def synthesize_hit(frequency: float) -> list[float]:
    frame_count = round(HIT_SECONDS * SAMPLE_RATE)
    samples: list[float] = []
    noise = random.Random(round(frequency * 1000))
    softened_noise = 0.0
    for frame in range(frame_count):
        time = frame / SAMPLE_RATE
        attack = 1.0 - math.exp(-time / 0.0045)
        fundamental = 0.78 * math.sin(math.tau * frequency * time) * math.exp(-time / 0.046)
        mode_two = 0.15 * math.sin(math.tau * frequency * 3.92 * time + 0.35) * math.exp(-time / 0.024)
        mode_three = 0.045 * math.sin(math.tau * frequency * 9.08 * time + 0.8) * math.exp(-time / 0.014)
        softened_noise = softened_noise * 0.82 + noise.uniform(-1.0, 1.0) * 0.18
        mallet = 0.055 * softened_noise * math.exp(-time / 0.007)
        tail_fade = min(1.0, max(0.0, (HIT_SECONDS - time) / 0.012))
        samples.append((fundamental + mode_two + mode_three + mallet) * attack * tail_fade)
    return normalized(samples, 0.78)


def normalized(samples: list[float], peak: float) -> list[float]:
    maximum = max(abs(sample) for sample in samples) or 1.0
    scale = peak / maximum
    return [sample * scale for sample in samples]


def copy_motif(d5: list[float], d6: list[float]) -> list[float]:
    onset = round(MOTIF_SECOND_ONSET_SECONDS * SAMPLE_RATE)
    mixed = [0.0] * (onset + len(d6))
    for index, sample in enumerate(d5):
        mixed[index] += sample
    for index, sample in enumerate(d6):
        mixed[onset + index] += sample
    return normalized(mixed, 0.78)


def find_sndfile(explicit: str | None) -> str:
    discovered = ctypes.util.find_library("sndfile")
    candidates = [
        explicit,
        r"C:\Program Files\Audacity\sndfile.dll",
        r"C:\Program Files (x86)\Audacity\sndfile.dll",
    ]
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return candidate
    if discovered:
        return discovered
    raise SystemExit("libsndfile was not found; pass the library path with --sndfile")


class SoundFileInfo(ctypes.Structure):
    _fields_ = [
        ("frames", ctypes.c_int64),
        ("samplerate", ctypes.c_int),
        ("channels", ctypes.c_int),
        ("format", ctypes.c_int),
        ("sections", ctypes.c_int),
        ("seekable", ctypes.c_int),
    ]


def encode_ogg(library_path: str, ogg_path: Path, samples: list[float]) -> None:
    library = ctypes.CDLL(library_path)
    open_file = getattr(library, "sf_wchar_open", None)
    if open_file is not None:
        open_file.argtypes = [ctypes.c_wchar_p, ctypes.c_int, ctypes.POINTER(SoundFileInfo)]
        open_file.restype = ctypes.c_void_p
        encoded_path: str | bytes = str(ogg_path)
    else:
        open_file = library.sf_open
        open_file.argtypes = [ctypes.c_char_p, ctypes.c_int, ctypes.POINTER(SoundFileInfo)]
        open_file.restype = ctypes.c_void_p
        encoded_path = str(ogg_path).encode()
    library.sf_write_float.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_float), ctypes.c_int64]
    library.sf_write_float.restype = ctypes.c_int64
    library.sf_close.argtypes = [ctypes.c_void_p]
    library.sf_close.restype = ctypes.c_int
    library.sf_strerror.argtypes = [ctypes.c_void_p]
    library.sf_strerror.restype = ctypes.c_char_p

    ogg_path.unlink(missing_ok=True)
    info = SoundFileInfo(0, SAMPLE_RATE, 1, 0x200060, 0, 0)  # OGG container + Vorbis codec
    handle = open_file(encoded_path, 0x20, ctypes.byref(info))  # SFM_WRITE
    if not handle:
        raise RuntimeError(library.sf_strerror(None).decode("utf-8", errors="replace"))
    try:
        frames = (ctypes.c_float * len(samples))(*samples)
        written = library.sf_write_float(handle, frames, len(samples))
        if written != len(samples):
            raise RuntimeError(f"libsndfile wrote {written} of {len(samples)} samples to {ogg_path}")
    finally:
        library.sf_close(handle)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sndfile", help="path to the libsndfile shared library")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    destination = root / "src/main/resources/assets/ralle/sounds/ui/xylophone"
    destination.mkdir(parents=True, exist_ok=True)
    sndfile = find_sndfile(args.sndfile)
    synthesized = {name: synthesize_hit(frequency) for name, frequency in NOTES.items()}
    synthesized["chat_copy_success"] = copy_motif(synthesized["d5"], synthesized["d6"])

    for name, samples in synthesized.items():
        encode_ogg(sndfile, destination / f"{name}.ogg", samples)


if __name__ == "__main__":
    main()
