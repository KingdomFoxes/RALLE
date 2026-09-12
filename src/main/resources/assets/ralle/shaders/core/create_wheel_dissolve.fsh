#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    // Vertex red selects a premade frame; green is capture pixel density.
    int frame = int(round(vertexColor.r * 255.0));
    float density = max(1.0, round(vertexColor.g * 255.0));
    vec2 logicalPixel = floor(vec2(texCoord0.x, 1.0 - texCoord0.y) * vec2(textureSize(Sampler0, 0)) / density);
    vec2 tilePixel = mod(logicalPixel, 128.0);
    vec2 atlasPixel = vec2(frame % 8, frame / 8) * 128.0 + tilePixel + 0.5;
    float opacity = texture(Sampler1, atlasPixel / 1024.0).a;
    vec4 source = texture(Sampler0, texCoord0);
    if (source.a * opacity <= 0.0) discard;
    // The offscreen GUI pass produces premultiplied RGB; GUI_TEXTURED uses straight alpha.
    fragColor = vec4(source.rgb / max(source.a, 0.0001), source.a * opacity);
}
