/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hm.videoedit.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;


import com.hm.videoedit.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * WaveformView is an Android view that displays a visual representation
 * of an audio waveform.  It retrieves the frame gains from a CheapSoundFile
 * object and recomputes the shape contour at several zoom levels.
 *
 * This class doesn't handle selection or any of the touch interactions
 * directly, so it exposes a listener interface.  The class that embeds
 * this view should add itself as a listener and make the view scroll
 * and respond to other events appropriately.
 *
 * WaveformView doesn't actually handle selection, but it will just display
 * the selected part of the waveform in a different color.
 */
public class WaveformView extends View {
    public interface WaveformListener {
        public void waveformTouchStart(float x);
        public void waveformTouchMove(float x);
        public void waveformTouchEnd();
        public void waveformFling(float x);
        public void waveformDraw();
        public void waveformZoomIn();
        public void waveformZoomOut();
    };

    // Colors
    private Paint mGridPaint;
    private Paint mSelectedLinePaint;
    private Paint mUnselectedLinePaint;
    private Paint mUnselectedBkgndLinePaint;
    private Paint mBorderLinePaint;
    private Paint mPlaybackLinePaint;
    private Paint mTimecodePaint;

    private int[] mLenByZoomLevel;
    private double[][] mValuesByZoomLevel;
    private double[] mZoomFactorByZoomLevel;
    private int[] mHeightsAtThisZoomLevel;
    private int mZoomLevel;
    private int mNumZoomLevels;
    private int mSampleRate = 100000;
    private int mSamplesPerFrame = 1000;
    private int mOffset;
    private int mSelectionStart;
    private int mSelectionEnd;
    private int mPlaybackPos;
    private float mDensity;
    private float mInitialScaleSpan;
    private WaveformListener mListener;
    private GestureDetector mGestureDetector;
    private ScaleGestureDetector mScaleGestureDetector;
    private SparseArray<Bitmap> bitmaps;
    private boolean mInitialized;

    private long duration = 0;
    private static int mZoomLevels[] = new int[]{1,5,10,30,60};
    private static float mZooms[] = new float[]{10,2,2.5f,2};
    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // We don't want keys, the markers get these
        setFocusable(false);

        Resources res = getResources();
        mGridPaint = new Paint();
        mGridPaint.setAntiAlias(false);
        mGridPaint.setColor(res.getColor(R.color.grid_line));
        mSelectedLinePaint = new Paint();
        mSelectedLinePaint.setAntiAlias(false);
        mSelectedLinePaint.setColor(res.getColor(R.color.waveform_selected));
        mUnselectedLinePaint = new Paint();
        mUnselectedLinePaint.setAntiAlias(false);
        mUnselectedLinePaint.setColor(res.getColor(R.color.waveform_unselected));
        mUnselectedBkgndLinePaint = new Paint();
        mUnselectedBkgndLinePaint.setAntiAlias(false);
        mUnselectedBkgndLinePaint.setColor(res.getColor(R.color.waveform_unselected_bkgnd_overlay));
        mBorderLinePaint = new Paint();
        mBorderLinePaint.setAntiAlias(true);
        mBorderLinePaint.setStrokeWidth(1.5f);
        mBorderLinePaint.setPathEffect(new DashPathEffect(new float[] { 3.0f, 2.0f }, 0.0f));
        mBorderLinePaint.setColor(res.getColor(R.color.selection_border));
        mPlaybackLinePaint = new Paint();
        mPlaybackLinePaint.setAntiAlias(false);
        mPlaybackLinePaint.setColor(res.getColor(R.color.playback_indicator));
        mTimecodePaint = new Paint();
        mTimecodePaint.setTextSize(12);
        mTimecodePaint.setAntiAlias(true);
        mTimecodePaint.setColor(res.getColor(R.color.timecode));
        mTimecodePaint.setShadowLayer(2, 1, 1, res.getColor(R.color.timecode_shadow));

