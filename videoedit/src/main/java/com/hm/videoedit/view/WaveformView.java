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
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.hm.videoedit.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


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
        public void waveformImage(int loadSecs);
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
    private final SparseArray<Bitmap> bitmaps = new SparseArray<>();
    private final SparseArray<Bitmap> removeBitmaps = new SparseArray<>();
    private boolean mInitialized;

    private long duration = 0;
    private static int mZoomLevels[] = new int[]{1,5,10,30,60};
    private static float mZooms[] = new float[]{4,2,2.5f,2,1};
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
        mPlaybackLinePaint.setColor(res.getColor(R.color.colorAccent));
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
    private List<Integer> xList = new ArrayList<>();
    private List<Integer> numberList = new ArrayList<>();
//    private int imageSecs = -1;
//    private int imageWidth = -1,imageHeight = -1;
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

        double fractionalSecs = mOffset * onePixelInSecs;
//        int integerSecs = (int) fractionalSecs;
        int i = 0;
//        int s = -1;
//        int l = -1;
//        int c = -1;
//        imageSecs = -1;
//        removeBitmaps.clear();
//        int drawWidth = 0;
//        Rect rect = null;
//        while (i < width) {
//            i++;
//            fractionalSecs += onePixelInSecs;
//            int integerSecsNew = (int) fractionalSecs;
//            if (integerSecsNew != integerSecs) {
//                integerSecs = integerSecsNew;
//                if(integerSecs%mZoomLevels[mZoomLevel] == 0){
//                    if(c == -1){
//                        c = integerSecs;
//                        l = i;
//                    }
//                    if(c != integerSecs){
//                        if(drawWidth == 0){
//                            drawWidth = i - l;
//                        }
//                    }
//                    if(imageHeight <= 0){
//                        imageHeight = getMeasuredHeight();
//                    }
//                    if(drawWidth != 0 && drawWidth != imageWidth){
//                        imageWidth = drawWidth;
//                    }
//                    if(imageWidth != 0){
//                        if(s == -1){
//                            int oneSecs = integerSecs - mZoomLevels[mZoomLevel] - mZoomLevels[mZoomLevel];
//                            if(oneSecs >= 0){
//                                Bitmap bitmap = bitmaps.get(oneSecs);
//                                if(bitmap != null){
//                                    int bw = bitmap.getWidth();
//                                    int bh = bitmap.getHeight();
//                                    if(rect == null){
//                                        rect = imageRect(bw,bh,imageWidth,imageHeight);
//                                    }
//                                    removeBitmaps.put(oneSecs,bitmap);
//                                    src.right = bw;
//                                    src.bottom = bh;
//                                    int left = l - imageWidth;
//                                    dst.set(left + rect.left,rect.top,left+rect.width() + 1,rect.top+rect.height());
//                                    canvas.drawBitmap(bitmap,src,dst,mSelectedLinePaint);
//                                }else{
//                                    if(imageSecs == -1){
//                                        imageSecs = oneSecs;
//                                    }
//                                }
//                            }
//                            int twoSecs = integerSecs - mZoomLevels[mZoomLevel];
//                            if(twoSecs >= 0){
//                                Bitmap bitmap = bitmaps.get(twoSecs);
//                                if(bitmap != null){
//                                    int bw = bitmap.getWidth();
//                                    int bh = bitmap.getHeight();
//                                    if(rect == null){
//                                        rect = imageRect(bw,bh,imageWidth,imageHeight);
//                                    }
//                                    removeBitmaps.put(twoSecs,bitmap);
//                                    src.right = bitmap.getWidth();
//                                    src.bottom = bitmap.getHeight();
//                                    int left = i - imageWidth;
//                                    dst.set(left + rect.left,rect.top,left+rect.width() + 1,rect.top+rect.height());
//                                    canvas.drawBitmap(bitmap,src,dst,mSelectedLinePaint);
//                                    s = 1;
//                                }else{
//                                    if(imageSecs == -1){
//                                        imageSecs = twoSecs;
//                                    }
//                                }
//                            }
//                        }
//                        if(integerSecs >= 0){
//                            Bitmap bitmap = bitmaps.get(integerSecs);
//                            if(bitmap != null){
//                                int bw = bitmap.getWidth();
//                                int bh = bitmap.getHeight();
//                                if(rect == null){
//                                    rect = imageRect(bw,bh,imageWidth,imageHeight);
//                                }
//                                removeBitmaps.put(integerSecs,bitmap);
//                                src.right = bitmap.getWidth();
//                                src.bottom = bitmap.getHeight();
//                                int left = i;
//                                dst.set(left + rect.left,rect.top,left+rect.width() + 1,rect.top+rect.height());
//                                canvas.drawBitmap(bitmap,src,dst,mSelectedLinePaint);
//                            }else{
//                                if(imageSecs == -1){
//                                    imageSecs = integerSecs;
//                                }
//                            }
//                        }
//                        canvas.drawLine(i, 0, i, measuredHeight, mGridPaint);
//                    }
//                }
//            }
//        }
//        bitmaps.clear();
//        for (int k = 0; k < removeBitmaps.size(); k++){
//            int key = removeBitmaps.keyAt(k);
//            bitmaps.put(key,removeBitmaps.get(key));
//        }
//        removeBitmaps.clear();
//        final int is = imageSecs;
//        postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                if(is !=  -1){
//                    if (mListener != null) {
//                        mListener.waveformImage(is);
//                    }
//                }
//            }
//        },100);



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
        int startX = -1;
        int startNumber = -1;
        int drawWidth = -1;
        int drawHeight = measuredHeight;
        Rect rect = null;
        int imageSecs = -1;
        xList.clear();
        numberList.clear();
        while (i <= width) {

            fractionalSecs += onePixelInSecs;
            int integerSecs = (int) fractionalSecs;

            int integerTimecodeNew = (int) (fractionalSecs /
                                            timecodeIntervalSecs);
            if (integerTimecodeNew != integerTimecode) {
                if(startX == -1)startX = i;
                else if(drawWidth == -1) drawWidth = i -startX;
                integerTimecode = integerTimecodeNew;
                if(startNumber == -1) startNumber = integerSecs - 1;
                numberList.add(integerSecs);
                xList.add(i);
//                if(startNumber != integerTimecode){
//                    Bitmap bitmap = bitmaps.get(integerTimecode);
//                    if(bitmap != null){
//                        if(drawWidth != -1){
//                            int bw = bitmap.getWidth();
//                            int bh = bitmap.getHeight();
//                            if(rect == null){
//                                rect = imageRect(bw,bh,drawWidth,drawHeight);
//                            }
//                            src.right = bw;
//                            src.bottom = bh;
//                            int left = i;
//                            dst.set(left + rect.left,rect.top,left+rect.width() + 1,rect.top+rect.height());
//                            canvas.drawBitmap(bitmap,src,dst,mSelectedLinePaint);
//                        }
//                        removeBitmaps.put(integerTimecode,bitmap);
//                    }else {
//                        if(imageSecs == -1)imageSecs = integerTimecode;
//                    }
//                }
                // Turn, e.g. 67 seconds into "1:07"

            }
            i++;
        }
        Bitmap startBitmap = bitmaps.get(startNumber);
        if(startBitmap != null){
            int bw = startBitmap.getWidth();
            int bh = startBitmap.getHeight();
            rect = imageRect(bw,bh,drawWidth,drawHeight);
            src.right = bw;
            src.bottom = bh;
            int left = startX - drawWidth;
            dst.set(left + rect.left,rect.top,left+rect.width() + 1,rect.top+rect.height());
            canvas.drawBitmap(startBitmap,src,dst,mSelectedLinePaint);
            removeBitmaps.put(startNumber,startBitmap);
        }else {
            imageSecs = startNumber;
        }
        for (int j = 0; j < numberList.size(); j++) {
            int integerSecs = numberList.get(j);
            int x = xList.get(j);
            Bitmap bitmap = bitmaps.get(integerSecs);
            if(bitmap != null){
                if(drawWidth != -1){
                    int bw = bitmap.getWidth();
                    int bh = bitmap.getHeight();
                    if(rect == null){
                        rect = imageRect(bw,bh,drawWidth,drawHeight);
                    }
                    src.right = bw;
                    src.bottom = bh;
                    int left = x;
                    dst.set(left + rect.left,rect.top,left+rect.width() + 1,rect.top+rect.height());
                    canvas.drawBitmap(bitmap,src,dst,mSelectedLinePaint);
                }
                removeBitmaps.put(integerSecs,bitmap);
            }else {
                if(imageSecs == -1)imageSecs = integerSecs;
            }
            String timecodeMinutes = "" + (integerSecs / 60);
            String timecodeSeconds = "" + (integerSecs % 60);
            if ((integerSecs % 60) < 10) {
                timecodeSeconds = "0" + timecodeSeconds;
            }
            String timecodeStr = timecodeMinutes + ":" + timecodeSeconds;
            float offset = (float) (
                    0.5 * mTimecodePaint.measureText(timecodeStr));
            canvas.drawLine(x, 0, x, measuredHeight, mGridPaint);
            canvas.drawText(timecodeStr,
                    x - offset,
                    (int)(12 * mDensity),
                    mTimecodePaint);
        }
