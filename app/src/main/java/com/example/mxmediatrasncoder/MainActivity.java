package com.example.mxmediatrasncoder;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity {

    final static String TAG = "Transcoder";
    final static String MAJ_VER = "0";
    final static String MIN_VER = "1";
    final static String PAT_VER = "3";

    private static TranscoderThread mTranscoder = null;

    private static boolean bSelectFile = false;
    private static boolean bForceStop = false;
    private static boolean bUseGoogleEncoder = false;

    private static String szPath = Environment.getExternalStorageDirectory()+"/Download/Sintel_1080p.mp4";
    private static String szOutFileName = null;
    private static String szDeviceInfo = null;

    private static TextView textPath = null;
    private static TextView txtTranscodingRate = null;
    private static TextView deviceInformation = null;

    private static Button btnSelectFile = null;
    private static RadioGroup selectResolutionGroup = null;

    private static EditText editBitRate = null;
    private static EditText editFrameRate = null;
    private static EditText editFrameInterval = null;

    private static EditText editComplexityLower = null;
    private static EditText editComplexityUpper = null;

    private static EditText editQualityLower = null;
    private static EditText editQualityUpper = null;

    private static RadioGroup selectBitRateModeGroup = null;

    private static Spinner spinnerEncoder = null;

    private static  Button btnStartTranscoding = null;
    private static  Button btnStopTranscoding = null;
    private static  Button btnCompareVideoFile = null;

    private static ProgressBar proTranscodingRate = null;
    private static Handler handler;
    private static final int MESSAGE_PROGRESSBAR_UPDATE = 1;
    private static final int MESSAGE_PROCESS_WORK_END = 2;

    //default setting value for video encoder
    private static final String OUTPUT_VIDEO_MIME_TYPE = "video/avc";
    private static final int OUTPUT_VIDEO_BIT_RATE = 15000000;   //15MB
    private static final int OUTPUT_VIDEO_FRAME_RATE = 30;
    private static final int OUTPUT_VIDEO_IFRAME_INTERVAL = 1;
    private static final int OUTPUT_VIDEO_WIDTH = 1280;
    private static final int OUTPUT_VIDEO_HEIGHT = 720;
    private static final int OUTPUT_VIDEO_COLOR_FORMAT = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;

    private static int mOriginalWidth = 0;
    private static int mOriginalHeight = 0;

    private static int mWidth = OUTPUT_VIDEO_WIDTH;
    private static int mHeight = OUTPUT_VIDEO_HEIGHT;
    private static int mVideoBitRate = 0;
    private static int mVideoFrameRate = 0;
    private static int mFrameInterval = 0;
    private static int mComplexityRange = 0;
    private static int mQualityRange = 0;
    private static boolean mBitRateModeCBR = false;
    private static boolean mBitRateModeVBR = false;
    private static boolean mBitRateModeCQ = false;

    private static ArrayList<String> encoderList = new ArrayList<>();
    private static ArrayAdapter spinnerAdapter;
    private static String szSelectedEncoderName = null;

    private static final int EDITOR_DEMO_PERMISSION_REQUEST_STORAGE = 100;
    PowerManager.WakeLock wakeLock = null;

    Context mContext;
    Activity mActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getApplicationContext();
        mActivity = MainActivity.this;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        PowerManager pm = (PowerManager) getBaseContext().getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, MainActivity.class.getName());
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire();

        setContentView(R.layout.activity_main);

        Log.d(TAG, "Media Transcoder VERSION : " + MAJ_VER + "." + MIN_VER + "." + PAT_VER);

        setUserInterfaceResource();

        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_PROGRESSBAR_UPDATE:
                        txtTranscodingRate.setText(msg.arg1 + " %");
                        if (msg.arg1 == 100) {
                            Toast.makeText(getApplicationContext(), "Transcoding completed!", Toast.LENGTH_SHORT).show();
                        }
                        break;

                    case MESSAGE_PROCESS_WORK_END:
                        releaseTranscoderResource();
                        if (!bForceStop) {
                            btnCompareVideoFile.setEnabled(true);
                        }
                        mTranscoder = null;
                        bForceStop = false;
                        break;
                }
            }
        };

        btnSelectFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent( );
                intent.setType("video/*");
                intent.setAction(Intent.ACTION_PICK);
                mActivity.startActivityForResult(intent, 100);
                btnCompareVideoFile.setEnabled(false);
            }
        });

        btnStartTranscoding.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "startTranscoder........");

                getEncodingOption();

                startTranscoder();
                btnStartTranscoding.setEnabled(false);
                btnStopTranscoding.setEnabled(true);
            }
        });

        btnStopTranscoding.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "stopTranscoder........");
                bForceStop = true;
                stopTranscoder();
            }
        });

        btnCompareVideoFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "compare original and transcoding video file!");
                String TransPath = Environment.getExternalStorageDirectory() + "/Download/" + szOutFileName;

                Intent intent = new Intent(getBaseContext(), CompareActivity.class);
                intent.putExtra("in_path", szPath);
                intent.putExtra("out_path", TransPath);
                intent.putExtra("org_width", mOriginalWidth);
                intent.putExtra("org_height", mOriginalHeight);
                intent.putExtra("width", mWidth);
                intent.putExtra("height", mHeight);

                startActivity(intent);
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            checkPermission();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK)
        {
            switch (requestCode) {
                case 100:
                    Uri selectedMediaUri = data.getData();
                    //szPath = getRealPathFromURI(mContext, selectedMediaUri);
                    szPath = FileUtils.getPath(this, selectedMediaUri);
                    if (szPath != null) {
                        textPath.setText(szPath);

                        /*
                         * 파일 선택한 후 파일정보 및 encoder 옵션 설정하게 해주는 화면설정.
                         */
                        setCapabilityScreen();

                        /*
                         * 단말에서 지원하는 mime type의 video encoder list를 위해서 spinner를 array 사용하게 해준다.
                         */
                        spinnerAdapter = new ArrayAdapter(this, R.layout.support_simple_spinner_dropdown_item, encoderList);
                        spinnerEncoder.setAdapter(spinnerAdapter);

                        bSelectFile = true;
                    } else {
                        textPath.setText("Please select video file!");
                    }

                default:
                    break;
            }
        }
    }

    private void setUserInterfaceResource() {
        /*
         * 각 UI handle을 얻어 놓는다.
         */
        textPath                = (TextView)findViewById(R.id.file_path);
        deviceInformation       = (TextView)findViewById(R.id.device_information);
        selectResolutionGroup   = (RadioGroup)findViewById(R.id.select_resolution);
        editBitRate             = (EditText)findViewById(R.id.video_bitrate);
        editFrameRate           = (EditText)findViewById(R.id.video_framerate);
        editFrameInterval       = (EditText)findViewById(R.id.video_frameinterval);
        editComplexityLower     = (EditText)findViewById(R.id.complexity_lower);
        editComplexityUpper     = (EditText)findViewById(R.id.complexity_upper);
        editQualityLower        = (EditText)findViewById(R.id.quality_lower);
        editQualityUpper        = (EditText)findViewById(R.id.quality_upper);

        selectBitRateModeGroup = (RadioGroup)findViewById(R.id.select_bitratemode);

        /*
         * encoder 선택 할 수 있게 해준다.
         */
        spinnerEncoder          = (Spinner)findViewById(R.id.spinner_encoder);

        txtTranscodingRate      = (TextView)findViewById(R.id.text2);
        proTranscodingRate      = (ProgressBar)findViewById(R.id.progressBar);

        btnSelectFile           = (Button)findViewById(R.id.select_file);
        btnStartTranscoding     = (Button)findViewById(R.id.start_transcoder);
        btnStopTranscoding      = (Button)findViewById(R.id.stop_transcoder);
        btnCompareVideoFile     = (Button)findViewById(R.id.compare_video);

        textPath.setText("Please Select Media File!");

        /*
         * 컨텐츠 선택하기 전까지 모든 UI widget disable 시켜 놓는다.
         */
        for (int i = 0; i < selectBitRateModeGroup.getChildCount(); i++) {
            ((RadioButton)selectBitRateModeGroup.getChildAt(i)).setEnabled(false);
        }

        spinnerEncoder.setEnabled(false);

        for (int i = 0; i < selectResolutionGroup.getChildCount(); i++) {
            ((RadioButton)selectResolutionGroup.getChildAt(i)).setEnabled(false);
        }

        //disable widget before selecting file.
        editBitRate.setEnabled(false);
        editFrameRate.setEnabled(false);
        editFrameInterval.setEnabled(false);
        editComplexityLower.setEnabled(false);
        editComplexityUpper.setEnabled(false);
        editQualityLower.setEnabled(false);
        editQualityUpper.setEnabled(false);

        btnStartTranscoding.setEnabled(false);
        btnStopTranscoding.setEnabled(false);
        btnCompareVideoFile.setEnabled(false);

        deviceInformation.setVisibility(View.INVISIBLE);
    }

    private void getEncodingOption() {
        /* 사용자 encoding 옵션 설정값을 가져온다. */
        int ResolutionId = selectResolutionGroup.getCheckedRadioButtonId();
        RadioButton selectResolution = (RadioButton)findViewById(ResolutionId);
        String regText = selectResolution.getText().toString();
        switch (regText) {
            case "org":
                mWidth = 0;
                mHeight = 0;
                break;

            case "540":
                mWidth = 960;
                mHeight = 540;
                break;

            case "720":
                mWidth = 1280;
                mHeight = 720;
                break;

            case "1080":
                mWidth = 1920;
                mHeight = 1080;
                break;

            case "1440":
                mWidth = 2560;
                mHeight = 1440;
                break;

            case "2160":
                mWidth = 3840;
                mHeight = 2160;
                break;
        }

        String sTemp = editBitRate.getText().toString();
        if (sTemp.getBytes().length <= 0) {
            mVideoBitRate = 0;
        } else {
            mVideoBitRate = Integer.parseInt(editBitRate.getText().toString());
        }

        sTemp = editFrameRate.getText().toString();
        if (sTemp.getBytes().length <= 0) {
            mVideoFrameRate = 0;
        } else {
            mVideoFrameRate = Integer.parseInt(editFrameRate.getText().toString());
        }

        sTemp = editFrameInterval.getText().toString();
        if (sTemp.getBytes().length <= 0) {
            mFrameInterval = 0;
        } else {
            mFrameInterval = Integer.parseInt(editFrameInterval.getText().toString());
        }

        sTemp = editComplexityUpper.getText().toString();
        if (sTemp.getBytes().length <= 0) {
            mComplexityRange = 0;
        } else {
            mComplexityRange = Integer.parseInt(editComplexityUpper.getText().toString());
        }

        sTemp = editQualityUpper.getText().toString();
        if (sTemp.getBytes().length <= 0) {
            mQualityRange = 0;
        } else {
            mQualityRange = Integer.parseInt(editQualityUpper.getText().toString());
        }

        int BitRateModeId = selectBitRateModeGroup.getCheckedRadioButtonId();
        if (BitRateModeId >= 0) {
            RadioButton selectBitrateMode = (RadioButton)findViewById(BitRateModeId);
            String selectText = selectBitrateMode.getText().toString();
            switch (selectText) {
                case "CBR":
                    mBitRateModeCBR = true;
                    mBitRateModeVBR = false;
                    mBitRateModeCQ = false;
                    break;

                case "VBR":
                    mBitRateModeVBR = true;
                    mBitRateModeCBR = false;
                    mBitRateModeCQ = false;
                    break;

                case "CQ":
                    mBitRateModeCQ = true;
                    mBitRateModeCBR = false;
                    mBitRateModeVBR = false;
                    break;

                default:
                    mBitRateModeCBR = false;
                    mBitRateModeVBR = false;
                    mBitRateModeCQ = false;
            }
        }

        // 선택된 encoder name 가져오기.
        szSelectedEncoderName = spinnerEncoder.getSelectedItem().toString();
        if (szSelectedEncoderName != null)
            Log.d(TAG, "selected video encoder name : " + szSelectedEncoderName);
    }

    private void setCapabilityScreen() {
        /*
         * 컨텐츠를 선택 했으면 RadioButton에서 해상도 설정할 수 있게 변경해준다.
         * encoder/decoder max support resolution check해서 radioButton enable/disable 시켜준다.
         */
        for (int i = 0; i < selectResolutionGroup.getChildCount(); i++) {
            ((RadioButton)selectResolutionGroup.getChildAt(i)).setEnabled(true);

            //set contents original resolution by default.
            if (i == 0)
                ((RadioButton)selectResolutionGroup.getChildAt(i)).setChecked(true);
        }

        editBitRate.setEnabled(true);
        editFrameRate.setEnabled(true);
        editFrameInterval.setEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaExtractor infoExtractor = new MediaExtractor();
            try {
                infoExtractor.setDataSource(szPath);
            } catch (IOException e) {
                e.printStackTrace();
            }

            MediaFormat infoFormat = selectVideoTrack(infoExtractor, "video/");
            MediaCodecInfo capCodecInfo = selectCodec(OUTPUT_VIDEO_MIME_TYPE);
            MediaCodecInfo.CodecCapabilities codecCapabilities = capCodecInfo.getCapabilitiesForType(OUTPUT_VIDEO_MIME_TYPE);
            MediaCodecInfo.EncoderCapabilities encoderCapabilities = codecCapabilities.getEncoderCapabilities();

            //szDeviceInfo = "//////////////////////// Information ////////////////////////\n";
            //szDeviceInfo += "Content Resolution : " + infoFormat.getInteger(MediaFormat.KEY_WIDTH) + "x" + infoFormat.getInteger(MediaFormat.KEY_HEIGHT) + "\n";

            editComplexityLower.setEnabled(false);
            editComplexityUpper.setEnabled(true);
            editComplexityLower.setText(encoderCapabilities.getComplexityRange().getLower().toString());
            editComplexityUpper.setText(encoderCapabilities.getComplexityRange().getUpper().toString());

            //szDeviceInfo += "Encoder Complexity Range : " + encoderCapabilities.getComplexityRange().getLower().toString() + "~" + encoderCapabilities.getComplexityRange().getUpper().toString() + "\n";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                editQualityLower.setEnabled(false);
                editQualityUpper.setEnabled(true);
                editQualityLower.setText(encoderCapabilities.getQualityRange().getLower().toString());
                editQualityUpper.setText(encoderCapabilities.getQualityRange().getUpper().toString());

                //szDeviceInfo += "Encoder Quality Range : " + encoderCapabilities.getQualityRange().getLower().toString() + "~" + encoderCapabilities.getQualityRange().getUpper().toString() + "\n";
            }

            //szDeviceInfo += "Encoder Support BitRate Mode\n";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)) {
                    ((RadioButton)selectBitRateModeGroup.getChildAt(0)).setEnabled(true);
                    //szDeviceInfo += "CBR support\n";
                } else {
                    //szDeviceInfo += "CBR not support\n";
                }

                if (encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)) {
                    ((RadioButton)selectBitRateModeGroup.getChildAt(1)).setEnabled(true);
                    //szDeviceInfo += "VBR support\n";
                } else {
                    //szDeviceInfo += "VBR not support\n";
                }

                if (encoderCapabilities.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ)) {
                    ((RadioButton)selectBitRateModeGroup.getChildAt(2)).setEnabled(true);
                    //szDeviceInfo += "CQ support\n";
                } else {
                    //szDeviceInfo += "CQ not support\n";
                }

                //encoder capability에서 지원하지 않아도 한번 적용 할 수 있게 해본다.
                //((RadioButton)selectBitRateModeGroup.getChildAt(0)).setEnabled(true);
                //((RadioButton)selectBitRateModeGroup.getChildAt(1)).setEnabled(true);
                //((RadioButton)selectBitRateModeGroup.getChildAt(2)).setEnabled(true);
            }

            getCodecList(OUTPUT_VIDEO_MIME_TYPE);
            spinnerEncoder.setEnabled(true);

            if (infoExtractor != null) {
                try {
                    infoExtractor.release();
                    infoExtractor = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            //deviceInformation.setVisibility(View.VISIBLE);
            //deviceInformation.setText(szDeviceInfo);

            btnStartTranscoding.setEnabled(true);
        }
    }

    void releaseTranscoderResource() {
        Log.d(TAG, "call releaseTranscoderResource()");

        textPath.setText("no selected");

        /*
         * 컨텐츠 선택하기 전까지 모든 UI widget disable 시켜 놓는다.
         */
        for (int i = 0; i < selectBitRateModeGroup.getChildCount(); i++) {
            ((RadioButton)selectBitRateModeGroup.getChildAt(i)).setChecked(false);
            ((RadioButton)selectBitRateModeGroup.getChildAt(i)).setEnabled(false);
        }
        selectBitRateModeGroup.clearCheck();

        mVideoBitRate   = 0;
        mVideoFrameRate = 0;
        mFrameInterval  = 0;

        mBitRateModeCBR = false;
        mBitRateModeVBR = false;
        mBitRateModeCQ  = false;

        for (int i = 0; i < selectResolutionGroup.getChildCount(); i++) {
            ((RadioButton)selectResolutionGroup.getChildAt(i)).setEnabled(false);
            if (i == 0) {
                ((RadioButton)selectResolutionGroup.getChildAt(i)).setChecked(true);
            }
        }

        spinnerEncoder.setEnabled(false);

        /*
         * encoderList에 저장된 item을 삭제해준다.
         */
        encoderList.clear();

        //disable widget before selecting file.
        editBitRate.setEnabled(false);
        editBitRate.setHint("Default:15,000,000(15MB)");

        editFrameRate.setEnabled(false);
        editFrameRate.setHint("Default:30fps");

        editFrameInterval.setEnabled(false);
        editFrameInterval.setHint("Default:1");

        editComplexityLower.setEnabled(false);
        editComplexityLower.setHint("min");
        editComplexityUpper.setEnabled(false);
        editComplexityUpper.setHint("max");

        editQualityLower.setEnabled(false);
        editQualityLower.setHint("min");
        editQualityUpper.setEnabled(false);
        editQualityUpper.setHint("max");

        btnStartTranscoding.setEnabled(false);
        btnStopTranscoding.setEnabled(false);
        btnCompareVideoFile.setEnabled(false);

        txtTranscodingRate.setText("0 %");
        proTranscodingRate.setProgress(0);

        //deviceInformation.setText("");
        //deviceInformation.setVisibility(View.INVISIBLE);

        bSelectFile = false;
        Log.d(TAG, "release Resource end...");
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop() calling...");

        if (bSelectFile == true) {
            Log.d(TAG, "stopTranscoder.....");
            stopTranscoder();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy() calling...");
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void checkPermission() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // Explain to the user why we need to write the permission.
                Toast.makeText(mContext, "Read/Write external storage", Toast.LENGTH_SHORT).show();
            }

            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    EDITOR_DEMO_PERMISSION_REQUEST_STORAGE);
        }
    }

    void startTranscoder() {
        if (mTranscoder == null) {
            mTranscoder = new TranscoderThread(szPath);
            mTranscoder.start();
        }
    }

    void stopTranscoder() {
        if (mTranscoder == null)
            return;

        mTranscoder.bThreadStop = true;

        /*
         * 2020-11-28 : 여기서 기다리면 Main Thread에 의해서 스케쥴링 영향을 받아 OpenGL onFrameAvailable() 호출이 지연되어 crash가 발생한다. -_-;
         *              Thread가 종료될때 main thread로 handler 이벤트를 보내게 해서 releaseTranscoderResource() 호출하게 해줬음.
         */

        //releaseTranscoderResource();
    }

    private class TranscoderThread extends Thread {
        private MediaExtractor extractor = null;
        private MediaCodec encoder = null;
        private MediaCodec decoder = null;
        private MediaMuxer muxer = null;
        private MediaCodecInfo codecInfo = null;
        private boolean bThreadStop = false;

        private OutputSurface outputSurface = null;
        private InputSurface inputSurface = null;

        private String mPath;
        private int trackIndex = 0;
        private long totalMediaTime = 0;

        MediaFormat inputFormat, outputFormat;

        public TranscoderThread(String path) {
            Log.d(TAG, "Created TranscoderThread...!");
            mPath = path;
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void run() {
            super.run();

            /*
             * 미디어파일 정보를 읽어 오기 위해서 MediaExtractor를 생성한다.
             */
            extractor = new MediaExtractor();
            try {
                Log.d(TAG, "Init MediaExtractor, path=" + mPath);
                extractor.setDataSource(mPath);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            /*
             * 원본 미디어파일에서 Transcoding 할 video track을 선택한다.
             */
            inputFormat = selectVideoTrack(extractor, "video/");
            if (inputFormat == null) {
                Log.d(TAG, "input format null! go to stop!");
                return;
            }

            Log.d(TAG, "width=" + inputFormat.getInteger(MediaFormat.KEY_WIDTH) + ", height=" + inputFormat.getInteger(MediaFormat.KEY_HEIGHT));
            Log.d(TAG, "mimeType=" + inputFormat.getString(MediaFormat.KEY_MIME) + ", TotalTime=" + inputFormat.getLong(MediaFormat.KEY_DURATION));

            mOriginalWidth = inputFormat.getInteger(MediaFormat.KEY_WIDTH);
            mOriginalHeight= inputFormat.getInteger(MediaFormat.KEY_HEIGHT);
            totalMediaTime = inputFormat.getLong(MediaFormat.KEY_DURATION);

            if (mWidth == 0 || mHeight == 0) {
                mWidth = mOriginalWidth;
                mHeight = mOriginalHeight;
            }

            outputFormat = createOutputFormat(inputFormat);
            if (outputFormat == null) {
                Log.d(TAG, "output format null! go to stop!");
                return;
            }

            /*
             * Encoder를 만든다.
             * UI에서 선택한 encoder로 만든다.
             */
            try {
                encoder = MediaCodec.createByCodecName(szSelectedEncoderName);
            } catch (IOException e) {
                e.printStackTrace();
            }
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            /*
             * extractor > decoder > transcoder > encoder > muxer
             * 위 처리를 위한 input/output surface를 만든다.
             */
            AtomicReference<Surface> inputSurfaceReference = new AtomicReference<Surface>();
            inputSurfaceReference.set(encoder.createInputSurface());
            inputSurface = new InputSurface(inputSurfaceReference.get());
            inputSurface.makeCurrent();

            outputSurface = new OutputSurface();

            /*
             * Decoder를 만든다.
             */
            try {
                decoder = MediaCodec.createDecoderByType(OUTPUT_VIDEO_MIME_TYPE);
            } catch (IOException e) {
                e.printStackTrace();
            }

            /*
             * encoder에서 만든 surface를 decoder의 surface에 mapping 해준다.
             */
            decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);

            /*
             * Encoding된 frame을 video file로 저장하 위해서 MediaMuxer를 만든다.
             */
            szOutFileName = "trans_" + szSelectedEncoderName + "_" + mWidth + "_" + mHeight + "_" + mVideoFrameRate + "_" + mFrameInterval + "_";
            if (mBitRateModeCBR == true) szOutFileName += "CBR_";
            else if (mBitRateModeVBR == true) szOutFileName += "VBR_";
            else if (mBitRateModeCQ == true) szOutFileName += "CQ_";
            else Log.d(TAG, "BitRate mode : nothing setting");

            szOutFileName += mPath.substring(mPath.lastIndexOf("/")+1);
            Log.d(TAG, "output file name : " + szOutFileName);

            try {
                muxer = new MediaMuxer(new File(Environment.getExternalStorageDirectory() + "/Download", szOutFileName).getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } catch (IOException e) {
                e.printStackTrace();
            }

            /*
             * encoder/decoder start
             */
            encoder.start();
            decoder.start();

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            boolean allInputExtracted = false;
            boolean allInputDecoded = false;
            boolean allOutputEncoded = false;

            while (!allOutputEncoded) {

                if (bThreadStop) {
                    Log.d(TAG, "Transcoding process end....");
                    break;
                }

                /*
                 * Decoder에 input video frame을 넣는다.
                 */
                if (!allInputExtracted) {
                    int inputBufferId = decoder.dequeueInputBuffer(3000);
                    Log.d(TAG, "deocder : this routine is that push the video frame to video decoder, inputBufferId=" + inputBufferId);
                    if (inputBufferId >= 0) {
                        Log.d(TAG, "deoder : return dequeueInputBuffer(" + inputBufferId + ")");
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);

                        if (sampleSize >= 0) {
                            Log.d(TAG, "decoder : queueInputBuffer : frame size=" + sampleSize + ", pts=" + extractor.getSampleTime() + ", index=" + extractor.getSampleTrackIndex());
                            decoder.queueInputBuffer(inputBufferId, 0, sampleSize, extractor.getSampleTime(), extractor.getSampleFlags());
                            extractor.advance();
                        } else {
                            Log.d(TAG, "decoder : queueInputBuffer(end of stream)");
                            decoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            allInputExtracted = true;
                        }
                    }
                }

                boolean encoderOutputAvailable = true;
                boolean decoderOutputAvailable = !allInputDecoded;

                while (encoderOutputAvailable || decoderOutputAvailable) {
                    /*
                     * Encoder에서 data를 꺼내서 muxer에 넣어준다.
                     */
                    int encoderOutputBufferId = encoder.dequeueOutputBuffer(bufferInfo, 3000);
                    Log.d(TAG, "encoder : this routine is that push the video image to media muxer, encoderOutputBufferId=" + encoderOutputBufferId);
                    if (encoderOutputBufferId >= 0) {
                        int progressRate = 0;

                        /*
                         * encoder의 bufferInfo.size가 0이란건 encoding작업이
                         * 모두 완료 됐다는 뜻이다. End Of Stream 처리함.
                         */
                        if (bufferInfo.size == 0) {
                            progressRate = 100;
                        } else {
                            progressRate = getTranscodingProgressRate(bufferInfo, totalMediaTime);
                        }
                        proTranscodingRate.setProgress(progressRate);

                        //update percentage rate
                        Message msg = handler.obtainMessage();
                        msg.what = MESSAGE_PROGRESSBAR_UPDATE;
                        msg.arg1 = progressRate;
                        handler.sendMessage(msg);

                        ByteBuffer encodedBuffer = encoder.getOutputBuffer(encoderOutputBufferId);

                        Log.d(TAG, "muxer : write video image to media muxer, bufferInfo.size=" + bufferInfo.size + ", bufferInfo.offset=" + bufferInfo.offset + ", bufferInfo.presentationTimeUs=" + bufferInfo.presentationTimeUs);
                        muxer.writeSampleData(trackIndex, encodedBuffer, bufferInfo);
                        encoder.releaseOutputBuffer(encoderOutputBufferId, false);

                        /*
                         * MPEG4 포맷의 경우, BUFFER_FLAG_END_OF_STREAM 플래그와 함께 빈 버퍼(bufferInfo.size = 0) 을 전달하여 트랙 내 마지막 샘플을 설정할 수 있다.
                         * 즉, 이 과정이 모두 끝나면 작업이 모두 완료되었다고 가정함.
                         */
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d(TAG, "muxer : end of stream - write video image done!");
                            allOutputEncoded = true;
                            break;
                        }
                    } else if (encoderOutputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        encoderOutputAvailable = false;
                    } else if (encoderOutputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.d(TAG, "muxer : return encoder.INFO_OUTPUT_FORMAT_CHANGED : start media muxer");
                        trackIndex = muxer.addTrack(encoder.getOutputFormat());
                        muxer.start();
                    }

                    if (encoderOutputBufferId != MediaCodec.INFO_TRY_AGAIN_LATER) {
                        Log.d(TAG, "encoder : INFO_TRY_AGAIN_LATER");
                        continue;
                    }

                    /*
                     * Decoder의 output을 읽어서 encoder에 넣어준다.
                     */
                    if (!allInputDecoded) {
                        int decoderOutputBufferId = decoder.dequeueOutputBuffer(bufferInfo, 3000);
                        Log.d(TAG, "decoder : this routine is that video decoder get the output image, decoderOutputBufferId=" + decoderOutputBufferId + ", timeStamp=" + bufferInfo.presentationTimeUs / 1000);
                        if (decoderOutputBufferId >= 0) {
                            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                decoder.releaseOutputBuffer(decoderOutputBufferId, false);
                                break;
                            } else {
                                boolean render = bufferInfo.size > 0 ? true : false;

                                Log.d(TAG, "decoder : push the video image to video encoder");
                                decoder.releaseOutputBuffer(decoderOutputBufferId, render);
                                if (render) {
                                    inputSurface.makeCurrent();

                                    outputSurface.awaitNewImage();
                                    outputSurface.drawImage();

                                    inputSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000);
                                    inputSurface.swapBuffers();
                                }
                            }

                            /*
                             * Decoder의 출력을 Texture에 push하고 onFrameAvailable()가 호출된다.
                             * render가 true이고, onFrameAvailable가 호출되면 Surface에 작업을 한다(openGL)
                             */
                            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                allInputDecoded = true;
                                encoder.signalEndOfInputStream();
                            }
                        } else if (decoderOutputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            Log.d(TAG, "decoder : return INFO_TRY_AGAIN_LATER");
                            decoderOutputAvailable = false;
                        } else if (decoderOutputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            Log.d(TAG, "decoder : INFO_OUTPUT_FORMAT_CHANGED");
                            outputFormat = decoder.getOutputFormat();
                        }
                    }
                }
            }

            Log.d(TAG, "Transcoder ending.... try to release resource");
            try {
                if (extractor != null) {
                    extractor.release();
                    extractor = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (decoder != null) {
                    decoder.stop();
                    decoder.release();
                    decoder = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (encoder != null) {
                    encoder.stop();
                    encoder.release();
                    encoder = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (muxer != null) {
                    muxer.stop();
                    muxer.release();
                    muxer = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (inputSurface != null) {
                    inputSurface.release();
                    inputSurface = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                if (outputSurface != null) {
                    outputSurface.release();
                    outputSurface = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Message msg = handler.obtainMessage();
            msg.what = MESSAGE_PROCESS_WORK_END;
            msg.arg1 = 0;
            handler.sendMessage(msg);

            Log.d(TAG, "Transcoder ended!");
            bThreadStop = false;
        }
    }

    private static MediaFormat createOutputFormat(MediaFormat inputFormat) {
        MediaFormat outFormat = MediaFormat.createVideoFormat(OUTPUT_VIDEO_MIME_TYPE, mWidth, mHeight);
        if (outFormat == null) {
            return null;
        }

        Log.d(TAG, "createOutputFormat - bitRate : " + mVideoBitRate + ", mVideoFrameRate : " + mVideoFrameRate + ", mFrameInteval : " + mFrameInterval);

        outFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, OUTPUT_VIDEO_COLOR_FORMAT);
        if (mVideoBitRate != 0) {
            outFormat.setInteger(MediaFormat.KEY_BIT_RATE, mVideoBitRate);
        } else {
            outFormat.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_VIDEO_BIT_RATE);
            mVideoBitRate = OUTPUT_VIDEO_BIT_RATE;
        }

        if (mVideoFrameRate != 0) {
            outFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mVideoFrameRate);
        } else {
            outFormat.setInteger(MediaFormat.KEY_FRAME_RATE, OUTPUT_VIDEO_FRAME_RATE);
            mVideoFrameRate = OUTPUT_VIDEO_FRAME_RATE;
        }

        if (mFrameInterval != 0) {
            outFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mFrameInterval);
        } else {
            outFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, OUTPUT_VIDEO_IFRAME_INTERVAL);
            mFrameInterval = OUTPUT_VIDEO_IFRAME_INTERVAL;
        }

        outFormat.setInteger(MediaFormat.KEY_COMPLEXITY, mComplexityRange);
        outFormat.setInteger(MediaFormat.KEY_QUALITY, mQualityRange);

        if (mBitRateModeCBR == true)
            outFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        else if (mBitRateModeVBR == true)
            outFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        else if (mBitRateModeCQ == true)
            outFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
        else
            Log.d(TAG, "no setting BitRateMode");

        Log.d(TAG, "output video format : " + outFormat);
        return outFormat;
    }

    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.d(TAG, "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    private static MediaFormat selectVideoTrack(MediaExtractor extractor, String mimeType) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);

            if (mime.startsWith("video/")) {
                extractor.selectTrack(i);
                return format;
            }
        }
        Log.d(TAG, "Can't select video track!");
        return null;
    }

    private static void getCodecList(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    encoderList.add(codecInfo.getName());
                }
            }
        }
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private static int getTranscodingProgressRate(MediaCodec.BufferInfo info, long TimeStamp) {
        //Log.d(TAG, "Current BufferInfo.presentationTimeUs = " + info.presentationTimeUs);

        long initValue = TimeStamp / 1000;
        long currentTs = info.presentationTimeUs / 1000;
        long uProgress = (currentTs * 100) / initValue;

        Log.d(TAG, "current progress=" + uProgress);
        return (int)uProgress;
    }
}