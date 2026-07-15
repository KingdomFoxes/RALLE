#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

const ivec2 OFFSETS[16] = ivec2[](
    ivec2(-2, -1), ivec2(-2, 0), ivec2(-2, 1),
    ivec2(-1, -2), ivec2(-1, 0), ivec2(-1, 2),
    ivec2(0, -2), ivec2(0, -1), ivec2(0, 1), ivec2(0, 2),
    ivec2(1, -2), ivec2(1, 0), ivec2(1, 2),
    ivec2(2, -1), ivec2(2, 0), ivec2(2, 1)
);

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    float coverage = 0.0;
    for (int i = 0; i < 16; i++) {
        float sampleAlpha = texture(Sampler0, texCoord0 + vec2(OFFSETS[i]) * texel).a;
        coverage = 1.0 - (1.0 - coverage) * (1.0 - sampleAlpha);
    }

    float alpha = coverage * vertexColor.a * ColorModulator.a;
    if (alpha == 0.0) discard;
    fragColor = vec4(0.0, 0.0, 0.0, alpha);
}
