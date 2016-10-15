package bachelorgogo.com.robotcontrolapp;

/**
 * Created by rasmus on 10/13/2016.
 */
/////////////////// Import of Protocol to send/receive //////////////////////////
import static bachelorgogo.com.robotcontrolapp.RobotProtocol.DATA_TAGS.*;
import static bachelorgogo.com.robotcontrolapp.RobotProtocol.SEND_COMMANDS.*;
/////////////////////////////////////////////////////////////////////////////////

public class StatusMessage {
    
    private String mRawData = "";
    private String mSegmentedRawData[];

    private String carName = "BachelorGogo";
    private String macAddr = "MAC:A:B:C:1:2:3";
    private String ipAddr = "192.180.0.0";
    private int batteryPercentage = 30;
    private boolean cameraAvailable = true;
    private String storageSpace = "40TB";
    private String storageRemaining = "1kB";

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
                case MAC_ADDRESS_TAG :
                    macAddr = tempDataSegment[1];
                    break;
                case CAMERA_TAG :
                    if(tempDataSegment[1] == TRUE)
                        cameraAvailable = true;
                    else if (tempDataSegment[1] == FALSE)
                        cameraAvailable = false;
                    break;
                case STORAGE_SPACE_TAG :
                    storageSpace = tempDataSegment[1];
                    break;
                case STORAGE_REMAINING_TAG :
                    storageRemaining = tempDataSegment[1];
                    break;
                case IP_ADDRESS_TAG :
                    ipAddr = tempDataSegment[1];
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

    public String getStorageRemaining() {
        return storageRemaining;
    }

    public String getStorageSpace() {
        return storageSpace;
    }

    public boolean getCameraAvailable(){
        return cameraAvailable;
    }

    public String getMac() {
        return macAddr;
    }

    public String getIpAddr() {
        return ipAddr;
    }
}