//        if(startBitmap != null){
//            int bw = startBitmap.getWidth();
//            int bh = startBitmap.getHeight();
//            if(rect == null){
//                rect = imageRect(bw,bh,drawWidth,drawHeight);
//            }
//            src.right = bw;
//            src.bottom = bh;
//            int left = startX - drawWidth;
//            dst.set(left + rect.left,rect.top,left+rect.width() + 1,rect.top+rect.height());
//            canvas.drawBitmap(startBitmap,src,dst,mSelectedLinePaint);
//            removeBitmaps.put(integerTimecode,startBitmap);
//        }
        bitmaps.clear();
        for (int j = 0; j < removeBitmaps.size(); j++) {
            int key = removeBitmaps.keyAt(j);
            bitmaps.put(key, removeBitmaps.get(key));
        }
        removeBitmaps.clear();
        final int is = imageSecs;
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if(is !=  -1){
                    if (mListener != null) {
                        mListener.waveformImage(is);
                    }
                }
            }
        },10);
        if (mListener != null) {
            mListener.waveformDraw();
        }
    }
    public void putBitmap(int time,Bitmap bitmap,int rotationDegrees){
        if(bitmaps.get(time) == null){
            if(rotationDegrees == 90)bitmaps.put(time,bitmapRotation(bitmap,90));
            else bitmaps.put(time,bitmap);
        }
        postInvalidate();
    }
    private Bitmap bitmapRotation(Bitmap bm, final int orientationDegree) {
        Matrix m = new Matrix();
        m.setRotate(orientationDegree, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
        try {
            return Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
        } catch (OutOfMemoryError ex) {
        }
        return bm;
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
        mLenByZoomLevel[0] = numFrames*2;
        mZoomFactorByZoomLevel[0] = 2.0;

        // Level 1 is normal
        mLenByZoomLevel[1] = numFrames/2;
        mZoomFactorByZoomLevel[1] = 0.5;

        mLenByZoomLevel[2] = numFrames/4;
        mZoomFactorByZoomLevel[2] = 0.25;


        mLenByZoomLevel[3] = numFrames/10;
        mZoomFactorByZoomLevel[3] = 0.1;

        mLenByZoomLevel[4] = numFrames/20;
        mZoomFactorByZoomLevel[4] = 0.05;

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
            for (int j = 0; j < bitmaps.size(); j++) {
                int key = bitmaps.keyAt(j);
                Bitmap bitmap = bitmaps.get(key);
                if(bitmap != null && !bitmap.isRecycled()){
                    bitmap.recycle();
                }
            }
            bitmaps.clear();
        }
    }

//    public Map<String,Bitmap> getBitmaps() {
//        return bitmaps;
//    }

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

    private Rect imageRect(int imageWidth , int imageHeight, int viewWidth, int viewHeight){
        Rect rect = new Rect();
        float vh = viewWidth*1.0f/viewHeight;
        float ih = imageWidth *1.0f/ imageHeight;
        int width,height;
        if(vh < ih){
            rect.left = 0;
            width = viewWidth;
            height = (int)(imageHeight *1.0f/ imageWidth *width);
            rect.top = (viewHeight - height)/2;
        }else{
            rect.top = 0;
            height = viewHeight;
            width = (int)(imageWidth *1.0f/ imageHeight *height);
            rect.left = (viewWidth - width)/2;
        }
        rect.right = rect.left + width;
        rect.bottom = rect.top + height;
        return rect;
    }
}
