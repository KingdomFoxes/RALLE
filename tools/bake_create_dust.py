"""Bake the original 94489fa Create dust trajectories. Run offline; requires numpy/Pillow.

64 frames; 256px tiles cover wheel coordinates [-128,127] before repeating.
Each output pixel stores up to eight source contributors in original paint order.
RGBA stores two (1 + dx + 7*dy, opacity) pairs; zero displacement code means empty.
Four logical 2048px pages are losslessly packed into unique 8px tiles. The final
128 rows hold the 512x512 tile index, flattened to 2048px rows. The shader uses
exact texel fetches; contributor bytes and overlap order are unchanged.
"""
from pathlib import Path
import numpy as np
from PIL import Image


def pack_atlas(atlas):
    tiles, indices, lookup = [], [], {}
    for y in range(0, 4096, 8):
        for x in range(0, 4096, 8):
            tile = atlas[y:y+8, x:x+8].tobytes()
            if tile not in lookup:
                lookup[tile] = len(tiles)
                tiles.append(tile)
            indices.append(lookup[tile])
    rows = (len(tiles) + 255) // 256 * 8
    packed = np.zeros((rows + 128, 2048, 4), dtype=np.uint8)
    for index, tile in enumerate(tiles):
        y, x = index // 256 * 8, index % 256 * 8
        packed[y:y+8, x:x+8] = np.frombuffer(tile, dtype=np.uint8).reshape(8, 8, 4)
    index = np.array(indices, dtype=np.uint32).reshape(128, 2048)
    for channel in range(3):
        packed[rows:, :, channel] = (index >> (channel * 8)) & 255
    packed[rows:, :, 3] = 255
    # Reconstruct every byte using the exact addressing contract used by the shader.
    for y in range(0, 4096, 8):
        for x in range(0, 4096, 8):
            position = y // 8 * 512 + x // 8
            encoded = packed[rows + position // 2048, position % 2048].astype(np.uint32)
            tile = int(encoded[0] | encoded[1] << 8 | encoded[2] << 16)
            ty, tx = tile // 256 * 8, tile % 256 * 8
            assert np.array_equal(atlas[y:y+8, x:x+8], packed[ty:ty+8, tx:tx+8])
    return packed


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
    packed = pack_atlas(atlas)
    Image.fromarray(packed).save(path, optimize=True)
    print(f'Baked {path}: {path.stat().st_size:,} bytes; {packed.nbytes:,} RGBA8 bytes; all 64 frames verified lossless')


if __name__ == '__main__':
    bake()
