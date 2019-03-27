package com.hyq.hm.lame;


/**
 * Created by clam314 on 2017/3/26
 */

public class SimpleLame {
    static {
        System.loadLibrary("simple_lame");
    }
    /**
     * pcm文件转换mp3函数
     */
    public static native void convert(Object listener,int index, String jwav, String jmp3,
                                      int inSampleRate, int outChannel, int outSampleRate, int outBitrate,
                                      int quality);
}
