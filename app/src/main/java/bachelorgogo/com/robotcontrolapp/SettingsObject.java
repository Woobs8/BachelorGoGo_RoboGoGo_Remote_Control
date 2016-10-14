package bachelorgogo.com.robotcontrolapp;

public class SettingsObject {
    private String mFormattedString;
    private String mDeviceName;
    private String mVideoQualityIndex;
    private boolean mPowerSaveMode;
    private boolean mAssistedDrivingMode;

    // VEHICLE SETTING
    public final String VEHICLE_NAME_SETTING = "CV*NM";
    public final String VEHICLE_DRIVE_MODE = "CV*DM";
    public final String VEHICLE_POWER_SAVE_MODE = "CV*CA";

    // CAMERA SETTINGS
    public final String CAMERA_VIDEO_QUALITY = "CC*VQ";

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

        // name
        mFormattedString += VEHICLE_NAME_SETTING + ":" + mDeviceName+";";

        // resolution
        mFormattedString += CAMERA_VIDEO_QUALITY + ":" + mVideoQualityIndex + ";";

        // power mode
        if(mPowerSaveMode)
            mFormattedString += VEHICLE_POWER_SAVE_MODE + ":" + Integer.toString(1) + ";";
        else
            mFormattedString += VEHICLE_POWER_SAVE_MODE + ":" + Integer.toString(0) + ";";

        // assisted driving mode
        if(mAssistedDrivingMode)
            mFormattedString += VEHICLE_DRIVE_MODE + ":" + Integer.toString(1) + ";";
        else
            mFormattedString += VEHICLE_DRIVE_MODE + ":" + Integer.toString(0) + ";";
    }

    public String getDataString() {
        return mFormattedString;
    }

    // Should be overridden
    public void onSuccess() {

    }

    // Should be overridden
    public void onFailure() {

    }
}
