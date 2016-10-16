package bachelorgogo.com.robotcontrolapp;

//import android.app.DialogFragment;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatDialogFragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class ConnectActivity extends AppCompatActivity implements ConnectDialogFragment.ConnectDialogListener{

    private String TAG = "ConnectActivity";


    // Fragment argument keys
    public final static String DEVICE_NAME = "deviceName";
    public final static String DEVICE_ADDRESS = "deviceAddress";

    //Extra Keys
    public final static String DISCOVER_PEERS = "Discover peers";
    public final static String DEVICE_OBJECTS_LIST = "mDeviceObjects";

    // WifiService stuff
    WiFiDirectService mService;
    boolean mBound;
    boolean mConnected = false;
    boolean mIsDiscoveringStarted = false;

    WifiP2pManager mWifiManager;
    WifiP2pManager.Channel mChannel;
    WifiP2pManager.PeerListListener mPeerListListener;

    IntentFilter mIntentFilter;

    // UI Elements
    ListView lstViewDevices;
    private RelativeLayout mProgress;

    ArrayList<DeviceObject> mDeviceObjects;
    DeviceObjectAdapter mDeviceObjectAdapter;

    // Toasts
    Toast mToast;

    // constans
    final static int mTimeout_ms = 10000;

    //
    private String mSelectedDeviceAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        //Start Wifi Service
        Intent wifiServiceIntent = new Intent(ConnectActivity.this, WiFiDirectService.class);
        startService(wifiServiceIntent);

        // Initialize list of devices, adapter and attach adapter to listview
        if(savedInstanceState != null) {
            mDeviceObjects = savedInstanceState.getParcelableArrayList(DEVICE_OBJECTS_LIST);
        }
        else {
            mDeviceObjects = new ArrayList<DeviceObject>();
        }

        mDeviceObjectAdapter = new DeviceObjectAdapter(ConnectActivity.this, mDeviceObjects);
        lstViewDevices = (ListView)findViewById(R.id.lstViewAvailRobots);

        View empty = getLayoutInflater().inflate(R.layout.connect_spinner,null,false);
        addContentView(empty, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.MATCH_PARENT));
        lstViewDevices.setEmptyView(empty);

        lstViewDevices.setAdapter(mDeviceObjectAdapter);
        lstViewDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DeviceObject item = (DeviceObject)lstViewDevices.getItemAtPosition(position);

                mSelectedDeviceAddress = item.getDeviceAddress();
                showConnectDialog(item.getName(), mSelectedDeviceAddress);
            }
        });

        // Progress spinner view
        mProgress = (RelativeLayout)findViewById(R.id.progressBarView);

    }

    @Override
    protected void onPause() {
        unbindFromService();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        if(mToast != null)
            mToast.cancel();
        super.onPause();
    }

    @Override
    protected void onDestroy() {

        Intent wifiServiceIntent = new Intent(ConnectActivity.this, WiFiDirectService.class);
        stopService(wifiServiceIntent);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        Log.d("onResume", "Called");
        mIntentFilter = new IntentFilter();

        mIntentFilter.addAction(WiFiDirectService.WIFI_DIRECT_CONNECTION_CHANGED);
        mIntentFilter.addAction(WiFiDirectService.WIFI_DIRECT_SERVICES_CHANGED);

        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, mIntentFilter);
        //Bind to Wifi Service
        Intent wifiServiceIntent = new Intent(ConnectActivity.this, WiFiDirectService.class);
        mIsDiscoveringStarted = true;
        wifiServiceIntent.putExtra(DISCOVER_PEERS, true);
        bindToService(wifiServiceIntent);
        super.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList(DEVICE_OBJECTS_LIST, mDeviceObjects);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_bar_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_update:
                mToast = Toast.makeText(ConnectActivity.this, R.string.text_refreshing_device, Toast.LENGTH_SHORT);
                mToast.show();
                restartPeerListening();
        }
        return super.onOptionsItemSelected(item);
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d("ConnectActivity", "onServiceConnected called");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            WiFiDirectService.LocalBinder binder = (WiFiDirectService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            //Add peer discovery listener
            mService.addListener(true,false);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d("ConnectActivity", "onServiceDisconnected called");
            mBound = false;
        }
    };

    protected void bindToService(Intent service) {
        Log.d("bindToService", "called");
        bindService(service, mConnection, 0/*Context.BIND_AUTO_CREATE*/);
    }

    protected void unbindFromService() {
        // Unbind from the service
        if (mBound) {
            //Remove peer discovery listener
            mService.removeListener(true,false);
            unbindService(mConnection);
        }
    }

    // Broadcast handler for received Intents.
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                boolean status = intent.getBooleanExtra(WiFiDirectService.WIFI_DIRECT_CONNECTION_UPDATED_KEY, false);
                switch (intent.getAction()) {
                    case WiFiDirectService.WIFI_DIRECT_CONNECTION_CHANGED:
                        Log.d(TAG,"Recieved Broadcast status String is : " + Boolean.toString(status));
                        if(status) {
                            mConnected = true;
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                            Intent startControlActivity = new Intent(ConnectActivity.this, ControlActivity.class);
                            startActivity(startControlActivity);
                        }
                        else {
                            mConnected = false;
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                            if(!mIsDiscoveringStarted) {
                                mToast = Toast.makeText(ConnectActivity.this, R.string.text_Connection_failed, Toast.LENGTH_SHORT);
                                mToast.show();
                                mService.disconnectFromDevice();
                                restartPeerListening();
                            }
                            mIsDiscoveringStarted = false;
                        }
                        break;
                    case WiFiDirectService.WIFI_DIRECT_SERVICES_CHANGED:
                        String deviceName = intent.getStringExtra(WiFiDirectService.WIFI_DIRECT_PEER_NAME_KEY);
                        String deviceAddress = intent.getStringExtra(WiFiDirectService.WIFI_DIRECT_PEER_ADDRESS_KEY);
                        if(deviceName != null && deviceAddress != null) {
                            UpdateDeviceList(deviceName, deviceAddress);
                        }
                        break;
                }
            }
        }
    };

    private void UpdateDeviceList(String deviceName, String deviceAddress) {
        DeviceObject newDeviceObject = new DeviceObject(deviceName, deviceAddress);
        int adapterLength = mDeviceObjectAdapter.getCount();
        Log.d(TAG, "UpdateDeviceList: adapterLength " + adapterLength );
        if(adapterLength > 0) {
            for (int i = 0; i < adapterLength; i++) {
                Log.d(TAG, "UpdateDeviceList: checking Device exists");
                if (!deviceName.equals(mDeviceObjectAdapter.getItem(i).getName())
                        && !deviceAddress.equals(mDeviceObjectAdapter.getItem(i).getDeviceAddress())) {
                    mDeviceObjectAdapter.add(newDeviceObject);
                    Log.d(TAG, "UpdateDeviceList: Device didnt Exist " + mDeviceObjectAdapter.getItem(i).getDeviceAddress() +
                            "and" +  deviceAddress);
                }
            }
        }
        else{
            mDeviceObjectAdapter.add(newDeviceObject);
        }
    }

    protected void showConnectDialog(String name, String address) {
        ConnectDialogFragment dialog = new ConnectDialogFragment();
        Bundle args = new Bundle();
        args.putString(DEVICE_NAME, name);
        args.putString(DEVICE_ADDRESS, address);
        dialog.setArguments(args);
        dialog.show(getSupportFragmentManager(), "dialog");
    }

    // onClick listener implemented for ConnectDialogFragment
    @Override
    public void onDialogPositiveClick(AppCompatDialogFragment dialog) {
        for (int i = 0; i < lstViewDevices.getChildCount();i++){
            mDeviceObjectAdapter.disableAll();
            Log.d(TAG, "onDialogPositiveClick: List item "+ i +" disabled");
        }
        lstViewDevices.setAlpha((float)(0.5));
        mToast = Toast.makeText(ConnectActivity.this, R.string.text_Connecting, Toast.LENGTH_LONG);
        mToast.show();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        mService.connectToDevice(mSelectedDeviceAddress);
    }

    private void restartPeerListening(){
        mDeviceObjectAdapter.clear();
        mService.removeListener(true,false);
        mService.addListener(true,false);
        lstViewDevices.setAlpha((float)(1));
    }
}

