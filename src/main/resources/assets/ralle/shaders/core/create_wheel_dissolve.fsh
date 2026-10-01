#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

ivec4 contributorsAt(ivec2 logicalPixel) {
    ivec2 tile = logicalPixel / 8;
    int index = tile.y * 512 + tile.x;
    int indexStart = textureSize(Sampler1, 0).y - 128;
    ivec3 encoded = ivec3(round(texelFetch(Sampler1,
            ivec2(index % 2048, indexStart + index / 2048), 0).rgb * 255.0));
    int id = encoded.r + encoded.g * 256 + encoded.b * 65536;
    ivec2 packedPixel = ivec2(id % 256, id / 256) * 8 + logicalPixel % 8;
    return ivec4(round(texelFetch(Sampler1, packedPixel, 0) * 255.0));
}

void main() {
    // Vertex red selects a premade frame; green is capture pixel density.
    int frame = int(round(vertexColor.r * 255.0));
    float density = max(1.0, round(vertexColor.g * 255.0));
    vec2 sourceSize = vec2(textureSize(Sampler0, 0));
    vec2 logicalPixel = floor(vec2(texCoord0.x, 1.0 - texCoord0.y) * sourceSize / density)
            - sourceSize / (2.0 * density);
    vec2 atlasPixel = vec2(frame % 8, frame / 8) * 256.0 + mod(logicalPixel + 128.0, 256.0) + 0.5;
    vec4 accumulated = vec4(0.0);
    // Each page stores two source pixels. Composite collisions in the old raster's paint order.
    for (int page = 0; page < 4; page++) {
        vec2 pageOffset = vec2(page % 2, page / 2) * 2048.0;
        ivec4 contributors = contributorsAt(ivec2(atlasPixel + pageOffset));
        for (int pair = 0; pair < 2; pair++) {
            int code = contributors[pair * 2];
            if (code == 0) continue;
            float opacity = float(contributors[pair * 2 + 1]) / 255.0;
            int displacement = code - 1;
            vec2 uv = texCoord0 - vec2(displacement % 7, displacement / 7) * density / sourceSize;
            if (any(lessThan(uv, vec2(0.0))) || any(greaterThanEqual(uv, vec2(1.0)))) continue;
            vec4 source = texture(Sampler0, uv) * opacity;
            accumulated = source + accumulated * (1.0 - source.a);
        }
    }
    if (accumulated.a <= 0.0) discard;
    // Captures and the accumulation are premultiplied; the GUI blend expects straight alpha.
    fragColor = vec4(accumulated.rgb / accumulated.a, accumulated.a);
}
