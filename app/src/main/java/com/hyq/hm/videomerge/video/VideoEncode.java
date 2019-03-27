package com.hyq.hm.videomerge.video;

import android.media.MediaCodec;
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

    private MediaCodec audioEncode;
    private MediaCodec videoEncode;
    private MediaMuxer mediaMuxer;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;

    private Handler audioHandler;
    private HandlerThread audioThread;


    private AudioDecoder audioDecoder;
    private VideoDecoder videoDecoder;

    private List<VideoExtractor> videoExtractors = new ArrayList<>();
    private List<AudioExtractor> audioExtractors = new ArrayList<>();


    private final Object object = new Object();

    private OnVideoEncodeListener onVideoEncodeListener;
    public VideoEncode(List<VideoHolder> lists,OnVideoEncodeListener onVideoEncodeListener){
        this.onVideoEncodeListener = onVideoEncodeListener;
        //初始化解码器数组
        for (int i = 0; i < lists.size();i++){
            VideoExtractor videoExtractor = new VideoExtractor(lists.get(i));
            videoExtractors.add(videoExtractor);

            AudioExtractor audioExtractor = new AudioExtractor(lists.get(i));
            audioExtractors.add(audioExtractor);
        }

        audioDecoder = new AudioDecoder();
        videoDecoder = new VideoDecoder();

        audioThread = new HandlerThread("AudioEncode");
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());

    }


    public void start(final String path){
        //创建混合器
        try {
            mediaMuxer = new MediaMuxer(path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //开始音频解码线程
        audioDecoder.start(audioExtractors, new AudioDecoder.OnAudioDecoderListener() {
            @Override
            public void onSynAudio(MediaFormat mediaFormat) {
                //初始化音频编码器audioEncode
                try {
                    audioEncode = MediaCodec.createEncoderByType(AudioDecoder.AUDIO);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                audioEncode.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                audioEncode.start();
                //开启编码线程
                //音频无法做单线程处理，单线程时编码会出现丢帧现象
                encoderAudio();
                //填充第一帧数据，只有填充了数据编码器才会进入NFO_OUTPUT_FORMAT_CHANGED
                audioDecoder.prepare();
            }

            @Override
            public void onSynAudioProgress(int index,int progress) {
                if(onVideoEncodeListener != null){
                    onVideoEncodeListener.onSynAudio(index,progress);
                }
            }

            @Override
            public void onDecoder(byte[] bytes, MediaCodec.BufferInfo info, int progress,boolean isEnd) {
                encodeInputBuffer(ByteBuffer.wrap(bytes),audioEncode,info,isEnd);
            }

            @Override
            public void onOver() {

            }
        });
        //开始视频解码线程
        videoDecoder.start(videoExtractors, new VideoDecoder.OnVideoDecoderListener() {
            @Override
            public void onSynVideo(MediaFormat mediaFormat) {
                //初始化视频编码器
                try {
                    videoEncode = MediaCodec.createEncoderByType(VideoDecoder.VIDEO);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                videoEncode.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                Surface surface = videoEncode.createInputSurface();
                videoEncode.start();
                //初始化opengl，将videoEncode生成的Surface传给egl，这样就可以把opengl处理过的画面直接渲染到编码器内
                videoDecoder.initGLSL(surface);
                //开始解码第一帧
                videoDecoder.prepare(videoExtractors);
            }
            @Override
            public void onPrepare() {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (true){
                    int inputIndex = videoEncode.dequeueOutputBuffer(info, 50000);
                    if (inputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        //准备mediaMuxer，因为转换音频的时间比较长不进行编码，等音频转换完成后再开始线程
                        if (videoTrackIndex == -1) {
                            MediaFormat mediaFormat = videoEncode.getOutputFormat();
                            videoTrackIndex = mediaMuxer.addTrack(mediaFormat);
                            break;
                        }
                    }
                }
            }

            @Override
            public void onDecoder(long presentationTimeUs, VideoHolder videoHolder,int progress) {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (true){
                    int inputIndex = videoEncode.dequeueOutputBuffer(info, 50000);
                    if(inputIndex >= 0){
                        //获得编码数据
                        ByteBuffer encodedData = videoEncode.getOutputBuffer(inputIndex);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            info.size = 0;
                        }
                        if (info.size != 0) {
                            encodedData.position(info.offset);
                            encodedData.limit(info.offset + info.size);
                            info.presentationTimeUs = presentationTimeUs;
                            //填充数据
                            mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info);
                        }
                        videoEncode.releaseOutputBuffer(inputIndex, false);
                        //这一帧结束后直接跳出循环，因为是单线程
                        //所以videoEncode.dequeueOutputBuffer要等解码器解出画面后才会回调
                        break;
                    }
                }
                if(onVideoEncodeListener != null){
                    onVideoEncodeListener.onDecoder(progress);
                }
            }

            @Override
            public void onOver() {
                videoEncode.stop();
                videoEncode.release();
                synchronized (object){
                    videoEncode = null;
                    if(audioEncode == null){
                        over();
                    }
                }
            }
        });
    }
    private void over(){
        mediaMuxer.stop();
        mediaMuxer.release();
        if(onVideoEncodeListener != null){
            onVideoEncodeListener.onOver();
        }
        audioThread.quit();
        audioDecoder.release();
        videoDecoder.release();
    }
    private void encodeInputBuffer(ByteBuffer data,MediaCodec mediaCodec,MediaCodec.BufferInfo info,boolean isEnd){
        int inputIndex = mediaCodec.dequeueInputBuffer(50000);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputIndex);
            inputBuffer.clear();
            inputBuffer.put(data);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 && isEnd) {
                mediaCodec.queueInputBuffer(inputIndex, 0, data.limit(), info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }else{
                mediaCodec.queueInputBuffer(inputIndex, 0, data.limit(), info.presentationTimeUs, 0);
            }
        }
    }
    private void encoderAudio(){
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                //开始编码
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (true){
                    int inputIndex = audioEncode.dequeueOutputBuffer(info, 50000);
                    if (inputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (audioTrackIndex == -1) {
                            //混合器准备完成
                            MediaFormat mediaFormat = audioEncode.getOutputFormat();
                            audioTrackIndex = mediaMuxer.addTrack(mediaFormat);
                            //当mediaMuxer.start()后mediaMuxer才可以接收数据
                            mediaMuxer.start();
                            //开启解码线程
                            audioDecoder.decoder();
                            videoDecoder.decoder(videoExtractors);
                        }
                    }else if(inputIndex >= 0){
                        //获得编码数据
                        ByteBuffer encodedData = audioEncode.getOutputBuffer(inputIndex);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            info.size = 0;
                        }
                        if (info.size != 0) {
                            encodedData.position(info.offset);
                            encodedData.limit(info.offset + info.size);
                            //填充数据
                            mediaMuxer.writeSampleData(audioTrackIndex, encodedData, info);
                        }
                        audioEncode.releaseOutputBuffer(inputIndex, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break;
                        }
                    }
                }
                //释放
                audioEncode.stop();
                audioEncode.release();
                //结束，因为是多线程所以加个synchronized，基本音频比视频快，所以基本不是这里结束
                synchronized (object){
                    audioEncode = null;
                    if(videoEncode == null){
                        over();
                    }
                }
            }
        });
    }

    public interface OnVideoEncodeListener{
        void onSynAudio(int index,int progress);
        void onDecoder(int progress);
        void onOver();
    }
}
