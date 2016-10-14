package bachelorgogo.com.robotcontrolapp;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

/**
 * Created by MadsNKjaersgaard on 14-10-2016.
 */

public class DeviceInfoDialogFragment extends AppCompatDialogFragment {

    private String mDeviceMac = "unknown";
    private boolean mDeviceCameraAvailable = false;
    private String mDeviceStorageSpace = "unknown";
    private String mDeviceStorageRemaining = "unknown";


    public void setmDeviceMac(String deviceName) {
        this.mDeviceMac = deviceName;
    }

    public void setmDeviceStorageRemaining(String deviceAddress) {
        this.mDeviceStorageRemaining = deviceAddress;
    }

    public void setmDeviceStorageSpace(String deviceStorageSpace) {
        this.mDeviceStorageSpace = deviceStorageSpace;
    }

    public void setmDeviceCameraAvailable(boolean haveCam) {
        this.mDeviceCameraAvailable = haveCam;
    }

    // Probrably dont need interface


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        if(arguments != null) {
            setmDeviceMac(arguments.getString(ControlActivity.DEVICE_MAC_STRING));
            setmDeviceStorageRemaining(arguments.getString(ControlActivity.DEVICE_STORAGE_REMAINING_STRING));
            setmDeviceStorageSpace(arguments.getString(ControlActivity.DEVICE_STORAGE_SPACE_STRING));
            setmDeviceCameraAvailable(arguments.getBoolean(ControlActivity.DEVICE_CAMERA_BOOL));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        View content = inflater.inflate(R.layout.device_info_dialog, null);
        builder.setView(content);
        TextView tvDeviceMac = (TextView)content.findViewById(R.id.tvDeviceMacHere);
        TextView tvDeviceStorageRemaining = (TextView)content.findViewById(R.id.tvDeviceStorageRemainingHere);
        TextView tvDeviceStorageSpace = (TextView)content.findViewById(R.id.tvDeviceStorageSpaceHere);
        TextView tvCamera = (TextView)content.findViewById(R.id.tvDeviceCameraAvailableHere);

        tvDeviceMac.setText(mDeviceMac);
        tvDeviceStorageRemaining.setText(mDeviceStorageRemaining);
        tvDeviceStorageSpace.setText(mDeviceStorageSpace);
        if(mDeviceCameraAvailable) {
            tvCamera.setText(getString(R.string.text_yes));
        } else {
            tvCamera.setText(getString(R.string.text_no));
        }
        // Add title
        builder.setTitle(R.string.text_info_dialog_title);
        // Add action button
        builder.setPositiveButton(R.string.text_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        //return super.onCreateDialog(savedInstanceState);
        return builder.create();
    }
}
