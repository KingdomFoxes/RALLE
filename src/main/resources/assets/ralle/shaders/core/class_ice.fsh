#version 330
uniform sampler2D Arrival;
layout(std140) uniform Ice {
    vec4 IceData;
};
#define Progress IceData.x
#define Cells IceData.yz
#define OutlineWidth IceData.w
in vec2 texCoord;
out vec4 fragColor;

float arrival(vec2 cell) {
    vec2 size = vec2(textureSize(Arrival, 0));
    cell = clamp(cell, vec2(0), Cells - 1.0);
    ivec2 first = ivec2(floor(cell * size / Cells));
    ivec2 last = min(ivec2(size), ivec2(ceil((cell + 1.0) * size / Cells)));
    float earliest = 1.0;
    // Minimum arrival over the logical cell footprint preserves connected thin branches
    // at both resolutions, unlike a nearest sample or averaging encoded arrival times.
    for (int y = first.y; y < last.y; y++) for (int x = first.x; x < last.x; x++) {
        vec2 encoded = texelFetch(Arrival, ivec2(x, y), 0).rg;
        earliest = min(earliest, (encoded.r * 65280.0 + encoded.g * 255.0) / 65535.0);
    }
    return earliest;
}
void main() {
    vec2 cell = floor(texCoord * Cells);
    bool frozen = Progress > 0.0 && arrival(cell) <= Progress;
    if (frozen) { fragColor = vec4(0, 0, 0, 1); return; }
    bool edge = Progress > 0.0 && (
        arrival(cell + vec2(OutlineWidth, 0)) <= Progress || arrival(cell - vec2(OutlineWidth, 0)) <= Progress ||
        arrival(cell + vec2(0, OutlineWidth)) <= Progress || arrival(cell - vec2(0, OutlineWidth)) <= Progress);
    fragColor = edge ? vec4(1) : vec4(0);
}
