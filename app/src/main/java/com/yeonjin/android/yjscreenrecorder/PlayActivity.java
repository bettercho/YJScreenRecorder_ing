package com.yeonjin.android.yjscreenrecorder;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

/**
 * Created by yeonjin.cho on 2018-05-04.
 */

public class PlayActivity extends Activity implements SurfaceHolder.Callback {
    private static String TAG = "[YJ] PlayActivity";

    private SurfaceView mSurfaceView = null;
    private SurfaceHolder mSurfaceHolder = null;

    private String mFilePath = null;
    private MediaCodecPlayer mPlayer = null;
    private MediaTrimer mTrimer = null;

    private Button mBtPlay, mBtPause, mBtStop, mBtTrim;
    private SeekBar mSeekBar;
    private EditText mEtStart, mEtEnd;
    private ProgressDialog mProgress;

    private boolean isPlaying = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);

        mSurfaceView = (SurfaceView) findViewById(R.id.sv_video);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);

        mBtPlay = (Button)findViewById(R.id.bt_play);
        mBtPause = (Button)findViewById(R.id.bt_pause);
        mBtStop = (Button)findViewById(R.id.bt_stop);
        mBtTrim = (Button)findViewById(R.id.bt_trim);
        mSeekBar = (SeekBar)findViewById(R.id.seekBar) ;
        mEtStart = (EditText)findViewById(R.id.et_start);
        mEtEnd = (EditText)findViewById(R.id.et_end);

        mBtPlay.setOnClickListener(mListener);
        mBtPause.setOnClickListener(mListener);
        mBtStop.setOnClickListener(mListener);
        mBtTrim.setOnClickListener(mListener);
        mSeekBar.setOnSeekBarChangeListener(mBarListener);

        Intent intent = getIntent();
        mFilePath = intent.getStringExtra("filepath");

        mPlayer = new MediaCodecPlayer(true);
        mPlayer.setFilePath(mFilePath);
    }




    class SeekBarThread extends Thread {
        @Override
        public void run() {
            while(isPlaying) {
                mSeekBar.setProgress((int)(mPlayer.getCurrentPosition()/1000000));
                try {
                    Thread.sleep(1000);
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    SeekBar.OnSeekBarChangeListener mBarListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            int seekPoint = seekBar.getProgress();
            Log.d(TAG, "seekPoint " + seekPoint);
            mPlayer.seek((long)seekPoint*1000000);
        }
    };

    View.OnClickListener mListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            boolean ret = false;

            switch(view.getId()) {
                case R.id.bt_play:
                    if(mPlayer == null) {
                        mPlayer = new MediaCodecPlayer(true);
                        mPlayer.setFilePath(mFilePath);

                        mPlayer.setSurface(mSurfaceHolder.getSurface());
                        ret = mPlayer.prepare();

                        if(!ret) {
                            Log.e(TAG, "Not supported format");
                            mPlayer = null;
                            finish();
                            return;
                        }

                        mSeekBar.setMax((int)(mPlayer.getDuration()/1000000));
                        mPlayer.start();

                        isPlaying = true;
                    }
                    else {
                        mPlayer.resume();
                    }

                    break;
                case R.id.bt_pause:
                    if(mPlayer == null)
                        return;
                    mPlayer.pause();
                    break;
                case R.id.bt_stop:
                    if(mPlayer == null)
                        return;

                    mPlayer.stop();
                    mPlayer = null;

                    isPlaying = false;

                    break;
                case R.id.bt_trim:
                    Log.d(TAG, "FilePath " + mFilePath);
                    mTrimer = new MediaTrimer(mFilePath);
                    mTrimer.setTrimTime(Integer.parseInt(mEtStart.getText().toString()), Integer.parseInt(mEtEnd.getText().toString()));
                    mTrimer.trimToLowQuality();
                    /*new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "progress dialog start");
                            mProgress = new ProgressDialog(PlayActivity.this);
                            mProgress.setTitle("hello");
                            mProgress.setMessage("this is test");
                            mProgress.show();
                        }
                    }).run();

                    mTrimer.trimToSameQuality();
                    Log.d(TAG, "trim complete!");
                    if(mProgress != null)
                        mProgress.dismiss();
                    else
                        Log.d(TAG, "progress is null");*/

                    break;
            }

        }
    };

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceCreated");
        boolean ret = false;

        mPlayer.setSurface(mSurfaceHolder.getSurface());
        ret = mPlayer.prepare();
        if(!ret) {
            Toast.makeText(this, "Not supported codec", Toast.LENGTH_SHORT).show();
            mPlayer = null;
            finish();
        }

        mSeekBar.setMax((int)(mPlayer.getDuration()/1000000));
        mPlayer.start();

        isPlaying = true;

        new SeekBarThread().start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceDestroyed");
        mPlayer.stop();
        mPlayer = null;
        isPlaying = false;
    }

}
