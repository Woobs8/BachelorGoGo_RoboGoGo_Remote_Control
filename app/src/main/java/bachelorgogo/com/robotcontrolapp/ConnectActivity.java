package bachelorgogo.com.robotcontrolapp;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatDialogFragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
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
import android.widget.RelativeLayout;
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
    private WiFiDirectService mService;
    boolean mBound;
    boolean mConnected = false;
    private boolean mConnectionAttempted = false;
    private Handler restartPeerListeningHandler = new Handler();
    private Runnable restartPeerListenerRunnable;
    private Runnable connectionTimeoutRunnable;
    private final int DELAY_SERVICE_BIND_MS = 3000;
    private final int RESTART_LISTENING_TIMER_MS = 15000;
    private final int CONNECTION_ATTEMPT_TIMEOUT = 31000;   //31 sec * 1000 msec

    private IntentFilter mIntentFilter;

    // UI Elements
    private ListView lstViewDevices;
    private RelativeLayout mProgress;

    private ArrayList<DeviceObject> mDeviceObjects;
    private DeviceObjectAdapter mDeviceObjectAdapter;

    private SharedPreferences mSharedPrefs;

    // Toasts
    private Toast mToast;

    private String mSelectedDeviceAddress;
    private String mDeviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        // Get shared preferences
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        //Start Wifi Service
        Intent wifiServiceIntent = new Intent(ConnectActivity.this, WiFiDirectService.class);
        startService(wifiServiceIntent);

        // Get saved instance state
        if(savedInstanceState != null) {
            mDeviceObjects = savedInstanceState.getParcelableArrayList(DEVICE_OBJECTS_LIST);
        }
        else {
            mDeviceObjects = new ArrayList<DeviceObject>();
        }

        // Get new adapter and find listview
        mDeviceObjectAdapter = new DeviceObjectAdapter(ConnectActivity.this, mDeviceObjects);
        lstViewDevices = (ListView)findViewById(R.id.lstViewAvailRobots);

        // set a progress spinner when listview is empty
        View empty = getLayoutInflater().inflate(R.layout.connect_spinner,null,false);
        addContentView(empty, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.MATCH_PARENT));
        lstViewDevices.setEmptyView(empty);

        // set adapter for listview
        lstViewDevices.setAdapter(mDeviceObjectAdapter);
        // onItemClickListener for listView - show dialog on item click
        lstViewDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DeviceObject item = (DeviceObject)lstViewDevices.getItemAtPosition(position);

                mSelectedDeviceAddress = item.getDeviceAddress();
                mDeviceName = item.getName();
                //Locking screen orientation when connecting, to prevent activity from being destroyed
                int currentOrientation = getResources().getConfiguration().orientation;
                if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                    Log.d(TAG,"Screen orientation locked to landscape");
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
                }
                else {
                    Log.d(TAG,"Screen orientation locked to portrait");
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
                }
                showConnectDialog(item.getName(), mSelectedDeviceAddress);
            }
        });

        // Progress spinner view
        mProgress = (RelativeLayout)findViewById(R.id.progressBarView);


        // After we have been bound
        // Reset after a few seconds to make sure broadcasting from car started before listening from device
        // Afterwards every 30 seconds we want to Restart Listening in the service
        restartPeerListenerRunnable = new Runnable()
        {
            public void run()
            {
                if(!mConnectionAttempted) {
                    Log.d(TAG, "restartPeerListenerRunnable run: Restart Listening without list clear ");
                    restartPeerListening(false);
                    restartPeerListeningHandler.postDelayed(restartPeerListenerRunnable, RESTART_LISTENING_TIMER_MS);
                }
            }
        };

        connectionTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Connection attempt timed out");
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                Toast.makeText(mService, R.string.text_Connection_failed, Toast.LENGTH_SHORT).show();
                restartPeerListening(true);
            }
        };
    }

    @Override
    protected void onPause() {
        unbindFromService();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        if(mToast != null)
            mToast.cancel();
        if (restartPeerListenerRunnable != null)
            restartPeerListeningHandler.removeCallbacks(restartPeerListenerRunnable);
        if (connectionTimeoutRunnable != null)
            restartPeerListeningHandler.removeCallbacks(connectionTimeoutRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // make sure service is stopped when app is shut down
        Intent wifiServiceIntent = new Intent(ConnectActivity.this, WiFiDirectService.class);
        stopService(wifiServiceIntent);
        if (restartPeerListenerRunnable != null)
            restartPeerListeningHandler.removeCallbacks(restartPeerListenerRunnable);
        if (connectionTimeoutRunnable != null)
            restartPeerListeningHandler.removeCallbacks(connectionTimeoutRunnable);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume Called");
        // Intent filter with desired actions
        mIntentFilter = new IntentFilter();
        mConnectionAttempted = false;
        mIntentFilter.addAction(WiFiDirectService.WIFI_DIRECT_CONNECTION_CHANGED);
        mIntentFilter.addAction(WiFiDirectService.WIFI_DIRECT_SERVICES_CHANGED);
        // register local broadcastReceiver
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, mIntentFilter);
        //Bind to Wifi Service
        final Intent wifiServiceIntent = new Intent(ConnectActivity.this, WiFiDirectService.class);
        wifiServiceIntent.putExtra(DISCOVER_PEERS, true);
        // delay Hack to allow robot to register on network before service start looking for it
        bindToService(wifiServiceIntent);
        restartPeerListeningHandler.postDelayed(restartPeerListenerRunnable,DELAY_SERVICE_BIND_MS);


        // Change the runnable to Refresh Listening every 30 seconds


        super.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // save robot objects
        outState.putParcelableArrayList(DEVICE_OBJECTS_LIST, mDeviceObjects);
        super.onSaveInstanceState(outState);
    }

    // Actionbar menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_bar_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    // callback on selected actionbar menu items
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_update:
                mToast = Toast.makeText(ConnectActivity.this, R.string.text_refreshing_device, Toast.LENGTH_SHORT);
                mToast.show();
                restartPeerListening(true);
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
            //Add peer discovery listener in WiFiDirectService
            mService.addListener(true,false);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d("ConnectActivity", "onServiceDisconnected called");
            mBound = false;
        }
    };

    protected void bindToService(Intent service) {
        Log.d(TAG, "bindToService called");
        bindService(service, mConnection, 0/*Context.BIND_AUTO_CREATE*/);
    }

    protected void unbindFromService() {
        // Unbind from the service
        if (mBound) {
            //Remove peer discovery listener in WiFiDirectServiec
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
                        // React on connection succes or fault
                        Log.d(TAG,"Connection status changed to: " + Boolean.toString(status));
                        if(status) {
                            mConnected = true;
                            //Unlock screen orientation
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                            mDeviceObjectAdapter.clear();
                            SharedPreferences.Editor editor = mSharedPrefs.edit();
                            editor.putString(getString(R.string.settings_device_name_key), mDeviceName);
                            editor.putString(getString(R.string.settings_device_MAC_address_key), mSelectedDeviceAddress);
                            editor.commit();
                            // Start control activity when connected to robot
                            Intent startControlActivity = new Intent(ConnectActivity.this, ControlActivity.class);
                            startActivity(startControlActivity);
                        }
                        else {
                            mConnected = false;
                            //Unlock screen orientation
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                            if(mConnectionAttempted) {
                                mToast = Toast.makeText(ConnectActivity.this, R.string.text_Connection_failed, Toast.LENGTH_SHORT);
                                mToast.show();
                                mService.disconnectFromDevice();
                                mConnectionAttempted = false;
                                restartPeerListening(true);
                            }
                        }
                        break;
                    case WiFiDirectService.WIFI_DIRECT_SERVICES_CHANGED:
                        // update listview with available robot peers
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

    // Updates listview with new robot peer
    private void UpdateDeviceList(String deviceName, String deviceAddress) {
        DeviceObject newDeviceObject = new DeviceObject(deviceName, deviceAddress);
        int adapterLength = mDeviceObjectAdapter.getCount();
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

    // Creates new instant of ConnectDialogFragment and shows it
    protected void showConnectDialog(String name, String address) {
        ConnectDialogFragment dialog = new ConnectDialogFragment();
        Bundle args = new Bundle();
        args.putString(DEVICE_NAME, name);
        args.putString(DEVICE_ADDRESS, address);
        dialog.setArguments(args);
        dialog.show(getSupportFragmentManager(), "dialog");
    }

    // onClick listener implemented for ConnectDialogFragment
    // disable UI-inputs and attempt connection to robot by calling connect on WiFiDirectService
    @Override
    public void onDialogPositiveClick(AppCompatDialogFragment dialog) {
        if(lstViewDevices.getChildCount() > 0){
            mDeviceObjectAdapter.disableAll();
            Log.d(TAG, "onDialogPositiveClick: List item disabled");
        }
        lstViewDevices.setAlpha((float)(0.5));
        mToast = Toast.makeText(ConnectActivity.this, R.string.text_Connecting, Toast.LENGTH_LONG);
        mToast.show();
        mConnectionAttempted = true;
        mService.connectToDevice(mSelectedDeviceAddress);
        restartPeerListeningHandler.postDelayed(connectionTimeoutRunnable, CONNECTION_ATTEMPT_TIMEOUT);
    }

    // callBack when ConnectDialog is cancelled
    @Override
    public void onDialogCancelled() {
        Log.d(TAG,"Screen orientation unlocked");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    // Clears available robot peers and restarts peer search in WIFIDirectService
    private void restartPeerListening(boolean clearAdapter){
        if(mService != null) {
            if(clearAdapter)
                mDeviceObjectAdapter.clear();
            mService.removeListener(true, false);
            mService.addListener(true, false);
            lstViewDevices.setAlpha((float)(1));
        }
    }
}

