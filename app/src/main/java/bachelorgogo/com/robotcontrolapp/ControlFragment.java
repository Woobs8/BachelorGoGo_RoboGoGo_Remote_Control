package bachelorgogo.com.robotcontrolapp;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
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

public class ControlFragment extends android.support.v4.app.Fragment {

    String TAG = "ControlFragment";

    private Context mContext;
    boolean DEVELOPING = false;

    // Communication Parameters
    public final static String BROADCAST_STATUS = "Broadcast Status";

    // Layout Paramters
    private RelativeLayout mLayout;

    // Camera Parameters
    private boolean WASLONGCLICK = false;

    // Joystick Parameters
    int JOYSTICK_UPDATE_TIME_IN_MS = 35;
    int JOYSTICK_SEND_DELAY_IN_MS  = 300;
    int JOYSTICK_LAYOUT_SIZE = 500;
    boolean GET_NEW_TOCH_EVENT = true;
    boolean SEND_JOYSTICK_INFORMATION = false;
    RelativeLayout layout_joystick;
    Joystick js;
    TextView AngleTxt;
    TextView PowerTxt;

    // Progress Bar (Battery) Parameters
    ProgressBar mProgressBar;

    // Battery counter
    int DEVELOPMENT_BATTERY_COUNTER_TIME_IN_MS = 100;
    int BATTERY_MAX = 100;
    int BATTERY_MIN = 0;
    int _Battery = 100;
    Handler mHandler;
    Runnable mHandlertask;
    Handler sendJoystickHandler = new Handler();
    Runnable sendJoystickRunnable = new Runnable()
    {
        public void run()
        {
            Log.d(TAG, "SEND_JOYSTICK_INFORMATION is set to true");
            SEND_JOYSTICK_INFORMATION = true;

        }
    };

    TextView BatteryPct;


    // Communication paramters
    IntentFilter mIntentFilter;
    WiFiDirectService mService;
    boolean mBound;
    boolean mConnected = false;



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        Log.d(TAG, "onCreateView");
        super.onCreate(savedInstanceState);

        // Inflate/Get the Layout
        mLayout = (RelativeLayout)inflater.inflate(R.layout.control_fragment,null);
        mContext = getActivity().getApplicationContext();

        ////////////////////////////////
        // Joystick Setup             //
        ////////////////////////////////
        JoystickSetup();

        ////////////////////////////////
        // Setup Progress Battery Bar //
        ////////////////////////////////
        BatteryBarSetup();

        ////////////////////////////////
        // Setup Camera Button        //
        ////////////////////////////////
        CameraButtonSetup();

        ////////////////////////////////
        // Setup Stream Button        //
        ////////////////////////////////
        StreamSwitchSetup();

        settingsIconsSetup();

