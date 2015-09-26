package com.github.prefanatic.theia;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;

import hugo.weaving.DebugLog;
import permissions.dispatcher.DeniedPermission;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;
import permissions.dispatcher.ShowsRationale;
import timber.log.Timber;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {
    private SurfaceView mSurfaceView;
    private CameraManager mManager;
    private String mCameraId;
    private CameraDevice mCamera;
    private CameraCaptureSession mSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSurfaceView = (SurfaceView) findViewById(R.id.surface_view);
        mManager = getSystemService(CameraManager.class);
        mCameraId = getNormalCamera();

        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                MainActivityPermissionsDispatcher.openCameraWithCheck(MainActivity.this);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }

    @DebugLog
    private void createPreviewSession() {
        try {
            List<Surface> surfaceList = new ArrayList<>();
            surfaceList.add(mSurfaceView.getHolder().getSurface());

            mCamera.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mSession = session;
                    Timber.d("Preview session created.");

                    CaptureRequest request = createPreviewRequest();
                    if (request == null) {
                        Timber.e("Failed to create request.");
                        return;
                    }

                    try {
                        session.setRepeatingRequest(request, null, null);
                    } catch (CameraAccessException e) {
                        Timber.e("Failed to set repeating request: %s", e.getMessage());
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Timber.d("Preview session failed.");
                }
            }, null);
        } catch (CameraAccessException e) {
            Timber.e("Failed to preview: %s", e.getMessage());
        }
    }

    @DebugLog
    private CaptureRequest createPreviewRequest() {
        try {
            CaptureRequest.Builder builder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(mSurfaceView.getHolder().getSurface());

            return builder.build();
        } catch (CameraAccessException e) {
            Timber.e("Failed to create preview: %s", e.getMessage());
        }

        return null;
    }

    @NeedsPermission(Manifest.permission.CAMERA)
    void openCamera() {
        try {
            mManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    mCamera = camera;
                    Timber.d("Opened camera.");

                    createPreviewSession();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    mCamera = null;

                    Timber.d("Closed camera.");
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    mCamera = null;

                    Timber.e("Camera error: %d", error);
                }
            }, null);
        } catch (CameraAccessException e) {
            Timber.e("Failed to open camera: %s", e.getMessage());
        } catch (SecurityException e) {
            // Had to add this otherwise Lint would flip shit over no permissions, even though they ARE being handled.
        }
    }

    @ShowsRationale(Manifest.permission.CAMERA)
    void showCameraRationale() {
        Snackbar.make(mSurfaceView, "You don't need any rational!", Snackbar.LENGTH_SHORT).show();
    }

    @DeniedPermission(Manifest.permission.CAMERA)
    void showCameraDenied() {
        Snackbar.make(mSurfaceView, "You idiot.", Snackbar.LENGTH_SHORT).show();
    }

    @DebugLog
    private String getNormalCamera() {
        try {
            for (String id : mManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mManager.getCameraCharacteristics(id);
                Integer face = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (face != null && face == CameraCharacteristics.LENS_FACING_BACK)
                    return id;
            }
        } catch (CameraAccessException e) {
            Timber.e("Error accessing camera: %s", e.getMessage());
        }

        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mSession != null) {
            mSession.close();
        }

        if (mCamera != null)
            mCamera.close();
    }
}
