"""Offline asset preparation. Requires Pillow; audio conversion also needs imageio-ffmpeg.

Run from the repository root after downloading the user-selected references to build/.
The game only reads the resulting PNG/OGG assets; it never generates random fade pixels.
"""
from pathlib import Path
import random
import subprocess
import sys
from PIL import Image

root = Path('src/main/resources/assets/ralle')
textures = root / 'textures/gui/wheel'
textures.mkdir(parents=True, exist_ok=True)

# 64 ready-to-sample erosion frames, arranged in an 8x8 atlas of 128px masks.
rng = random.Random(0x52414C4C45)
thresholds = [.12 + .52 * rng.random() for _ in range(128 * 128)]
atlas = Image.new('RGBA', (1024, 1024))
for frame in range(64):
    progress = frame / 63
    tile = Image.new('RGBA', (128, 128))
    tile.putdata([(255, 255, 255, round(255 * (1 - min(1, max(0, (progress - t) / .36)))))
                  for t in thresholds])
    atlas.paste(tile, ((frame % 8) * 128, (frame // 8) * 128))
atlas.save(textures / 'create_dissolve.png', optimize=True)

sheet = Image.open('build/explosion-source.png').convert('RGBA')
explosion = Image.new('RGBA', (71 * 17, 100))
for frame in range(17):
    x, y = 1 + frame % 6 * 72, 15 + frame // 6 * 101
    tile = sheet.crop((x, y, x + 71, y + 100))
    tile.putdata([(0, 0, 0, 0) if p[:3] == (121, 230, 234) else p for p in tile.get_flattened_data()])
    explosion.paste(tile, (frame * 71, 0))
explosion.save(textures / 'kick_explosion.png', optimize=True)

sys.path.insert(0, str(Path('build/asset-tools').resolve()))
import imageio_ffmpeg
sound = root / 'sounds/ui/kick_explosion.ogg'
sound.parent.mkdir(parents=True, exist_ok=True)
subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-y', '-i', 'build/explosion-source.mp3',
                '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5', str(sound)], check=True)
