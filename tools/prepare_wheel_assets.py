"""Offline asset preparation. Requires NumPy/Pillow; audio also needs imageio-ffmpeg.

Run from the repository root after downloading the user-selected references to build/.
The game only reads the resulting PNG/OGG assets; it never generates random fade pixels.
"""
from pathlib import Path
from bake_create_dust import bake
import subprocess
import sys
from PIL import Image

root = Path('src/main/resources/assets/ralle')
textures = root / 'textures/gui/wheel'
textures.mkdir(parents=True, exist_ok=True)

bake()

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
