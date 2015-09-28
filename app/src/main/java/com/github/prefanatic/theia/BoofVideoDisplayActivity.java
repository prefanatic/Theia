package com.github.prefanatic.theia;

import android.hardware.Camera;

import boofcv.android.gui.VideoDisplayActivity;

public class BoofVideoDisplayActivity extends VideoDisplayActivity {
    @Override
    protected Camera openConfigureCamera(Camera.CameraInfo cameraInfo) {
        return null;
    }
}
