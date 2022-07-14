package com.yeonjin.android.yjscreenrecorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Created by yeonjin.cho on 2018-05-10.
 */

public class MediaTrimer {
    private static String TAG = "[YJ] MediaTrimer";

    private MediaExtractor mExtractor = null;
    private MediaMuxer mMuxer = null;

    private long mStartTime = -1, mEndTime = -1;
    private static final int TIMEOUT_US = 10000;
    private boolean mEos = false;

    int mAudioTrackIndex = -1, mVideoTrackIndex = -1, mAudioMuxerIndex = -1, mVideoMuxerIndex = -1;
    String mAudioMime, mVideoMime;
    MediaFormat mAudioFormat, mVideoFormat;

    int mTrackCount = 0, mAddedTrackCount =0;

    public MediaTrimer(String filepath) {

        try {
            mMuxer = new MediaMuxer("/storage/emulated/0/YJRecorder/test.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch(Exception e) {
            e.printStackTrace();
        }

        mExtractor = new MediaExtractor();
        try {
            Log.d(TAG, "filepath " + filepath);
            mExtractor.setDataSource("storage/emulated/0/YJRecorder/audio.mp3");
        } catch (Exception e){
            e.printStackTrace();
        }
        mTrackCount = mExtractor.getTrackCount();

        for (int i = 0; i < mTrackCount; i++) {
            mExtractor.selectTrack(i);
            MediaFormat format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            /*if(mime.startsWith("video")) {
                mVideoMime = mime;
                mVideoFormat = format;
                mVideoTrackIndex = i;
                mMuxer.addTrack(format);
            }else {*/
            if(mime.startsWith("audio")) {
                mAudioMime = mime;
                mAudioFormat = format;
                mAudioTrackIndex = i;
                mMuxer.addTrack(format);
            }
            //}
        }

        //Log.d(TAG, "mVideoMime : " + mVideoMime +", mVideoTrackIndex : " +mVideoTrackIndex );
        Log.d(TAG, "mAudioMime : " + mAudioMime +", mAudioTrackIndex : " +mAudioTrackIndex );
    }

    public void setTrimTime(int start, int end) {
        mStartTime = start * 1000000L;
        mEndTime = end * 1000000L;
    }


    // trim 하여 더 낮은 Quality 로 저장함
    public void trimToLowQuality() {
        TrimThread trimThread = new TrimThread();
        trimThread.start();
    }

    class TrimThread extends Thread{

        private static final String AUDIO_MIME_TYPE = "audio/amr-wb";
        private static final String VIDEO_MIME_TYPE = "video/avc";

        private static final int FRAME_RATE = 30;
        private static final int IFRAME_INTERVAL = 10;
        private static final int TIMEOUT_US = 10000;
        private static final float BPP = 0.25f;

        int CHANNEL_COUNT = 1;
        int SAMPLE_RATE = 44100;
        int CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO;
        int BIT_PER_SAMPLE = AudioFormat.ENCODING_PCM_16BIT;
        int BIT_RATE = 128000;

        private int mWidth =720;
        private int mHeight = 480;

        MediaCodec mAudioDecoder = null, mAudioEncoder = null;
        MediaCodec mVideoDecoder = null, mVideoEncoder = null;

        MediaCodec.BufferInfo mAudioDecoderBufferInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo mAudioEncoderBufferInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo mVideoDecoderBufferInfo = new MediaCodec.BufferInfo();
        MediaCodec.BufferInfo mVideoEncoderBufferInfo = new MediaCodec.BufferInfo();

        boolean mEos = false;

        public TrimThread() {
            try {
                mAudioDecoder = MediaCodec.createDecoderByType(mAudioMime);
                mAudioDecoder.configure(mAudioFormat, null, null, 0);
                mAudioDecoder.start();

            } catch (Exception e) {
                e.printStackTrace();
            }

            /*try {
                mVideoDecoder = MediaCodec.createDecoderByType(mVideoMime);
                mVideoDecoder.configure(mVideoFormat, null, null, 0);
                mVideoDecoder.start();

            } catch (Exception e) {
                e.printStackTrace();
            }*/

            mAudioFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT);
            mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, CHANNEL_MASK);
            mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE );
            mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CHANNEL_COUNT);

            try {
                mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
                mAudioEncoder.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mAudioEncoder.start();
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*mVideoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, mWidth, mHeight);
            mVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            mVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate());
            mVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            mVideoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

            try {
                mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
                mVideoEncoder.configure(mVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mVideoEncoder.start();
                Log.d(TAG, "video encoder started");
            } catch (Exception e) {
                e.printStackTrace();
            }*/

            mExtractor.seekTo(60*3*1000000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        }

        @Override
        public void run() {
            while(!mEos) {
                ByteBuffer decodedAudioBuffer = doAudioDecode();
                //ByteBuffer decodedVideoBuffer = doVideoDecode();

                if(decodedAudioBuffer != null)
                    doAudioEncode(decodedAudioBuffer);

                //if(decodedVideoBuffer != null)
                    //doVideoEncode(decodedVideoBuffer);
            }

            mMuxer.stop();
            mMuxer.release();

            mAudioDecoder.stop();
           // mVideoDecoder.stop();

            mAudioEncoder.stop();
            //mVideoEncoder.stop();

            mAudioDecoder.release();
            //mVideoDecoder.release();

            mAudioEncoder.release();
            //mVideoDecoder.release();

            mExtractor.release();

        }

        public ByteBuffer doVideoDecode() {
            ByteBuffer decodedBuffer = null;

            int inputbufferIndex = mVideoDecoder.dequeueInputBuffer(TIMEOUT_US);
            if (inputbufferIndex >= 0) {
                ByteBuffer inputBuffer = mVideoDecoder.getInputBuffer(inputbufferIndex);
                mExtractor.selectTrack(mVideoTrackIndex);
                int sampleSize = mExtractor.readSampleData(inputBuffer, 0);

                if (sampleSize < 0) {
                    Log.e(TAG, "sample size is 0");
                    mVideoDecoder.queueInputBuffer(inputbufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                } else {
                    mVideoDecoder.queueInputBuffer(inputbufferIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
                    mExtractor.advance();
                }
            }

            int outputbufferIndex = mVideoDecoder.dequeueOutputBuffer(mVideoDecoderBufferInfo, TIMEOUT_US);
            if (outputbufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "output buffer format changed");
            } else if (outputbufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(TAG, "info try again layter");
                try {
                    Thread.sleep(20);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (outputbufferIndex >= 0) {
                decodedBuffer = mVideoDecoder.getOutputBuffer(outputbufferIndex);
                mVideoDecoder.releaseOutputBuffer(outputbufferIndex, false);

            }
            if ((mVideoDecoderBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "eos");
                mEos = true;
            }
            return decodedBuffer;

        }

        public void doVideoEncode(ByteBuffer pcmBuffer) {

            int mPosition = 0;
            boolean end = false;
            Log.e(TAG, "original pcm Buffer position : " + pcmBuffer.position() + ", limit : " + pcmBuffer.limit() + ", capa : " +pcmBuffer.capacity());
            pcmBuffer.clear();

            ByteBuffer tmpBuffer = ByteBuffer.allocateDirect(pcmBuffer.capacity());
            Log.e(TAG, "original pcm Buffer position : " + pcmBuffer.position() + ", limit : " + pcmBuffer.limit() + ", capa : " +pcmBuffer.capacity());
            tmpBuffer.put(pcmBuffer);
            Log.e(TAG, "original tmp Buffer position : " + tmpBuffer.position() + ", limit : " + tmpBuffer.limit() + ", capa : " +tmpBuffer.capacity());
            tmpBuffer.clear();

            while(true) {
                int indexInputBuffer = mVideoEncoder.dequeueInputBuffer(TIMEOUT_US);
                if (indexInputBuffer >= 0) {
                    ByteBuffer inputBuffer = mVideoEncoder.getInputBuffer(indexInputBuffer);
                    inputBuffer.clear();

                    Log.d(TAG, "mPosition : " + mPosition +" inputBuffer capa " + inputBuffer.capacity());
                    tmpBuffer.position(mPosition);

                    if(tmpBuffer.capacity() - mPosition <= inputBuffer.capacity()) {
                        Log.d(TAG, "all pcm buffer is encoded~");
                        tmpBuffer.limit(tmpBuffer.capacity());
                        Log.d(TAG, "tmp Buffer position : " + tmpBuffer.position() + ", limit : " + tmpBuffer.limit() + ", capa : " + tmpBuffer.capacity());
                        inputBuffer.put(tmpBuffer.slice());
                        end = true;
                    }
                    else {
                        tmpBuffer.limit(inputBuffer.capacity() + mPosition); // position : 0 ,limit : inputbuffer capa
                        inputBuffer.put(tmpBuffer.slice());
                        Log.d(TAG, "tmp Buffer position : " + tmpBuffer.position() + ", limit : " + tmpBuffer.limit() + ", capa : " + tmpBuffer.capacity());
                    }

                    mPosition += inputBuffer.capacity();

                    if (mEos) {
                        mVideoEncoder.queueInputBuffer(indexInputBuffer, 0, inputBuffer.capacity(), System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        mVideoEncoder.queueInputBuffer(indexInputBuffer, 0, inputBuffer.capacity(), System.nanoTime() / 1000, 0);
                    }
                }

                int indexOutputBuffer = mVideoEncoder.dequeueOutputBuffer(mVideoEncoderBufferInfo, TIMEOUT_US);
                Log.i(TAG, "dequeue output buffer audio index = " + indexOutputBuffer);
                if (indexOutputBuffer == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else if (indexOutputBuffer == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = mVideoEncoder.getOutputFormat();
                    //mVideoMuxIndex = mMuxer.addTrack(newFormat);
                    Log.d(TAG, "video newformat : " + newFormat);
                    //mMuxer.start();

                } else if (indexOutputBuffer >= 0) {
                    Log.d(TAG, "status " + indexOutputBuffer);
                    ByteBuffer encodedData = mVideoEncoder.getOutputBuffer(indexOutputBuffer);

                    if ((mVideoEncoderBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                        mVideoEncoderBufferInfo.size = 0;
                    }

                    if (mVideoEncoderBufferInfo.size != 0) {
                        encodedData.position(mVideoEncoderBufferInfo.offset);
                        encodedData.limit(mVideoEncoderBufferInfo.offset + mVideoEncoderBufferInfo.size);

                        mVideoEncoderBufferInfo.presentationTimeUs = System.nanoTime() / 1000;
                        mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mVideoEncoderBufferInfo);
                    }
                    mVideoEncoder.releaseOutputBuffer(indexOutputBuffer, false);

                    if ((mVideoEncoderBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "end of audio stream");
                        mEos = true;
                        break;
                    }
                }

                if(end)
                    break;
            }

        }

        public ByteBuffer doAudioDecode() {
            ByteBuffer decodedBuffer = null;
            int inputbufferIndex = mAudioDecoder.dequeueInputBuffer(TIMEOUT_US);
            if (inputbufferIndex >= 0) {
                ByteBuffer inputBuffer = mAudioDecoder.getInputBuffer(inputbufferIndex);
                mExtractor.selectTrack(mAudioTrackIndex);
                int sampleSize = mExtractor.readSampleData(inputBuffer, 0);
                Log.d(TAG, "inputBuffer capa " + inputBuffer.capacity());

                if (sampleSize < 0) {
                    Log.e(TAG, "sample size is 0");
                    mAudioDecoder.queueInputBuffer(inputbufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                } else {
                    mAudioDecoder.queueInputBuffer(inputbufferIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
                    mExtractor.advance();
                }
            }

            int outputbufferIndex = mAudioDecoder.dequeueOutputBuffer(mAudioDecoderBufferInfo, TIMEOUT_US);
            if (outputbufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "output buffer format changed");
            } else if (outputbufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(TAG, "info try again layter");
                try {
                    Thread.sleep(20);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (outputbufferIndex >= 0) {
                decodedBuffer = mAudioDecoder.getOutputBuffer(outputbufferIndex);
                mAudioDecoder.releaseOutputBuffer(outputbufferIndex, false);

            }
            if ((mAudioDecoderBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "eos");
                mEos = true;
            }

            return decodedBuffer;
        }

        public void doAudioEncode(ByteBuffer pcmBuffer) {

            int mPosition = 0;
            boolean end = false;
            Log.e(TAG, "original pcm Buffer position : " + pcmBuffer.position() + ", limit : " + pcmBuffer.limit() + ", capa : " +pcmBuffer.capacity());
            pcmBuffer.clear();

            ByteBuffer tmpBuffer = ByteBuffer.allocateDirect(pcmBuffer.capacity());
            Log.e(TAG, "original pcm Buffer position : " + pcmBuffer.position() + ", limit : " + pcmBuffer.limit() + ", capa : " +pcmBuffer.capacity());
            tmpBuffer.put(pcmBuffer);
            Log.e(TAG, "original tmp Buffer position : " + tmpBuffer.position() + ", limit : " + tmpBuffer.limit() + ", capa : " +tmpBuffer.capacity());
            tmpBuffer.clear();

            while(true) {
                int indexInputBuffer = mAudioEncoder.dequeueInputBuffer(TIMEOUT_US);
                if (indexInputBuffer >= 0) {
                    ByteBuffer inputBuffer = mAudioEncoder.getInputBuffer(indexInputBuffer);
                    inputBuffer.clear();

                    Log.d(TAG, "mPosition : " + mPosition +" inputBuffer capa " + inputBuffer.capacity());
                    tmpBuffer.position(mPosition);

                    if(tmpBuffer.capacity() - mPosition <= inputBuffer.capacity()) {
                        Log.d(TAG, "all pcm buffer is encoded~");
                        tmpBuffer.limit(tmpBuffer.capacity());
                        Log.d(TAG, "tmp Buffer position : " + tmpBuffer.position() + ", limit : " + tmpBuffer.limit() + ", capa : " + tmpBuffer.capacity());
                        inputBuffer.put(tmpBuffer.slice());
                        end = true;
                    }
                    else {
                        tmpBuffer.limit(inputBuffer.capacity() + mPosition); // position : 0 ,limit : inputbuffer capa
                        inputBuffer.put(tmpBuffer.slice());
                        Log.d(TAG, "tmp Buffer position : " + tmpBuffer.position() + ", limit : " + tmpBuffer.limit() + ", capa : " + tmpBuffer.capacity());
                    }

                    mPosition += inputBuffer.capacity();

                    if (mEos) {
                        mAudioEncoder.queueInputBuffer(indexInputBuffer, 0, inputBuffer.capacity(), System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        mAudioEncoder.queueInputBuffer(indexInputBuffer, 0, inputBuffer.capacity(), System.nanoTime() / 1000, 0);
                    }
                }

                int indexOutputBuffer = mAudioEncoder.dequeueOutputBuffer(mAudioEncoderBufferInfo, TIMEOUT_US);
                Log.i(TAG, "dequeue output buffer audio index = " + indexOutputBuffer);
                if (indexOutputBuffer == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else if (indexOutputBuffer == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = mAudioEncoder.getOutputFormat();
                    mAudioMuxerIndex = mMuxer.addTrack(newFormat);
                    mMuxer.start();
                    Log.d(TAG, "newformat : " + newFormat + ", mAudioMuxerIndex" +mAudioMuxerIndex);
                } else if (indexOutputBuffer >= 0) {
                    Log.d(TAG, "status " + indexOutputBuffer);
                    ByteBuffer encodedData = mAudioEncoder.getOutputBuffer(indexOutputBuffer);

                    if ((mAudioEncoderBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                        mAudioEncoderBufferInfo.size = 0;
                    }

                    if (mAudioEncoderBufferInfo.size != 0) {
                        encodedData.position(mAudioEncoderBufferInfo.offset);
                        encodedData.limit(mAudioEncoderBufferInfo.offset + mAudioEncoderBufferInfo.size);

                        mAudioEncoderBufferInfo.presentationTimeUs = System.nanoTime() / 1000;
                        Log.d(TAG,"writeSampleData "+ mAudioEncoderBufferInfo.size);
                        mMuxer.writeSampleData(mAudioMuxerIndex, encodedData, mAudioEncoderBufferInfo);
                    }
                    mAudioEncoder.releaseOutputBuffer(indexOutputBuffer, false);

                    if ((mAudioEncoderBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "end of audio stream");
                        mEos = true;
                        break;
                    }
                }

                if(end)
                    break;
            }
        }
        private int calcBitRate() {
            final int bitrate = (int)(BPP * FRAME_RATE * mWidth * mHeight);
            Log.i(TAG, "bitrate : " + bitrate);
            return bitrate;
        }
    }

    // 동일한 수준으로 Trim. Decoding/Encoding 없이 Extractor 에서 읽어온 그대로 Muxer 에 씀.
    public void trimToSameQuality() {

        boolean sawEOS = false;
        int bufferSize = 256*1024;
        int frameCount = 0;
        int offset = 100;

        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        mExtractor.seekTo(mStartTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        mMuxer.start();
        while (!sawEOS) {
            bufferInfo.offset = offset;
            bufferInfo.size = mExtractor.readSampleData(dstBuf, offset);

            if (bufferInfo.size < 0) {
                Log.d(TAG, "saw input EOS.");

                sawEOS = true;
                bufferInfo.size = 0;
            } else {
                bufferInfo.presentationTimeUs = mExtractor.getSampleTime();
                bufferInfo.flags = mExtractor.getSampleFlags();
                int trackIndex = mExtractor.getSampleTrackIndex();

                if(trackIndex == mAudioTrackIndex) {
                    mMuxer.writeSampleData(mAudioTrackIndex, dstBuf, bufferInfo);
                }
                else {
                    mMuxer.writeSampleData(mVideoTrackIndex, dstBuf, bufferInfo);
                }

                mExtractor.advance();

                frameCount++;
                Log.d(TAG, "Frame (" + frameCount + ") " +
                            "PresentationTimeUs:" + bufferInfo.presentationTimeUs +
                            " Flags:" + bufferInfo.flags +
                            " TrackIndex:" + trackIndex +
                            " Size(KB) " + bufferInfo.size / 1024);

                if(bufferInfo.presentationTimeUs >= mEndTime) {
                    Log.d(TAG, "trim end");
                    break;
                }
            }
        }

        mMuxer.stop();
        mMuxer.release();
    }


}
