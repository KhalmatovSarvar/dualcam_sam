package com.herohan.uvcdemo;

import android.Manifest;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcapp.VideoCapture;
import com.herohan.uvcdemo.fragment.DeviceListDialogFragment;
import com.herohan.uvcdemo.fragment.VideoFormatDialogFragment;
import com.herohan.uvcdemo.utils.Permissions;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.utils.FileUtils;
import com.serenegiant.utils.UriHelper;
import com.serenegiant.widget.AspectRatioSurfaceView;
import com.serenegiant.widget.CameraViewInterface;
import com.serenegiant.widget.UVCCameraTextureView;

import java.io.File;
import java.util.Calendar;


public class RecordVideoActivity extends AppCompatActivity implements View.OnClickListener {

    private static final boolean DEBUG = true;

    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;

    private final Object mSync = new Object();

    private boolean mIsCameraConnected = false;

    private static final int PERMISSION_REQUEST_CODE = 4;


    private int mPreviewWidth = DEFAULT_WIDTH;
    /**
     * Camera preview height
     */
    private int mPreviewHeight = DEFAULT_HEIGHT;
    private final Handler handler = new Handler();


    private ICameraHelper mCameraHelper;

    private UVCCameraTextureView mCameraViewMain;

    private ImageButton bthCaptureVideo;

    private UsbDevice mUsbDevice;

    private DeviceListDialogFragment mDeviceListDialog;
    private VideoFormatDialogFragment mVideoFormatDialog;

    private Runnable startPeriodTaskAndStop;
    private Runnable stopPeriodTaskAndStart;

    private long savingTime = 760L;
    boolean isExceedMilliseconds = false;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_video);
        setTitle(R.string.entry_record_video);

        initPermissions();
        initViews();
        initListeners();
        initRunnables();
