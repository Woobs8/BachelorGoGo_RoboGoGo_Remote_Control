package bachelorgogo.com.robotcontrolapp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;



/**
 * Created by MadsNKjaersgaard on 06-10-2016.
 */

public class ConnectDialogFragment extends DialogFragment {

    private String mdeviceName = "test";
    private String mdeviceAddress;

    public void setDeviceName(String deviceName) {
        this.mdeviceName = deviceName;
    }

    public void setDeviceAddress(String deviceAddress) {
        this.mdeviceAddress = deviceAddress;
    }

    public interface ConnectDialogListener {
        public void onDialogPositiveClick(DialogFragment dialog);
    }

    // Use this instance of the dialog interface to deliver action events to activity
    ConnectDialogListener mListener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the dialog listener so we can send events to the host activity
            mListener = (ConnectDialogListener) context;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(context.toString()
                    + " must implement NoticeDialogListener");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        if(arguments != null) {
            setDeviceName(arguments.getString(ConnectActivity.DEVICE_NAME));
            setDeviceAddress(arguments.getString(ConnectActivity.DEVICE_ADDRESS));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        View content = inflater.inflate(R.layout.connect_dialog, null);
        builder.setView(content);
        TextView tvDeviceName = (TextView)content.findViewById(R.id.tvDeviceNameHere);
        TextView tvDeviceAddress = (TextView)content.findViewById(R.id.tvIpAddressHere);
        tvDeviceName.setText(mdeviceName);
        tvDeviceAddress.setText(mdeviceAddress);
        // Add title
        builder.setTitle(R.string.text_dialog_title);
        // Add action button
        builder.setPositiveButton(R.string.text_Connect, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mListener.onDialogPositiveClick(ConnectDialogFragment.this);
            }
        });
        //return super.onCreateDialog(savedInstanceState);
        return builder.create();
        //return super.onCreateDialog(savedInstanceState);
    }
}
