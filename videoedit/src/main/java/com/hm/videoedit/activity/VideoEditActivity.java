package com.hm.videoedit.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.hm.videoedit.R;
import com.hm.videoedit.holder.VideoHolder;
import com.hm.videoedit.mediacodec.VideoEncode;
import com.hm.videoedit.mediacodec.VideoExtractor;
import com.hm.videoedit.view.CutView;
import com.hm.videoedit.view.MarkerView;
import com.hm.videoedit.view.WaveformView;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoEditActivity extends AppCompatActivity implements MarkerView.MarkerListener,
        WaveformView.WaveformListener{

    private String mFilename;
    private WaveformView mWaveformView;
    private MarkerView mStartMarker;
    private MarkerView mEndMarker;
    private TextView mStartText;
    private TextView mEndText;
    private ImageButton mPlayButton;
    private ImageButton mRewindButton;
    private ImageButton mFfwdButton;
    private TextView info;
    private boolean mKeyDown;

    private int mWidth;
    private int mMaxPos;
    private int mStartPos;
    private int mEndPos;
    private boolean mStartVisible;
    private boolean mEndVisible;
    private int mLastDisplayedStartPos;
    private int mLastDisplayedEndPos;
    private int mOffset;
    private int mOffsetGoal;
    private int mFlingVelocity;
    private int mPlayStartMsec;
    private int mPlayEndMsec;
    private Handler mHandler;
    private boolean mIsPlaying;

    private boolean mTouchDragging;
    private float mTouchStart;
    private int mTouchInitialOffset;
    private int mTouchInitialStartPos;
    private int mTouchInitialEndPos;
    private long mWaveformTouchStartMsec;
    private float mDensity;
    private int mWidthPixels;
    private int mHeightPixels;
    private int mMarkerLeftInset;
    private int mMarkerRightInset;
    private int mMarkerTopOffset;
    private int mMarkerBottomOffset;


    private Handler playerHandler;
    private TextureView playerView;
    private SimpleExoPlayer player;
    private Surface mSurface;

    private RelativeLayout videoView;
    private CutView cutView;


//    private ExecutorService fixedThreadPool = Executors.newFixedThreadPool(1);

    private VideoExtractor videoExtractor;
    private Handler videoHandler;
    private HandlerThread videoThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_edit);
        videoThread = new HandlerThread("VideoExtractor");
        videoThread.start();
        videoHandler = new Handler(videoThread.getLooper());

        mIsPlaying = false;

        playerHandler = new Handler();

        mFilename = getIntent().getStringExtra("path");
        mKeyDown = false;

        mHandler = new Handler();

        loadGui();

//        videoEncode = new VideoEncode();
//        videoEncode.setEncoderListener(new VideoEncode.OnEncoderListener() {
//            @Override
//            public void onStart() {
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//
//                    }
//                });
//            }
//
//            @Override
//            public void onStop() {
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//
//                    }
//                });
//            }
//        });
//        videoEncode.init(mFilename);

