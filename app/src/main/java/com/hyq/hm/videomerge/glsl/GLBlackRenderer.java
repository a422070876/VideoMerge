package com.hyq.hm.videomerge.glsl;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by 海米 on 2017/8/16.
 */

public class GLBlackRenderer {
    private int programId = -1;
    private int aPositionHandle;
    private int uTextureSamplerHandle;
    private int aTextureCoordHandle;


    private int[] bos = new int[2];
    private int[] textures = new int[1];

    public void initShader(){
        String fragmentShader =
//                "varying highp vec2 vTexCoord;\n" +
//                "uniform sampler2D sTexture;\n" +
                "void main() {\n" +
//                "    highp vec4 video = texture2D(sTexture , vec2(vTexCoord.x,1.0 - vTexCoord.y));\n"+
//                "    if(video.a < 0.1)video = vec4(0.2,0.2,0.2,1.0);\n"+
                "    gl_FragColor = vec4(0.0,0.0,0.0,1.0);\n" +
                "}";
        String vertexShader =
                "attribute vec4 aPosition;\n" +
//                "attribute vec2 aTexCoord;\n" +
//                "varying vec2 vTexCoord;\n" +
                "void main() {\n" +
//                "  vTexCoord = aTexCoord;\n" +
                "  gl_Position = aPosition;\n" +
                "}";
        programId = ShaderUtils.createProgram(vertexShader, fragmentShader);
        aPositionHandle = GLES20.glGetAttribLocation(programId, "aPosition");
//        uTextureSamplerHandle = GLES20.glGetUniformLocation(programId, "sTexture");
//        aTextureCoordHandle = GLES20.glGetAttribLocation(programId, "aTexCoord");

        float[] vertexData = {
                1f, -1f, 0f,
                -1f, -1f, 0f,
                1f, 1f, 0f,
                -1f, 1f, 0f
        };


        float[] textureVertexData = {
                1f, 0f,
                0f, 0f,
                1f, 1f,
                0f, 1f
        };
        FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(vertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData);
        vertexBuffer.position(0);

        FloatBuffer textureVertexBuffer = ByteBuffer.allocateDirect(textureVertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(textureVertexData);
        textureVertexBuffer.position(0);

        GLES20.glGenBuffers(2,bos,0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,bos[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexData.length* 4,vertexBuffer, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,bos[1]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, textureVertexData.length*4,textureVertexBuffer, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0);

        GLES20.glGenTextures(textures.length,textures,0);
        for (int texture : textures) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }
    public void drawFrame(){
        GLES20.glUseProgram(programId);
//        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
//        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap,0);
//        GLES20.glUniform1i(uTextureSamplerHandle, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,bos[0]);
        GLES20.glEnableVertexAttribArray(aPositionHandle);
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
                0, 0);
//        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,bos[1]);
//        GLES20.glEnableVertexAttribArray(aTextureCoordHandle);
//        GLES20.glVertexAttribPointer(aTextureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }
    public void release(){
        GLES20.glDeleteProgram(programId);
        GLES20.glDeleteTextures(textures.length,textures,0);
        GLES20.glDeleteBuffers(bos.length,bos,0);
    }
}
