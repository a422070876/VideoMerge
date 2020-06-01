package com.hm.videoedit.mediacodec;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by 海米 on 2018/9/12.
 */

public class VideoExtractor {
    private static final String VIDEO = "video/";

    private Context context;
    private MediaCodec videoDecoder = null;
    private MediaExtractor videoExtractor;
    private int trackIndex;
    private MediaFormat format = null;
    private long duration = 0;
    public VideoExtractor(Context context, String path){
        this.context = context;
        try {
            videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(path);
            for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
                format = videoExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith(VIDEO)) {
                    videoExtractor.selectTrack(i);
                    trackIndex = i;
                    duration = format.getLong(MediaFormat.KEY_DURATION)/1000;
                    break;
                }
            }
            String mime = format.getString(MediaFormat.KEY_MIME);
            videoDecoder = MediaCodec.createDecoderByType(mime);
        } catch (IOException e) {
            e.printStackTrace();
        }
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        videoDecoder.configure(format, null, null, 0);
        videoDecoder.start();
    }
    private OnEncodeListener listener;

    public void setOnEncodeListener(OnEncodeListener listener) {
        this.listener = listener;
    }
    private long frameTime;

    public long getFrameTime() {
        return frameTime;
    }

    public long getDuration() {
        return duration;
    }
    public void encoder(final long begin, final int gifWidth, final int gifHeight){
        if(begin > duration){
            throw new RuntimeException("开始时间不能大于视频时长");
        }
        stop = true;
        long endTime = duration;
        videoExtractor.seekTo(begin*1000,trackIndex);
        FastYUVtoRGB fastYUVtoRGB = new FastYUVtoRGB(context);
        int width = format.getInteger(MediaFormat.KEY_WIDTH);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        frameTime = 0;
        long ft = 0;
        while (stop){
            extractorVideoInputBuffer();
            int outIndex = videoDecoder.dequeueOutputBuffer(info, 500000);
            if(outIndex >= 0){
                long time = info.presentationTimeUs/1000;
                if(this.frameTime == 0 ){
                    if(ft == 0){
                        ft = info.presentationTimeUs;
                    }else if(info.presentationTimeUs > ft){
                        this.frameTime = info.presentationTimeUs - ft;
                    }
                }
                boolean isOver = false;
                if(time >= begin && time <= begin+200){
                    Image image = videoDecoder.getOutputImage(outIndex);
                    Bitmap bitmap = fastYUVtoRGB.convertYUVtoRGB(getDataFromImage(image),width,height);
                    if(gifWidth != -1 && gifHeight != -1){
                        bitmap = Bitmap.createScaledBitmap(bitmap,gifWidth,gifHeight,true);
                    }else{
                        bitmap = Bitmap.createScaledBitmap(bitmap,width/4,height/4,true);
                    }
                    if(listener != null){
                        listener.onBitmap((int) (time/1000),bitmap);
                    }
                    isOver = true;
                }
                videoDecoder.releaseOutputBuffer(outIndex, true /* Surface init */);
                if(isOver){
                    break;
                }
                if(time >= endTime){
                    break;
                }
            }
        }
    }
    public void release(){
        videoDecoder.stop();
        videoDecoder.release();
        videoExtractor.release();
    }
    private boolean stop = true;
    public void stop(){
        stop = false;
    }
    private void extractorVideoInputBuffer(){
        int inputIndex = videoDecoder.dequeueInputBuffer(500000);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = videoDecoder.getInputBuffer(inputIndex);
            long sampleTime = videoExtractor.getSampleTime();
            int sampleSize = videoExtractor.readSampleData(inputBuffer, 0);
            if (videoExtractor.advance()) {
                videoDecoder.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
            } else {
                if(sampleSize > 0){
                    videoDecoder.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
                    videoExtractor.seekTo(0,trackIndex);
                }else{
                    videoExtractor.seekTo(0,trackIndex);
                    sampleTime = videoExtractor.getSampleTime();
                    sampleSize = videoExtractor.readSampleData(inputBuffer, 0);
                    if (videoExtractor.advance()) {
                        videoDecoder.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
                    }
                }
            }
        }
    }
    private byte[] getDataFromImage(Image image) {
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    channelOffset = width * height + 1;
                    outputStride = 2;
                    break;
                case 2:
                    channelOffset = width * height;
                    outputStride = 2;
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();

            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }
        return data;
    }
    public interface OnEncodeListener{
        void onBitmap(int time, Bitmap bitmap);
    }
}
