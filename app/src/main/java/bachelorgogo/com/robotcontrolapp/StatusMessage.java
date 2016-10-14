package bachelorgogo.com.robotcontrolapp;

/**
 * Created by rasmus on 10/13/2016.
 */

public class StatusMessage {
    
    private String mRawData = "";
    private String mSegmentedRawData[];

    final private String CAR_NAME_TAG = "Name";
    final private String BATTERY_TAG = "Battery";
    final private String FIRMWARE_TAG = "Firmware";
    final private String CAMERA_TAG = "Camera";
    final private String STORAGE_SPACE_TAG = "Space";
    final private String STORAGE_REMAINING = "Remaining";

    private String carName = "";
    private int batteryPercentage = 0;
    private String firmwareVersion = "";
    private boolean cameraAvailable = false;
    private String storageSpace = "";
    private String storageRemaining = "";

    StatusMessage(String RawData){
        mRawData = RawData;

        // Removing Command Header to only have Data
        // Example of header see CommandObject
        RawData.substring(RawData.indexOf("*")+2);

        mSegmentedRawData = mRawData.split(";");

        for(int i = 0; i < mSegmentedRawData.length; i++){
            String tempDataSegment[] = mSegmentedRawData[i].split(":");
            switch (tempDataSegment[0]){
                case CAR_NAME_TAG :
                    carName = tempDataSegment[1];
                    break;
                case BATTERY_TAG :
                    batteryPercentage = Integer.parseInt(tempDataSegment[1]);
                    break;
                case FIRMWARE_TAG :
                    firmwareVersion = tempDataSegment[1];
                    break;
                case CAMERA_TAG :
                    cameraAvailable = (tempDataSegment[1] == "1") ? true : false;
                    break;
                case STORAGE_SPACE_TAG :
                    storageSpace = tempDataSegment[1];
                    break;
                case STORAGE_REMAINING :
                    storageRemaining = tempDataSegment[1];
                    break;
            }
        }
    }

    public int getBatteryPercentage() {
        return batteryPercentage;
    }

    public String getCarName() {
        return carName;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public String getStorageRemaining() {
        return storageRemaining;
    }

    public String getStorageSpace() {
        return storageSpace;
    }
    public boolean getCameraAvailable(){
        return cameraAvailable;
    }

}
