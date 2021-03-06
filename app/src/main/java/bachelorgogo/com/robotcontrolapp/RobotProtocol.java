package bachelorgogo.com.robotcontrolapp;
import static bachelorgogo.com.robotcontrolapp.RobotProtocol.SEND_COMMANDS.*;
import static bachelorgogo.com.robotcontrolapp.RobotProtocol.DATA_TAGS.*;
/**
 * Created by rasmus on 10/15/2016.
 */

public final class RobotProtocol {
    // This Class Holds the Protocol strings
    // This is to Ensure that Both receiver and sender knows the protocol!

     public final class SEND_COMMANDS{
         // COMMAND
         public static final String CMD_CONTROL = "CMD*CT";
         public static final String CMD_STATUS = "CMD*ST";
         public static final String CMD_SETTINGS = "CMD*SE";
         public static final String CMD_ACK = "CMD*OK";
         public static final String CMD_NACK = "CMD*NO";


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
        public static final String ASSISTED_DRIVE_MODE_TAG = "Assisted";
        public static final String CAMERA_VIDEO_QUALITY_TAG = "VideoQuality";
        public static final String POWER_SAVE_DRIVE_MODE_TAG = "PowerMode";
        public static final String STEERING_X_COORDINATE_TAG = "X";
        public static final String STEERING_Y_COORDINATE_TAG = "Y";
        public static final String STEERING_POWER_TAG = "Pwr";
        public static final String SEERING_ANGLE_TAG = "Agl";

        private DATA_TAGS(){}
    }


    private RobotProtocol(){}
}
