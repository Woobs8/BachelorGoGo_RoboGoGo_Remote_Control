package bachelorgogo.com.robotcontrolapp;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ControlFragment extends android.support.v4.app.Fragment
implements TextureView.SurfaceTextureListener {

    String TAG = "ControlFragment";

    // Development flag
    boolean DEVELOPING = false;

    // Context
    private Context mContext;

    // UI Elements
    private TextView BatteryPct;
    private SharedPreferences mSharedPrefs;
    private Switch mSwitch;
    private TextureView mTextureView;
    private ImageView mImagePowerSaveMode;
    private ImageView mImageAssistedDriveMode;
    private ImageButton mCameraCircleImageButton;
    private ImageButton mCameraIconImageButton;
    private RelativeLayout mLayout;
    private RelativeLayout layout_joystick;
    private TextView AngleTxt;
    private TextView PowerTxt;
    private ProgressBar mProgressBar;
    private MediaPlayer mMediaPlayer;

    // Camera Parameters
    private boolean WASLONGCLICK = false;

    // Joystick Parameters
    private int JOYSTICK_SEND_DELAY_IN_MS  = 300;
    private boolean SEND_JOYSTICK_INFORMATION = false;
    private Joystick js;
    private Handler sendJoystickHandler = new Handler();
    private Runnable sendJoystickRunnable;

    // Battery paramters
    private int DEVELOPMENT_BATTERY_COUNTER_TIME_IN_MS = 100;
    private int BATTERY_MAX = 100;
    private int BATTERY_MIN = 0;
    private int _Battery = 100;
    private Handler mHandler;
    private Runnable mHandlertask;

    // Communication Parameters
    public final static String BROADCAST_STATUS = "Broadcast Status";
    private IntentFilter mIntentFilter;
    private WiFiDirectService mService;
    private boolean mBound;
    private StatusMessage mStatus = new StatusMessage("Default");
    private String mDeviceAddress;
    private int mDeviceVideoPort = -1;

        // Object To handle sending Commands via WiFi
    private CommandObject mCommandObject = new CommandObject(){
        @Override
        public void onSuccess(String successString) {
            super.onSuccess(successString);
        }

        @Override
        public void onFailure(String failureString) {
            super.onFailure(failureString);
            Toast.makeText(mContext, failureString, Toast.LENGTH_SHORT).show();
        }
    };

        // Broadcast handler for received Intents.
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                switch (intent.getAction()) {
                    case WiFiDirectService.ROBOT_STATUS_RECEIVED:
                        mStatus.desipherMeassage(intent.getStringExtra(WiFiDirectService.ROBOT_STATUS_RECEIVED_KEY));
                        mProgressBar.setProgress(mStatus.getBatteryPercentage());
                        BatteryPct.setText(Integer.toString(mStatus.getBatteryPercentage())+"%");
                        break;
                    case WiFiDirectService.WIFI_DIRECT_CONNECTION_CHANGED:
                        boolean connection_state = intent.getBooleanExtra(WiFiDirectService.WIFI_DIRECT_CONNECTION_UPDATED_KEY,true);
                        if(!connection_state)
                            Toast.makeText(mContext, R.string.text_Disconnected, Toast.LENGTH_LONG).show();
                        getActivity().finish();
                }
            }
        }
    };


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        Log.d(TAG, "onCreateView");
        super.onCreate(savedInstanceState);

        // Inflate/Get the Layout
        mLayout = (RelativeLayout)inflater.inflate(R.layout.control_fragment,null);
        mContext = getActivity().getApplicationContext();

        // Instantiate the Joystick and Setup Functionality to Receive User Inputs
        // In the User Inputs Handler the The Controls Are Send via WiFi to the Robot
        JoystickSetup();

        // Get the objects to Handle the Battery Bar
        // If Development is Enabled A Timer will Test the BatteryBar
        // by continuously changing the battery percentage
        BatteryBarSetup();

        // Get the views to Handle Camera button Clicks.
        // and implements the functionality when camera
        // button is clicked
        CameraButtonSetup();

        // Get the views to Handle Streaming control switch clicks.
        StreamSwitchSetup();

        return mLayout;
    }

    @Override
    public void onStart() {
        Log.d(TAG,"started");
        // set up Icons to Show if Assisted Drive mode Or/And Power Save Mode is Enabled/Disabled
        // Gets The views and handles the Visibility of the views
        settingsIconsSetup();
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }


    @Override
    public void onResume() {
        Log.d(TAG,"Resumed");
        // Set up WiFi Direct
        // Registering Broadcast Receiver to Receive messages from the WiFi-Service
        // Binds to The service to be able to send Control Commands
        // and receive StatusMessages
        setupWiFiDirect();
        super.onResume();
    }

    @Override
    public void onPause() {
        Log.d(TAG,"Paused");
        pauseWiFiDirect();
        mTextureView.setVisibility(TextureView.VISIBLE);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"Destroyed");
        unbindFromService();
        super.onDestroy();
    }


    private void setupWiFiDirect(){
        // Add Intent Actions to IntentFilter
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WiFiDirectService.ROBOT_STATUS_RECEIVED);
        mIntentFilter.addAction(WiFiDirectService.WIFI_DIRECT_CONNECTION_CHANGED);

        // Register Broadcast Receiver
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mBroadcastReceiver, mIntentFilter);

        // Bind To Wifi Service
        Intent wifiServiceIntent = new Intent(mContext, WiFiDirectService.class);
        wifiServiceIntent.putExtra(ControlFragment.BROADCAST_STATUS, true);
        bindToService(wifiServiceIntent);
    }

    private void pauseWiFiDirect(){
        // Unregister Broadcast Receiver when Pausing Activity
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mBroadcastReceiver);
        // Unbind from Wifi Service when Pausing Activity
        unbindFromService();
    }

    private void JoystickSetup()
    {
        // Get Development Text Views
        PowerTxt = (TextView) mLayout.findViewById(R.id.PowerId);
        AngleTxt = (TextView) mLayout.findViewById(R.id.AngleId);

        // Get the Actual Joystick Layout
        layout_joystick = (RelativeLayout) mLayout.findViewById(R.id.layout_joystick);


        // Create the joystick
        js = new Joystick(getContext(), layout_joystick, R.drawable.innerjoystickring);

        // Set up Runnable for Timer
        sendJoystickRunnable = new Runnable()
        {
            public void run()
            {
                Log.d(TAG, "SEND_JOYSTICK_INFORMATION is set to true");
                SEND_JOYSTICK_INFORMATION = true;

            }
        };

        // Setup text views from start for development
        if(DEVELOPING) {
            AngleTxt.setText(js.getAngle() + "degree");
            PowerTxt.setText(js.getDistancePercentage() + " %");
        }
        else {
            AngleTxt.setVisibility(View.GONE);
            PowerTxt.setVisibility(View.GONE);
        }

        // If Joystick Is Touched And OnTouchListener is implemented to
        // Handle User imputs on the Joystick
        layout_joystick.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View arg0, MotionEvent arg1) {

                if ( arg1.getAction() == MotionEvent.ACTION_DOWN  || arg1.getAction() == MotionEvent.ACTION_MOVE)
                {  }
                else if (arg1.getAction() == MotionEvent.ACTION_UP)
                {
                    // When joystick is released Force joystick to be reset on screen
                    SEND_JOYSTICK_INFORMATION = true;
                }
                // Draw the Joystick On the Coordinate gotten in MotionEvent arg1
                js.drawStick(arg1);

                // Setup text views for development
                if(DEVELOPING)
                {
                    String angle = String.format("%.2f", js.getAngle());
                    String power = String.format("%.2f", js.getDistancePercentage());
                    AngleTxt.setText(angle + "degree");
                    PowerTxt.setText(power + " %");
                }

                // We will ONLY send Joystick Information about 3 times a Second
                // The Commands Are not necessary to Send more often than that
                if(SEND_JOYSTICK_INFORMATION){
                    // Send Commands with the Object
                    mCommandObject.setCommandWithCoordinates(js.getXpercent(),js.getYpercent());
                    // Send the Obejct through Wifi
                    mService.sendCommandObject(mCommandObject);

                    SEND_JOYSTICK_INFORMATION = false;
                    // Timer is Called to make sure We are only sending 3 times a second
                    sendJoystickHandler.removeCallbacks(sendJoystickRunnable);
                    sendJoystickHandler.postDelayed(sendJoystickRunnable, JOYSTICK_SEND_DELAY_IN_MS);
                }
                return true;
            }
        });
    }


    private void BatteryBarSetup()
    {
        // Get The Battery Bar
        mProgressBar = (ProgressBar) mLayout.findViewById(R.id.progressBar);
        BatteryPct = (TextView)mLayout.findViewById(R.id.BatteryTextView);

        // Code to Test Battery if Development Flag is set
        // Will continuously countdown Battery percentage
        if(DEVELOPING)
        {
            mHandler = new Handler();
            mHandlertask = new Runnable() {
                @Override
                public void run() {
                    if (_Battery <= BATTERY_MIN)
                        _Battery = BATTERY_MAX;
                    else
                        _Battery--;

                    mProgressBar.setProgress(_Battery);
                    BatteryPct.setText(_Battery + "%");
                    mHandler.postDelayed(mHandlertask, DEVELOPMENT_BATTERY_COUNTER_TIME_IN_MS);
                }
            };
            mHandlertask.run();
        }
    }

    private void CameraButtonSetup()
    {
        // Get The ImageButton
        mCameraCircleImageButton = (ImageButton) mLayout.findViewById(R.id.camera_circle);
        mCameraCircleImageButton.setVisibility(ImageButton.GONE);

        mCameraIconImageButton = (ImageButton) mLayout.findViewById(R.id.camera_icon);
        mCameraIconImageButton.setVisibility(ImageButton.GONE);

        // Set An Onclick listener to Receive if User clicks Button
        // A short press on the button will take a screenshot of the video stream
        mCameraCircleImageButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // Check if we have just received an OnLongClick Event
                // For some reason OnClick is received when releasing
                // the button in a Long Click
                if(WASLONGCLICK) {
                    WASLONGCLICK = false;
                    return;
                }

                // Take a screenshot of the TextureView
                if(mTextureView != null)
                    saveScreenShot(mTextureView);
            }
        });
        // Currently long click does nothing
        // TODO: implement burst image feature
        mCameraCircleImageButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // If we have set the development flag we can se a log message showing
                // if we get A Long Click event
                if(DEVELOPING)
                {
                    Log.d(TAG, "onClick: LONG");
                    mHandlertask.run();
                }
                // Set that We have just received Long Click
                // For some reason OnClick is received when releasing
                // the button in a Long Click
                WASLONGCLICK = true;
                return false;
            }
        });
    }

    /*
        Implementation of SurfaceTextureListener
     */
    // Setup the MediaPlayer when textures are available.
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG,"SurfaceTexture available");
        Surface s = new Surface(surface);

        setUpMediaplayer(s);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG,"SurfaceTexture size changed");
    }

    // Release the MediaPlayer when textures are destroyed - we no longer need it
    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.d(TAG,"SurfaceTexture destroyed");
        if(mMediaPlayer != null)
            mMediaPlayer.release();
        return true;
    }

    private void setUpMediaplayer(Surface s) {
        try
        {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setSurface(s);
            mMediaPlayer.setLooping(true);
            mMediaPlayer.setOnPreparedListener(PreparedListener);
            mSwitch.setVisibility(Switch.VISIBLE);
        }
        catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    // Set up MediaPlayer http stream from the robot.
    private void StreamSwitchSetup()
    {
        // Get the Switch
        mSwitch = (Switch) mLayout.findViewById(R.id.CameraStreamSwitch);
        mSwitch.setVisibility(Switch.GONE);

        // Get The VideoView
        mTextureView =(TextureView) mLayout.findViewById(R.id.TextureViewID);
        mTextureView.setSurfaceTextureListener(this);

        // If We Receive a Switch Event we will start or stop the stream of video
        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d("STREAM", "onCheckedChanged: " + isChecked);
                if (isChecked) {
                    if(mDeviceAddress != null && mDeviceVideoPort>0) {
                        String path = "http://" + mDeviceAddress + ":" + Integer.toString(mDeviceVideoPort);
                        Log.d(TAG,"Starting video stream from: " + path);
                        try {
                            mMediaPlayer.setDataSource(path);
                            mMediaPlayer.prepareAsync();    // Asynchronous to avoid blocking UI thread
                            mTextureView.setVisibility(TextureView.VISIBLE);
                            mCameraCircleImageButton.setVisibility(ImageButton.VISIBLE);
                            mCameraIconImageButton.setVisibility(ImageButton.VISIBLE);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (IllegalStateException e) {
                            Log.e(TAG,"Media player was in illegal state");
                            e.printStackTrace();
                            Toast.makeText(mContext, R.string.text_video_unavailable, Toast.LENGTH_SHORT).show();
                            mSwitch.setChecked(false);
                        }
                    } else {
                        Log.e(TAG,"Device address and/or port was invalid");
                        Toast.makeText(mContext, R.string.text_video_unavailable, Toast.LENGTH_SHORT).show();
                        mSwitch.setChecked(false);
                    }
                } else {
                    mMediaPlayer.reset();
                    mTextureView.setVisibility(TextureView.GONE);
                    mCameraCircleImageButton.setVisibility(ImageButton.GONE);
                    mCameraIconImageButton.setVisibility(ImageButton.GONE);
                }
            }
        });
    }

    MediaPlayer.OnPreparedListener PreparedListener = new MediaPlayer.OnPreparedListener(){
        // Mute sound found on stackoverlow
        // http://stackoverflow.com/questions/11021503/muting-one-audio-and-playing-other-audio-in-its-place-videoview
        @Override
        public void onPrepared(MediaPlayer m) {
            try {
                m.setVolume(0f, 0f);
                m.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private void settingsIconsSetup() {
        // Get Shared Preferences
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        // Get the Views To be Showed
        mImagePowerSaveMode = (ImageView)mLayout.findViewById(R.id.imgBatterySaver);
        mImageAssistedDriveMode = (ImageView)mLayout.findViewById(R.id.imgWheel);

        // Determine if the views are to be showed or not!
        if(mSharedPrefs.getBoolean(getString(R.string.settings_power_save_mode_key),false)) {
            mImagePowerSaveMode.setVisibility(View.VISIBLE);
            Log.d(TAG, "VISIBLE");
        } else {

            mImagePowerSaveMode.setVisibility(View.INVISIBLE);
            Log.d(TAG, "INVISIBLE");
        }

        if(mSharedPrefs.getBoolean(getString(R.string.settings_assisted_driving_mode_key),false)) {
            mImageAssistedDriveMode.setVisibility(View.VISIBLE);
            Log.d(TAG, "VISIBLE");
        } else {
            mImageAssistedDriveMode.setVisibility(View.INVISIBLE);
            Log.d(TAG, "INVISIBLE");
        }
    }

    /*
        Capture screenshot from TextureView. This is simple do to since TextureView supplies a
        getBitmap method. The captured bitmap is then stored on the device.
     */
    private void saveScreenShot(TextureView v) {
        Log.d(TAG,"Taking Screenshot");
        Bitmap bm = v.getBitmap();
        if(bm != null) {
            saveBitmap(bm);
        } else {
            Log.e(TAG, "Bitmap is null");
        }
    }

    /*
        Save a bitmap to external storage. All file operations are done in an AsyncTask to avoid
        blocking the UI thread.
        @http://stackoverflow.com/questions/27435985/how-to-capture-screenshot-of-videoview-when-streaming-video-in-android
     */
    private void saveBitmap(final Bitmap bm) {
        Log.d(TAG,"Saving screenshot");
        final String mPath = Environment.getExternalStorageDirectory().toString()
                + "/Pictures/" + "RoboGoGo_" + System.currentTimeMillis() + ".png";

        AsyncTask saveBitmap = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] params) {
                String mPath = Environment.getExternalStorageDirectory().toString()
                        + "/Pictures/" + "RoboGoGo_" + System.currentTimeMillis() + ".png";
                OutputStream fos = null;
                File imageFile = new File(mPath);

                try {
                    fos = new FileOutputStream(imageFile);
                    bm.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                    fos.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Object o) {
                Toast.makeText(mContext, R.string.text_screenshot_saved + " " + mPath, Toast.LENGTH_SHORT).show();
                super.onPostExecute(o);
            }
        };
        /*
            In order to run multiple AsyncTask in parallel, the call to execute them is dependent on
            build version
            @ http://stackoverflow.com/questions/9119627/android-sdk-asynctask-doinbackground-not-running-subclass
        */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            saveBitmap.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
        else
            saveBitmap.execute((Void[]) null);
    }

    protected void bindToService(Intent service) {
        Log.d("bindToService", "called");
        mContext.bindService(service, mConnection, 0/*Context.BIND_AUTO_CREATE*/);
    }

    protected void unbindFromService() {
        // Unbind from the service
        Log.d(TAG, "unbindFromService: ");
        if (mBound) {
            mBound = false;
            mService.removeListener(false, true);
            mContext.unbindService(mConnection);
        }
    }

    protected WiFiDirectService getService() {
        return mService;
    }

    protected StatusMessage getStatus() {
        return mStatus;
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "onServiceConnected called");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            WiFiDirectService.LocalBinder binder = (WiFiDirectService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.addListener(false, true);
            mDeviceAddress = mService.getDeviceIP();
            mDeviceVideoPort = mService.getdeviceHTTPPort();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "onServiceDisconnected called");
            mBound = false;
        }
    };
}
