package bachelorgogo.com.robotcontrolapp;

/////////////////// Import of Protocol to send/receive //////////////////////////
import static bachelorgogo.com.robotcontrolapp.RobotProtocol.SEND_COMMANDS.*;
import static bachelorgogo.com.robotcontrolapp.RobotProtocol.DATA_TAGS.*;
/////////////////////////////////////////////////////////////////////////////////

public class CommandObject {
    // This object Holds the Data-string to be received by the Robot
    // The Object is handed to the WifiDirectClient

    // STEERING BY COORDINATES 0-100
    private final int X_MAX = 100;
    private final int X_MIN = -100;
    private final int Y_MAX = 100;
    private final int Y_MIN = -100;

    // STEERING BY POWER(0-100) AND ANGLE(+-0to180)
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

        DataCommandString = CMD_CONTROL;
        DataCommandString += SPACING_BETWEEN_STRINGS;
        DataCommandString += STEERING_X_COORDINATE_TAG  + SPACING_BETWEEN_TAG_AND_DATA + String.format("%.2f", x);
        DataCommandString += SPACING_BETWEEN_STRINGS;
        DataCommandString += STEERING_Y_COORDINATE_TAG + SPACING_BETWEEN_TAG_AND_DATA + String.format("%.2f", y);
        DataCommandString += SPACING_BETWEEN_STRINGS;
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

        DataCommandString = CMD_CONTROL;
        DataCommandString += SPACING_BETWEEN_STRINGS;
        DataCommandString += STEERING_POWER_TAG  + SPACING_BETWEEN_TAG_AND_DATA + String.format("%.2f", power);
        DataCommandString += SPACING_BETWEEN_STRINGS;
        DataCommandString += SEERING_ANGLE_TAG + SPACING_BETWEEN_TAG_AND_DATA + String.format("%.2f", angle);
        DataCommandString += SPACING_BETWEEN_STRINGS;

    }

    public String getDataCommandString() {
        return DataCommandString;
    }

    public void onSuccess(String successString){

    }

    public void onFailure(String failureString){

    }
}