        mGestureDetector = new GestureDetector(
            context,
            new GestureDetector.SimpleOnGestureListener() {
                public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                    mListener.waveformFling(vx);
                    return true;
                }
            }
        );

        mScaleGestureDetector = new ScaleGestureDetector(
            context,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                public boolean onScaleBegin(ScaleGestureDetector d) {
                    Log.v("Ringdroid", "ScaleBegin " + d.getCurrentSpanX());
                    mInitialScaleSpan = Math.abs(d.getCurrentSpanX());
                    return true;
                }
                public boolean onScale(ScaleGestureDetector d) {
                    float scale = Math.abs(d.getCurrentSpanX());
                    Log.v("Ringdroid", "Scale " + (scale - mInitialScaleSpan));
                    if (scale - mInitialScaleSpan > 20) {
                        mListener.waveformZoomIn();
                        mInitialScaleSpan = scale;
                    }
                    if (scale - mInitialScaleSpan < -20) {
                        mListener.waveformZoomOut();
                        mInitialScaleSpan = scale;
                    }
                    return true;
                }
                public void onScaleEnd(ScaleGestureDetector d) {
                    Log.v("Ringdroid", "ScaleEnd " + d.getCurrentSpanX());
                }
            }
        );

        mLenByZoomLevel = null;
        mValuesByZoomLevel = null;
        mHeightsAtThisZoomLevel = null;
        mOffset = 0;
        mPlaybackPos = -1;
        mSelectionStart = 0;
        mSelectionEnd = 0;
        mDensity = 1.0f;
        mInitialized = false;

        bitmaps = new SparseArray<>();
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleGestureDetector.onTouchEvent(event);
        if (mGestureDetector.onTouchEvent(event)) {
            return true;
        }
        switch(event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mListener.waveformTouchStart(event.getX());
            break;
        case MotionEvent.ACTION_MOVE:
            mListener.waveformTouchMove(event.getX());
            break;
        case MotionEvent.ACTION_UP:
            mListener.waveformTouchEnd();
            break;
        }
        return true;
    }

    public boolean hasSoundFile() {
        return duration != 0;
    }


    private MediaMetadataRetriever media;
    private ExecutorService fixedThreadPool = Executors.newFixedThreadPool(3);
    public void setFileName(String fileName){
        if(media != null){
            media.release();
            media= null;
        }
        media = new MediaMetadataRetriever();
        media.setDataSource(fileName);
    }

    public void setDuration(long duration) {
        this.duration = duration;
        computeDoublesForAllZoomLevels();
        mHeightsAtThisZoomLevel = null;
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    public int getZoomLevel() {
        return mZoomLevel;
    }

    public void setZoomLevel(int zoomLevel) {
        while (mZoomLevel > zoomLevel) {
            zoomIn();
        }
        while (mZoomLevel < zoomLevel) {
            zoomOut();
        }
    }

    public boolean canZoomIn() {
        return (mZoomLevel > 0);
    }

    public void zoomIn() {
        if (canZoomIn()) {
            mZoomLevel--;
            float zoom = mZooms[mZoomLevel];
            mSelectionStart *= zoom;
            mSelectionEnd *= zoom;
            mHeightsAtThisZoomLevel = null;
            mOffset *= zoom;
            if (mOffset < 0)
                mOffset = 0;
            invalidate();
        }
    }

    public boolean canZoomOut() {
        return (mZoomLevel < mNumZoomLevels - 1);
    }

    public void zoomOut() {

        if (canZoomOut()) {

            if(mZoomLevel == 0 && duration <= 30000){
                return;
            }else if(mZoomLevel == 1 && duration <= 60000){
                return;
            }else if(mZoomLevel == 2 && duration <= 300000){
                return;
            }else if(mZoomLevel == 3 && duration <= 600000){
                return;
            }
            float zoom = mZooms[mZoomLevel];
            mZoomLevel++;
            mSelectionStart /= zoom;
            mSelectionEnd /= zoom;
            mOffset /= zoom;
            if (mOffset < 0)
                mOffset = 0;
            mHeightsAtThisZoomLevel = null;
            invalidate();
        }
    }

    public int maxPos() {
        return mLenByZoomLevel[mZoomLevel];
    }

    public int secondsToFrames(double seconds) {
        return (int)(1.0 * seconds * mSampleRate / mSamplesPerFrame + 0.5);
    }

    public int secondsToPixels(double seconds) {
        double z = mZoomFactorByZoomLevel[mZoomLevel];
        return (int)(z * seconds * mSampleRate / mSamplesPerFrame + 0.5);
    }

    public double pixelsToSeconds(int pixels) {
        double z = mZoomFactorByZoomLevel[mZoomLevel];
        return (pixels * (double)mSamplesPerFrame / (mSampleRate * z));
    }

    public int millisecsToPixels(int msecs) {
        double z = mZoomFactorByZoomLevel[mZoomLevel];
        return (int)((msecs * 1.0 * mSampleRate * z) /
                     (1000.0 * mSamplesPerFrame) + 0.5);
    }

    public int pixelsToMillisecs(int pixels) {
        double z = mZoomFactorByZoomLevel[mZoomLevel];
        return (int)(pixels * (1000.0 * mSamplesPerFrame) /
                     (mSampleRate * z) + 0.5);
    }

    public void setParameters(int start, int end, int offset) {
        mSelectionStart = start;
        mSelectionEnd = end;
        mOffset = offset;
    }

    public int getStart() {
        return mSelectionStart;
    }

    public int getEnd() {
        return mSelectionEnd;
    }

    public int getOffset() {
        return mOffset;
    }

    public void setPlayback(int pos) {
        mPlaybackPos = pos;
    }

    public int getPlaybackPos() {
        return mPlaybackPos;
    }

    public void setListener(WaveformListener listener) {
        mListener = listener;
    }

    public void recomputeHeights(float density) {
        mHeightsAtThisZoomLevel = null;
        mDensity = density;
        mTimecodePaint.setTextSize((int)(12 * density));

        invalidate();
    }

    protected void drawWaveformLine(Canvas canvas,
                                    int x, int y0, int y1,
                                    Paint paint) {
        canvas.drawLine(x, y0, x, y1, paint);
    }




    private Rect src = new Rect(0,0,50,50);
    private RectF dst = new RectF();
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (duration == 0)
            return;

        if (mHeightsAtThisZoomLevel == null)
            computeIntsForThisZoomLevel();

        // Draw waveform
        int measuredWidth = getMeasuredWidth();
        int measuredHeight = getMeasuredHeight();
        int start = mOffset;
        int width = mHeightsAtThisZoomLevel.length - start;
        int ctr = measuredHeight / 2;

        if (width > measuredWidth)
            width = measuredWidth;

        // Draw grid
        double onePixelInSecs = pixelsToSeconds(1);
        boolean onlyEveryFiveSecs = (onePixelInSecs > 1.0 / 50.0);
        double fractionalSecs = mOffset * onePixelInSecs;
        int integerSecs = (int) fractionalSecs;
        int i = 0;
        int s = -1;
        int l = -1;
        int imageWidth = 0;
        int imageHeight = 0;
        int c = -1;
        while (i < width) {
            i++;
            fractionalSecs += onePixelInSecs;
            int integerSecsNew = (int) fractionalSecs;
            if (integerSecsNew != integerSecs) {
                integerSecs = integerSecsNew;
                if (!onlyEveryFiveSecs || 0 == (integerSecs % 5)) {

                    if(integerSecs%mZoomLevels[mZoomLevel] == 0){
                        if(c == -1){
                            c = integerSecs;
                            l = i;
                        }
                        if(c != integerSecs){
                            if(imageWidth == 0){
                                imageWidth = i - l;
                            }
                            if(imageHeight == 0){
                                imageHeight = getMeasuredHeight() - 10 - (int) (12 * mDensity+5);
                            }
                        }
                        if(imageWidth != 0){
                            int top = (int) (12 * mDensity+5);
                            if(s == -1){
                                Bitmap b = bitmaps.get(integerSecs - mZoomLevels[mZoomLevel] - mZoomLevels[mZoomLevel]);
                                if(b != null){
                                    int left = l - imageWidth;
                                    dst.set(left,top,left+imageWidth + 1,top + imageHeight);
                                    canvas.drawBitmap(b,src,dst,mSelectedLinePaint);
                                }
                                b = bitmaps.get(integerSecs - mZoomLevels[mZoomLevel]);
                                if(b != null){
                                    int left = i - imageWidth;
                                    dst.set(left,top,left+imageWidth + 1,top + imageHeight);
                                    canvas.drawBitmap(b,src,dst,mSelectedLinePaint);
                                    s = 1;
                                }
                            }
                            Bitmap bitmap = bitmaps.get(integerSecs);
                            if(bitmap != null){
                                int left = i;
                                dst.set(left,top,left+imageWidth  + 1,top + imageHeight);
                                canvas.drawBitmap(bitmap,src,dst,mSelectedLinePaint);
                            }
                        }
                    }

                    canvas.drawLine(i, 0, i, measuredHeight, mGridPaint);
                }
            }
        }

//        if(bitmaps != null){
//            int m = i+mOffset;
//            while (i < m && s < bitmaps.size()){
//                i++;
//                if(i >= s*imageWidth){
//                    Bitmap bitmap = bitmaps.get(s);
//                    canvas.drawBitmap(bitmap,i-mOffset,12 * mDensity+5,mSelectedLinePaint);
//                    s++;
//                }
//            }
//        }

        // Draw waveform
        for (i = 0; i < width; i++) {
//            Paint paint;
            if (i + start >= mSelectionStart &&
                i + start < mSelectionEnd) {
//                paint = mSelectedLinePaint;
            } else {
                drawWaveformLine(canvas, i, 0, measuredHeight,
                                 mUnselectedBkgndLinePaint);
//                paint = mUnselectedLinePaint;
            }

//            drawWaveformLine(
//                canvas, i,
//                ctr - mHeightsAtThisZoomLevel[start + i],
//                ctr + 1 + mHeightsAtThisZoomLevel[start + i],
//                paint);

            if (i + start == mPlaybackPos) {
                canvas.drawLine(i, 0, i, measuredHeight, mPlaybackLinePaint);
            }
        }

        // If we can see the right edge of the waveform, draw the
        // non-waveform area to the right as unselected
        for (i = width; i < measuredWidth; i++) {
            drawWaveformLine(canvas, i, 0, measuredHeight,
                             mUnselectedBkgndLinePaint);
        }

        // Draw borders
        canvas.drawLine(
            mSelectionStart - mOffset + 0.5f, 30,
            mSelectionStart - mOffset + 0.5f, measuredHeight,
            mBorderLinePaint);
        canvas.drawLine(
            mSelectionEnd - mOffset + 0.5f, 0,
            mSelectionEnd - mOffset + 0.5f, measuredHeight - 30,
            mBorderLinePaint);

        // Draw timecode
        double timecodeIntervalSecs = 1.0;
        if(mZoomLevel == 1){
            timecodeIntervalSecs = 5.0;
        }else if(mZoomLevel == 2){
            timecodeIntervalSecs = 10.0;
        }else if(mZoomLevel == 3){
            timecodeIntervalSecs = 30.0;
        }else if(mZoomLevel == 4){
            timecodeIntervalSecs = 60.0;
        }
//        if (timecodeIntervalSecs / onePixelInSecs < 50) {
//            timecodeIntervalSecs = 5.0;
//        }
//        if (timecodeIntervalSecs / onePixelInSecs < 50) {
//            timecodeIntervalSecs = 10.0;
//        }

        // Draw grid
        fractionalSecs = mOffset * onePixelInSecs;
        int integerTimecode = (int) (fractionalSecs / timecodeIntervalSecs);
        i = 0;
        while (i <= width) {
            i++;
            fractionalSecs += onePixelInSecs;
            integerSecs = (int) fractionalSecs;

            int integerTimecodeNew = (int) (fractionalSecs /
                                            timecodeIntervalSecs);
            if (integerTimecodeNew != integerTimecode) {
                integerTimecode = integerTimecodeNew;
                // Turn, e.g. 67 seconds into "1:07"
                String timecodeMinutes = "" + (integerSecs / 60);
                String timecodeSeconds = "" + (integerSecs % 60);
                if ((integerSecs % 60) < 10) {
                    timecodeSeconds = "0" + timecodeSeconds;
                }
                String timecodeStr = timecodeMinutes + ":" + timecodeSeconds;
                float offset = (float) (
                    0.5 * mTimecodePaint.measureText(timecodeStr));
                canvas.drawText(timecodeStr,
                                i - offset,
                                (int)(12 * mDensity),
                                mTimecodePaint);


            }
        }

        if (mListener != null) {
            mListener.waveformDraw();
        }
    }

    /**
     * Called once when a new sound file is added
     */
    private void computeDoublesForAllZoomLevels() {

        if(duration <= 30000){
            mZoomLevel = 0;
        }else if(duration <= 60000){
            mZoomLevel = 1;
        }else if(duration <= 300000){
            mZoomLevel = 2;
        }else if(duration <= 600000){
            mZoomLevel = 3;
        }else {
            mZoomLevel = 4;
        }

        int numFrames = (int) (duration/10);

        mNumZoomLevels = 5;
        mLenByZoomLevel = new int[5];
        mZoomFactorByZoomLevel = new double[5];
        mValuesByZoomLevel = new double[5][];

        // Level 0 is doubled, with interpolated values
        mLenByZoomLevel[0] = numFrames * 2;
        mZoomFactorByZoomLevel[0] = 2.0;

        // Level 1 is normal
        mLenByZoomLevel[1] = numFrames/5;
        mZoomFactorByZoomLevel[1] = 0.2;

        mLenByZoomLevel[2] = numFrames/10;
        mZoomFactorByZoomLevel[2] = 0.1;


        mLenByZoomLevel[3] = numFrames/25;
        mZoomFactorByZoomLevel[3] = 0.04;

        mLenByZoomLevel[4] = numFrames/50;
        mZoomFactorByZoomLevel[4] = 0.02;


        for (int i = 0; i < 5;i++){
            mValuesByZoomLevel[i] = new double[mLenByZoomLevel[i]];
            for (int j = 0; j < mLenByZoomLevel[i]; j++) {
                mValuesByZoomLevel[i][j] = 0;
            }
        }
        // 3 more levels are each halved
//        for (int j = 2; j < 5; j++) {
//            mLenByZoomLevel[j] = mLenByZoomLevel[j - 1] / 2;
//            mValuesByZoomLevel[j] = new double[mLenByZoomLevel[j]];
//            mZoomFactorByZoomLevel[j] = mZoomFactorByZoomLevel[j - 1] / 2.0;
//            for (int i = 0; i < mLenByZoomLevel[j]; i++) {
//                mValuesByZoomLevel[j][i] =
//                    0.5 * (mValuesByZoomLevel[j - 1][2 * i] +
//                           mValuesByZoomLevel[j - 1][2 * i + 1]);
//            }
//        }

        mInitialized = true;
    }
    public void release(){
        if(bitmaps != null){
            for (int i = 0;i < bitmaps.size();i++){
                Bitmap bitmap = bitmaps.valueAt(i);
                if(bitmap != null && !bitmap.isRecycled()){
                    bitmap.recycle();
                }
            }
            bitmaps.clear();
            bitmaps = null;
        }
    }

    public SparseArray<Bitmap> getBitmaps() {
        return bitmaps;
    }

    /**
     * Called the first time we need to draw when the zoom level has changed
     * or the screen is resized
     */
    private void computeIntsForThisZoomLevel() {
        int halfHeight = (getMeasuredHeight() / 2) - 1;
        mHeightsAtThisZoomLevel = new int[mLenByZoomLevel[mZoomLevel]];
        for (int i = 0; i < mLenByZoomLevel[mZoomLevel]; i++) {
            mHeightsAtThisZoomLevel[i] =
                (int)(mValuesByZoomLevel[mZoomLevel][i] * halfHeight);
        }
    }
}