        return mLayout;
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        settingsIconsSetup();
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }


    CommandObject mCommandObject = new CommandObject(){
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

    @Override
    public void onResume() {
        Log.d("onResume", "Called");
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WiFiDirectService.ROBOT_STATUS_RECEIVED);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mBroadcastReceiver, mIntentFilter);

        Intent wifiServiceIntent = new Intent(mContext, WiFiDirectService.class);
        wifiServiceIntent.putExtra(ControlFragment.BROADCAST_STATUS, true);
        bindToService(wifiServiceIntent);
        super.onResume();
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mBroadcastReceiver);
        unbindFromService();
        super.onPause();
    }

    // Broadcast handler for received Intents.
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                switch (intent.getAction()) {
                    case WiFiDirectService.ROBOT_STATUS_RECEIVED:
                        StatusMessage status = new StatusMessage(intent.getStringExtra(WiFiDirectService.ROBOT_STATUS_RECEIVED_KEY));
                        mProgressBar.setProgress(status.getBatteryPercentage());
                }
            }
        }
    };

    @Override
    public void onDestroy() {
        unbindFromService();
        super.onDestroy();
    }

    private void JoystickSetup()
    {
        ////////////////////////////
        // Development Text Views //
        ////////////////////////////
        PowerTxt = (TextView) mLayout.findViewById(R.id.PowerId);
        AngleTxt = (TextView) mLayout.findViewById(R.id.AngleId);

        layout_joystick = (RelativeLayout) mLayout.findViewById(R.id.layout_joystick);

        // Create the joystick and size it
        js = new Joystick(getContext(), layout_joystick, R.drawable.innerjoystickring);
        js.setAutoSizeSquare(JOYSTICK_LAYOUT_SIZE);

        // Setup text views from start for development
        if(DEVELOPING)
        {
            AngleTxt.setText(js.getAngle() + "degree");
            PowerTxt.setText(js.getDistancePercentage() + " %");
        }
        else
        {
            AngleTxt.setVisibility(View.GONE);
            PowerTxt.setVisibility(View.GONE);
        }

        layout_joystick.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View arg0, MotionEvent arg1) {

                if ( arg1.getAction() == MotionEvent.ACTION_DOWN  || arg1.getAction() == MotionEvent.ACTION_MOVE)
                {  }
                else if (arg1.getAction() == MotionEvent.ACTION_UP)
                {
                    // When joystick is released Force joystick to be reset on screen
                    GET_NEW_TOCH_EVENT = true;
                    SEND_JOYSTICK_INFORMATION = true;
                }
                js.drawStick(arg1);

                // Setup text views for development
                if(DEVELOPING)
                {
                    String angle = String.format("%.2f", js.getAngle());
                    String power = String.format("%.2f", js.getDistancePercentage());
                    AngleTxt.setText(angle + "degree");
                    PowerTxt.setText(power + " %");
                }
                GET_NEW_TOCH_EVENT = false;

                if(SEND_JOYSTICK_INFORMATION){
                    mCommandObject.setCommandWithCoordinates(js.getXpercent(),js.getYpercent());
                    mService.sendCommandObject(mCommandObject);
                    SEND_JOYSTICK_INFORMATION = false;
                    Log.d(TAG, "SEND_JOYSTICK_INFORMATION is set to false");
                    sendJoystickHandler.removeCallbacks(sendJoystickRunnable);
                }
                else{
                    sendJoystickHandler.postDelayed(sendJoystickRunnable, JOYSTICK_SEND_DELAY_IN_MS);
                }

                return true;
            }
        });
    }


    private void BatteryBarSetup()
    {
        mProgressBar = (ProgressBar) mLayout.findViewById(R.id.progressBar);
        BatteryPct = (TextView)mLayout.findViewById(R.id.BatteryTextView);

        // Code to Test Battery
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
        ImageButton imgBtn = (ImageButton) mLayout.findViewById(R.id.camera_circle);
        imgBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if(WASLONGCLICK) {
                    WASLONGCLICK = false;
                    return;
                }
                if(DEVELOPING)
                {
                    Log.d("CLICK", "onClick: SHORT");
                    mHandler.removeCallbacks(mHandlertask);
                }
            }
        });
        imgBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if(DEVELOPING)
                {
                    Log.d("CLICK", "onClick: LONG");
                    mHandlertask.run();
                }
                WASLONGCLICK = true;
                return false;
            }
        });
    }

    private void StreamSwitchSetup()
    {
        Switch mSwitch = (Switch) mLayout.findViewById(R.id.CameraStreamSwitch);
        VideoView video=(VideoView)mLayout.findViewById(R.id.VideoViewID);
        video.setVisibility(VideoView.GONE);
        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(DEVELOPING)
                {
                    Log.d("STREAM", "onCheckedChanged: " + isChecked);

                    String path="http://clips.vorwaerts-gmbh.de/VfE_html5.mp4";
                    VideoView video=(VideoView)mLayout.findViewById(R.id.VideoViewID);
                    if(isChecked){
                        Uri uri=Uri.parse(path);
                        video.setVideoURI(uri);
                        video.setVisibility(VideoView.VISIBLE);
                        video.start();
                        video.setOnPreparedListener(PreparedListener);
                    }
                    else{
                        video.stopPlayback();
                        video.setVisibility(VideoView.GONE);
                    }
                }
            }
        });
    }

    private void settingsIconsSetup() {
        SharedPreferences mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        ImageView imgSaveMode = (ImageView)mLayout.findViewById(R.id.imgBatterySaver);
        ImageView imgDriveAssist = (ImageView)mLayout.findViewById(R.id.imgWheel);
        if(mSharedPrefs.getBoolean(getString(R.string.settings_power_save_mode_key),false)) {
            imgSaveMode.setVisibility(View.VISIBLE);
            Log.d(TAG, "VISIBLE");
        } else {

            imgSaveMode.setVisibility(View.INVISIBLE);
            Log.d(TAG, "INVISIBLE");
        }

        if(mSharedPrefs.getBoolean(getString(R.string.settings_assisted_driving_mode_key),false)) {
            imgDriveAssist.setVisibility(View.VISIBLE);
            Log.d(TAG, "VISIBLE");
        } else {
            imgDriveAssist.setVisibility(View.INVISIBLE);
            Log.d(TAG, "INVISIBLE");
        }
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

    protected void bindToService(Intent service) {
        Log.d("bindToService", "called");
        mContext.bindService(service, mConnection, 0/*Context.BIND_AUTO_CREATE*/);
    }

    protected void unbindFromService() {
        // Unbind from the service
        Log.d(TAG, "unbindFromService: ");
        if (mBound) {
            mBound = false;
            mContext.unbindService(mConnection);
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d("onServiceConnected", "called");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            WiFiDirectService.LocalBinder binder = (WiFiDirectService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.addListener(false, true);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            mService.removeListener(false, true);
        }
    };
}
