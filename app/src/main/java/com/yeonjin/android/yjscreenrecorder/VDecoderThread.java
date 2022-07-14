package com.yeonjin.android.yjscreenrecorder;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by yeonjin.cho on 2018-05-03.
 */

public class VDecoderThread extends Thread {

    private static final String TAG = "[YJ] VDecoderThread";
    private static final int TIMEOUT_US = 10000;

    private String mFilePath = null;

    private MediaExtractor mExtractor = null;
    private MediaCodec mDecoder = null;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private boolean mIsEos = false;

    private Surface mSurface = null;

    boolean mIsPaused = false;
    boolean first = false;

    private long mDeltaTimeUs = -1;
    private long mLastMediaTimeUs = 0;

    private Object mPausedLock = new Object();

    public VDecoderThread(String path, Surface surface) {
        Log.d(TAG, "VDecoderThread");
        mFilePath = path;
        mSurface = surface;
    }

    public boolean prepare() {
        Log.d(TAG, "prepare");

        mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(mFilePath);
        } catch (Exception e){
            e.printStackTrace();
        }
        MediaFormat format = null;
        String mime = null;
        for(int i=0; i < mExtractor.getTrackCount(); i++ ){
            format = mExtractor.getTrackFormat(i);
            mime = format.getString(MediaFormat.KEY_MIME);
            Log.d(TAG, "mime type is " + mime);

            if (mime.startsWith("video/")) {
                mExtractor.selectTrack(i);
                Log.d(TAG, "format " + format);
                break;
            }
        }
        try {
            mDecoder = MediaCodec.createDecoderByType(mime);
            //mDecoder.setCallback(mCallback);
            mDecoder.configure(format, mSurface, null, 0);
            mDecoder.start();

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void seekTo(long seektime) {
        if(mExtractor != null ){
            mExtractor.seekTo(seektime, MediaExtractor.SEEK_TO_NEXT_SYNC);
            //mDecoder.flush();
            //mSeekDuration = System.currentTimeMillis() - mStartTime - mPausedDuration + seektime;
            //Log.d(TAG, "mSeekDuration : " + mSeekDuration);
        }
    }

    @Override
    public void run() {
        mIsPaused = false;

        while (!mIsEos) {
            int inputbufferIndex = mDecoder.dequeueInputBuffer(TIMEOUT_US);
            Log.d(TAG, "inputBufferIndex "+ inputbufferIndex);
            if (inputbufferIndex >= 0) {
                ByteBuffer inputBuffer = mDecoder.getInputBuffer(inputbufferIndex);
                int sampleSize = mExtractor.readSampleData(inputBuffer, 0);
                if (sampleSize < 0) {
                    Log.e(TAG, "sample size is 0");
                    mDecoder.queueInputBuffer(inputbufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                } else {
                    mDecoder.queueInputBuffer(inputbufferIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
                    mExtractor.advance();
                }
            }

            int outputbufferIndex = mDecoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
            Log.d(TAG, "outputBufferIndex " + outputbufferIndex);
            if (outputbufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "output buffer format changed");
            } else if (outputbufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER ) {
                Log.d(TAG, "info try again layter");
                try {
                    Thread.sleep(20);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (outputbufferIndex >= 0 ) {
                ByteBuffer buffer = mDecoder.getOutputBuffer(outputbufferIndex);

                if (!first) {
                    //mStartTime = System.currentTimeMillis() ;
                    first = true;
                }
                //long sleepTime = (mBufferInfo.presentationTimeUs / 1000) - (System.currentTimeMillis() - mStartTime);

                Log.d(TAG, "video presentation time : "+ mBufferInfo.presentationTimeUs +" audio time : " + ADecoderThread.mNowAudioTimeUs);


                long sleepTime = mBufferInfo.presentationTimeUs - ADecoderThread.mNowAudioTimeUs;
                Log.d(TAG, "sleepTime " + sleepTime + ", presentation " +mBufferInfo.presentationTimeUs + ", nowAudio " + ADecoderThread.mNowAudioTimeUs);
                if(sleepTime < 0) {
                    mDecoder.releaseOutputBuffer(outputbufferIndex, false);
                }
                else {
                    try {
                        Thread.sleep(sleepTime / 1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if(mSurface.isValid())
                        mDecoder.releaseOutputBuffer(outputbufferIndex, true);
                }

            }
            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.e(TAG, "end of stream");
                break;
            }

            if (mIsPaused) {
                Log.d(TAG, "paused!");
                synchronized ( mPausedLock ) {
                    try {
                        mPausedLock.wait();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        release();
    }

    public void pauseDecoder() {
        mIsPaused = true;
        //mPauseTime = System.currentTimeMillis();
    }

    public void resumeDecoder() {
        mIsPaused = false;
        //mResumeTime = System.currentTimeMillis();

        //mPausedDuration += (mResumeTime - mPauseTime);
        synchronized ( mPausedLock ) {
            Log.d(TAG, "resume");
            mPausedLock.notifyAll();
        }
    }

    public void stopDecoder() {
        mIsEos = true;
    }

    private void release () {
        Log.d(TAG, "release");

        mDecoder.stop();
        mDecoder.release();
        mDecoder = null;

        mExtractor.release();
        mExtractor = null;

    }

    MediaCodec.Callback mCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int i) {
            Log.d(TAG, "onInputBufferAvailable");

        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int i, @NonNull MediaCodec.BufferInfo bufferInfo) {
            Log.d(TAG, "onOutputBufferAvailable");

        }

        @Override
        public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
            Log.d(TAG, "onError");

        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
            Log.d(TAG, "onOutputFormatChanged");

        }
    };
}
