package com.github.prefanatic.theia;

import android.hardware.Camera;
import android.os.Bundle;

import boofcv.android.gui.VideoDisplayActivity;

public class BoofVideoDisplayActivity extends VideoDisplayActivity {
    final int CAMERA_ID = 1;

    public BoofVideoDisplayActivity() {
        super();
    }

    public BoofVideoDisplayActivity(boolean hidePreview) {
        super(hidePreview);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setShowFPS(true);
    }

    @Override
    protected Camera openConfigureCamera(Camera.CameraInfo cameraInfo) {
        Camera mCamera = Camera.open(CAMERA_ID);
        Camera.getCameraInfo(CAMERA_ID, cameraInfo);

        Camera.Parameters param = mCamera.getParameters();

        Camera.Size sizePreview = param.getSupportedPreviewSizes().get(5);
        param.setPreviewSize(sizePreview.width, sizePreview.height);
        //Camera.Size sizePicture = param.getSupportedPictureSizes().get(preference.picture);
        //param.setPictureSize(sizePicture.width, sizePicture.height);

        mCamera.setParameters(param);

        return mCamera;
    }
}
