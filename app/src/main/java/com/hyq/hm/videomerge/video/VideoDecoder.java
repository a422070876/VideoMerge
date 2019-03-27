package com.hyq.hm.videomerge.video;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
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
 * Created by 海米 on 2019/3/25.
 */

public class VideoDecoder {
    public static final String VIDEO = "video/avc";
    private int cropWidth = 0;
    private int cropHeight = 0;
    private long frameTime = 0;


    private Handler videoHandler;
    private HandlerThread videoThread;


    private EGLUtils eglUtils;
    private GLFramebuffer framebuffer;

    public VideoDecoder(){
        videoThread = new HandlerThread("VideoDecoder");
        videoThread.start();
        videoHandler = new Handler(videoThread.getLooper());
    }
    private OnVideoDecoderListener decoderListener;
    public void start(final List<VideoExtractor> lists, OnVideoDecoderListener decoderListener){
        this.decoderListener = decoderListener;
        //初始化数组
        videoHandler.post(new Runnable() {
            @Override
            public void run() {
//                encoders(path);
                timeUs = 0;
                synVideo(lists);
            }
        });
    }
    private long duration = 0;
    private void synVideo(List<VideoExtractor> lists){
        cropWidth = lists.get(0).getCropWidth();
        cropHeight = lists.get(0).getCropHeight();
        int frameRate = lists.get(0).getFrameRate();
        frameTime = lists.get(0).getFrameTime();
        duration = 0;
        //宽高选择方案
        //以最小宽高来显示还是根据宽高比计算最小显示分辨率
        //我这选用根据宽高比计算最小显示分辨率
        float sh = cropWidth*1.0f/cropHeight;
        if(lists.size() != 1) {
            for (VideoExtractor holder : lists) {
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
                duration = duration + holder.getDuration();
            }
        }else{
            duration = lists.get(0).getDuration();
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
        if(decoderListener != null){
            decoderListener.onSynVideo(mediaFormat);
        }
    }
    private SurfaceTexture surfaceTexture;
    public void initGLSL(Surface surface){
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
    private MediaCodec videoDecoder;
    public void prepare(List<VideoExtractor> lists){
        VideoExtractor extractor = lists.get(0);
        startFrameTime = 0;
        //计算裁剪的显示画面
        float f = extractor.getCropLeft()*1.0f/extractor.getVideoWidth();
        float t = 1.0f - extractor.getCropTop()*1.0f/extractor.getVideoHeight();
        float r = (extractor.getCropLeft()+extractor.getCropWidth())*1.0f/extractor.getVideoWidth();
        float b = 1.0f - (extractor.getCropTop()+extractor.getCropHeight())*1.0f/extractor.getVideoHeight();
        float[] textureVertexData = {
                r, b,
                f, b,
                r, t,
                f, t
        };
        //将要显示的画面传给opengl
        framebuffer.setVertexDat(textureVertexData);
        framebuffer.setRect(extractor.getCropWidth(),extractor.getCropHeight());

        VideoHolder videoHolder = extractor.getVideoHolder();
        //初始化解码器
        try {
            videoDecoder = MediaCodec.createDecoderByType(extractor.getMime());
        } catch (IOException e) {
            e.printStackTrace();
        }
        //OpenGL生成的surfaceTexture可以接收解码器解出来的画面
        //在视频硬解码和编码中，把编码器生成的Surface直接传给解码器，而我是在中间加了个opengl
        //把编码器生成的Surface传给opengl，再用opengl生成个Surface传给解码器，这样就可以用opengl处理视频画面了
        videoDecoder.configure(extractor.getFormat(), new Surface(surfaceTexture), null, 0);
        videoDecoder.start();
        //解码
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int run = extractorVideoInputBuffer(extractor.getVideoExtractor(),videoDecoder);
            if(run == 1){
                int outIndex = videoDecoder.dequeueOutputBuffer(info, 50000);
                if(outIndex >= 0){
                    videoDecoder.releaseOutputBuffer(outIndex, true);
                    //根据时间进行解码
                    if(info.presentationTimeUs >= videoHolder.getStartTime() &&
                            info.presentationTimeUs <= videoHolder.getEndTime()){
                        //只要第一帧，用于初始化mediaMuxer
                        break;
                    }
                }
            }else if(run == -1){
                break;
            }
        }
        //渲染，这样编码器就可以接收到一帧画面了
        framebuffer.drawFrameBuffer();
        eglUtils.swap();
        //解码一帧完成后回调
        if(decoderListener != null){
            decoderListener.onPrepare();
        }

    }

    public void decoder(final List<VideoExtractor> lists){
        videoHandler.post(new Runnable() {
            @Override
            public void run() {
                initDecoder(lists);
                for (int i = 1; i < lists.size();i++){
                    decoderVideo(lists.get(i));
                }
                //释放资源
                surfaceTexture.release();
                eglUtils.release();
                if(decoderListener != null){
                    decoderListener.onOver();
                }
            }
        });
    }
    private void initDecoder(List<VideoExtractor> lists){
        VideoExtractor extractor = lists.get(0);
        VideoHolder videoHolder = extractor.getVideoHolder();
        //编码第一帧
        if(decoderListener != null){
            decoderListener.onDecoder(timeUs,videoHolder,0);
        }
        timeUs = timeUs+frameTime;
        startFrameTime = startFrameTime + frameTime;
        //开始解码
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int run = extractorVideoInputBuffer(extractor.getVideoExtractor(),videoDecoder);
            if(run == 1){
                int outIndex = videoDecoder.dequeueOutputBuffer(info, 50000);
                if(outIndex >= 0){
                    //根据时间进行解码
                    if(info.presentationTimeUs >= videoHolder.getStartTime() &&
                            info.presentationTimeUs <= videoHolder.getEndTime()){
                        if((info.presentationTimeUs - videoHolder.getStartTime())/1000 >= startFrameTime/1000){
                            framebuffer.drawFrameBuffer();
                            eglUtils.swap();
                            int progress = (int) ((timeUs*100)/duration);
                            if(decoderListener != null){
                                decoderListener.onDecoder(timeUs,videoHolder,progress);
                            }
                            timeUs = timeUs+frameTime;
                            startFrameTime = startFrameTime + frameTime;
                        }
                    }
                    videoDecoder.releaseOutputBuffer(outIndex, true);
                    //判断是否裁剪内部分解码完成
                    if(info.presentationTimeUs >= videoHolder.getEndTime()){
                        break;
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }else if(run == -1){
                break;
            }
        }
        //释放资源
        videoDecoder.stop();
        videoDecoder.release();
    }
    private void decoderVideo(VideoExtractor extractor){
        startFrameTime = 0;
        //计算裁剪的显示画面
        float f = extractor.getCropLeft()*1.0f/extractor.getVideoWidth();
        float t = 1.0f - extractor.getCropTop()*1.0f/extractor.getVideoHeight();
        float r = (extractor.getCropLeft()+extractor.getCropWidth())*1.0f/extractor.getVideoWidth();
        float b = 1.0f - (extractor.getCropTop()+extractor.getCropHeight())*1.0f/extractor.getVideoHeight();
        float[] textureVertexData = {
                r, b,
                f, b,
                r, t,
                f, t
        };
        //将要显示的画面传给opengl
        framebuffer.setVertexDat(textureVertexData);
        framebuffer.setRect(extractor.getCropWidth(),extractor.getCropHeight());

        extractor.initStartTime();
        VideoHolder videoHolder = extractor.getVideoHolder();
        MediaCodec videoDecoder = null;
        try {
            videoDecoder = MediaCodec.createDecoderByType(extractor.getMime());
        } catch (IOException e) {
            e.printStackTrace();
        }
        videoDecoder.configure(extractor.getFormat(), new Surface(surfaceTexture), null, 0);
        videoDecoder.start();
        //解码
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int run = extractorVideoInputBuffer(extractor.getVideoExtractor(),videoDecoder);
            if(run == 1){
                int outIndex = videoDecoder.dequeueOutputBuffer(info, 50000);
                if(outIndex >= 0){
                    //根据时间进行解码
                    if(info.presentationTimeUs >= videoHolder.getStartTime() &&
                            info.presentationTimeUs <= videoHolder.getEndTime()){
                        if((info.presentationTimeUs - videoHolder.getStartTime())/1000 >= startFrameTime/1000){
                            framebuffer.drawFrameBuffer();
                            eglUtils.swap();
                            int progress = (int) ((timeUs*100)/duration);
                            if(decoderListener != null){
                                decoderListener.onDecoder(timeUs,videoHolder,progress);
                            }
                            timeUs = timeUs+frameTime;
                            startFrameTime = startFrameTime + frameTime;
                        }
                    }
                    videoDecoder.releaseOutputBuffer(outIndex, true);
                    //判断是否裁剪内部分解码完成
                    if(info.presentationTimeUs >= videoHolder.getEndTime()){
                        break;
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }else if(run == -1){
                break;
            }else{
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        //释放资源
        videoDecoder.stop();
        videoDecoder.release();
    }

    private int extractorVideoInputBuffer(MediaExtractor mediaExtractor, MediaCodec mediaCodec){
        int inputIndex = mediaCodec.dequeueInputBuffer(50000);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                inputBuffer = mediaCodec.getInputBuffer(inputIndex);
            }else{
                inputBuffer = mediaCodec.getInputBuffers()[inputIndex];
            }
            long sampleTime = mediaExtractor.getSampleTime();
            int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);
            if (mediaExtractor.advance()) {
                mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
                return 1;
            } else {
                if(sampleSize > 0){
                    mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
                    return 1;
                }else{
                    return -1;
                }
            }
        }
        return 0;
    }
    public void release(){
        videoThread.quit();
    }
    public interface OnVideoDecoderListener{
        void onSynVideo(MediaFormat mediaFormat);
        void onPrepare();
        void onDecoder(long presentationTimeUs, VideoHolder videoHolder,int progress);
        void onOver();
    }
}