//        fixedThreadPool.execute(new Runnable() {
//            @Override
//            public void run() {
//                final VideoExtractor videoExtractor = new VideoExtractor(VideoEditActivity.this,mFilename);
//                long d;
//                if(videoExtractor.getDuration()<= 20000){
//                    d = videoExtractor.getDuration();
//                }else{
//                    d = videoExtractor.getDuration()/4;
//                }
//
//                videoExtractor.encoder(0, d, 1,50,50, new VideoExtractor.OnEncodeListener() {
//                    @Override
//                    public void onBitmap(int time, Bitmap bitmap) {
//                        frameTime = videoExtractor.getFrameTime();
//                        if(fixedThreadPool.isShutdown()){
//                            videoExtractor.stop();
//                        }else{
//                            SparseArray<Bitmap> bitmaps = mWaveformView.getBitmaps();
//                            if(bitmaps != null){
//                                bitmaps.put(time,bitmap);
//                            }
//                            mWaveformView.postInvalidate();
//                        }
//                    }
//                });
//            }
//        });
//        fixedThreadPool.execute(new Runnable() {
//            @Override
//            public void run() {
//                final VideoExtractor videoExtractor = new VideoExtractor(VideoEditActivity.this,mFilename);
//                if(videoExtractor.getDuration() <= 20000){
//                    return;
//                }
//                long d = videoExtractor.getDuration()/4;
//                videoExtractor.encoder(d+1000, d*2 , 1,50,50, new VideoExtractor.OnEncodeListener() {
//                    @Override
//                    public void onBitmap(int time, Bitmap bitmap) {
//                        if(fixedThreadPool.isShutdown()){
//                            videoExtractor.stop();
//                        }else{
//                            SparseArray<Bitmap> bitmaps = mWaveformView.getBitmaps();
//                            if(bitmaps != null){
//                                bitmaps.put(time,bitmap);
//                            }
//                            mWaveformView.postInvalidate();
//                        }
//
//                    }
//                });
//            }
//        });
//        fixedThreadPool.execute(new Runnable() {
//            @Override
//            public void run() {
//                final VideoExtractor videoExtractor = new VideoExtractor(VideoEditActivity.this,mFilename);
//                if(videoExtractor.getDuration() <= 20000){
//                    return;
//                }
//                long d = videoExtractor.getDuration()/4;
//                long start = 2*d;
//
//                videoExtractor.encoder(start+1000, start + d , 1,50,50, new VideoExtractor.OnEncodeListener() {
//                    @Override
//                    public void onBitmap(int time, Bitmap bitmap) {
//                        if(fixedThreadPool.isShutdown()){
//                            videoExtractor.stop();
//                        }else{
//                            SparseArray<Bitmap> bitmaps = mWaveformView.getBitmaps();
//                            if(bitmaps != null){
//                                bitmaps.put(time,bitmap);
//                            }
//                            mWaveformView.postInvalidate();
//                        }
//
//                    }
//                });
//            }
//        });
//        fixedThreadPool.execute(new Runnable() {
//            @Override
//            public void run() {
//                final VideoExtractor videoExtractor = new VideoExtractor(VideoEditActivity.this,mFilename);
//                if(videoExtractor.getDuration()<= 20000){
//                    return;
//                }
//                long d = videoExtractor.getDuration()/4;
//                long start = 3*d;
//                videoExtractor.encoder(start+1000, videoExtractor.getDuration() , 1,50,50, new VideoExtractor.OnEncodeListener() {
//                    @Override
//                    public void onBitmap(int time, Bitmap bitmap) {
//                        if(fixedThreadPool.isShutdown()){
//                            videoExtractor.stop();
//                        }else{
//                            SparseArray<Bitmap> bitmaps = mWaveformView.getBitmaps();
//                            if(bitmaps != null){
//                                bitmaps.put(time,bitmap);
//                            }
//                            mWaveformView.postInvalidate();
//                        }
//
//                    }
//                });
//            }
//        });

        videoExtractor = new VideoExtractor(this,mFilename);
        videoExtractor.setOnEncodeListener(new VideoExtractor.OnEncodeListener() {
            @Override
            public void onBitmap(int time, Bitmap bitmap) {
                isImageLoad = false;
                if(isPause){
                    videoExtractor.stop();
                }else{
                    if(time >= 0){
                        SparseArray<Bitmap> bitmaps = mWaveformView.getBitmaps();
                        if(bitmaps != null && bitmaps.get(time) == null){
                            bitmaps.put(time,bitmap);
                        }
                        mWaveformView.postInvalidate();
                    }
                }
            }
        });
        mHandler.postDelayed(mTimerRunnable, 100);
    }


    private void loadGui() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mDensity = metrics.density;
        mWidthPixels = metrics.widthPixels;
        mHeightPixels = (int) getResources().getDimension(R.dimen.dp300);

        mMarkerLeftInset = (int)(46 * mDensity);
        mMarkerRightInset = (int)(48 * mDensity);
        mMarkerTopOffset = (int)(10 * mDensity);
        mMarkerBottomOffset = (int)(10 * mDensity);

        videoView = findViewById(R.id.video_view);



        mStartText = (TextView)findViewById(R.id.starttext);
        mEndText = (TextView)findViewById(R.id.endtext);


        mPlayButton = (ImageButton)findViewById(R.id.play);
        mPlayButton.setOnClickListener(mPlayListener);
        mRewindButton = (ImageButton)findViewById(R.id.rew);
        mRewindButton.setOnClickListener(mRewindListener);
        mFfwdButton = (ImageButton)findViewById(R.id.ffwd);
        mFfwdButton.setOnClickListener(mFfwdListener);

        TextView markStartButton = (TextView) findViewById(R.id.mark_start);
        markStartButton.setOnClickListener(mMarkStartListener);
        TextView markEndButton = (TextView) findViewById(R.id.mark_end);
        markEndButton.setOnClickListener(mMarkEndListener);

        enableDisableButtons();

        mWaveformView = (WaveformView)findViewById(R.id.waveform);
        mWaveformView.setListener(this);


        mMaxPos = 0;
        mLastDisplayedStartPos = -1;
        mLastDisplayedEndPos = -1;

        if (player != null && player.getDuration() != 0 && !mWaveformView.hasSoundFile()) {
            mWaveformView.setDuration(player.getDuration());
            mWaveformView.recomputeHeights(mDensity);
            mMaxPos = mWaveformView.maxPos();
        }

        mStartMarker = (MarkerView)findViewById(R.id.startmarker);
        mStartMarker.setListener(this);
        mStartMarker.setAlpha(1f);
        mStartMarker.setFocusable(true);
        mStartMarker.setFocusableInTouchMode(true);
        mStartVisible = true;

        mEndMarker = (MarkerView)findViewById(R.id.endmarker);
        mEndMarker.setListener(this);
        mEndMarker.setAlpha(1f);
        mEndMarker.setFocusable(true);
        mEndMarker.setFocusableInTouchMode(true);
        mEndVisible = true;

        info = findViewById(R.id.info);
        if(player == null){
            Uri url = Uri.parse(mFilename);
            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();


            TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
            TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

            player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

            DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                    Util.getUserAgent(this, "ExoPlayerTime"), bandwidthMeter);

            MediaSource videoSource = new ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(url, playerHandler,null);
            player.prepare(videoSource);
        }
        Message message = mPlayerHandler.obtainMessage();
        message.what = 100;
        mPlayerHandler.sendMessageDelayed(message,100);

    }



    private Handler mPlayerHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if(player != null && player.getDuration() > 0 && player.getVideoFormat() != null){
                finishOpeningSoundFile();
                String mCaption = "0.00 seconds "+formatTime(mMaxPos) + " " +
                        "seconds";
                info.setText(mCaption);

                int videoWidth = player.getVideoFormat().width;
                int videoHeight = player.getVideoFormat().height;

                int screenWidth = mWidthPixels;
                int screenHeight = mHeightPixels;

                int left,top,viewWidth,viewHeight;
                float sh = screenWidth*1.0f/screenHeight;
                float vh = videoWidth *1.0f/ videoHeight;
                if(sh < vh){
                    left = 0;
                    viewWidth = screenWidth;
                    viewHeight = (int)(videoHeight *1.0f/ videoWidth *viewWidth);
                    top = (screenHeight - viewHeight)/2;
                }else{
                    top = 0;
                    viewHeight = screenHeight;
                    viewWidth = (int)(videoWidth *1.0f/ videoHeight *viewHeight);
                    left = (screenWidth - viewWidth)/2;
                }

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(viewWidth,viewHeight);
                params.leftMargin = left;
                params.topMargin = top;
                params.bottomMargin = mHeightPixels - top - viewHeight;
                videoView.setLayoutParams(params);

                RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

                playerView = new TextureView(VideoEditActivity.this);
                playerView.setKeepScreenOn(true);
                playerView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                        mSurface = new Surface(surface);
                        player.setVideoSurface(mSurface);
                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

                    }

                    @Override
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                        return false;
                    }

                    @Override
                    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                    }
                });
                videoView.addView(playerView,p);
                cutView = new CutView(VideoEditActivity.this);
                videoView.addView(cutView,p);
            }else{
                Message message = mPlayerHandler.obtainMessage();
                message.what = 100;
                mPlayerHandler.sendMessageDelayed(message,100);
            }
        }
    };


    private boolean isPause = false;
    @Override
    protected void onPause() {
        super.onPause();
        isPause = true;
        if(player == null){
            return;
        }
        player.setPlayWhenReady(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPause = false;
        if(mIsPlaying && player != null){
            player.setPlayWhenReady(true);
        }
    }

    /** Called when the activity is finally destroyed. */
    @Override
    protected void onDestroy() {
        Log.v("Ringdroid", "EditActivity OnDestroy");
        if(player != null){
            if(player.getPlayWhenReady()){
                player.setPlayWhenReady(false);
            }
            player.release();
            player = null;
        }
        if(mSurface != null){
            mSurface.release();
        }
        videoHandler.post(new Runnable() {
            @Override
            public void run() {
                if(videoExtractor != null){
                    videoExtractor.stop();
                    videoExtractor.release();
                }
                videoThread.quit();
            }
        });
        mPlayerHandler.removeMessages(100);
//        fixedThreadPool.shutdown();
        mWaveformView.release();
        super.onDestroy();
    }


    /**
     * Every time we get a message that our waveform drew, see if we need to
     * animate and trigger another redraw.
     */
    public void waveformDraw() {
        mWidth = mWaveformView.getMeasuredWidth();
        if (mOffsetGoal != mOffset && !mKeyDown)
            updateDisplay();
        else if (mIsPlaying) {
            updateDisplay();
        } else if (mFlingVelocity != 0) {
            updateDisplay();
        }
    }

    public void waveformTouchStart(float x) {
        mTouchDragging = true;
        mTouchStart = x;
        mTouchInitialOffset = mOffset;
        mFlingVelocity = 0;
        mWaveformTouchStartMsec = getCurrentTime();
    }

    public void waveformTouchMove(float x) {
        mOffset = trap((int)(mTouchInitialOffset + (mTouchStart - x)));
        updateDisplay();
    }

    public void waveformTouchEnd() {
        mTouchDragging = false;
        mOffsetGoal = mOffset;

        long elapsedMsec = getCurrentTime() - mWaveformTouchStartMsec;
        if (elapsedMsec < 300) {
            if (mIsPlaying) {
                int seekMsec = mWaveformView.pixelsToMillisecs(
                        (int)(mTouchStart + mOffset));
                if (seekMsec >= mPlayStartMsec &&
                        seekMsec < mPlayEndMsec) {
                    player.seekTo(seekMsec);
                } else {
                    handlePause();
                }
            } else {
                onPlay((int)(mTouchStart + mOffset));
            }
        }
    }

    public void waveformFling(float vx) {
        mTouchDragging = false;
        mOffsetGoal = mOffset;
        mFlingVelocity = (int)(-vx);
        updateDisplay();
    }

    public void waveformZoomIn() {
        mWaveformView.zoomIn();
        mStartPos = mWaveformView.getStart();
        mEndPos = mWaveformView.getEnd();
        mMaxPos = mWaveformView.maxPos();
        mOffset = mWaveformView.getOffset();
        mOffsetGoal = mOffset;
        updateDisplay();
    }

    public void waveformZoomOut() {
        mWaveformView.zoomOut();
        mStartPos = mWaveformView.getStart();
        mEndPos = mWaveformView.getEnd();
        mMaxPos = mWaveformView.maxPos();
        mOffset = mWaveformView.getOffset();
        mOffsetGoal = mOffset;
        updateDisplay();
    }
    private boolean isImageLoad = false;
    @Override
    public void waveformImage(final int loadSecs) {
        if(isPause){
            return;
        }
        if(!isImageLoad){
            isImageLoad = true;
            videoHandler.post(new Runnable() {
                @Override
                public void run() {
                    long begin = loadSecs*1000;
                    videoExtractor.encoder(begin);
                }
            });
//            fixedThreadPool.execute(new Runnable() {
//                @Override
//                public void run() {
//                    long begin = loadSecs*1000;
//                    videoExtractor.encoder(begin);
//                }
//            });
        }
    }
    //
    // MarkerListener
    //

    public void markerDraw() {
    }

    public void markerTouchStart(MarkerView marker, float x) {
        mTouchDragging = true;
        mTouchStart = x;
        mTouchInitialStartPos = mStartPos;
        mTouchInitialEndPos = mEndPos;
    }

    public void markerTouchMove(MarkerView marker, float x) {
        float delta = x - mTouchStart;

        if (marker == mStartMarker) {
            mStartPos = trap((int)(mTouchInitialStartPos + delta));
            mEndPos = trap((int)(mTouchInitialEndPos + delta));
        } else {
            mEndPos = trap((int)(mTouchInitialEndPos + delta));
            if (mEndPos < mStartPos)
                mEndPos = mStartPos;
        }
        updateDisplay();
    }

    public void markerTouchEnd(MarkerView marker) {
        mTouchDragging = false;
        if (marker == mStartMarker) {
            setOffsetGoalStart();
        } else {
            setOffsetGoalEnd();
        }
    }

    public void markerLeft(MarkerView marker, int velocity) {
        mKeyDown = true;

        if (marker == mStartMarker) {
            int saveStart = mStartPos;
            mStartPos = trap(mStartPos - velocity);
            mEndPos = trap(mEndPos - (saveStart - mStartPos));
            setOffsetGoalStart();
        }

        if (marker == mEndMarker) {
            if (mEndPos == mStartPos) {
                mStartPos = trap(mStartPos - velocity);
                mEndPos = mStartPos;
            } else {
                mEndPos = trap(mEndPos - velocity);
            }

            setOffsetGoalEnd();
        }

        updateDisplay();
    }

    public void markerRight(MarkerView marker, int velocity) {
        mKeyDown = true;
        if (marker == mStartMarker) {
            int saveStart = mStartPos;
            mStartPos += velocity;
            if (mStartPos > mMaxPos)
                mStartPos = mMaxPos;
            mEndPos += (mStartPos - saveStart);
            if (mEndPos > mMaxPos)
                mEndPos = mMaxPos;

            setOffsetGoalStart();
        }

        if (marker == mEndMarker) {
            mEndPos += velocity;
            if (mEndPos > mMaxPos)
                mEndPos = mMaxPos;

            setOffsetGoalEnd();
        }

        updateDisplay();
    }

    public void markerEnter(MarkerView marker) {

    }

    public void markerKeyUp() {
        mKeyDown = false;
        updateDisplay();
    }

    public void markerFocus(MarkerView marker) {
        mKeyDown = false;
        if (marker == mStartMarker) {
            setOffsetGoalStartNoUpdate();
        } else {
            setOffsetGoalEndNoUpdate();
        }

        // Delay updaing the display because if this focus was in
        // response to a touch event, we want to receive the touch
        // event too before updating the display.
        mHandler.postDelayed(new Runnable() {
            public void run() {
                updateDisplay();
            }
        }, 100);
    }




    private void finishOpeningSoundFile() {
        mWaveformView.setDuration(player.getDuration());
        mWaveformView.recomputeHeights(mDensity);

        mMaxPos = mWaveformView.maxPos();
        mLastDisplayedStartPos = -1;
        mLastDisplayedEndPos = -1;

        mTouchDragging = false;

        mOffset = 0;
        mOffsetGoal = 0;
        mFlingVelocity = 0;
        resetPositions();
        if (mEndPos > mMaxPos)
            mEndPos = mMaxPos;

        updateDisplay();
    }

    private synchronized void updateDisplay() {
        if (mIsPlaying) {
            int now = (int) player.getCurrentPosition();
            int frames = mWaveformView.millisecsToPixels(now);
            mWaveformView.setPlayback(frames);
            setOffsetGoalNoUpdate(frames - mWidth / 2);
            if (now >= mPlayEndMsec) {
                handlePause();
            }
        }

        if (!mTouchDragging) {
            int offsetDelta;

            if (mFlingVelocity != 0) {
                offsetDelta = mFlingVelocity / 30;
                if (mFlingVelocity > 80) {
                    mFlingVelocity -= 80;
                } else if (mFlingVelocity < -80) {
                    mFlingVelocity += 80;
                } else {
                    mFlingVelocity = 0;
                }

                mOffset += offsetDelta;

                if (mOffset + mWidth / 2 > mMaxPos) {
                    mOffset = mMaxPos - mWidth / 2;
                    mFlingVelocity = 0;
                }
                if (mOffset < 0) {
                    mOffset = 0;
                    mFlingVelocity = 0;
                }
                mOffsetGoal = mOffset;
            } else {
                offsetDelta = mOffsetGoal - mOffset;

                if (offsetDelta > 10)
                    offsetDelta = offsetDelta / 10;
                else if (offsetDelta > 0)
                    offsetDelta = 1;
                else if (offsetDelta < -10)
                    offsetDelta = offsetDelta / 10;
                else if (offsetDelta < 0)
                    offsetDelta = -1;
                else
                    offsetDelta = 0;
                mOffset += offsetDelta;
            }
        }

        mWaveformView.setParameters(mStartPos, mEndPos, mOffset);
        mWaveformView.invalidate();

        mStartMarker.setContentDescription(
                getResources().getText(R.string.start_marker) + " " +
                        formatTime(mStartPos));
        mEndMarker.setContentDescription(
                getResources().getText(R.string.end_marker) + " " +
                        formatTime(mEndPos));

        int startX = mStartPos - mOffset - mMarkerLeftInset;
        if (startX + mStartMarker.getWidth() >= 0) {
            if (!mStartVisible) {
                // Delay this to avoid flicker
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        mStartVisible = true;
                        mStartMarker.setAlpha(1f);
                    }
                }, 0);
            }
        } else {
            if (mStartVisible) {
                mStartMarker.setAlpha(0f);
                mStartVisible = false;
            }
            startX = 0;
        }

        int endX = mEndPos - mOffset - mEndMarker.getWidth() + mMarkerRightInset;
        if (endX + mEndMarker.getWidth() >= 0) {
            if (!mEndVisible) {
                // Delay this to avoid flicker
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        mEndVisible = true;
                        mEndMarker.setAlpha(1f);
                    }
                }, 0);
            }
        } else {
            if (mEndVisible) {
                mEndMarker.setAlpha(0f);
                mEndVisible = false;
            }
            endX = 0;
        }

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(
                startX,
                mMarkerTopOffset,
                -mStartMarker.getWidth(),
                -mStartMarker.getHeight());
        mStartMarker.setLayoutParams(params);

        params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(
                endX,
                mWaveformView.getMeasuredHeight() - mEndMarker.getHeight() - mMarkerBottomOffset,
                -mStartMarker.getWidth(),
                -mStartMarker.getHeight());
        mEndMarker.setLayoutParams(params);
    }

    private Runnable mTimerRunnable = new Runnable() {
        public void run() {
            // Updating an EditText is slow on Android.  Make sure
            // we only do the update if the text has actually changed.
            if (mStartPos != mLastDisplayedStartPos &&
                    !mStartText.hasFocus()) {
                mStartText.setText(formatTime(mStartPos));
                mLastDisplayedStartPos = mStartPos;
            }

            if (mEndPos != mLastDisplayedEndPos &&
                    !mEndText.hasFocus()) {
                mEndText.setText(formatTime(mEndPos));
                mLastDisplayedEndPos = mEndPos;
            }

            if(mWaveformView.getPlaybackPos() != -1){
                String mCaption = formatTime(mWaveformView.getPlaybackPos())+" seconds "+formatTime(mMaxPos) + " " +
                        "seconds";
                info.setText(mCaption);
            }

            mHandler.postDelayed(mTimerRunnable, 100);
        }
    };

    private void enableDisableButtons() {
        if (mIsPlaying) {
            mPlayButton.setImageResource(android.R.drawable.ic_media_pause);
            mPlayButton.setContentDescription(getResources().getText(R.string.stop));
        } else {
            mPlayButton.setImageResource(android.R.drawable.ic_media_play);
            mPlayButton.setContentDescription(getResources().getText(R.string.play));
        }
    }

    private void resetPositions() {
        mStartPos = mWaveformView.secondsToPixels(0.0);
        mEndPos = mWaveformView.secondsToPixels(15.0);
    }

    private int trap(int pos) {
        if (pos < 0)
            return 0;
        if (pos > mMaxPos)
            return mMaxPos;
        return pos;
    }

    private void setOffsetGoalStart() {
        setOffsetGoal(mStartPos - mWidth / 2);
    }

    private void setOffsetGoalStartNoUpdate() {
        setOffsetGoalNoUpdate(mStartPos - mWidth / 2);
    }

    private void setOffsetGoalEnd() {
        setOffsetGoal(mEndPos - mWidth / 2);
    }

    private void setOffsetGoalEndNoUpdate() {
        setOffsetGoalNoUpdate(mEndPos - mWidth / 2);
    }

    private void setOffsetGoal(int offset) {
        setOffsetGoalNoUpdate(offset);
        updateDisplay();
    }

    private void setOffsetGoalNoUpdate(int offset) {
        if (mTouchDragging) {
            return;
        }

        mOffsetGoal = offset;
        if (mOffsetGoal + mWidth / 2 > mMaxPos)
            mOffsetGoal = mMaxPos - mWidth / 2;
        if (mOffsetGoal < 0)
            mOffsetGoal = 0;
    }

    private String formatTime(int pixels) {
        if (mWaveformView != null && mWaveformView.isInitialized()) {
            return formatDecimal(mWaveformView.pixelsToSeconds(pixels));
        } else {
            return "";
        }
    }

    private String formatDecimal(double x) {
        int xWhole = (int)x;
        int xFrac = (int)(100 * (x - xWhole) + 0.5);

        if (xFrac >= 100) {
            xWhole++; //Round up
            xFrac -= 100; //Now we need the remainder after the round up
            if (xFrac < 10) {
                xFrac *= 10; //we need a fraction that is 2 digits long
            }
        }

        if (xFrac < 10)
            return xWhole + ".0" + xFrac;
        else
            return xWhole + "." + xFrac;
    }

    private synchronized void handlePause() {
        if (player != null && player.getPlayWhenReady()) {
            player.setPlayWhenReady(false);
        }
        mWaveformView.setPlayback(-1);
        mIsPlaying = false;
        enableDisableButtons();
    }

    private synchronized void onPlay(int startPosition) {
        if (mIsPlaying) {
            handlePause();
            return;
        }

        if (player == null) {
            // Not initialized yet
            return;
        }

        try {
            mPlayStartMsec = mWaveformView.pixelsToMillisecs(startPosition);
            if (startPosition < mStartPos) {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mStartPos);
            } else if (startPosition > mEndPos) {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mMaxPos);
            } else {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mEndPos);
            }
