`wynncraft-character-book-2026-10-19.json` is the user's locally captured Wynncraft
character-book item payload. The account UUID is replaced with a test UUID. It
includes a complete 15-position character grid, four existing characters and the
observed control items. This is game-data evidence for decoder tests, not mod
source or copied resource-pack artwork. Tests derive partial and hostile variants
from it. No other mod's cache or identity heuristics are used by discovery.

`transition-video-samples.json` contains anonymized binary dark-pixel masks from
17 timestamps in the user's `2026-10-18 23-49-44.mp4`, sampled at 10 fps and nearest
64×36. Only the central 32×18 threshold mask (RGB <= 8), with bits in row-major
least-significant-bit-first order, is retained. No original RGB, name, chat or
resource-pack artwork is stored. Expected black labels distinguish book/animation
from actual blackouts. Initial blackouts are intentionally positive too: the
switching controller must ignore them until the matching selected book closes.
