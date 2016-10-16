package bachelorgogo.com.robotcontrolapp;

/**
 * Created by rasmus on 10/13/2016.
 */
/////////////////// Import of Protocol to send/receive //////////////////////////
import android.util.Log;

import static bachelorgogo.com.robotcontrolapp.RobotProtocol.DATA_TAGS.*;
import static bachelorgogo.com.robotcontrolapp.RobotProtocol.SEND_COMMANDS.*;
/////////////////////////////////////////////////////////////////////////////////

public class StatusMessage {
    private final String TAG = "StatusMessage";
    private String mRawData = "";
    private String mSegmentedRawData[];

    private String carName = "BachelorGogo";
    private String macAddr = "";
    private String ipAddr = "";
    private int batteryPercentage = 0;
    private boolean cameraAvailable = true;
    private String storageSpace = "";
    private String storageRemaining = "";

    StatusMessage(String RawData){
        mRawData = RawData;

    }

    public void desipherMeassage(String RawData){
        // Removing Command Header to only have Data
        // Example of header see CommandObject
        mRawData = RawData.substring(RawData.indexOf("*")+3);

        mSegmentedRawData = mRawData.split(";");

        for(int i = 0; i < mSegmentedRawData.length; i++){
            Log.d(TAG,mSegmentedRawData[i]);
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
                    if(tempDataSegment[1].equals(TRUE))
                        cameraAvailable = true;
                    else if (tempDataSegment[1].equals(FALSE))
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

    public void setCarName(String carName) {
        this.carName = carName;
    }

    public void setMacAddr(String macAddr) {
        this.macAddr = macAddr;
    }

    public void setIpAddr(String ipAddr) {
        this.ipAddr = ipAddr;
    }
}
