package com.hyq.hm.videomerge.video;

import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.hm.videoedit.holder.VideoHolder;

import java.io.IOException;

/**
 * Created by 海米 on 2019/3/25.
 */

public class AudioExtractor {
    public static final String AUDIO = "audio/";
    public static int[] SampleRates = {48000,44100,32000,24000,22050,16000,12000,11025,8000};
    public static int[] Mpeg1BitRates = {320,256,224,192,160,128,112,96,80,64,56,48,40,32};
    public static int[] Mpeg2BitRates = {160,144,128,112,96,80,64,56,48,40,32,24,16,8};
    public static int[] Mpeg25BitRates = {64,56,48,40,32,24,16,8};

    private int trackIndex;
    private VideoHolder videoHolder;

    private MediaExtractor audioExtractor;

    private MediaFormat format;

    private long duration;

    public AudioExtractor(VideoHolder videoHolder){
        this.videoHolder = videoHolder;
        audioExtractor = new MediaExtractor();
        try {
            audioExtractor = new MediaExtractor();
            audioExtractor.setDataSource(videoHolder.getVideoFile());
            for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                format = audioExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith(AUDIO)) {
                    duration = format.getLong(MediaFormat.KEY_DURATION);
                    audioExtractor.selectTrack(i);
                    trackIndex = i;
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public int getBitRate(){
        //有时候取不到数，也不知道咋整
        return format.getInteger(MediaFormat.KEY_BIT_RATE)/1000;
    }
    public int getSampleRate(){
        return format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
    }
    public int getChannelCount(){
        return format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
    }
    public long getStartTime() {
        return videoHolder.getStartTime();
    }

    public long getEndTime() {
        return videoHolder.getEndTime();
    }

    public MediaFormat getFormat() {
        return format;
    }
    public String getMime(){
        return format.getString(MediaFormat.KEY_MIME);
    }

    public MediaExtractor getAudioExtractor() {

        return audioExtractor;
    }
    public void initStartTime(){
        audioExtractor.seekTo(videoHolder.getStartTime(),trackIndex);
    }
}
