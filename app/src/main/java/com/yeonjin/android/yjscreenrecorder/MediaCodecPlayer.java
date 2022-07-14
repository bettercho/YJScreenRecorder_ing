package com.yeonjin.android.yjscreenrecorder;

import android.util.Log;
import android.view.Surface;

/**
 * Created by yeonjin.cho on 2018-05-04.
 */

public class MediaCodecPlayer {
    private static String TAG = "[YJ] MediaCodecPlayer";

    private String mFilePath = null;

    private VDecoderThread mVDecoderThread = null;
    private ADecoderThread mADecoderThread = null;

    private boolean mIsVideo = false;

    private Surface mSurface = null;

    private int mStartTime = 0, mEndTime = 0;

    public MediaCodecPlayer(boolean isVideo) {
        Log.d(TAG, "MediaCodecPlayer " + isVideo);
        mIsVideo = isVideo;
    }

    public void setFilePath(String path) {
        Log.d(TAG, "setFilePath " + path);
        mFilePath = path;
    }

    public void setSurface(Surface surface) {
        Log.d(TAG, "setSurface " + surface);
        mSurface = surface;
    }

    public void setTrimTime(int startTime, int endTime) {
        mStartTime = startTime;
        mEndTime = endTime;
    }

    public boolean prepare() {
        Log.d(TAG, "prepare");
        boolean ret = false;
        if(mVDecoderThread == null && mIsVideo) {
            mVDecoderThread = new VDecoderThread(mFilePath, mSurface);
            ret = mVDecoderThread.prepare();
            if (!ret)
                return false;
        }

        if(mADecoderThread == null) {
            mADecoderThread = new ADecoderThread(mFilePath, false);
            ret = mADecoderThread.prepare();
            if (!ret)
                return false;
        }
        return true;
    }

    public void start() {
        Log.d(TAG, "start");
        if(mVDecoderThread != null) {
            mVDecoderThread.start();
        }
        if(mADecoderThread != null) {
            mADecoderThread.start();
        }
    }

    public void resume() {
        Log.d(TAG, "resume");
        mADecoderThread.resumeDecoder();
        mVDecoderThread.resumeDecoder();
    }

    public void pause() {
        Log.d(TAG, "pause");
        mADecoderThread.pauseDecoder();
        mVDecoderThread.pauseDecoder();
    }

    public void seek(long seekTime) {
        if(mVDecoderThread != null) {
            mVDecoderThread.seekTo(seekTime);
        }
        if(mADecoderThread != null) {
            mADecoderThread.seekTo(seekTime);
        }
    }

    public void stop() {
        if(mVDecoderThread != null) {
            mVDecoderThread.stopDecoder();
            mVDecoderThread = null;
        }
        if(mADecoderThread != null) {
            mADecoderThread.stopDecoder();
            mADecoderThread = null;
        }
    }

    public void trim() {


    }

    public long getDuration() {
        return mADecoderThread.getDuration();
    }

    public long getCurrentPosition() {
        return mADecoderThread.getSampleTime();
    }

}
