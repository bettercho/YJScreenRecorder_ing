package com.yeonjin.android.yjscreenrecorder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by yeonjin.cho on 2018-05-02.
 */

public class EditorActivity extends Activity {

    private static String TAG = "[YJ] EditorActivity";

    private static String FILE_MP4 = "mp4";
    private static String FILE_3GP = "3gp";

    GridView mGv = null;
    GridViewAdapter mGAdapter = null;
    List mFileList = null;
    ArrayList<Bitmap> mBitmaps = new ArrayList<Bitmap>();

    String mFilePath = Environment.getExternalStorageDirectory().toString() + "/YJRecorder/";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        File file = new File( mFilePath);
        if(!file.exists()) {
            Log.e(TAG, "Not exist file in this directory");
            return;
        }
        mFileList = new ArrayList();

        File list[] = file.listFiles();
        for( int i=0; i<list.length; i++) {
            Uri uri = Uri.fromFile(list[i]);
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if(fileExtension.equals(FILE_MP4) || fileExtension.equals(FILE_3GP)) {
                mFileList.add( list[i].getName() );

                Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(Environment.getExternalStorageDirectory()+"/YJRecorder/" + list[i].getName(), MediaStore.Video.Thumbnails.FULL_SCREEN_KIND);
                if(bitmap == null) {
                    Log.e(TAG, "fail to create tumbnail " + list[i].getName());
                }
                else {
                    Log.d(TAG, "success to create tumbnail " + list[i].getName());
                }
                mBitmaps.add(bitmap);
            }
        }
        Log.d(TAG, "bitmap size " + mBitmaps.size());
        mGAdapter = new GridViewAdapter(getApplicationContext(), R.layout.row, mBitmaps);
        mGv = (GridView)findViewById(R.id.gv_thumbnail);
        mGv.setAdapter(mGAdapter);

        mGv.setOnItemClickListener(mListener);
        mGv.setOnItemLongClickListener(mLongClickListener);
    }

    AdapterView.OnItemClickListener mListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
            Log.d(TAG, "position : " + position);
            Intent intent = new Intent(EditorActivity.this, PlayActivity.class);
            intent.putExtra("filepath", mFilePath+mFileList.get(position));
            startActivity(intent);
        }
    };

    AdapterView.OnItemLongClickListener mLongClickListener = new AdapterView.OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
            Toast.makeText(getApplicationContext(), "Filename : " + mFileList.get(i), Toast.LENGTH_SHORT).show();
            return false;
        }
    };

}
