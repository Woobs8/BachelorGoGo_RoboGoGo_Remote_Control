package bachelorgogo.com.robotcontrolapp;


/////////////////// Import of Protocol to send/receive //////////////////////////
import static bachelorgogo.com.robotcontrolapp.RobotProtocol.SEND_COMMANDS.*;
import static bachelorgogo.com.robotcontrolapp.RobotProtocol.DATA_TAGS.*;
/////////////////////////////////////////////////////////////////////////////////

/*
    SettingsObject class is used to pass the settings to be sent to the robot between an activity
    and the WiFiDirectService.
    SettingsObject has two callbacks, which can be overridden by the declaring activity, in order
    to handle success or failure scenarios.
 */
public class SettingsObject {
    private String mFormattedString;
    private String mDeviceName;
    private String mVideoQualityIndex;
    private boolean mPowerSaveMode;
    private boolean mAssistedDrivingMode;

    SettingsObject(String name, String videoQuality, boolean powerMode, boolean assistedDrivingMode) {
        setSettings(name, videoQuality, powerMode, assistedDrivingMode);
    }

    SettingsObject() {
        setSettings("","1",false, false);
    }

    public void setSettings(String name, String videoQuality, boolean powerMode, boolean assistedDrivingMode) {
        mDeviceName = name;
        mVideoQualityIndex = videoQuality;
        mPowerSaveMode = powerMode;
        mAssistedDrivingMode = assistedDrivingMode;
        formatString();
    }

    public void setDeviceName(String name) {
        mDeviceName = name;
        formatString();
    }

    public void setResolution(String videoQuality) {
        mVideoQualityIndex = videoQuality;
        formatString();
    }

    public void setPowerMode(boolean powerMode) {
        mPowerSaveMode = powerMode;
        formatString();
    }

    public void setAssistedDrivingMode(boolean assistedDrivingMode) {
        mAssistedDrivingMode = assistedDrivingMode;
        formatString();
    }

    /*
        This function returns the current settings as a string formatted to a custom protocol
     */
    private void formatString() {
        mFormattedString = "";

        RobotProtocol protocol;
        // COMMAND INSERT
        mFormattedString = CMD_SETTINGS;
        // CAR NAME INSERT
        mFormattedString += CAR_NAME_TAG + SPACING_BETWEEN_TAG_AND_DATA + mDeviceName;
        mFormattedString += SPACING_BETWEEN_STRINGS;

        // VIDEO QUALITY INSERT
        mFormattedString += CAMERA_VIDEO_QUALITY_TAG + SPACING_BETWEEN_TAG_AND_DATA + mVideoQualityIndex;
        mFormattedString += SPACING_BETWEEN_STRINGS;

        // POWER SAVE MODE INSERT
        mFormattedString += POWER_SAVE_DRIVE_MODE_TAG + SPACING_BETWEEN_TAG_AND_DATA + (mPowerSaveMode==true ? TRUE : FALSE);
        mFormattedString += SPACING_BETWEEN_STRINGS;

        // ASSISTED DRIVE MODE INSERT
        mFormattedString += ASSERTED_DRIVE_MODE_TAG + SPACING_BETWEEN_TAG_AND_DATA + (mAssistedDrivingMode==true ? TRUE : FALSE)   ;

    }

    public String getDataString() {
        return mFormattedString;
    }

    public String getAckString() {
        return CMD_ACK;
    }

    // Invoked by SettingsClient. Should be overridden
    public void onSuccess(String command) {

    }

    // Invoket by SettingsClient. Should be overridden
    public void onFailure(String command) {

    }
}
