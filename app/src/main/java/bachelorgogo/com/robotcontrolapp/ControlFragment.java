package bachelorgogo.com.robotcontrolapp;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

public class ControlFragment extends android.support.v4.app.Fragment {

    private Context mContext;
    boolean DEVELOPING = false;

    // Layout Paramters
    private RelativeLayout mLayout;

    // Camera Parameters
    private boolean WASLONGCLICK = false;

    // Joystick Parameters
    int JOYSTICK_UPDATE_TIME_IN_MS = 35;
    int JOYSTICK_LAYOUT_SIZE = 500;
    boolean GET_NEW_TOCH_EVENT = true;
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
    TextView BatteryPct;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Inflate/Get the Layout
        mLayout = (RelativeLayout)inflater.inflate(R.layout.control_fragment,null);

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

        return mLayout;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
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
                }

                if(GET_NEW_TOCH_EVENT)
                {
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
                }
                else
                {
                    // Handler takes care of how often the Drawer can put the Joystick onto the screen
                    // This is to ensure the joystick doesnt use too many resources.
                    // GET_NEW_TOCH_EVENT Flag will handle when to update the joystick view.
                    new Handler().postDelayed(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            GET_NEW_TOCH_EVENT = true;
                        }
                    }, JOYSTICK_UPDATE_TIME_IN_MS);
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
        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(DEVELOPING)
                {
                    Log.d("STREAM", "onCheckedChanged: " + isChecked);
                }
            }
        });
    }

}
