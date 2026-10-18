# Shared Fox header emblem

`fox_overlay.png` is the unmodified original 777 × 1186 white Kingdom of Foxes
crest. It is the source for the header textures, rather than a texture rendered
directly in the menu. No generated reinterpretation is used.

`header/fox_1x.png` through `header/fox_16x.png` are area-filtered RGBA textures for
the header's 18 × 28 logical-pixel footprint at each integer GUI scale. Each output
pixel averages the complete corresponding area of the original, including
fractional source-pixel coverage. This preserves thin lines as translucent white
rather than dropping them through sparse sampling of a large source texture.
An opaque black shadow offset 0.5 logical pixels right/down is composited behind
the white crest at source resolution before area filtering. Premultiplied color
and alpha are filtered together to preserve white details and prevent dark
fringes. There is no blur or added spread. A one-logical-pixel right/bottom gutter
contains the shadow, making each texture `19 × scale` by `29 × scale` pixels.
Compensating component margins preserve the original crest position and layout.
The shadow is baked into the texture: rendering remains a single textured quad,
with no runtime shadow generation, additional pass, or theme-specific texture.

Regenerate from the repository root with Java 21:

```text
java scripts/GenerateHeaderEmblems.java
```

`RalleHeader` selects the matching native-pixel texture when Settings or Raid LFG
is built. Minecraft rebuilds the screens when the GUI scale changes; scales above
16 use the largest variant. The logical footprint and layout stay unchanged.
The variants deliberately use ordinary nearest sampling: coverage is already
baked into their alpha, and each texel maps to a framebuffer pixel at its scale.

Banner lettering is live Karla text rendered by
`HeaderTitleComponent`, with an opaque black shadow offset by 0.5 logical pixels
right/down and no extra spread; it retains the ordinary label layout.
