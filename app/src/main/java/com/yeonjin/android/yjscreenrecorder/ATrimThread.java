package com.yeonjin.android.yjscreenrecorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Created by yeonjin.cho on 2018-05-12.
 */

public class ATrimThread extends Thread {
    private static String TAG = "[YJ] ATrimThread";

    private static String MIME_TYPE = "audio/amr-wb";
    private static int CHANNEL_COUNT = 2;
    private static int SAMPLE_RATE = 8000;
    private static int CHANNEL_MASK = AudioFormat.CHANNEL_IN_STEREO;
    private static int BIT_PER_SAMPLE = AudioFormat.ENCODING_PCM_16BIT;
    private static int BIT_RATE = 128000;
    private static final int TIMEOUT_US = 10000;
    private static int MIN_BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, BIT_PER_SAMPLE);

    MediaCodec mDecoder = null, mEncoder = null;
    MediaExtractor mExtractor = null;
    MediaMuxer mMuxer = null;
    MediaFormat mAudioFormat = null;

    MediaCodec.BufferInfo mDecoderBufferInfo = new MediaCodec.BufferInfo();
    MediaCodec.BufferInfo mEncoderBufferInfo = new MediaCodec.BufferInfo();

    LinkedList<ByteBuffer> mDecodedBuffer = new LinkedList<>();
    LinkedList<ByteBuffer> mEncodedBuffer = new LinkedList<>();

    int mAudioTrackIndex = 0;

    public ATrimThread() {
    }

    public void prepare() {
        mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource("/storage/emulated/0/YJRecorder/audio.mp3");
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
                break;
            }
        }
        try {
            mDecoder = MediaCodec.createDecoderByType(mime);
            mDecoder.configure(format, null, null, 0);
            mDecoder.start();

        } catch (Exception e) {
            e.printStackTrace();
        }

        mAudioFormat = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT);
        mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, CHANNEL_MASK);
        mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE );
        mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CHANNEL_COUNT);

        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
            mEncoder.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mEncoder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            mMuxer = new MediaMuxer("/storage/emulated/0/YJRecorder/audio_after.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            e.printStackTrace();
        }

        mMuxer.addTrack(mEncoder.getOutputFormat());
        mMuxer.start();
    }

    @Override
    public void run() {
        while(true){

            // 디코딩
            int inputbufferIndex = mDecoder.dequeueInputBuffer(TIMEOUT_US);
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

            int outputbufferIndex = mDecoder.dequeueOutputBuffer(mDecoderBufferInfo, TIMEOUT_US);
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
                mDecodedBuffer.add(buffer);
                mDecoder.releaseOutputBuffer(outputbufferIndex, false);

            }

            boolean eos = false;
            if ((mDecoderBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                eos = true;
            }

            // 인코딩
            int indexInputBuffer = mEncoder.dequeueInputBuffer(TIMEOUT_US);
            if( indexInputBuffer >= 0 ) {
                int readBufferSize = 0;
                ByteBuffer inputBuffer = mEncoder.getInputBuffer(indexInputBuffer);

                if(inputBuffer.capacity() >=  MIN_BUFFER_SIZE) {
                    readBufferSize = inputBuffer.capacity();
                }
                else {
                    Log.e(TAG, "buffer capacity small than buffer min size");
                }

                ByteBuffer pcm = mDecodedBuffer.poll();
                byte[] byte_buffer = new byte[readBufferSize];
                pcm.get(byte_buffer);

                inputBuffer.clear();
                inputBuffer.put(byte_buffer);
                Log.d(TAG, "queueIntputBuffer " + byte_buffer.length);

                if(eos) {
                    mEncoder.queueInputBuffer(indexInputBuffer, 0, byte_buffer.length, System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
                else {
                    mEncoder.queueInputBuffer(indexInputBuffer, 0, byte_buffer.length, System.nanoTime() / 1000, 0);
                }
            }

            int indexOutputBuffer = mEncoder.dequeueOutputBuffer(mEncoderBufferInfo, TIMEOUT_US);
            Log.i(TAG, "dequeue output buffer audio index = " + indexOutputBuffer);
            if(indexOutputBuffer == MediaCodec.INFO_TRY_AGAIN_LATER) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (indexOutputBuffer == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = mEncoder.getOutputFormat();
                mAudioTrackIndex = mMuxer.addTrack(newFormat);
                Log.d(TAG, "newformat : " + newFormat);
            } else if (indexOutputBuffer >= 0 ) {
                Log.d(TAG, "status " + indexOutputBuffer);
                ByteBuffer encodedData = mEncoder.getOutputBuffer(indexOutputBuffer);

                if((mEncoderBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mEncoderBufferInfo.size = 0;
                }

                if( mEncoderBufferInfo.size != 0 ){
                    encodedData.position(mEncoderBufferInfo.offset);
                    encodedData.limit(mEncoderBufferInfo.offset + mEncoderBufferInfo.size);

                    mEncoderBufferInfo.presentationTimeUs = System.nanoTime() / 1000;
                    mMuxer.writeSampleData(mAudioTrackIndex, encodedData, mEncoderBufferInfo);
                }
                mEncoder.releaseOutputBuffer(indexOutputBuffer, false);

                if ((mEncoderBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "end of audio stream");
                    break;
                }
            }

        }
    }
}
