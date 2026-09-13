"""Bake the original 94489fa Create dust trajectories. Run offline; requires numpy/Pillow.

64 frames; 256px tiles cover wheel coordinates [-128,127] before repeating.
Each output pixel stores up to eight source contributors in original paint order.
RGBA stores two (1 + dx + 7*dy, opacity) pairs; zero displacement code means empty.
Four 2048px pages form one 4096px atlas. No runtime hash, scatter, or pixel draw calls.
"""
from pathlib import Path
import numpy as np
from PIL import Image


def bake():
    size = 256
    y, x = np.indices((size, size))
    xx, yy = (x - 128).astype(np.int64), (y - 128).astype(np.int64)
    h = ((xx * 0x1f123bb5) ^ (yy * 0x5f356495)) & 0xffffffff
    h ^= h >> 16
    h = (h * 0x45d9f3b) & 0xffffffff
    h ^= h >> 16
    threshold = .12 + .52 * (h & 65535) / 65535
    atlas = np.zeros((4096, 4096, 4), dtype=np.uint8)
    for frame in range(64):
        opacity = 1 - np.clip((frame / 63 - threshold) / .36, 0, 1)
        dx = np.floor((1 - opacity) * (3 + 4 * threshold) + .5).astype(int)
        dy = np.floor((1 - opacity) * 6 + .5).astype(int)
        alpha = np.floor(opacity * 255 + .5).astype(np.uint8)
        slots = np.zeros((size, size), dtype=int)
        # Earlier source rows first; within a row larger dx is the earlier source column.
        for up in range(7):
            for right in range(6, -1, -1):
                valid = (dx == right) & (dy == up) & (alpha > 0)
                sy, sx = np.where(valid)
                ty, tx = (sy - up) % size, (sx + right) % size
                slot = slots[ty, tx]
                assert np.all(slot < 8), 'Increase atlas capacity instead of dropping collisions'
                page = slot // 2
                ay = (page // 2) * 2048 + (frame // 8) * size + ty
                ax = (page % 2) * 2048 + (frame % 8) * size + tx
                channel = (slot % 2) * 2
                atlas[ay, ax, channel] = 1 + right + up * 7
                atlas[ay, ax, channel + 1] = alpha[sy, sx]
                slots[ty, tx] += 1
    path = Path('src/main/resources/assets/ralle/textures/gui/wheel/create_dissolve.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(atlas).save(path, optimize=True)
    print(f'Baked {path}: {path.stat().st_size:,} bytes; fixed 64 MiB GPU atlas')


if __name__ == '__main__':
    bake()
