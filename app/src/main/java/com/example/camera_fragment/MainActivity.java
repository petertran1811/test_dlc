package com.example.camera_fragment;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.Manifest;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {


    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * Method to navigate to CameraPreviewFragment
     */

    private void goToCameraPreviewFragment() {
        if (hasPermission(MainActivity.this)) {

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();// = getFragmentManager().beginTransaction();
            transaction.add(R.id.main_content, CameraPreviewFragment.create());
            transaction.commit();
        } else {
            requestPermission();
        }
    }

    public static boolean hasPermission(Context mContext) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return mContext.checkSelfPermission(Constants.PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        goToCameraPreviewFragment();

    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String[] permissions, final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Constants.PERMISSIONS_REQUEST) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                goToCameraPreviewFragment();
            }
        }
    }

    /**
     * Method to request Camera permission
     */
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(Constants.PERMISSION_CAMERA)) {
                Toast.makeText(this,
                        "Need Permission", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{Constants.PERMISSION_CAMERA}, Constants.PERMISSIONS_REQUEST);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop()");
    }

}