//            mPlayer.setOnCompletionListener(new SamplePlayer.OnCompletionListener() {
//                @Override
//                public void onCompletion() {
//                    handlePause();
//                }
//            });
            mIsPlaying = true;

            player.seekTo(mPlayStartMsec);
            player.setPlayWhenReady(true);
            updateDisplay();
            enableDisableButtons();
        } catch (Exception e) {

            return;
        }
    }






    private View.OnClickListener mPlayListener = new View.OnClickListener() {
        public void onClick(View sender) {
            onPlay(mStartPos);
        }
    };

    private View.OnClickListener mRewindListener = new View.OnClickListener() {
        public void onClick(View sender) {
            if (mIsPlaying) {
                int newPos = (int) (player.getCurrentPosition() - 5000);
                if (newPos < mPlayStartMsec)
                    newPos = mPlayStartMsec;
                player.seekTo(newPos);
            } else {
                mStartMarker.requestFocus();
                markerFocus(mStartMarker);
            }
        }
    };

    private View.OnClickListener mFfwdListener = new View.OnClickListener() {
        public void onClick(View sender) {
            if (mIsPlaying) {
                int newPos = (int) (5000 + player.getCurrentPosition());
                if (newPos > mPlayEndMsec)
                    newPos = mPlayEndMsec;
                player.seekTo(newPos);
            } else {
                mEndMarker.requestFocus();
                markerFocus(mEndMarker);
            }
        }
    };

    private View.OnClickListener mMarkStartListener = new View.OnClickListener() {
        public void onClick(View sender) {
            if (mIsPlaying) {
                mStartPos = mWaveformView.millisecsToPixels(
                        (int) player.getCurrentPosition());
                updateDisplay();
            }
        }
    };

    private View.OnClickListener mMarkEndListener = new View.OnClickListener() {
        public void onClick(View sender) {
            if (mIsPlaying) {
                mEndPos = mWaveformView.millisecsToPixels(
                        (int) player.getCurrentPosition());
                updateDisplay();
                handlePause();
            }
        }
    };


    private long getCurrentTime() {
        return System.nanoTime() / 1000000;
    }

    private String getStackTrace(Exception e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    public void onHome(View view){
        finish();
    }
    public void onConfirm(View view){
        long vst = (long)(Double.parseDouble(formatTime(mStartPos))*1000*1000);
        long vse = (long)(Double.parseDouble(formatTime(mEndPos))*1000*1000);
        if(vse - vst < 5 * 1000*1000){
            Toast.makeText(this,"时长不能小于5秒",Toast.LENGTH_SHORT).show();
            return;
        }
        long frameTime = videoExtractor.getFrameTime();
        if(frameTime == 0){
            return;
        }
        int videoWidth = player.getVideoFormat().width;
        int videoHeight = player.getVideoFormat().height;
        float[] cutArr = cutView.getCutArr();
        float left = cutArr[0];
        float top = cutArr[1];
        float right = cutArr[2];
        float bottom = cutArr[3];

        int cutWidth = cutView.getRectWidth();
        int cutHeight = cutView.getRectHeight();


        float leftPro = left / cutWidth;
        float topPro = top / cutHeight;
        float rightPro = right / cutWidth;
        float bottomPro = bottom / cutHeight;

        //得到裁剪位置
        int cropWidth = (int) (videoWidth * (rightPro - leftPro));
        int cropHeight = (int) (videoHeight * (bottomPro - topPro));
        if(cropWidth%2 != 0){
            cropWidth = cropWidth - 1;
        }
        if(cropHeight%2 != 0){
            cropHeight = cropHeight - 1;
        }

        VideoHolder videoHolder = new VideoHolder();
        videoHolder.setVideoFile(mFilename);
        videoHolder.setCropWidth(cropWidth);
        videoHolder.setCropHeight(cropHeight);
        videoHolder.setCropLeft((int) (leftPro*videoWidth));
        videoHolder.setCropTop((int) (topPro*videoHeight));
        videoHolder.setStartTime(vst);
        videoHolder.setEndTime(vse);
        videoHolder.setFrameTime(frameTime);
        Intent intent = new Intent();
        intent.putExtra("videoHolder",videoHolder);
        setResult(RESULT_OK,intent);
        finish();

//        float f = left/cutView.getWidth();
//        float t = 1.0f - top/cutView.getHeight();
//        float r = right/cutView.getWidth();
//        float b = 1.0f - bottom/cutView.getHeight();
//
//
//        float[] textureVertexData = {
//                r, b,
//                f, b,
//                r, t,
//                f, t
//        };
//        videoEncode.init(mFilename,
//                (long)(Double.valueOf(formatTime(mStartPos))*1000*1000),
//                (long)(Double.valueOf(formatTime(mEndPos))*1000*1000),
//                cropWidth,cropHeight,textureVertexData);
    }

}
