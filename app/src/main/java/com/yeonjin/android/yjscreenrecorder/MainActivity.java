package com.yeonjin.android.yjscreenrecorder;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;

/**
 * Created by yeonjin.cho on 2018-05-02.
 */

public class MainActivity extends Activity {
    private static String TAG = "[YJ] MainActivity";

    private Button mBtRecord = null;
    private Button mBtEdit = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate");

        mBtRecord = (Button)findViewById(R.id.bt_record);
        mBtEdit = (Button)findViewById(R.id.bt_edit);

        mBtRecord.setOnClickListener(mListener);
        mBtEdit.setOnClickListener(mListener);

    }

    View.OnClickListener mListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Intent intent;
            switch(view.getId()) {
                case R.id.bt_record :
                    intent = new Intent(MainActivity.this, RecorderActivity.class);
                    startActivity(intent);
                    break;
                case R.id.bt_edit:
                    intent = new Intent(MainActivity.this, EditorActivity.class);
                    startActivity(intent);
                    break;
            }
        }
    };
}
