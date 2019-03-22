#extension GL_OES_EGL_image_external : require
uniform samplerExternalOES tex_v;
uniform highp mat4 st_matrix;
varying highp vec2 tx;
void main() {
    highp vec2 tx_transformed = (st_matrix * vec4(tx, 0, 1.0)).xy;
    highp vec4 video = texture2D(tex_v, tx_transformed);
    gl_FragColor = video;
}