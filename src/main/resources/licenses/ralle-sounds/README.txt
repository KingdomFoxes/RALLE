RALLE chat-selection sound asset provenance

Retained procedural banks
-------------------------
The xylophone, acoustic guitar, bass guitar, Piano (Old), and Drums (Old)
assets are RALLE-authored procedural audio distributed under the repository's
GNU GPL v3.0 license. The game plays the packaged OGG files directly;
offline generation tools are not required to build or run the mod.

Recorded Piano
--------------
Source: Salamander Grand Piano V3 by Alexander Holm, from the pinned SFZ
collection maintained at:
https://github.com/sfzinstruments/SalamanderGrandPiano/tree/3382bf9496bba2486f5ab0de55a264d1dfc38404
Original archive: https://archive.org/details/SalamanderGrandPianoV3
Source commit: 3382bf9496bba2486f5ab0de55a264d1dfc38404
License: Creative Commons Attribution 3.0 Unported (CC BY 3.0). The complete
license text is bundled in CC-BY-3.0.txt. Attribution: Alexander Holm,
Salamander Grand Piano; no endorsement is implied.

The source was recorded at 48 kHz/24-bit and sampled in minor thirds. The SFZ
maps velocity layer v8 to MIDI velocities 57–64; this medium layer was used for
every source note. Outputs are mono 44.1 kHz Vorbis; missing notes use a
maximum one-semitone resampling shift. Source FLAC SHA-256 values:

Output stem       Source filename   Shift   Layer   Source SHA-256
d4                D#4v8.flac        -1      v8      295179461449c598c44ad712977d47b41c0c25d9c782d349094e748dc06c0dfa
e4                D#4v8.flac        +1      v8      295179461449c598c44ad712977d47b41c0c25d9c782d349094e748dc06c0dfa
f_sharp_4         F#4v8.flac         0      v8      be2dc339e8cec5f2f1da4ff583672dbc518bf5319547a08901b094617ac6998e
g4                F#4v8.flac        +1      v8      be2dc339e8cec5f2f1da4ff583672dbc518bf5319547a08901b094617ac6998e
a4                A4v8.flac          0       v8      59529694455698d7fd212cde8eec4029eaae8e5e6d454bb48bf36428eaa8009b
b4                C5v8.flac         -1      v8      f663242a552aad54113f186a6dd90c907c81d1f6374887a23179814544f78d79
c_sharp_5         C5v8.flac         +1      v8      f663242a552aad54113f186a6dd90c907c81d1f6374887a23179814544f78d79
d5                D#5v8.flac        -1      v8      f841ad0506994313f4202fbe3f07d917e60eeb10fb3461df3eae4fb9cd58432a
e5                D#5v8.flac        +1      v8      f841ad0506994313f4202fbe3f07d917e60eeb10fb3461df3eae4fb9cd58432a
f_sharp_5         F#5v8.flac         0       v8      7757ef988e874313432a3b4ed586f0407fd4a9933b7ccc613b8ff96a2f83bc87
copy_success      d4 + d5, second onset 80 ms later, each mixed at 0.48

Recorded Drums
--------------
Source library: AVL Drumkits by Glen MacArthur, Black Pearl kit, SFZ sample
mapping in Black_Pearl_4pc.sfz. Source repository:
https://github.com/studiorack/avl-drumkits/tree/b06cb2c27359cbc68a830a82bddced05bd73d1a0
Pinned source commit: b06cb2c27359cbc68a830a82bddced05bd73d1a0
License: CC BY-SA 3.0 with the library creator's additional terms. Bundled
license copies are AVL-Drumkits CC-BY-SA License.pdf and CC-BY-SA-3.0.txt.
Attribution: Glen MacArthur, AVL Drumkits. This modified sample selection is
named “RALLE selection drum cues, derived from AVL Drumkits”; the samples were
trimmed, downmixed, level-balanced, faded, and converted to OGG Vorbis. This
adapted sample selection is under CC BY-SA 3.0. The original library name is
not reused as the modified library's name.

The SFZ assigns five velocity layers: layer 3 covers velocities 53–77 and was
selected as the medium layer. Each file below is the exact source FLAC and its
SHA-256 from the pinned repository revision.

Output stem       Source filename                    MIDI part   Layer   Source SHA-256
bass_drum         36-Pearl22Kick-3.flac              36          3       14cae10ffe5b8f6ff084bb2c06089742cb7f024df3dba7b35debc7d401de86ae
floor_tom         41-Pearl16FloorTom-3.flac          41          3       af97516a24b9ce53152e27383c37b405162c390586cff6f4ede27a72bc927e9a
low_tom           43-Pearl16FloorTomEdge-3.flac      43          3       d6b3fd7e227629f8eaa58bb83ebc5439a89512b418ed6295b3408f9073ae2118
high_tom          45-Pearl12Tom-3.flac               45          3       5d3a3b8ca1362352f73bf2a6f338b493224bb0f04053ba270d7e83f6495a2511
snare             38-PearlSnare-3.flac               38          3       5de6946051d73f689390d56e4e208ff791b6cd6855cfa820078db62a4eb60bf3
crash             49-SabianAA16Crash-3.flac           49          3       e9c94a6c7bff785d4f0dd35e14838b7e36c1023ab647389d69e182a9f874cda3

Source recordings
-----------------
The listed Salamander v8 FLAC files are available from
https://raw.githubusercontent.com/sfzinstruments/SalamanderGrandPiano/3382bf9496bba2486f5ab0de55a264d1dfc38404/Samples/ and the listed AVL FLAC files from the
pinned AVL repository using the exact filenames above. The normal game uses
only the packaged OGG files and performs no downloading or sample processing.
