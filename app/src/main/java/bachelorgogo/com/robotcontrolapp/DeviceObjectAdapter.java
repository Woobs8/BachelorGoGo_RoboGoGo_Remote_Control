package bachelorgogo.com.robotcontrolapp;

import android.content.Context;
import android.util.SparseBooleanArray;
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

// Adapter to let DeviceObjects in listview
    // Adapted from android docs: https://developer.android.com/guide/topics/ui/layout/listview.html
    // and: https://developer.android.com/reference/android/widget/Adapter.html
public class DeviceObjectAdapter extends ArrayAdapter<DeviceObject> {
    TextView txtViewName;
    private SparseBooleanArray enabledItems = new SparseBooleanArray();

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

        //Check if enabled
        if(!isEnabled(position)) {
            enabledItems.put(position,true);
        }

        // Return the view to render in listview
        return convertView;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return enabledItems.get(position, true);
    }

    public void disableAll() {
        for(int i = 0; i < getCount() ; i++)
            enabledItems.put(i,false);
    }


}
