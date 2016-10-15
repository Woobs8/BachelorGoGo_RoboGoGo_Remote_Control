package bachelorgogo.com.robotcontrolapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.ArrayList;

/**
 * Created by THP on 04-10-2016.
 */

public class DeviceObjectAdapter extends ArrayAdapter<DeviceObject> {
    TextView txtViewName;

    public DeviceObjectAdapter(Context context, ArrayList<DeviceObject> DeviceObjects) {
        super(context, 0, DeviceObjects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        DeviceObject deviceObject = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_device, parent, false);
        }
        // Lookup view for data population
        txtViewName = (TextView) convertView.findViewById(R.id.name_txt);

        // Populate the data into the template view using the data object
        txtViewName.setText(deviceObject.getName());

        // Return the view to render in listview
        return convertView;
    }
}
