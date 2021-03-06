package com.github.prefanatic.theia;

import android.Manifest;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import boofcv.android.ConvertBitmap;
import hugo.weaving.DebugLog;
import permissions.dispatcher.DeniedPermission;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;
import permissions.dispatcher.ShowsRationale;
import timber.log.Timber;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {
    private AutoFitTextureView mTextureView;
    private CameraManager mManager;
    private String mCameraId;
    private CameraDevice mCamera;
    private CameraCaptureSession mSession;
    private Size[] mSizeArray;
    private Size mPreviewSize;

    private ImageReader mImageReader;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private int mSurfaceWidth, mSurfaceHeight;


    private GrayImage latestImage = new GrayImage();
    private GrayImage previousImage;
    private final GrayImage background = new GrayImage();
    private Bitmap processedImage;
    private byte[] storage;

    private Util utilHold = new Util();

    private int BUFFER_SIZE = 0;
    private byte[] processStorage;
    private long lastFrameTime = 0;
    private int frameCount = 0;
    private boolean resetBackground = false;

    private ImageReader.OnImageAvailableListener mImageReady = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image == null) return;

            frameCount++;
            if (System.currentTimeMillis() > lastFrameTime) {
                Timber.d("FPS %d", frameCount);
                frameCount = 0;
                lastFrameTime = System.currentTimeMillis() + 1000;
            }

            if (background.data == null || resetBackground) {
                Util.imageToGrayImage(image, background);
                image.close();
                resetBackground = false;
                return;
            }

            Util.imageToGrayImage(image, latestImage);
            //Util.median(latestImage, 5);
            //Util.binarize(latestImage, 10);
            //Util.addToPool(latestImage);
            //Util.subtractFromAveragedPool(latestImage);
            //Util.subtractFromPool(latestImage, 3);
            //Util.sobel(latestImage);

            //Util.gaussianBlur(latestImage);
            //Util.convertToBitmap(latestImage, processedImage, storage);
            //Util.lowPass(latestImage);
            Util.detectionPainter(50, latestImage, background, processedImage, storage);

            Canvas canvas = mTextureView.lockCanvas();
            canvas.drawBitmap(processedImage, 0, 0, null);

            mTextureView.unlockCanvasAndPost(canvas);

            image.close();
        }
    };

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            mSurfaceWidth = width;
            mSurfaceHeight = height;

            Timber.d("Texture view available at %dx%d", width, height);
            MainActivityPermissionsDispatcher.openCameraWithCheck(MainActivity.this);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Timber.d("Texture size changing from %dx%d to %dx%d", mSurfaceWidth, mSurfaceHeight, width, height);
            mSurfaceWidth = width;
            mSurfaceHeight = height;

            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextureView = (AutoFitTextureView) findViewById(R.id.surface_view);
        mManager = getSystemService(CameraManager.class);

        mTextureView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetBackground = true;
            }
        });


    }

    @DebugLog
    private void createPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            final Surface surface = new Surface(texture);

            final List<Surface> surfaceList = new ArrayList<>();
            //surfaceList.add(surface);
            surfaceList.add(mImageReader.getSurface());

            mCamera.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mSession = session;
                    Timber.d("Preview session created.");

                    CaptureRequest request = createRequest(surfaceList);
                    if (request == null) {
                        Timber.e("Failed to create request.");
                        return;
                    }

                    try {
                        session.setRepeatingRequest(request, new CaptureCallback(), null);
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
    private CaptureRequest createRequest(List<Surface> surfaces) {
        try {
            CaptureRequest.Builder builder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO);

            for (Surface surface : surfaces)
                builder.addTarget(surface);

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
        Snackbar.make(mTextureView, "You don't need any rational!", Snackbar.LENGTH_SHORT).show();
    }

    @DeniedPermission(Manifest.permission.CAMERA)
    void showCameraDenied() {
        Snackbar.make(mTextureView, "You idiot.", Snackbar.LENGTH_SHORT).show();
    }

    @DebugLog
    private String getNormalCamera() {
        try {
            for (String id : mManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mManager.getCameraCharacteristics(id);
                Integer face = characteristics.get(CameraCharacteristics.LENS_FACING);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                mSizeArray = map.getOutputSizes(ImageFormat.YUV_420_888);
                if (face != null && face == CameraCharacteristics.LENS_FACING_BACK) {

                    // Do some aspect ratio things.
                    Size largest = Collections.max(
                            Arrays.asList(mSizeArray), new AreaComparator()
                    );

                    mPreviewSize = Util.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), mSurfaceWidth, mSurfaceHeight, largest);
                    mPreviewSize = new Size(640, 480);

                    Timber.d("Largest is %d x %d.", largest.getWidth(), largest.getHeight());
                    Timber.d("Choosing %d x %d as preview.", mPreviewSize.getWidth(), mPreviewSize.getHeight());

                    int orientation = getResources().getConfiguration().orientation;
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    } else {
                        mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                    }

                    //configureTransform(mSurfaceWidth, mSurfaceHeight);

                    mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 2);
                    mImageReader.setOnImageAvailableListener(mImageReady, mBackgroundHandler);

                    Timber.d("Projected buffer size: %d", mPreviewSize.getWidth() * mPreviewSize.getHeight());

                    processedImage = Bitmap.createBitmap(mPreviewSize.getWidth(), mPreviewSize.getHeight(), Bitmap.Config.ARGB_8888);
                    storage = ConvertBitmap.declareStorage(processedImage, storage);

                    return id;
                }
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

    private void startBackground() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackground() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Timber.e("Failed to end background thread: %s", e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mSession != null) {
            mSession.close();
        }

        if (mCamera != null)
            mCamera.close();

        stopBackground();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackground();

        mCameraId = getNormalCamera();

        if (mTextureView.isAvailable()) {
            Timber.d("Texture view is available");
            MainActivityPermissionsDispatcher.openCameraWithCheck(this);
        } else {
            Timber.d("Waiting on texture view.");
            mTextureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }
}
