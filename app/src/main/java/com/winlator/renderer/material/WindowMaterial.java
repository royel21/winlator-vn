package com.winlator.renderer.material;

public class WindowMaterial extends ShaderMaterial {
    public final Uniforms uniforms = new Uniforms();

    public static class Uniforms {
        public final Uniform xform = new Uniform("xform");
        public final Uniform viewSize = new Uniform("viewSize");
        public final Uniform texture = new Uniform("texture");
        public final Uniform noAlpha = new Uniform("noAlpha");
        public final Uniform flipY = new Uniform("flipY");
        public final Uniform uvRect = new Uniform("uvRect");
    }

    @Override
    protected String getVertexShader() {
        return String.join("\n",
            "uniform float xform[6];",
            "uniform vec2 viewSize;",
            "uniform bool flipY;",
            "uniform vec4 uvRect;",

            "in vec2 position;",
            "out vec2 vUV;",

            "void main() {",
                "vec2 uv = uvRect.xy + position * uvRect.zw;",
                "vUV = vec2(uv.x, flipY ? (1.0 - uv.y) : uv.y);",
                "vec2 transformedPos = applyXForm(position, xform);",
                "gl_Position = vec4(2.0 * transformedPos.x / viewSize.x - 1.0, 1.0 - 2.0 * transformedPos.y / viewSize.y, 0.0, 1.0);",
            "}"
        );
    }

    @Override
    protected String getFragmentShader() {
        return String.join("\n",
            "precision mediump float;",

            "uniform sampler2D windowTexture;",
            "uniform float noAlpha;",
            "in vec2 vUV;",

            "layout(location = 0) out vec4 outFragColor;",

            "void main() {",
                "vec4 texelColor = texture(windowTexture, vUV);",
                "outFragColor = vec4(texelColor.rgb, max(texelColor.a, noAlpha));",
            "}"
        );
    }
}
