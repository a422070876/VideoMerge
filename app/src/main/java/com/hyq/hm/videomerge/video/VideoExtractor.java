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

public class VideoExtractor {
    private static final String VIDEO = "video/";

    private MediaExtractor videoExtractor;
    private MediaCodec videoDecoder;
    private long duration;
    private MediaFormat format;
    private int trackIndex;

    private VideoHolder videoHolder;
    public VideoExtractor(VideoHolder videoHolder){
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


    public MediaExtractor getVideoExtractor() {
        return videoExtractor;
    }

    public MediaFormat getFormat() {
        return format;
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

    public VideoHolder getVideoHolder() {
        return videoHolder;
    }

    public long getDuration() {
        return videoHolder.getEndTime() - videoHolder.getStartTime();
    }

    public void initStartTime(){
        videoExtractor.seekTo(videoHolder.getStartTime(),trackIndex);
    }

}
