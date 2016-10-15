package bachelorgogo.com.robotcontrolapp;
import static bachelorgogo.com.robotcontrolapp.RobotProtocol.SEND_COMMANDS.*;
import static bachelorgogo.com.robotcontrolapp.RobotProtocol.DATA_TAGS.*;
/**
 * Created by rasmus on 10/15/2016.
 */

public final class RobotProtocol {
     public final class SEND_COMMANDS{
         // STEERING BY COORDINATES 0-100
         public static final String STEERING_XY_COORDINATE ="CS*XY";

         // STEERING BY POWER(0-100) AND ANGLE(+-0to180)
         public static final String STEERING_POWER_ANGLE = "CS*PA";

         // VEHICLE SETTING
         public static final String VEHICLE_NAME_SETTING = "CV*NM";
         public static final String VEHICLE_DRIVE_MODE = "CV*DM";
         public static final String VEHICLE_POWER_SAVE_MODE = "CV*CA";

         // STATUS MESSAGES
         public static final String STATUS_PAKECT = "ST*PK";
         public static final String STATUS_LOW_BATTERY_WARNING = "ST*WB";
         public static final String STATUS_LOW_STORAGE_WARNING = "ST*WS";
         public static final String STATUS_GENERAL_ERROR_SHUTDOWN = "ST*ER";

         // CAMERA SETTINGS
         public static final String CAMERA_INSTALLED = "CC*IN";
         public static final String CAMERA_VIDEO_QUALITY = "CC*VQ";
         public static final String CAMERA_RECORD = "CC*RE";
         public static final String CAMERA_TAKE_PICTURE = "CC*TP";
         public static final String CAMERA_RECORDING_MAX_LENGTH = "CC*ML";

         // STREAMING HELPER
         public static final String STREAMING_PORT = "CS*PO";
         public static final String STREAMING_SETTING = "CS*SE";
         public static final String STREAMING_STATUS = "CS*ST";

         // COMMAND
         public static final String CMD_CONTROL = "CMD*CT";
         public static final String CMD_STATUS = "CMD*ST";
         public static final String CMD_SETTINGS = "CMD*SE";
         public static final String CMD_ACK = "CMD*OK";


         public static final String SPACING_BETWEEN_TAG_AND_DATA = ":";
         public static final String SPACING_BETWEEN_STRINGS = ";";
         public static final String TRUE = "1";
         public static final String FALSE = "0";


         private SEND_COMMANDS(){}
    }

    public class DATA_TAGS {
        // STEERING BY COORDINATES 0-100
        public static final String CAR_NAME_TAG = "Name";
        public static final String MAC_ADDRESS_TAG = "Mac";
        public static final String IP_ADDRESS_TAG = "Ip";
        public static final String BATTERY_TAG = "Battery";
        public static final String CAMERA_TAG = "Camera";
        public static final String STORAGE_SPACE_TAG = "Space";
        public static final String STORAGE_REMAINING_TAG = "Remaining";
        public static final String ASSERTED_DRIVE_MODE_TAG = "Asserted";
        public static final String POWER_SAVE_DRIVE_MODE_TAG = "Asserted";
        public static final String STEERING_X_COORDINATE_TAG = "X";
        public static final String STEERING_Y_COORDINATE_TAG = "Y";
        public static final String STEERING_POWER_TAG = "Pwr";
        public static final String SEERING_ANGLE_TAG = "Agl";

        private DATA_TAGS(){}
    }


    private RobotProtocol(){}

    public static String getDataBroadcastString(String carName,String battery, String memSpace, String memRemaining, String cameraAvailable, String macAddress, String ipAddress){
        RobotProtocol protocol;
        // COMMAND INSERT
        String data = SEND_COMMANDS.CMD_STATUS;
        // CAR NAME INSERT
        data += CAR_NAME_TAG + SPACING_BETWEEN_STRINGS + carName;
        data += SPACING_BETWEEN_TAG_AND_DATA;

        // BATTERY INSERT
        data += BATTERY_TAG + SPACING_BETWEEN_STRINGS + battery;
        data += SPACING_BETWEEN_TAG_AND_DATA;

        // STORAGE SPACE INSERT
        data += STORAGE_SPACE_TAG + SPACING_BETWEEN_STRINGS + memSpace;
        data += SPACING_BETWEEN_TAG_AND_DATA;

        // STORAGE REMAINING INSERT
        data += STORAGE_REMAINING_TAG + SPACING_BETWEEN_STRINGS + memRemaining;
        data += SPACING_BETWEEN_TAG_AND_DATA;

        // CAMERA INSERT
        data += CAMERA_TAG + SPACING_BETWEEN_STRINGS + cameraAvailable;
        data += SPACING_BETWEEN_TAG_AND_DATA;


        // MAC ADDRESS INSERT
        data += MAC_ADDRESS_TAG + SPACING_BETWEEN_STRINGS + macAddress;
        data += SPACING_BETWEEN_TAG_AND_DATA;

        // IP ADDRESS INSERT
        data += IP_ADDRESS_TAG + SPACING_BETWEEN_STRINGS + ipAddress;

        return data;
    }

    public static String getDataBroadcastString(String carName,String battery, String memSpace, String memRemaining, String cameraAvailable){
        RobotProtocol protocol;
        // COMMAND INSERT
        String data = SEND_COMMANDS.CMD_STATUS;
        // CAR NAME INSERT
        data += CAR_NAME_TAG + SPACING_BETWEEN_STRINGS + carName;
        data += SPACING_BETWEEN_TAG_AND_DATA;

        // BATTERY INSERT
        data += BATTERY_TAG + SPACING_BETWEEN_STRINGS + battery;
        data += SPACING_BETWEEN_TAG_AND_DATA;

        // STORAGE SPACE INSERT
        data += STORAGE_SPACE_TAG + SPACING_BETWEEN_STRINGS + memSpace;
        data += SPACING_BETWEEN_TAG_AND_DATA;

        // STORAGE REMAINING INSERT
        data += STORAGE_REMAINING_TAG + SPACING_BETWEEN_STRINGS + memRemaining;
        data += SPACING_BETWEEN_TAG_AND_DATA;

        // CAMERA INSERT
        data += CAMERA_TAG + SPACING_BETWEEN_STRINGS + cameraAvailable;

        return data;
    }
}
