package bachelorgogo.com.robotcontrolapp;

/**
 * Created by rasmus on 10/6/2016.
 */

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Toast;

// THIS CLASS IS ADAPTED FROM
// http://www.akexorcist.com/2012/10/android-code-joystick-controller.html

public class Joystick {
    private boolean JOYSTICK_IS_TOUCHED = false;

    private int mMaxJoystickDistance = 0;
    private int mJoystickOpasity = 0;
    private int mMinJoystickDistance = 0;

    private Context mContext;
    private ViewGroup mViewGroup;
    private LayoutParams mParams;
    private int stick_width, stick_height;

    private int position_x = 0, position_y = 0;
    private double distance = 0, angle = 0;

    private DrawCanvas draw;

    private Paint paint = new Paint();
    private float percentToAlpha = 255/100;
    private int MAX_ALPHA = 255;

    private Bitmap stick;

////////////////////////////////////////////////////////
    ///////////// PUBLIC FUNCTIONS ///////////////
////////////////////////////////////////////////////////

    public Joystick (Context context, ViewGroup layout, int stick_res_id) {
        // Get Joystick Parameters and essential variables to control the viewgroup
        mContext = context;
        mViewGroup = layout;
        mParams = mViewGroup.getLayoutParams();

        // Get the bitmap of the stick. The ring around the stick is drawn as the background of the layout
        stick = BitmapFactory.decodeResource(mContext.getResources(),
                stick_res_id);
        stick_width = stick.getWidth();
        stick_height = stick.getHeight();

        // Get the drawing object
        draw = new DrawCanvas(mContext);
        setAutoSizeSquare();
        setDefaultStickLayout();
    }

    public void drawStick(MotionEvent arg1) {
        // Get current user inputs
        position_x = (int) (arg1.getX() - (mParams.width / 2));
        position_y = (int) (arg1.getY() - (mParams.height / 2));

        //calculate the distance and the angle from inputs
        distance = (float) Math.sqrt(Math.pow(position_x, 2) + Math.pow(position_y, 2));
        angle = (float) cal_angle(position_x, position_y);

        // If stick has just been touched
        if(arg1.getAction() == MotionEvent.ACTION_DOWN) {
            if(distance <= (mParams.width / 2) - mMaxJoystickDistance) {
                draw.position(arg1.getX(), arg1.getY());
                draw();
                JOYSTICK_IS_TOUCHED = true;
            }
        }
        // If the stick is being moved by the user
        else if(arg1.getAction() == MotionEvent.ACTION_MOVE && JOYSTICK_IS_TOUCHED) {
            if(distance <= (mParams.width / 2) - mMaxJoystickDistance) {
                // If the users finger is being moved but inside the canvas area just draw the canvas
                draw.position(arg1.getX(), arg1.getY());
                draw();
            }
            else if(distance > (mParams.width / 2) - mMaxJoystickDistance){
                // If the stick is being moved and the user moves finger outside joystick area
                // Calculate where the stick is to be set as the nearest point on the joystick canvas
                float x = (float) (Math.cos(Math.toRadians(cal_angle(position_x, position_y))) * ((mParams.width / 2) - mMaxJoystickDistance));
                float y = (float) (Math.sin(Math.toRadians(cal_angle(position_x, position_y))) * ((mParams.height / 2) - mMaxJoystickDistance));
                x += (mParams.width / 2);
                y += (mParams.height / 2);
                draw.position(x, y);
                draw();
            }
            else {
                mViewGroup.removeView(draw);
            }
        }
        // If the user releases the stick
        else if(arg1.getAction() == MotionEvent.ACTION_UP) {
            setDefaultStickLayout();
            JOYSTICK_IS_TOUCHED = false;
        }
    }

    public int getXpercent() {
        return (int)(getX() * 100 / (float)(mParams.width/2 - mMaxJoystickDistance));
    }

    public int getYpercent() {
        return (int)(getY() * 100 / (float)(mParams.height/2 - mMaxJoystickDistance));
    }

    public double getDistancePercentage() {
        return getDistance() * 100 / mMaxJoystickDistance;
    }

    public double getAngle() {
        if(distance > mMinJoystickDistance && JOYSTICK_IS_TOUCHED) {
            if(Math.signum((float)(getX())) == 1){
                return ((angle+90)%180);
            }
            else
                return ((angle+90)%180-180);
        }
        return 0;
    }

