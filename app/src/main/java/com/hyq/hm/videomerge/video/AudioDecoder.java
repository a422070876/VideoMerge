package com.hyq.hm.videomerge.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.hyq.hm.lame.SimpleLame;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by 海米 on 2019/3/25.
 */

public class AudioDecoder {
    public static final String AUDIO = "audio/mp4a-latm";
    private Handler audioHandler;
    private HandlerThread audioThread;
    public AudioDecoder(){
        audioThread = new HandlerThread("AudioDecoder");
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());
    }
    private List<String> mp3s;
    private  OnAudioDecoderListener decoderListener;
    public void start(final List<AudioExtractor> audioExtractors, final OnAudioDecoderListener decoderListener){
        this.decoderListener = decoderListener;
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                synAudio(audioExtractors);
            }
        });
    }
    private MediaExtractor audioExtractor;
    private MediaCodec audioDecoder;
    public void prepare(){
        audioExtractor = new MediaExtractor();
        long duration = 0;
        try {
            audioExtractor.setDataSource(mp3s.get(0));
            for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                MediaFormat format = audioExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if(mime.startsWith(AudioExtractor.AUDIO)){
                    duration = format.getLong(MediaFormat.KEY_DURATION);
                    audioExtractor.selectTrack(i);
                    audioDecoder = MediaCodec.createDecoderByType(mime);
                    audioDecoder.configure(format, null, null, 0);
                    audioDecoder.start();
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            extractorInputBuffer(audioExtractor, audioDecoder);
            int outIndex = audioDecoder.dequeueOutputBuffer(info, 50000);
            if (outIndex >= 0) {
                ByteBuffer data = audioDecoder.getOutputBuffer(outIndex);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    info.size = 0;
                }
                if (info.size != 0) {
                    data.position(info.offset);
                    data.limit(info.offset + info.size);
                    long presentationTimeUs = info.presentationTimeUs;
                    if(decoderListener != null){
                        int progress = (int) ((presentationTimeUs*100)/duration);
                        byte[] b = new byte[data.remaining()];
                        data.get(b, 0, b.length);
                        decoderListener.onDecoder(b,info,progress, false);
                    }
                    data.clear();
                }
                audioDecoder.releaseOutputBuffer(outIndex, false);
                break;
            }
        }
        startAudioTime = duration;
    }
    private void initDecoder(){
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            extractorInputBuffer(audioExtractor, audioDecoder);
            int outIndex = audioDecoder.dequeueOutputBuffer(info, 50000);
            if (outIndex >= 0) {
                ByteBuffer data = audioDecoder.getOutputBuffer(outIndex);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    info.size = 0;
                }
                if (info.size != 0) {
                    data.position(info.offset);
                    data.limit(info.offset + info.size);
                    long presentationTimeUs = info.presentationTimeUs;
                    if(decoderListener != null){
                        int progress = (int) ((presentationTimeUs*100)/startAudioTime);
                        byte[] b = new byte[data.remaining()];
                        data.get(b, 0, b.length);
                        decoderListener.onDecoder(b,info,progress,
                                mp3s.size() == 1&&((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                        );
                    }
                    data.clear();
                }

                audioDecoder.releaseOutputBuffer(outIndex, false);
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                break;
            }
        }
        audioDecoder.stop();
        audioDecoder.release();
        audioExtractor.release();
        File f = new File(mp3s.get(0));
        if(f.exists()){
            f.delete();
        }
    }
    public void decoder(){
        audioHandler.post(new Runnable() {
            @Override
            public void run() {
                initDecoder();
                for (int i = 1;i < mp3s.size();i++){
                    decoderMP3(mp3s.get(i),i,i == (mp3s.size() - 1));
                }
                if(decoderListener != null){
                    decoderListener.onOver();
                }
            }
        });
    }
    //多段音频要参数相同，所以选最小参数
    private int bitRate;
    private int sampleRate;
    private int channelCount;
    private void synAudio(List<AudioExtractor> audioExtractors){
        bitRate = audioExtractors.get(0).getBitRate();
        sampleRate = audioExtractors.get(0).getSampleRate();
        channelCount = audioExtractors.get(0).getChannelCount();
        if(audioExtractors.size() != 1){
            for (AudioExtractor holder:audioExtractors){
                bitRate = Math.min(bitRate,holder.getBitRate());
                sampleRate = Math.min(sampleRate,holder.getSampleRate());
                channelCount = Math.min(channelCount,holder.getChannelCount());
            }
        }
        sampleRate = format(sampleRate,AudioExtractor.SampleRates);
        if(sampleRate >= AudioExtractor.SampleRates[2]){
            bitRate = format(bitRate,AudioExtractor.Mpeg1BitRates);
        }else if(sampleRate <= AudioExtractor.SampleRates[6]){
            bitRate = format(bitRate,AudioExtractor.Mpeg25BitRates);
        }else{
            bitRate = format(bitRate,AudioExtractor.Mpeg2BitRates);
        }
        //临时文件，我这里写外部储存是为了检查效果，可写成内部储存
        String pcm = Environment.getExternalStorageDirectory().getAbsolutePath()+"/HMSDK/"+System.currentTimeMillis()+".pcm";
        mp3s = new ArrayList<>();
//        long duration = 0;
        for (int i = 0;i < audioExtractors.size();i++){
            AudioExtractor holder = audioExtractors.get(i);
            //临时文件，我这里写外部储存是为了检查效果，可写成内部储存
            String mp3 = Environment.getExternalStorageDirectory().getAbsolutePath()+"/HMSDK/"+System.currentTimeMillis()+".mp3";

            //将音频解码成pcm文件
            decoderPCM(i,holder,pcm);
            //把pcm文件转成mp3
            SimpleLame.convert(this,i,pcm,mp3
                    ,holder.getSampleRate(),
                    channelCount,sampleRate,bitRate,
                    1
            );
            mp3s.add(mp3);
        }
        File f = new File(pcm);
        if(f.exists()){
            f.delete();
        }
        //通过统一的参数创建MediaFormat
        MediaFormat mediaFormat = MediaFormat.createAudioFormat(AUDIO, sampleRate, channelCount);//参数对应-> mime type、采样率、声道数
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate*1000);//比特率
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 100 * 1024);
        //准备结束，回调
        if(decoderListener != null){
            decoderListener.onSynAudio(mediaFormat);
        }
    }
    private int format(int f,int[] fs){
        if(f >= fs[0]){
            return fs[0];
        }else if(f <= fs[fs.length - 1]){
            return fs[fs.length - 1];
        }else{
            for (int i = 1; i < fs.length;i++){
                if(f >= fs[i]){
                    return fs[i];
                }
            }
        }
        return -1;
    }
    private long decoderPCM(int index,AudioExtractor holder,String pcm){
        holder.initStartTime();
        long startTime = (holder.getStartTime());
        long endTime = (holder.getEndTime());
        //初始化MediaExtractor和MediaCodec
        MediaCodec audioDecoder = null;
        try {
            audioDecoder = MediaCodec.createDecoderByType(holder.getMime());
            audioDecoder.configure(holder.getFormat(), null, null, 0);
            audioDecoder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        File f = new File(pcm);
        if(f.exists()){
            f.delete();
        }
        //pcm文件
        OutputStream pcmos = null;
        try {
            pcmos = new FileOutputStream(f);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        //这段音频的时长
        long duration = endTime - startTime;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            extractorInputBuffer(holder.getAudioExtractor(), audioDecoder);
            int outIndex = audioDecoder.dequeueOutputBuffer(info, 50000);
            if (outIndex >= 0) {
                ByteBuffer data = audioDecoder.getOutputBuffer(outIndex);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    info.size = 0;
                }
                if (info.size != 0) {
                    //判断解码出来的数据是否在截取的范围内
                    if(info.presentationTimeUs >= startTime && info.presentationTimeUs <= endTime){
                        byte[] bytes = new byte[data.remaining()];
                        data.get(bytes,0,bytes.length);
                        data.clear();
                        //写入pcm文件
                        try {
                            pcmos.write(bytes);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        //进度条
                        int progress = (int) (((info.presentationTimeUs - startTime)*50)/duration);
                        if(decoderListener != null){
                            decoderListener.onSynAudioProgress(index,progress);
                        }
                    }
                }
                audioDecoder.releaseOutputBuffer(outIndex, false);
                //超过截取时间结束解码
                if(info.presentationTimeUs >= endTime){
                    break;
                }
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                break;
            }
        }
        try {
            pcmos.flush();
            pcmos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        audioDecoder.stop();
        audioDecoder.release();
        return duration;
    }
    private long startAudioTime = 0;
    /**
     * mp3转pcm
     */
    private void decoderMP3(String mp3,int index,boolean isEnd){
        MediaExtractor audioExtractor = new MediaExtractor();
        MediaCodec audioDecoder = null;
        long duration = 0;
        try {
            audioExtractor.setDataSource(mp3);
            for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                MediaFormat format = audioExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if(mime.startsWith(AudioExtractor.AUDIO)){
                    duration = format.getLong(MediaFormat.KEY_DURATION);
                    audioExtractor.selectTrack(i);
                    audioDecoder = MediaCodec.createDecoderByType(mime);
                    audioDecoder.configure(format, null, null, 0);
                    audioDecoder.start();
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            extractorInputBuffer(audioExtractor, audioDecoder);
            int outIndex = audioDecoder.dequeueOutputBuffer(info, 50000);
            if (outIndex >= 0) {
                ByteBuffer data = audioDecoder.getOutputBuffer(outIndex);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    info.size = 0;
                }
                if (info.size != 0) {
                    data.position(info.offset);
                    data.limit(info.offset + info.size);
                    long presentationTimeUs = info.presentationTimeUs;
                    info.presentationTimeUs = startAudioTime + presentationTimeUs;
                    if(decoderListener != null){
                        int progress = (int) ((presentationTimeUs*100)/duration);
                        byte[] b = new byte[data.remaining()];
                        data.get(b, 0, b.length);
                        decoderListener.onDecoder(b,info,progress,
                                isEnd&&((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                                );
                    }
                    data.clear();
                }
                audioDecoder.releaseOutputBuffer(outIndex, false);
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                break;
            }
        }
        audioDecoder.stop();
        audioDecoder.release();
        audioExtractor.release();
        startAudioTime = startAudioTime + duration;
        File f = new File(mp3);
        if(f.exists()){
            f.delete();
        }
    }

    private void extractorInputBuffer(MediaExtractor mediaExtractor, MediaCodec mediaCodec) {
        int inputIndex = mediaCodec.dequeueInputBuffer(50000);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputIndex);
            long sampleTime = mediaExtractor.getSampleTime();
            int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);
            if (mediaExtractor.advance()) {
                mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
            } else {
                if (sampleSize > 0) {
                    mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    mediaCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            }
        }
    }
    public void setProgress(long size,long total,int index){
        int progress = 50 + (int) ((total*50)/size);
        if(decoderListener != null){
            decoderListener.onSynAudioProgress(index,progress);
        }
    }
    public void release(){
        audioThread.quit();
    }
    public interface OnAudioDecoderListener{
        void onSynAudio(MediaFormat mediaFormat);
        void onSynAudioProgress(int index,int progress);
        void onDecoder(byte[] bytes,MediaCodec.BufferInfo info,int progress,boolean isEnd);
        void onOver();
    }
}
