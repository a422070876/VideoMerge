package com.hyq.hm.videomerge.video;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.hm.videoedit.holder.VideoHolder;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by 海米 on 2017/8/15.
 */

public class VideoDecoder {
    private static final String VIDEO = "video/";

    private MediaExtractor videoExtractor;
    private MediaCodec videoDecoder;
    private long duration;
    private MediaFormat format;
    private int trackIndex;

    private VideoHolder videoHolder;
    public VideoDecoder(VideoHolder videoHolder){
        this.videoHolder = videoHolder;
        //初始化解码器
        try {
            videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(videoHolder.getVideoFile());
            for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
                format = videoExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith(VIDEO)) {
                    duration = format.getLong(MediaFormat.KEY_DURATION);
                    videoExtractor.selectTrack(i);
                    trackIndex = i;
                    if(videoHolder.getStartTime() != 0){
                        videoExtractor.seekTo(videoHolder.getStartTime(),trackIndex);
                    }
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void initDecoder(Surface surface){
        //创建解码器
        try {
            videoDecoder = MediaCodec.createDecoderByType(getMime());
            videoDecoder.configure(format, surface, null, 0);
            videoDecoder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void decoder(OnDecoderListener decoderListener){
        //解码
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int run = extractorVideoInputBuffer(videoExtractor,videoDecoder);
            if(run == 1){
                int outIndex = videoDecoder.dequeueOutputBuffer(info, 50000);
                if(outIndex >= 0){
                    if(info.presentationTimeUs >= videoHolder.getStartTime() &&
                            info.presentationTimeUs <= videoHolder.getEndTime()){
                        //解码一帧完成后回调
                        if(decoderListener != null){
                            decoderListener.onDecoder(info.presentationTimeUs,videoHolder);
                        }
                    }
                    videoDecoder.releaseOutputBuffer(outIndex, true);
                    //判断是否裁剪内部分解码完成
                    if(info.presentationTimeUs >= videoHolder.getEndTime()){
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
        videoExtractor.release();
    }

    public int getVideoWidth(){
        return format.getInteger(MediaFormat.KEY_WIDTH);
    }
    public int getVideoHeight(){
        return format.getInteger(MediaFormat.KEY_HEIGHT);
    }
    public String getMime(){
        return format.getString(MediaFormat.KEY_MIME);
    }
    public int getFrameRate(){
        return format.getInteger(MediaFormat.KEY_FRAME_RATE);
    }
    public long getFrameTime(){
        return videoHolder.getFrameTime();
    }
    public int getCropWidth() {
        return videoHolder.getCropWidth();
    }


    public int getCropHeight() {
        return videoHolder.getCropHeight();
    }

    public int getCropLeft(){
        return videoHolder.getCropLeft();
    }
    public int getCropTop(){
        return videoHolder.getCropTop();
    }
    //解码
    private int extractorVideoInputBuffer(MediaExtractor mediaExtractor,MediaCodec mediaCodec){
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

    public interface OnDecoderListener{
        void onDecoder(long presentationTimeUs,VideoHolder videoHolder);
    }

}
