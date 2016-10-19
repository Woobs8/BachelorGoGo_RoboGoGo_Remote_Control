package bachelorgogo.com.robotcontrolapp;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.net.InetAddress;

public class ControlFragment extends android.support.v4.app.Fragment {

    String TAG = "ControlFragment";

    // Development flag
    boolean DEVELOPING = false;

    // Context
    private Context mContext;

    // UI Elements
    private TextView BatteryPct;
    private SharedPreferences mSharedPrefs;
    private Switch mSwitch;
    private VideoView mVideoView;
    private ImageView mImagePowerSaveMode;
    private ImageView mImageAssistedDriveMode;
    private ImageButton mImageButton;
    private RelativeLayout mLayout;
    private RelativeLayout layout_joystick;
    private TextView AngleTxt;
    private TextView PowerTxt;
    private ProgressBar mProgressBar;

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
    private boolean mConnected = false;
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
        // Only Development functionality is implemented
        // TODO - streaming Camera streaming not yet implemented!
        CameraButtonSetup();

        // Get the views to Handle Streaming control switch clicks.
        StreamSwitchSetup();

        return mLayout;
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
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
        // Set up WiFi Direct
        // Registering Broadcast Receiver to Receive messages from the WiFi-Service
        // Binds to The service to be able to send Control Commands
        // and receive StatusMessages
        setupWiFiDirect();

        super.onResume();
    }

    @Override
    public void onPause() {

        pauseWiFiDirect();
        super.onPause();
    }

    @Override
    public void onDestroy() {
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
        mImageButton = (ImageButton) mLayout.findViewById(R.id.camera_circle);

        // Set An Onclick listener to Receive if User clicks Button
        // Real Camera Functionality have NOT been implemented due to
        // WiFi Camera Stream is not implemented yet!
        mImageButton.setOnClickListener(new View.OnClickListener()
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
                // If we have set the development flag we can se a log message showing
                // if we get A Short Click event
                if(DEVELOPING)
                {
                    Log.d(TAG, "onClick: SHORT");
                    mHandler.removeCallbacks(mHandlertask);
                }
            }
        });
        mImageButton.setOnLongClickListener(new View.OnLongClickListener() {
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

    private void StreamSwitchSetup()
    {
        // Get the Switch
        mSwitch = (Switch) mLayout.findViewById(R.id.CameraStreamSwitch);

        // Get The VideoView
        mVideoView =(VideoView)mLayout.findViewById(R.id.VideoViewID);
        mVideoView.setVisibility(VideoView.GONE);

        // If We Receive a Switch Event we will start or stop the stream of video
        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d("STREAM", "onCheckedChanged: " + isChecked);

                if (isChecked) {
                    if(mDeviceAddress != null && mDeviceVideoPort>0) {
                        //String path="http://clips.vorwaerts-gmbh.de/VfE_html5.mp4";
                        String path = "http://" + mDeviceAddress + ":" + Integer.toString(mDeviceVideoPort); //192.168.49.10:6000;
                        Log.d(TAG,"Starting video stream from: " + path);
                        Uri uri = Uri.parse(path);
                        mVideoView.setVideoURI(uri);
                        mVideoView.setVisibility(VideoView.VISIBLE);
                        //Restart when done
                        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mp) {
                                mVideoView.start();
                            }
                        });
                        mVideoView.start();
                        // Make sure that we Do NOT play audio received.
                        // No audio should be received from the robot
                        mVideoView.setOnPreparedListener(PreparedListener);
                    } else {
                        Toast.makeText(mContext, R.string.text_video_unavailable, Toast.LENGTH_SHORT).show();
                        mSwitch.setChecked(false);
                    }
                } else {
                    mVideoView.stopPlayback();
                    mVideoView.setVisibility(VideoView.GONE);
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
                if (m.isPlaying()) {
                    m.stop();
                    m.release();
                    m = new MediaPlayer();
                }
                m.setVolume(0f, 0f);
                m.setLooping(false);
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
