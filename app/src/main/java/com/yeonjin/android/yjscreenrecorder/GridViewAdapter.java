package com.yeonjin.android.yjscreenrecorder;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import java.util.ArrayList;

/**
 * Created by yeonjin.cho on 2018-05-03.
 */

public class GridViewAdapter extends BaseAdapter {

    private static String TAG = "[YJ] GridViewAdapter";

    Context mContext;
    int mLayout;
    LayoutInflater mInflater;
    ArrayList<Bitmap> mBitmaps = new ArrayList<Bitmap>();

    public GridViewAdapter(Context context, int layout, ArrayList<Bitmap> bitmaps) {
        mContext = context;
        mLayout = layout;
        mBitmaps = bitmaps;
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }
    @Override
    public int getCount() {
        return mBitmaps.size();
    }

    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        Log.d(TAG, "call getView " + position);
        if( view == null ) {
            view = mInflater.inflate(mLayout, null);
        }

        ImageView iv = (ImageView)view.findViewById(R.id.iv_video);
        Bitmap bitmap = mBitmaps.get(position);
        if(bitmap == null) {
            iv.setImageResource(R.drawable.images);
        }
        else {
            iv.setImageBitmap(bitmap);
        }
        return view;
    }

    @Override
    public Object getItem(int position) {
        return mBitmaps.get(position);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }
}


