attribute vec4 position;
attribute vec2 texcoord;
varying vec2 tx;
void main() {
    tx = texcoord;
    gl_Position = position;
}