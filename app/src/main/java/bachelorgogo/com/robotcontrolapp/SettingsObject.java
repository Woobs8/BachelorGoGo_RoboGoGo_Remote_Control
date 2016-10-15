package bachelorgogo.com.robotcontrolapp;


/////////////////// Import of Protocol to send/receive //////////////////////////
import static bachelorgogo.com.robotcontrolapp.RobotProtocol.SEND_COMMANDS.*;
import static bachelorgogo.com.robotcontrolapp.RobotProtocol.DATA_TAGS.*;
/////////////////////////////////////////////////////////////////////////////////

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

    private void formatString() {
        mFormattedString = "";

        RobotProtocol protocol;
        // COMMAND INSERT
        mFormattedString = CMD_SETTINGS;
        // CAR NAME INSERT
        mFormattedString += CAR_NAME_TAG + SPACING_BETWEEN_STRINGS + mDeviceName;
        mFormattedString += SPACING_BETWEEN_TAG_AND_DATA;

        // VIDEO QUALITY INSERT
        mFormattedString += CAMERA_VIDEO_QUALITY_TAG + SPACING_BETWEEN_STRINGS + mVideoQualityIndex;
        mFormattedString += SPACING_BETWEEN_TAG_AND_DATA;

        // POWER SAVE MODE INSERT
        mFormattedString += POWER_SAVE_DRIVE_MODE_TAG + SPACING_BETWEEN_STRINGS + (mPowerSaveMode==true ? TRUE : FALSE);
        mFormattedString += SPACING_BETWEEN_TAG_AND_DATA;

        // ASSISTED DRIVE MODE INSERT
        mFormattedString += ASSERTED_DRIVE_MODE_TAG + SPACING_BETWEEN_STRINGS + (mAssistedDrivingMode==true ? TRUE : FALSE)   ;

    }

    public String getDataString() {
        return mFormattedString;
    }

    public String getAckString() {
        return CMD_ACK;
    }

    // Should be overridden
    public void onSuccess(String command) {

    }

    // Should be overridden
    public void onFailure(String command) {

    }
}
