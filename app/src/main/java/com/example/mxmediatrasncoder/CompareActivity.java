package com.example.mxmediatrasncoder;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

public class CompareActivity extends AppCompatActivity {

    final static String TAG = "VideoCompare";

    private static String szOutPath = null;
    private static String szPath = null;
    private static VideoView vd1 = null;
    private static VideoView vd2 = null;
    private static Button mPauseResume = null;
    private static TextView mOrginalInfo = null;
    private static TextView mTranscodingInfo = null;
    private static boolean bPause = false;

    private static int mOriginalWidth = 0;
    private static int mOriginalHeight = 0;
    private static int mWidth = 0;
    private static int mHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compare);

        Intent mIntent = getIntent();
        szPath = mIntent.getStringExtra("in_path");
        szOutPath = mIntent.getStringExtra("out_path");
        mOriginalWidth = mIntent.getIntExtra("org_width", 0);
        mOriginalHeight= mIntent.getIntExtra("org_height", 0);
        mWidth = mIntent.getIntExtra("width", 0);
        mHeight = mIntent.getIntExtra("height", 0);

        Log.d(TAG, "Original FilePath : " + szPath);
        Log.d(TAG, "Transcoding FilePath : " + szOutPath);

        vd1 = (VideoView)findViewById(R.id.v1);
        vd2 = (VideoView)findViewById(R.id.v2);

        mPauseResume = (Button)findViewById(R.id.pause_resume);

        mOrginalInfo = (TextView)findViewById(R.id.org_info);
        mTranscodingInfo = (TextView)findViewById(R.id.trs_info);

        String originalInfoText = "Original : " + mOriginalWidth + " x " + mOriginalHeight;
        String transInfoText = "Transcoding : " + mWidth + " x " + mHeight;
        mOrginalInfo.setText(originalInfoText);
        mTranscodingInfo.setText(transInfoText);

        vd1.setVideoURI(Uri.parse(szPath));
        vd2.setVideoURI(Uri.parse(szOutPath));

        vd1.start();
        vd2.start();

        mPauseResume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bPause == true) {
                    bPause = false;
                    mPauseResume.setText("PAUSE");
                    vd1.start();
                    vd2.start();
                } else {
                    bPause = true;
                    mPauseResume.setText("RESUME");
                    vd1.pause();
                    vd2.pause();
                }
            }
        });
    }
}
