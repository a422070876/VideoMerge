package com.hm.videoedit.holder;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by 海米 on 2019/3/14.
 */

public class VideoHolder implements Parcelable {
    //视频路径
    private String videoFile;
    //裁剪的坐标长宽
    private int cropLeft;
    private int cropTop;
    private int cropWidth;
    private int cropHeight;
    //开始结束时间
    private long startTime;
    private long endTime;
    //帧时间
    private long frameTime;

    public VideoHolder(){
    }

    protected VideoHolder(Parcel in){
        videoFile = in.readString();
        cropLeft = in.readInt();
        cropTop = in.readInt();
        cropWidth = in.readInt();
        cropHeight = in.readInt();
        startTime = in.readLong();
        endTime = in.readLong();
        frameTime = in.readLong();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(videoFile);
        dest.writeInt(cropLeft);
        dest.writeInt(cropTop);
        dest.writeInt(cropWidth);
        dest.writeInt(cropHeight);
        dest.writeLong(startTime);
        dest.writeLong(endTime);
        dest.writeLong(frameTime);
    }

    public static final Creator<VideoHolder> CREATOR = new Creator<VideoHolder>() {
        @Override
        public VideoHolder createFromParcel(Parcel in) {
            return new VideoHolder(in);
        }
        @Override
        public VideoHolder[] newArray(int size) {
            return new VideoHolder[size];
        }
    };

    public String getVideoFile() {
        return videoFile;
    }

    public void setVideoFile(String videoFile) {
        this.videoFile = videoFile;
    }

    public int getCropLeft() {
        return cropLeft;
    }

    public void setCropLeft(int cropLeft) {
        this.cropLeft = cropLeft;
    }

    public int getCropTop() {
        return cropTop;
    }

    public void setCropTop(int cropTop) {
        this.cropTop = cropTop;
    }

    public int getCropWidth() {
        return cropWidth;
    }

    public void setCropWidth(int cropWidth) {
        this.cropWidth = cropWidth;
    }

    public int getCropHeight() {
        return cropHeight;
    }

    public void setCropHeight(int cropHeight) {
        this.cropHeight = cropHeight;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getFrameTime() {
        return frameTime;
    }

    public void setFrameTime(long frameTime) {
        this.frameTime = frameTime;
    }
}