    public void setMinimumDistance(int minDistance) {
        mMinJoystickDistance = minDistance;
    }

    public int getMinimumDistance() {
        return mMinJoystickDistance;
    }

    public void setMaxJoystickDistance(int MaxJoystickDistance) {
        mMaxJoystickDistance = MaxJoystickDistance;
    }

    public int getMaxJoystickDistance() {
        return mMaxJoystickDistance;
    }

    public int getJoystickOpasity() {
        return mJoystickOpasity;
    }

    public void setJoystickOpasity(int percent) {
        if(percent > 100)
            percent = 100;
        if(percent < 0)
            percent = 0;
        mJoystickOpasity = percent;
        mViewGroup.getBackground().setAlpha((int)(MAX_ALPHA - mJoystickOpasity*percentToAlpha));
        paint.setAlpha((int)(MAX_ALPHA - mJoystickOpasity*percentToAlpha));
    }

    public void setStickSize(int width, int height) {
        stick = Bitmap.createScaledBitmap(stick, width, height, false);
        stick_width = stick.getWidth();
        stick_height = stick.getHeight();
        setDefaultStickLayout();
    }
    public int getStickWidth() {
        return stick_width;
    }
    public int getStickHeight() {
        return stick_height;
    }

    public void setLayoutSize(int width, int height) {
        mParams.width = width;
        mParams.height = height;
        setDefaultStickLayout();
    }
    public int getLayoutWidth() {
        return mParams.width;
    }
    public int getLayoutHeight() {
        return mParams.height;
    }

    public void setAutoSizeSquare(){
        // This function autosizes analogue stick to being within ViewGroup
        // Stick is half the size of view and OFFSET will be set to half the sick size.
        int length = mParams.height;
        setLayoutSize(length, length);
        setStickSize(length/2,length/2);
        setMaxJoystickDistance(length/4);
        setMinimumDistance(0);
        setDefaultStickLayout();
    }

    ////////////////////////////////////////////////////////
    ///////////// PRIVATE FUNCTIONS ///////////////
    ////////////////////////////////////////////////////////
    private void setDefaultStickLayout() {
        draw.position(mParams.width/2,mParams.height/2);
        draw();
        setJoystickOpasity(0);
        position_x = 0;
        position_y = 0;
        distance = 0;
        angle = 0;
    }

    private double getDistance() {
        if(distance > mMinJoystickDistance && distance < ((mParams.width / 2) - mMaxJoystickDistance) && JOYSTICK_IS_TOUCHED) {
            return distance;
        }
        else if(distance > mMinJoystickDistance && distance > ((mParams.width / 2) - mMaxJoystickDistance) && JOYSTICK_IS_TOUCHED) {
            return mMaxJoystickDistance;
        }
        return 0;
    }

    private int getX() {
        if(distance > mMinJoystickDistance && JOYSTICK_IS_TOUCHED) {
            return (int)(draw.x)-((mParams.width / 2) - mMaxJoystickDistance);
        }
        return 0;
    }

    private int getY() {
        if(distance > mMinJoystickDistance && JOYSTICK_IS_TOUCHED) {
            return -( (int)(draw.y)-((mParams.height / 2) - mMaxJoystickDistance) );
        }
        return 0;
    }

    private void draw() {
        try
        {
            mViewGroup.removeView(draw);
        }
        catch (Exception e)
        {
            Toast.makeText(mContext, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        mViewGroup.addView(draw);
    }

    private class DrawCanvas extends View{
        float x, y;
        private DrawCanvas(Context mContext) {
            super(mContext);
        }
        public void onDraw(Canvas canvas) {
            canvas.drawBitmap(stick, x, y, paint);
        }
        private void position(float pos_x, float pos_y) {
            x = pos_x - (stick_width / 2);
            y = pos_y - (stick_height / 2);
        }
    }

    private double cal_angle(float x, float y) {
        if(x >= 0 && y >= 0)
            return Math.toDegrees(Math.atan(y / x));
        else if(x < 0 && y >= 0)
            return Math.toDegrees(Math.atan(y / x)) + 180;
        else if(x < 0 && y < 0)
            return Math.toDegrees(Math.atan(y / x)) + 180;
        else if(x >= 0 && y < 0)
            return Math.toDegrees(Math.atan(y / x)) + 360;
        return 0;
    }
}
