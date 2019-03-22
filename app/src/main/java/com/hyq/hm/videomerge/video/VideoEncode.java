package com.hyq.hm.videomerge.video;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.hm.videoedit.holder.VideoHolder;
import com.hyq.hm.videomerge.glsl.EGLUtils;
import com.hyq.hm.videomerge.glsl.GLFramebuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by 海米 on 2019/3/20.
 */

public class VideoEncode {

    private static final String VIDEO = "video/avc";
    private MediaCodec videoEncode;
    private MediaMuxer mediaMuxer;
    private int videoTrackIndex = -1;

    private int cropWidth = 0;
    private int cropHeight = 0;
    private long frameTime = 0;


    private Handler videoHandler;
    private HandlerThread videoThread;


    private EGLUtils eglUtils;
    private GLFramebuffer framebuffer;

    private List<VideoDecoder> decoderList = new ArrayList<>();
    public VideoEncode(List<VideoHolder> lists){
        //初始化解码器数组
        for (int i = 0; i < lists.size();i++){
            VideoDecoder videoDecoder = new VideoDecoder(lists.get(i));
            decoderList.add(videoDecoder);
        }
        //初始化数组
        videoThread = new HandlerThread("VideoMerge");
        videoThread.start();
        videoHandler = new Handler(videoThread.getLooper());
    }


    public void start(final String path){
        videoHandler.post(new Runnable() {
            @Override
            public void run() {
                encoders(path);
            }
        });
    }
    private void encoders(String path){
        //配置数据
        initEncode(path);
        //openGL
        initGLSL();
        //开始解码和编码
        encoder();
    }

    private void initEncode(String path){
        //这里遍历数据，选择最小的参数用来初始化编码器
        cropWidth = decoderList.get(0).getCropWidth();
        cropHeight = decoderList.get(0).getCropHeight();
        int frameRate = decoderList.get(0).getFrameRate();
        frameTime = decoderList.get(0).getFrameTime();
        //宽高选择方案
        //以最小宽高来显示还是根据宽高比计算最小显示分辨率
        //我这选用根据宽高比计算最小显示分辨率
        float sh = cropWidth*1.0f/cropHeight;
        if(decoderList.size() != 1) {
            for (VideoDecoder holder : decoderList) {
                float vh = holder.getCropWidth()*1.0f/holder.getCropHeight();
                if( sh < vh){
                    cropWidth = Math.min(cropWidth,holder.getCropWidth());
                    cropHeight = (int) (cropWidth*vh);
                    //宽高不能是奇数
                    if(cropHeight%2 != 0){
                        cropHeight = cropHeight - 1;
                    }
                }
                //cropWidth = Math.min(cropWidth,holder.getCropWidth());
                //cropHeight = Math.min(cropHeight,holder.getCropHeight());
                //选最小帧速率，对帧素率大的进行丢帧处理
                frameRate = Math.min(frameRate, holder.getFrameRate());
                //帧时间，帧速率越小，帧时间越大
                frameTime = Math.max(frameTime, holder.getFrameTime());
            }
        }
        //防止MediaFormat取不到帧速率
        if(frameRate < 1){
            frameRate = (int) (1000/(frameTime/1000));
        }
        //编码参数
        int BIT_RATE = cropWidth*cropHeight*2*8;
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(VIDEO, cropWidth, cropHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        //创建编码器
        try {
            videoEncode = MediaCodec.createEncoderByType(VIDEO);
        } catch (IOException e) {
            e.printStackTrace();
        }
        videoEncode.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        //创建MediaMuxer
        try {
            mediaMuxer = new MediaMuxer(path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private SurfaceTexture surfaceTexture;
    private void initGLSL(){
        //将编码器生成的Surface传给opengl来获取opengl的显示画面
        Surface surface = videoEncode.createInputSurface();
        videoEncode.start();
        //初始化opengl
        eglUtils = new EGLUtils();
        eglUtils.initEGL(surface);
        framebuffer = new GLFramebuffer();
        framebuffer.initFramebuffer(cropWidth,cropHeight);
        surfaceTexture = framebuffer.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(cropWidth,cropHeight);
    }
    private long startFrameTime = 0;
    private long timeUs = 0;
    private VideoDecoder.OnDecoderListener decoderListener = new VideoDecoder.OnDecoderListener() {
        @Override
        public void onDecoder(long presentationTimeUs, VideoHolder videoHolder) {
            //先渲染画面
            framebuffer.drawFrameBuffer();
            eglUtils.swap();
            //计算时间，这一帧画面必须裁剪时间内的画面
            if((presentationTimeUs - videoHolder.getStartTime())/1000 < startFrameTime/1000){
                return;
            }
            //开始编码
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true){
                int inputIndex = videoEncode.dequeueOutputBuffer(info, 50000);
                if (inputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    //初始化mediaMuxer的画面部分
                    //声音部分暂时没写所有直接start
                    if (videoTrackIndex == -1) {
                        MediaFormat mediaFormat = videoEncode.getOutputFormat();
                        videoTrackIndex = mediaMuxer.addTrack(mediaFormat);
                        mediaMuxer.start();
                    }
                }else if(inputIndex >= 0){
                    //获得编码数据
                    ByteBuffer encodedData = videoEncode.getOutputBuffer(inputIndex);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        info.size = 0;
                    }
                    if (info.size != 0) {
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);
                        info.presentationTimeUs = timeUs;
                        //填充数据
                        mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info);
                        Log.d("==============","timeUs = "+timeUs);
                        timeUs = timeUs+frameTime;
                        //结合presentationTimeUs - videoHolder.getStartTime() < startFrameTime
                        //对帧速率大的进行丢帧
                        startFrameTime = startFrameTime + frameTime;
                    }
                    videoEncode.releaseOutputBuffer(inputIndex, false);
                    //这一帧结束后直接跳出循环，因为是单线程
                    //所以videoEncode.dequeueOutputBuffer要等解码器解出画面后才会回调
                    break;
                }
            }

        }
    };
    private void encoder(){
        //循环解码
        for (VideoDecoder decoder:decoderList) {
            startFrameTime = 0;
            //计算裁剪的显示画面
            float f = decoder.getCropLeft()*1.0f/decoder.getVideoWidth();
            float t = 1.0f - decoder.getCropTop()*1.0f/decoder.getVideoHeight();
            float r = (decoder.getCropLeft()+decoder.getCropWidth())*1.0f/decoder.getVideoWidth();
            float b = 1.0f - (decoder.getCropTop()+decoder.getCropHeight())*1.0f/decoder.getVideoHeight();
            float[] textureVertexData = {
                    r, b,
                    f, b,
                    r, t,
                    f, t
            };
            //将要显示的画面传给opengl
            framebuffer.setVertexDat(textureVertexData);
            framebuffer.setRect(decoder.getCropWidth(),decoder.getCropHeight());
            //初始化解码器
            decoder.initDecoder(new Surface(surfaceTexture));
            //开始解码
            decoder.decoder(decoderListener);
        }
        //释放资源
        eglUtils.release();
        framebuffer.getSurfaceTexture().release();
        videoEncode.stop();
        videoEncode.release();
        mediaMuxer.stop();
        mediaMuxer.release();
        videoThread.quit();
        Log.d("==============","结束");
    }
}
