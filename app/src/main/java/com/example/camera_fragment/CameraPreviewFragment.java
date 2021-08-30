package com.example.camera_fragment;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class CameraPreviewFragment extends Fragment implements TextureView.SurfaceTextureListener {

    private String TAG = "Check";
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private TextureView mTextureView;
    private OverlayRenderer mOverlayRenderer;
    private CameraManager mCameraManager;
    private CameraCallback mCameraCallback;
    private CameraCaptureSession mCameraCaptureSession;
    private CaptureRequest.Builder builder;
    private CameraDevice mCameraDevice;
    private SurfaceTexture mSurfaceTexture;
    private boolean mNetworkLoaded;
    private SNPEHelper mSnpeHelper;
    private Bitmap mModelInputBitmap;
    private Canvas mModelInputCanvas;
    private Paint mModelBitmapPaint;
    private String searchLabel;
    private boolean mInferenceSkipped;


    private String[] ids;

    public static CameraPreviewFragment create() {
        final CameraPreviewFragment fragment = new CameraPreviewFragment();
        return fragment;
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        ;
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTextureView = (TextureView) view.findViewById(R.id.surface);
        mTextureView.setSurfaceTextureListener(this);
        mOverlayRenderer = view.findViewById(R.id.overlayRenderer);

        mCameraManager = (CameraManager) getActivity().getApplicationContext().
                getSystemService(Context.CAMERA_SERVICE);
        mCameraCallback = new CameraCallback();


        if (mCameraManager != null) {
            try {
                ids = mCameraManager.getCameraIdList();
            } catch (CameraAccessException ex) {
                ex.printStackTrace();
            }

        } else {
            Log.e("Check Error", "Camera Service null");
        }

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
        openCamera();
    }


    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
        mCameraCaptureSession.close();
        mCameraDevice.close();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            mCameraManager.openCamera(ids[0], mCameraCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreview() {
        mSurfaceTexture = mTextureView.getSurfaceTexture();
        Surface mSurface = new Surface(mSurfaceTexture);

        try {
            Log.d("Check", "createCaptureSession");
            mCameraDevice.createCaptureSession(Arrays.asList(mSurface), new CameraCapture(),
                    null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        try {
            Log.d("Check", "createCaptureRequest");
            builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(mSurface);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        startBackgroundThread();
        ensureNetCreated();

    }

    @Override
    public void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    private class CameraCallback extends CameraDevice.StateCallback {

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            Log.d(TAG, "onClosed()");
            super.onClosed(camera);
        }

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, "onOpened()");
            mCameraDevice = cameraDevice;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, "onDisconnected()");
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            switch (i) {
                case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                    Log.e(TAG, "Error in Camera Device");
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                    Log.e(TAG, "Camera Device is disabled");
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                    Log.e(TAG, "Camera Device is already in use");
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                    Log.e(TAG, "Error in Camera Service");
                    break;
                case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                    Log.e(TAG, "Error for MAX Cameras");
                    break;
                default:
                    Log.e(TAG, "default error");
                    break;
            }
        }
    }

    private class CameraCapture extends CameraCaptureSession.StateCallback {

        @Override
        public void onConfigured(@NonNull android.hardware.camera2.CameraCaptureSession
                                         cameraCaptureSession) {
            Log.d(TAG, "OnConfigured");
            mCameraCaptureSession = cameraCaptureSession;

            try {
                cameraCaptureSession.setRepeatingRequest(builder.build(), new CameraSession(),
                        mBackgroundHandler);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull android.hardware.camera2.CameraCaptureSession
                                              cameraCaptureSession) {
            Log.d(TAG, "OnConfiguredFailed");
        }

        private class CameraSession extends CameraCaptureSession.CaptureCallback {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull
                    CaptureRequest request, @NonNull TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
                if (mNetworkLoaded) {
                    Bitmap mBitmap = mTextureView.getBitmap(Constants.BITMAP_WIDTH, Constants.BITMAP_HEIGHT);

                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    mBitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream);
                    byte[] byteArray = stream.toByteArray();

                    Bitmap compressedBitmap = BitmapFactory.decodeByteArray(byteArray, 0,
                            byteArray.length);

                    final int inputWidth = mSnpeHelper.getInputTensorWidth();
                    final int inputHeight = mSnpeHelper.getInputTensorHeight();

                    if (mModelInputBitmap == null || mModelInputBitmap.getWidth() != inputWidth || mModelInputBitmap.getHeight() != inputHeight) {
                        // create ARGB8888 bitmap and canvas, with the right size
                        mModelInputBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888);
                        mModelInputCanvas = new Canvas(mModelInputBitmap);
                        mModelInputCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                        // compute the roto-scaling matrix (preview image -> screen image) and apply it to
                        // the canvas. this includes translation for 'letterboxing', i.e. the image will
                        // have black bands to the left and right if it's a portrait picture
                        final Matrix mtx = new Matrix();
                        final int previewWidth = compressedBitmap.getWidth();
                        final int previewHeight = compressedBitmap.getHeight();
                        final float scaleWidth = ((float) inputWidth) / previewWidth;
                        final float scaleHeight = ((float) inputHeight) / previewHeight;
                        final float frameScale = Math.min(scaleWidth, scaleHeight); // centerInside
                        //final float frameScale = Math.max(scaleWidth, scaleHeight); // centerCrop
                        final float dy = inputWidth - (previewWidth * frameScale);
                        final float dx = inputHeight - (previewHeight * frameScale);
                        mtx.postScale(frameScale, frameScale);
                        mtx.postTranslate(dx / 2, dy / 2);
                        if (rotation != 0) {
                            mtx.postTranslate(-inputWidth / 2, -inputHeight / 2);
                            mtx.postRotate(-rotation);
                            mtx.postTranslate(inputWidth / 2, inputHeight / 2);
                        }
                        mModelInputCanvas.setMatrix(mtx);

                        // create the "Paint", to set the antialiasing option
                        mModelBitmapPaint = new Paint();
                        mModelBitmapPaint.setFilterBitmap(true);

                    }
                    mModelInputCanvas.drawColor(Color.BLACK);
                    mModelInputCanvas.drawBitmap(compressedBitmap, 0, 0, mModelBitmapPaint);
                    final ArrayList<Box> boxes = mSnpeHelper.mobileNetSSDInference(mModelInputBitmap);

                    // [2-45ms] give the bitmap to SNPE for inference
                    mInferenceSkipped = boxes == null;

                    if (!mInferenceSkipped) {
                        Log.d(TAG, "mInferenceSkipped...." + mInferenceSkipped);
                        HashSet<String> nearStringsSet = new HashSet<>();
                        for (Box box : boxes) {
                            String textLabel = (box.type_name != null && !box.type_name.isEmpty()) ? box.type_name : String.valueOf(box.type_id);
                            Log.d(TAG, "type_score...." + box.type_score);
                            if (box.type_score < 0.8)
                                continue;
                            nearStringsSet.add(textLabel);
                            Log.d(TAG, "objects" + textLabel);
                            if (searchLabel != null && textLabel.toLowerCase().contains(searchLabel.toLowerCase())) {
                                String nearObjects = "";
                                int count = 0;
                                for (String word : nearStringsSet) {
                                    if (!searchLabel.contains(word) && count <= 2) {
                                        nearObjects += word + ", ";
                                    }
                                    count++;
                                }
                                Log.d(TAG, searchLabel);
                                searchLabel = null;
                                break;
                            }
                        }
                    }
                    // deep copy the results so we can draw the current set while guessing the next set
                    mOverlayRenderer.setBoxesFromAnotherThread(boxes);
                }

            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull
                    CaptureRequest request, @NonNull CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
                Log.e(TAG, "onCaptureFailed");
            }

            @Override
            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull
                    CaptureRequest request, @NonNull CaptureResult partialResult) {
                super.onCaptureProgressed(session, request, partialResult);
                Log.d(TAG, "onCaptureProgressed");
            }

            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull
                    CaptureRequest request, long timestamp, long frameNumber) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);
                Log.d(TAG, "onCaptureStarted");
            }

        }
    }

    private boolean ensureNetCreated() {
        if (mSnpeHelper == null) {
            // load the neural network for object detection with SNPE
            mSnpeHelper = new SNPEHelper(getActivity().getApplication());
            new Thread() {
                public void run() {
                    mNetworkLoaded = mSnpeHelper.loadMobileNetSSDFromAssets();
                    Log.d(TAG, " ensureNetCreated  " + mNetworkLoaded);
                }
            }.start() ;


        }
        return mNetworkLoaded;
    }


}

