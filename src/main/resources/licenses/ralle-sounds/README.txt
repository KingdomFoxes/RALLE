RALLE synthesized chat instrument sounds

These sounds are original procedural synthesis created for RALLE. They do not
contain samples or assets from Minecraft, Wynncraft, Hychat, or another game.
The generated OGG files are distributed under RALLE's GNU GPL v3.0 license.

The acoustic guitar, bass guitar, piano, and drum banks are also original
procedural synthesis, without recordings or soundfonts. Reproduce them with
tools/generate_chat_instruments.py (NumPy and imageio-ffmpeg required).
The existing processed-xylophone files are preserved by that generator.
Xylophone uses D5 through F-sharp 6, acoustic guitar D3 through F-sharp 4,
bass D2 through F-sharp 3, and piano D4 through F-sharp 5.
Their copy motif uses the root and its octave, with
an 80 ms second onset. Drums use bass drum, floor tom, low tom, high tom,
and snare, with a single crash cymbal on successful copying.

Use --previews to generate WAV auditions under build/audio-previews. These
previews use equal playback gain and are not included in the mod.
