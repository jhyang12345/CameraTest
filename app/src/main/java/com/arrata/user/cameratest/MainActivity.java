package com.arrata.user.cameratest;

import android.Manifest;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.cameraview.AspectRatio;
import com.google.android.cameraview.CameraView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;


/**
 * This demo app saves the taken picture to a constant file.
 * $ adb pull /sdcard/Android/data/com.google.android.cameraview.demo/files/Pictures/picture.jpg
 */
public class MainActivity extends AppCompatActivity implements
        ActivityCompat.OnRequestPermissionsResultCallback,
        AspectRatioFragment.Listener, SensorEventListener {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private static final String FRAGMENT_DIALOG = "dialog";

    private static final int[] FLASH_OPTIONS = {
            CameraView.FLASH_AUTO,
            CameraView.FLASH_OFF,
            CameraView.FLASH_ON,
    };

    private static final int[] FLASH_ICONS = {
            R.drawable.ic_flash_auto,
            R.drawable.ic_flash_off,
            R.drawable.ic_flash_on,
    };

    private static final int[] FLASH_TITLES = {
            R.string.flash_auto,
            R.string.flash_off,
            R.string.flash_on,
    };

    private int mCurrentFlash;

    private SensorManager sensorManager;
    private long lastUpdate = -1;
    private long lastStopTime;
    private boolean moving = true;
    private boolean notmoving = false;
    private boolean pictureTaken = false;

    private List<Handler> handlerList;
    private List<Runnable> runnableList;

    private CameraView mCameraView;

    private Handler mBackgroundHandler;

    private View mRootView;

    private FloatingActionButton mCaptureFab;

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.take_picture:
                    if (mCameraView != null) {
                        mCameraView.takePicture();
                    }
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRootView = findViewById(R.id.root_view);
        mCameraView = (CameraView) findViewById(R.id.camera);
        if (mCameraView != null) {
            mCameraView.addCallback(mCallback);
        }
        mCaptureFab = (FloatingActionButton) findViewById(R.id.take_picture);
        if (mCaptureFab != null) {
            mCaptureFab.setOnClickListener(mOnClickListener);
        }
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }

        //ask fo write permission
        if(Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        //registering sensorManager
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_NORMAL);
        lastUpdate = System.currentTimeMillis();
        handlerList = new ArrayList<Handler>();
        runnableList = new ArrayList<Runnable>();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            mCameraView.start();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ConfirmationDialogFragment
                    .newInstance(R.string.camera_permission_confirmation,
                            new String[]{Manifest.permission.CAMERA},
                            REQUEST_CAMERA_PERMISSION,
                            R.string.camera_permission_not_granted)
                    .show(getSupportFragmentManager(), FRAGMENT_DIALOG);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }

        //registering sensorManager
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        mCameraView.stop();

        sensorManager.unregisterListener(this);

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBackgroundHandler != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mBackgroundHandler.getLooper().quitSafely();
            } else {
                mBackgroundHandler.getLooper().quit();
            }
            mBackgroundHandler = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION:
                if (permissions.length != 1 || grantResults.length != 1) {
                    throw new RuntimeException("Error on requesting camera permission.");
                }
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.camera_permission_not_granted,
                            Toast.LENGTH_SHORT).show();
                }
                // No need to start camera here; it is handled by onResume
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.aspect_ratio:
                if (mCameraView != null) {
                    final Set<AspectRatio> ratios = mCameraView.getSupportedAspectRatios();
                    final AspectRatio currentRatio = mCameraView.getAspectRatio();
                    AspectRatioFragment.newInstance(ratios, currentRatio)
                            .show(getSupportFragmentManager(), FRAGMENT_DIALOG);
                }
                break;
            case R.id.switch_flash:
                if (mCameraView != null) {
                    mCurrentFlash = (mCurrentFlash + 1) % FLASH_OPTIONS.length;
                    item.setTitle(FLASH_TITLES[mCurrentFlash]);
                    item.setIcon(FLASH_ICONS[mCurrentFlash]);
                    mCameraView.setFlash(FLASH_OPTIONS[mCurrentFlash]);
                }
                break;
            case R.id.switch_camera:
                if (mCameraView != null) {
                    int facing = mCameraView.getFacing();
                    mCameraView.setFacing(facing == CameraView.FACING_FRONT ?
                            CameraView.FACING_BACK : CameraView.FACING_FRONT);
                }
                break;
        }
        return false;
    }

    private void repositionCaptureFab() {
        if ((mRootView == null) || (mCameraView == null) || (mCaptureFab == null)) {
            return;
        }
        mCaptureFab.post(new Runnable() {
            @Override
            public void run() {
                RelativeLayout.LayoutParams layoutParams =
                        (RelativeLayout.LayoutParams) mCaptureFab.getLayoutParams();

                DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                int minMargin = (int) (15 * displayMetrics.density + 0.5f);

                if (displayMetrics.widthPixels > displayMetrics.heightPixels) {
                    int rightMargin = ((mRootView.getWidth() - mCameraView.getWidth()) / 2) -
                            (mCaptureFab.getWidth() / 2);
                    if (rightMargin < minMargin) {
                        rightMargin = minMargin;
                    }
                    layoutParams.rightMargin = rightMargin;
                } else {
                    int bottomMargin = ((mRootView.getHeight() - mCameraView.getHeight()) / 2) -
                            (mCaptureFab.getHeight() / 2);
                    if (bottomMargin < minMargin) {
                        bottomMargin = minMargin;
                    }
                    layoutParams.bottomMargin = bottomMargin;
                }

                mCaptureFab.setLayoutParams(layoutParams);
            }
        });
    }

    @Override
    public void onAspectRatioSelected(@NonNull AspectRatio ratio) {
        if (mCameraView != null) {
            Toast.makeText(this, ratio.toString(), Toast.LENGTH_SHORT).show();
            mCameraView.setAspectRatio(ratio);
            repositionCaptureFab();
        }
    }

    private Handler getBackgroundHandler() {
        if (mBackgroundHandler == null) {
            HandlerThread thread = new HandlerThread("background");
            thread.start();
            mBackgroundHandler = new Handler(thread.getLooper());
        }
        return mBackgroundHandler;
    }

    private CameraView.Callback mCallback
            = new CameraView.Callback() {

        @Override
        public void onCameraOpened(CameraView cameraView) {
            Log.d(TAG, "onCameraOpened");
            repositionCaptureFab();
        }

        @Override
        public void onCameraClosed(CameraView cameraView) {
            Log.d(TAG, "onCameraClosed");
        }

        @Override
        public void onPictureTaken(CameraView cameraView, final byte[] data) {
            Log.d(TAG, "onPictureTaken " + data.length);
            Toast.makeText(cameraView.getContext(), R.string.picture_taken, Toast.LENGTH_SHORT)
                    .show();
            getBackgroundHandler().post(new Runnable() {
                @Override
                public void run() {

                    long code = System.currentTimeMillis();
                    String newFileName = String.valueOf(code);
                    File filepath = Environment.getExternalStorageDirectory();
                    String filePath = filepath.getAbsolutePath() + "/CameraTest/pictures";
                    Log.d("Saving:", filePath);
                    File dir = new File(filePath);

                    boolean success = dir.mkdirs();
                    Log.d("Created:", String.valueOf(success));
                    File file = new File(dir,//getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                            newFileName + ".jpg");

                    OutputStream os = null;
                    try {
                        os = new FileOutputStream(file);
                        os.write(data);
                        os.close();
                    } catch (IOException e) {
                        Log.w(TAG, "Cannot write to " + file, e);
                    } finally {
                        if (os != null) {
                            try {
                                os.close();
                            } catch (IOException e) {
                                // Ignore
                            }
                        }
                    }
                }
            });
        }

    };

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            getAccelerometer(event);
        }

    }

    private void getAccelerometer(SensorEvent event) {
        float[] values = event.values;

        float x = values[0];
        float y = values[1];
        float z = values[2];

        float accelationSquareRoot = (x * x + y * y + z * z)
                / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH);
        long actualTime = (new Date()).getTime()
                + (event.timestamp - System.nanoTime()) / 1000000L;
        if(lastUpdate == -1) {
            lastUpdate = actualTime;
        }

        if(Math.abs(x) < 0.01 && Math.abs(y) < 0.01 && Math.abs(z) < 0.01) {
            if(moving) {
                lastUpdate = actualTime;
                notmoving = false;
            } else {//moving has stopped
                if(actualTime - lastUpdate > 4000) {
                    Log.d("TimeGap", String.valueOf(actualTime - lastUpdate));
                    Log.d("lastUpdate", String.valueOf(lastUpdate));
                    Log.d("actualTime", String.valueOf(actualTime));

                    //Toast.makeText(this, "Device has stopped!", Toast.LENGTH_SHORT)
                    //        .show();
                    if(!notmoving && !pictureTaken) { // entered notmoving state for the first time
                        Log.d("Stopped:", "not moving!");

                        takePictureProcess();
                        pictureTaken = true;
                    }
                    notmoving = true;
                }

            }

            moving = false;

        } else {
            moving = true;
        }

        //Log.d("XYZ:", String.valueOf(x) + " " + String.valueOf(y) + " " + String.valueOf(z));
        if (accelationSquareRoot >= 2) //
        {
            if (actualTime - lastUpdate < 200) {
                return;
            }
            lastUpdate = actualTime;
            Toast.makeText(this, "Device was shuffled", Toast.LENGTH_SHORT)
                    .show();
            /*if (color) {
                view.setBackgroundColor(Color.GREEN);
            } else {
                view.setBackgroundColor(Color.RED);
            }
            color = !color;*/
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public static class ConfirmationDialogFragment extends DialogFragment {

        private static final String ARG_MESSAGE = "message";
        private static final String ARG_PERMISSIONS = "permissions";
        private static final String ARG_REQUEST_CODE = "request_code";
        private static final String ARG_NOT_GRANTED_MESSAGE = "not_granted_message";

        public static ConfirmationDialogFragment newInstance(@StringRes int message,
                                                             String[] permissions, int requestCode, @StringRes int notGrantedMessage) {
            ConfirmationDialogFragment fragment = new ConfirmationDialogFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_MESSAGE, message);
            args.putStringArray(ARG_PERMISSIONS, permissions);
            args.putInt(ARG_REQUEST_CODE, requestCode);
            args.putInt(ARG_NOT_GRANTED_MESSAGE, notGrantedMessage);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Bundle args = getArguments();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(args.getInt(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String[] permissions = args.getStringArray(ARG_PERMISSIONS);
                                    if (permissions == null) {
                                        throw new IllegalArgumentException();
                                    }
                                    ActivityCompat.requestPermissions(getActivity(),
                                            permissions, args.getInt(ARG_REQUEST_CODE));
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Toast.makeText(getActivity(),
                                            args.getInt(ARG_NOT_GRANTED_MESSAGE),
                                            Toast.LENGTH_SHORT).show();
                                }
                            })
                    .create();
        }

    }

    private void takePictureProcess() {
        Runnable first = new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "3", Toast.LENGTH_SHORT)
                        .show();
            }
        };
        Runnable second = new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "2", Toast.LENGTH_SHORT)
                        .show();
            }
        };
        Runnable third = new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "1", Toast.LENGTH_SHORT)
                        .show();
            }
        };
        Runnable takePicture = new Runnable() {
            @Override
            public void run() {
                mCameraView.takePicture();
                lastUpdate += 60000;
            }
        };
        delayStart(first, 0);
        delayStart(second, 1000);
        delayStart(third, 2000);
        delayStart(takePicture, 3000);

    }

    private void delayStart(Runnable runnable, int millis) {
        Handler myHandler = new Handler(Looper.getMainLooper());
        myHandler.postDelayed(runnable, millis);
        runnableList.add(runnable);
        handlerList.add(myHandler);
    }

    private void cancelTakePictureProcess() {
        for(int i = 0; i < handlerList.size(); ++i) {
            handlerList.get(i).removeCallbacks(runnableList.get(i));
        }
    }

}
