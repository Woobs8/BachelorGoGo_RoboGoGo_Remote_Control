package bachelorgogo.com.robotcontrolapp;

/**
 * Created by rasmus on 10/13/2016.
 */

public class CommandObject {
    // STEERING BY COORDINATES 0-100
    private final String STEERING_XY_COORDINATE = "CS*XY";
    private final int X_MAX = 100;
    private final int X_MIN = -100;
    private final int Y_MAX = 100;
    private final int Y_MIN = -100;

    // STEERING BY POWER(0-100) AND ANGLE(+-0to180)
    private final String STEERING_POWER_ANGLE = "CS*PA";
    private final int POWER_MAX = 100;
    private final int POWER_MIN = -100;
    private final int ANGLE_MAX = 180;
    private final int ANGLE_MIN = -180;

    private String DataCommandString = "";

    CommandObject() {    }

    // Called when using Coordinates
    public void setCommandWithCoordinates(float x, float y) {
        // Truncate Coordinates if out of Range
        if(x > X_MAX)
            x = X_MAX;
        else if (x < X_MIN)
            x = X_MIN;

        if(y > Y_MAX)
            y = Y_MAX;
        else if (y < Y_MIN)
            y = Y_MIN;

        DataCommandString = STEERING_XY_COORDINATE + String.format("%.2f", x) + ";" + String.format("%.2f", y);
    }

    // Called when using power / angle
    public void setCommandWithPowerAndAngle(float power, float angle) {
        if(power > POWER_MAX)
            power = POWER_MAX;
        else if (power < POWER_MIN)
            power = POWER_MIN;

        if(angle > ANGLE_MAX)
            angle = ANGLE_MAX;
        else if (angle < ANGLE_MIN)
            angle = ANGLE_MIN;

        DataCommandString = STEERING_POWER_ANGLE + Float.toString(power) + ";" + Float.toString(angle);

    }

    public String getDataCommandString() {
        return DataCommandString;
    }

    public void onSuccess(String successString){

    }

    public void onFailure(String failureString){

    }
}