//        showDeviceListDialog();

    }

    private void initRunnables() {
        stopPeriodTaskAndStart = () -> {
            stopRecord();
            handler.postDelayed(startPeriodTaskAndStop, savingTime);
        };

        startPeriodTaskAndStop = () -> {
            Log.d("TTT", "startPeriodTaskAndStop: I am here");
            Calendar calendar = Calendar.getInstance();
            if (calendar.get(Calendar.MILLISECOND) >= 800) {
                isExceedMilliseconds = true;
            }
            startRecord();
            if (isExceedMilliseconds) {
                Log.d("TTT", "isExceedMilliseconds: EXCEEDED");
                handler.postDelayed(stopPeriodTaskAndStart, 59_500 - savingTime);
                isExceedMilliseconds = false;
            } else {
                Log.d("TTT", "isExceedMilliseconds:  NOT EXCEEDED");
                handler.postDelayed(stopPeriodTaskAndStart, 60_000 - savingTime);
            }
        };
    }

    private void initListeners() {

        ImageView ivVideoFormat = findViewById(R.id.ic_video_format);
        ivVideoFormat.setOnClickListener(this);

        ImageView btnSettings = findViewById(R.id.ic_options);
        btnSettings.setOnClickListener(this);
    }

    private void initPermissions() {
            boolean hasPermissions = Permissions.checkPermissions(this);
            if (hasPermissions) {
                // Barcha huquqlar olingan
            } else {
                // Huquqlarni so'raymiz
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, PERMISSION_REQUEST_CODE);
            }
        }


    @Override
    public void onClick(View v) {

        if (v.getId() == R.id.bthCaptureVideo) {
            handler.removeCallbacks(startPeriodTaskAndStop);
            handler.removeCallbacks(stopPeriodTaskAndStart);

            if (mCameraHelper != null) {
                if (mCameraHelper.isRecording()) {
                    stopRecord();
                } else {
                    startRecordEveryMinute();
                }
            }
        }

        if (v.getId() == R.id.ic_video_format){
            showVideoFormatDialog();
        } else if (v.getId()==R.id.ic_options) {
            showDeviceListDialog();
        }

    }

    private void startRecordEveryMinute() {
        Calendar calendar = Calendar.getInstance();
        long currentSecond = calendar.get(Calendar.SECOND);
        long currentMilliSecond = calendar.get(Calendar.MILLISECOND);
        Log.d("TTT", "currentMilliSecond: " + currentMilliSecond);

        // Calculate the delay until the next minute
        long delay = 60_000 - (currentSecond*1000+currentMilliSecond);
        startRecord();

        handler.postDelayed(stopPeriodTaskAndStart, delay);

    }

    private void showVideoFormatDialog() {
        if (mVideoFormatDialog != null && mVideoFormatDialog.isAdded()) {
            return;
        }

        mVideoFormatDialog = new VideoFormatDialogFragment(
                mCameraHelper.getSupportedFormatList(),
                mCameraHelper.getPreviewSize());

        mVideoFormatDialog.setOnVideoFormatSelectListener(size -> {
            if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                mCameraHelper.stopPreview();
                mCameraHelper.setPreviewSize(size);
                mCameraHelper.startPreview();

                resizePreviewView(size);
            }

        });

        mVideoFormatDialog.show(getSupportFragmentManager(), "video format dialog");

    }

    private void resizePreviewView(Size size) {
        // Update the preview size
        mPreviewWidth = size.width;
        mPreviewHeight = size.height;
        // Set the aspect ratio of SurfaceView to match the aspect ratio of the camera
        mCameraViewMain.setAspectRatio(mPreviewWidth, mPreviewHeight);
        startRecordEveryMinute();
    }

    private void showDeviceListDialog() {
        mDeviceListDialog = new DeviceListDialogFragment(mCameraHelper, mIsCameraConnected ? mUsbDevice : null);
        mDeviceListDialog.setOnDeviceItemSelectListener(usbDevice -> {
            if (mCameraHelper != null && mIsCameraConnected) {
                mCameraHelper.closeCamera();
            }
            mUsbDevice = usbDevice;
            selectDevice(usbDevice);
//            showVideoFormatDialog();
        });

        mDeviceListDialog.show(getSupportFragmentManager(), "device_list_left");
    }


    private void initViews() {
        mCameraViewMain = findViewById(R.id.svCameraViewMain);
        mCameraViewMain.setAspectRatio(DEFAULT_WIDTH, DEFAULT_HEIGHT);


        mCameraViewMain.setCallback(new CameraViewInterface.Callback() {
            @Override
            public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
                if (mCameraHelper != null) {
                    mCameraHelper.addSurface(surface, false);
                }
            }

            @Override
            public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {

            }

            @Override
            public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
                if (mCameraHelper != null) {
                    mCameraHelper.removeSurface(surface);
                }
            }
        });

        bthCaptureVideo = findViewById(R.id.bthCaptureVideo);
        bthCaptureVideo.setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        initCameraHelper();
    }

    @Override
    protected void onStop() {
        super.onStop();
        clearCameraHelper();
    }

    public void initCameraHelper() {
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setStateCallback(mStateListener);
        }
    }

    private void clearCameraHelper() {
        if (mCameraHelper != null) {
            mCameraHelper.release();
            mCameraHelper = null;
        }
    }

    private void selectDevice(final UsbDevice device) {
        mCameraHelper.selectDevice(device);
        Log.d("TTT", "selectDevice: I am selected");
    }

    private final ICameraHelper.StateCallback mStateListener = new ICameraHelper.StateCallback() {
        @Override
        public void onAttach(UsbDevice device) {
            selectDevice(device);
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            mCameraHelper.openCamera();
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            Log.d("TTT", "onCameraOpen: I am opened");

//            mCameraHelper.startPreview();

            Size size = mCameraHelper.getPreviewSize();
            if (size != null) {
                int width = size.width;
                int height = size.height;
                //auto aspect ratio
                mCameraViewMain.setAspectRatio(width, height);
            }

            mCameraHelper.addSurface(mCameraViewMain.getSurface(), false);
//            mIsCameraConnected = true;
//            startRecordEveryMinute();

        }

        @Override
        public void onCameraClose(UsbDevice device) {

            if (mCameraHelper != null) {
                mCameraHelper.removeSurface(mCameraViewMain.getSurface());
            }

            mIsCameraConnected = false;

        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            if (DEBUG) Log.v("TAG", "onDeviceClose:");
        }

        @Override
        public void onDetach(UsbDevice device) {
            if (DEBUG) Log.v("TAG", "onDetach:");
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (DEBUG) Log.v("TAG", "onCancel:");
        }

    };



    private void startRecord() {
        File file = FileUtils.getCaptureFile(this, Environment.DIRECTORY_MOVIES, ".mp4");
        VideoCapture.OutputFileOptions options =
                new VideoCapture.OutputFileOptions.Builder(file).build();

//        ContentValues contentValues = new ContentValues();
//        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "NEW_VIDEO");
//        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
//
//        VideoCapture.OutputFileOptions options = new VideoCapture.OutputFileOptions.Builder(
//                getContentResolver(),
//                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
//                contentValues).build();

        mCameraHelper.startRecording(options, new VideoCapture.OnVideoCaptureCallback() {
            @Override
            public void onStart() {
                Calendar calendar = Calendar.getInstance();
                Log.d("TTT", "onStartVideoMIlliSeconds: " + calendar.get(Calendar.MILLISECOND));
            }

            @Override
            public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                Toast.makeText(
                        RecordVideoActivity.this,
                        "save \"" + UriHelper.getPath(RecordVideoActivity.this, outputFileResults.getSavedUri()) + "\"",
                        Toast.LENGTH_SHORT).show();
                Log.d("TTT", "onVideoSaved: " + System.currentTimeMillis());
                Log.d("TTT", "VideoSaved Succesfully: " + "save \"" + UriHelper.getPath(RecordVideoActivity.this, outputFileResults.getSavedUri()) + "\"");

                bthCaptureVideo.setColorFilter(0);
            }

            @Override
            public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                Toast.makeText(RecordVideoActivity.this, message, Toast.LENGTH_LONG).show();

                bthCaptureVideo.setColorFilter(0);
            }
        });

        bthCaptureVideo.setColorFilter(0x7fff0000);
    }

    private void stopRecord() {
        if (mCameraHelper != null) {
            mCameraHelper.stopRecording();
            Log.d("TTT", "Video is stopped: " + System.currentTimeMillis());

        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(startPeriodTaskAndStop);
        handler.removeCallbacks(stopPeriodTaskAndStart);
    }
}