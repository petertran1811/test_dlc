package com.example.camera_fragment;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.qualcomm.qti.snpe.FloatTensor;
import com.qualcomm.qti.snpe.NeuralNetwork;
import com.qualcomm.qti.snpe.SNPE;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SNPEHelper {
    private static final String TAG = SNPEHelper.class.getSimpleName();
    private final Application mApplication;
    private final Context mContext;
    private final BitmapToFloatArrayHelper mBitmapToFloatHelper;
    private final TimeStat mTimeStat;
    private String mSNPEVersionCached;


    // all of the following are allocated in the load function for the network
    private NeuralNetwork mNeuralNetwork;
    private String mRuntimeCoreName = "no core";
    private int[] mInputTensorShapeHWC;
    private FloatTensor mInputTensorReused;
    private Map<String, FloatTensor> mInputTensorsMap;

    public SNPEHelper(Application application) {
        mApplication = application;
        mContext = application;
        mBitmapToFloatHelper = new BitmapToFloatArrayHelper();
        mTimeStat = new TimeStat();
    }

    public int getInputTensorWidth() {
        return mInputTensorShapeHWC == null ? 0 : mInputTensorShapeHWC[1];
    }

    public int getInputTensorHeight() {
        return mInputTensorShapeHWC == null ? 0 : mInputTensorShapeHWC[2];
    }
    private static final String MNETSSD_MODEL_ASSET_NAME = "repvgg.dlc";
    private static final String MNETSSD_INPUT_LAYER = "";
    private static final String MNETSSD_OUTPUT_LAYER = "";
    private static final boolean MNETSSD_NEEDS_CPU_FALLBACK = true;
    private static int MNETSSD_NUM_BOXES = 100;
    private final float[] floatOutput = new float[MNETSSD_NUM_BOXES * 7];
    private final ArrayList<Box> mSSDBoxes = Box.createBoxes(MNETSSD_NUM_BOXES);
    public boolean loadMobileNetSSDFromAssets() {
        // cleanup
        disposeNeuralNetwork();

        // select core
        NeuralNetwork.Runtime selectedCore = NeuralNetwork.Runtime.GPU_FLOAT16;

        // load the network
        mNeuralNetwork = loadNetworkFromDLCAsset(mApplication, MNETSSD_MODEL_ASSET_NAME,
                selectedCore, MNETSSD_NEEDS_CPU_FALLBACK, MNETSSD_OUTPUT_LAYER);

        // if it didn't work, retry on CPU
        if (mNeuralNetwork == null) {
            Log.d(TAG,"Error loading the DLC network on the " + selectedCore + " core. Retrying on CPU.");
            mNeuralNetwork = loadNetworkFromDLCAsset(mApplication, MNETSSD_MODEL_ASSET_NAME,
                    NeuralNetwork.Runtime.CPU, MNETSSD_NEEDS_CPU_FALLBACK, MNETSSD_OUTPUT_LAYER);
            if (mNeuralNetwork == null) {
                Log.d(TAG,"Error also on CPU");
                return false;
            }
            Log.d(TAG,"Loading on the CPU worked");
        }

        // cache the runtime name
        mRuntimeCoreName = mNeuralNetwork.getRuntime().toString();
        // read the input shape
        mInputTensorShapeHWC = mNeuralNetwork.getInputTensorsShapes().get(MNETSSD_INPUT_LAYER);
        // allocate the single input tensor
        mInputTensorReused = mNeuralNetwork.createFloatTensor(mInputTensorShapeHWC);
        // add it to the map of inputs, even if it's a single input
        mInputTensorsMap = new HashMap<>();
        mInputTensorsMap.put(MNETSSD_INPUT_LAYER, mInputTensorReused);
        return true;
    }
    private static NeuralNetwork loadNetworkFromDLCAsset(
            Application application, String assetFileName, NeuralNetwork.Runtime selectedRuntime,
            boolean needsCpuFallback, String... outputLayerNames) {
        try {
            // input stream to read from the assets
            InputStream assetInputStream = application.getAssets().open(assetFileName);

            // create the neural network
            NeuralNetwork network = new SNPE.NeuralNetworkBuilder(application)
                    .setDebugEnabled(false)
                    .setOutputLayers(outputLayerNames)
                    .setModel(assetInputStream, assetInputStream.available())
                    .setPerformanceProfile(NeuralNetwork.PerformanceProfile.DEFAULT)
                    .setRuntimeOrder(selectedRuntime) // Runtime.DSP, Runtime.GPU_FLOAT16, Runtime.GPU, Runtime.CPU
                    .setCpuFallbackEnabled(needsCpuFallback)
                    .build();

            // close input
            assetInputStream.close();
            // all right, network loaded
            return network;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (IllegalStateException | IllegalArgumentException e2) {

            e2.printStackTrace();
            return null;
        }
    }
    public void disposeNeuralNetwork() {
        if (mNeuralNetwork == null)
            return;
        mNeuralNetwork.release();
        mNeuralNetwork = null;
        mInputTensorShapeHWC = null;
        mInputTensorReused = null;
        mInputTensorsMap = null;
    }
    public ArrayList<Box> mobileNetSSDInference(Bitmap modelInputBitmap) {

        try {
            // execute the inference, and get 3 tensors as outputs
            final Map<String, FloatTensor> outputs = inferenceOnBitmap(modelInputBitmap);
            if (outputs == null)
                return null;
            MNETSSD_NUM_BOXES = outputs.get(MNETSSD_OUTPUT_LAYER).getSize() / 7;
            Log.d(TAG, "MNETSSD_NUM_BOXES   " + MNETSSD_NUM_BOXES);
            // convert tensors to boxes - Note: Optimized to read-all upfront
            outputs.get(MNETSSD_OUTPUT_LAYER).read(floatOutput, 0, MNETSSD_NUM_BOXES * 7);

            } catch (Exception exception) {
            exception.printStackTrace();
        }
        return mSSDBoxes;
    }
    private Map<String, FloatTensor> inferenceOnBitmap(Bitmap inputBitmap) {
        final Map<String, FloatTensor> outputs;
        try {
            // safety check
            if (mNeuralNetwork == null || mInputTensorReused == null || inputBitmap.getWidth() != getInputTensorWidth() || inputBitmap.getHeight() != getInputTensorHeight()) {
                Log.d(TAG,"No NN loaded, or image size different than tensor size");
                return null;
            }

            // [0.3ms] Bitmap to RGBA byte array (size: 300*300*3 (RGBA..))
            mBitmapToFloatHelper.bitmapToBuffer(inputBitmap);

            // [2ms] Pre-processing: Bitmap (300,300,4 ints) -> Float Input Tensor (300,300,3 floats)
            mTimeStat.startInterval();
            final float[] inputFloatsHW3 = mBitmapToFloatHelper.bufferToNormalFloatsBGR();
            if (mBitmapToFloatHelper.isFloatBufferBlack())
                return null;
            mInputTensorReused.write(inputFloatsHW3, 0, inputFloatsHW3.length, 0, 0);
            mTimeStat.stopInterval("i_tensor", 20, false);
            // [31ms on GPU16, 50ms on GPU] execute the inference
            mTimeStat.startInterval();
            outputs = mNeuralNetwork.execute(mInputTensorsMap);
            mTimeStat.stopInterval("nn_exec ", 20, false);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, e.getCause() + "");
            return null;
        }

        return outputs;
    }


}
