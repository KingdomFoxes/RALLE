#version 330
uniform sampler2D InSampler;
in vec2 texCoord;
out vec4 fragColor;

float luma(vec2 uv) {
    vec3 rgb = texture(InSampler, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
    return dot(rgb, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    float center = luma(texCoord);
    vec2 stepUv = 6.0 / vec2(textureSize(InSampler, 0));
    float localAvg = (
        luma(texCoord + vec2(stepUv.x, 0.0)) +
        luma(texCoord - vec2(stepUv.x, 0.0)) +
        luma(texCoord + vec2(0.0, stepUv.y)) +
        luma(texCoord - vec2(0.0, stepUv.y)) +
        luma(texCoord + stepUv) +
        luma(texCoord - stepUv) +
        luma(texCoord + vec2(stepUv.x, -stepUv.y)) +
        luma(texCoord + vec2(-stepUv.x, stepUv.y))
    ) * 0.125;

    // Crush into stark, absurd full-black / full-white while keeping silhouettes legible in dark or bright scenes.
    float boosted = center + (center - localAvg) * 0.65;
    float bw = smoothstep(0.36, 0.39, boosted);
    fragColor = vec4(vec3(bw), 1.0);
}
