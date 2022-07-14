package com.yeonjin.android.yjscreenrecorder;

import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Created by yeonjin.cho on 2018-05-12.
 */

public class MyByteBuffer {
    private static String TAG = "[YJ] MyByteBuffer";
    private static int BUFFER_MIN_SIZE = 2048;
    byte[] mMyBuffer = new byte[BUFFER_MIN_SIZE];
    int mReadPosition = -1, mWritePosition = -1;

    public MyByteBuffer () {
    }

    public ByteBuffer getBuffer(int length) {
        Log.d(TAG, "getBuffer " + length);
        if( mReadPosition < 0)
            return null;

        byte[] newBuffer = new byte[length];
        System.arraycopy(mMyBuffer, mReadPosition, newBuffer, 0, length);
        mReadPosition += length;
        Log.d(TAG, "mybuffer : " + mMyBuffer + ", newBuffer : " + newBuffer + ", position " + mReadPosition);

        if( mReadPosition > BUFFER_MIN_SIZE )
            mReadPosition = 0;

        ByteBuffer newByteBuffer = ByteBuffer.allocate(newBuffer.length);
        newByteBuffer.put(newBuffer);

        return newByteBuffer;
    }

    public void putBuffer(ByteBuffer data) {
        Log.d(TAG, "putBuffer " + data.capacity());
        if(mWritePosition < 0 ) {
            data.get(mMyBuffer, 0, data.capacity());
        }
        else {
            data.get(mMyBuffer, mWritePosition, data.capacity());
        }
    }
}
