package com.yeonjin.android.yjscreenrecorder;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/**
 * Created by 조연진 on 2017-10-29.
 */
public class ADecoderThread extends Thread {

    private static final String TAG = "[YJ] ADecoderThread";
    private static final int TIMEOUT_US = 10000;

    private Object mLock = new Object();
    private Object mPausedLock = new Object();

    private String mFilePath = null;

    private MediaExtractor mExtractor = null;
    private MediaCodec mDecoder = null;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private AudioTrack mAudioTrack = null;
    private ByteBuffer mByteBuffer = null;

    private File mDstFile = null;
    private FileOutputStream mFOS = null;

    private boolean mIsBgm = false;
    private boolean mIsEos = false;
    private boolean mIsPaused = false;

    private long mSampleTime = 0;
    private long mDuration = 0;

    private int mSampleRate = 0;

    public static long mNowAudioTimeUs = 0;

    public ADecoderThread(String path, boolean isBgm) {
        Log.d(TAG, "ADecoderThread");
        mFilePath = path;
        mIsBgm = isBgm;
    }

    public boolean prepare() {
        Log.d(TAG, "prepare");

        mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(mFilePath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        MediaFormat format = null;
        String mime = null;
        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            format = mExtractor.getTrackFormat(i);
            mime = format.getString(MediaFormat.KEY_MIME);
            Log.d(TAG, "mime type is " + mime);

            if (mime.startsWith("audio/")) {
                mExtractor.selectTrack(i);
                Log.d(TAG, "format : " + format);
                mDuration = format.getLong(MediaFormat.KEY_DURATION);
                break;
            }
        }


        try {
            mDecoder = MediaCodec.createDecoderByType(mime);
            //mDecoder.setCallback(mCallback);
            mDecoder.configure(format, null, null, 0);
            mDecoder.start();

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        if (mIsBgm) {
            mDstFile = new File(Environment.getExternalStorageDirectory() + "/" + AEncoderThread.BGM + ".pcm");
            /*if(mDstFile.exists()) {
                Log.e(TAG, "file is already exist ! ");
                mIsEos = true;
                return true;
            }*/
            try {
                mFOS = new FileOutputStream(mDstFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (mByteBuffer != null) {
            Log.d(TAG, "write the buffer");
        }
        else {
            int minBuffSize = AudioTrack.getMinBufferSize(format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);

            mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate,
                    AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, minBuffSize, AudioTrack.MODE_STREAM);
            mAudioTrack.play();
        }
        return true;
    }

    public long getSampleTime() {
        return mSampleTime;
    }
    public long getDuration() { return mDuration; }

    public void seekTo(long seektime) {
        Log.d(TAG, "seekTo " + seektime);
        if(mExtractor != null ){
            mExtractor.seekTo(seektime, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            mAudioTrack.flush();
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
                    mSampleTime = mExtractor.getSampleTime();
                    mDecoder.queueInputBuffer(inputbufferIndex, 0, sampleSize, mSampleTime, 0);
                    mExtractor.advance();
                }
            }

            int outputbufferIndex = mDecoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
            Log.d(TAG, "outputBufferIndex " + outputbufferIndex);
            if (outputbufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "output buffer format changed");
                synchronized ( AEncoderThread.mLock ) {
                    AEncoderThread.mLock.notifyAll();
                }
            } else if (outputbufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER ) {
                Log.d(TAG, "info try again layter");
                try {
                    Thread.sleep(20);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (outputbufferIndex >= 0 ) {
                ByteBuffer buffer = mDecoder.getOutputBuffer(outputbufferIndex);
                final byte[] byte_buffer = new byte[mBufferInfo.size];
                buffer.get(byte_buffer);
                buffer.clear();
                Log.d(TAG, "byte buffer.." + byte_buffer);

                if (mIsBgm) {
                    try {
                        mFOS.write(byte_buffer);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else if (mByteBuffer != null) {
                    Log.d(TAG, "write to byte buffer");
                    mByteBuffer.put(buffer);
                }
                else {
                    mAudioTrack.write(byte_buffer, 0, byte_buffer.length);
                }
                mDecoder.releaseOutputBuffer(outputbufferIndex, false);

                AudioTimestamp timestamp = new AudioTimestamp();
                mAudioTrack.getTimestamp(timestamp);
                int playedFrame = mAudioTrack.getPlaybackHeadPosition();
                long playedTimestamp = timestamp.framePosition;

                mNowAudioTimeUs = (playedFrame * 1000000L) / mSampleRate; // 현재 Audio 시간을 micro second 로
                Log.d(TAG, "mNowAudioTimeUs " + mNowAudioTimeUs+ " playedTimestamp " + playedTimestamp + " extractor sampletime " + mExtractor.getSampleTime());
            }

            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.e(TAG, "end of stream");
                break;
            }
            if( mIsPaused ) {
                Log.d(TAG, "paused! ");
                synchronized ( mPausedLock ) {
                    try {
                        mPausedLock.wait();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        synchronized (mLock) {
            mLock.notifyAll();
        }
        release();
    }

    public void pauseDecoder() {
        mIsPaused = true;
    }

    public void resumeDecoder() {
        mIsPaused = false;
        synchronized ( mPausedLock ) {
            mPausedLock.notifyAll();
        }
    }

    public void stopDecoder () {
        Log.d(TAG, "exit");

        mIsEos = true;
        synchronized (mLock) {
            try {
                mLock.wait();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void release () {
        Log.d(TAG, "release");

        if(mExtractor != null) {
            mExtractor.release();
            mExtractor = null;
        }

        if(mDecoder != null) {
            mDecoder.stop();
            mDecoder.release();
            mDecoder = null;
        }

        if(mAudioTrack != null ){
            mAudioTrack.stop();
            mAudioTrack.release();
        }

        if(mFOS != null) {
            try {
                mFOS.close();
                mFOS = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void setDecodeBuffer (ByteBuffer buffer) {
        mByteBuffer = buffer;
    }
}
