# Shared Fox header emblem

`fox_overlay.png` is the original white Kingdom of Foxes crest, resampled from
777 × 1186 to 1554 × 2372 with high-quality bicubic interpolation. Its alpha mask
preserves the transparent background; RGB stays white even in transparent texels
to avoid dark fringes when filtering. No generated reinterpretation is used.

`fox_overlay.png.mcmeta` enables linear filtering and edge clamping for the small
header presentation. `RalleHeader` uses the full texture at 18 × 28 logical pixels
in both Settings and Raid LFG. Keep its source dimensions synchronized when
replacing the texture. Banner lettering is live Karla text rendered by
`HeaderTitleComponent`, with an opaque black shadow offset by 0.5 logical pixels
right/down and no extra spread; it retains the ordinary label layout.
