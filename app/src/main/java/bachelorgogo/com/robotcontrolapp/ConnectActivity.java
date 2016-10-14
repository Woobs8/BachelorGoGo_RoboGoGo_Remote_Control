package bachelorgogo.com.robotcontrolapp;

//import android.app.DialogFragment;
import android.support.v7.app.AppCompatDialogFragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;

public class ConnectActivity extends AppCompatActivity implements ConnectDialogFragment.ConnectDialogListener{

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

    WifiP2pManager mWifiManager;
    WifiP2pManager.Channel mChannel;
    WifiP2pManager.PeerListListener mPeerListListener;

    IntentFilter mIntentFilter;

    // UI Elements
    Toolbar myToolbar;
    ListView lstViewDevices;
    private ProgressBar mProgress;

    ArrayList<DeviceObject> mDeviceObjects;
    DeviceObjectAdapter mDeviceObjectAdapter;

    // constans
    final static int mTimeout_ms = 10000;

    //
    private String mSelectedDeviceAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("onCreate:", "called");
        setContentView(R.layout.activity_connect);

        final Button btnControlActivity = (Button)findViewById(R.id.btnControlActivity);
        btnControlActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ConnectActivity.this, ControlActivity.class);
                startActivity(intent);
            }
        });

        //initialize WifiManager
        /*mWifiManager = (WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mWifiManager.initialize(this, getMainLooper(), null);
        mPeerListListener = new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peers) {
                mDeviceObjectAdapter.clear();
                Log.d("Connect Activity", "onPeersAvailable");
                for(Iterator<WifiP2pDevice> i = peers.getDeviceList().iterator(); i.hasNext();) {
                    WifiP2pDevice item = i.next();
                    mDeviceObjectAdapter.add(new DeviceObject(item.deviceName, item.deviceAddress));
                }
            }
        };*/

        //myToolbar = (Toolbar)findViewById(R.id.toolbarConnActivity);
        //setSupportActionBar(myToolbar);

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
        lstViewDevices.setAdapter(mDeviceObjectAdapter);
        lstViewDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DeviceObject item = (DeviceObject)lstViewDevices.getItemAtPosition(position);
                //Toast.makeText(ConnectActivity.this, "Dialog opens here", Toast.LENGTH_SHORT).show();
                mSelectedDeviceAddress = item.getDeviceAddress();
                showConnectDialog(item.getName(), mSelectedDeviceAddress);
            }
        });
    }

    @Override
    protected void onPause() {
        unbindFromService();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
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
        //mIntentFilter.addAction(WiFiDirectService.WIFI_DIRECT_PEERS_CHANGED);
        mIntentFilter.addAction(WiFiDirectService.WIFI_DIRECT_CONNECTION_CHANGED);
        mIntentFilter.addAction(WiFiDirectService.WIFI_DIRECT_SERVICES_CHANGED);

        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, mIntentFilter);
        //Bind to Wifi Service
        Intent wifiServiceIntent = new Intent(ConnectActivity.this, WiFiDirectService.class);
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
                Toast.makeText(ConnectActivity.this, R.string.text_updating, Toast.LENGTH_SHORT).show();
                // TODO - Should this do anything usefull??
        }
        return super.onOptionsItemSelected(item);
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d("onServiceConnected", "called");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            WiFiDirectService.LocalBinder binder = (WiFiDirectService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            //Add peer discovery listener
            mService.addListener(true,false);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            //Remove peer discovery listener
            mService.removeListener(true,false);
        }
    };

    protected void bindToService(Intent service) {
        Log.d("bindToService", "called");
        bindService(service, mConnection, 0/*Context.BIND_AUTO_CREATE*/);
    }

    protected void unbindFromService() {
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
        }
    }
/*
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("Broadcast Receiver:", "Broadcast received from WiFiDirectService");
            if(WiFiDirectService.WIFI_DIRECT_PEERS_CHANGED.equals(action)) {
                Log.d("BroadcastReceiver:", "Peers changed");
                if (mWifiManager != null) {
                    mWifiManager.requestPeers(mChannel, mPeerListListener);
                }
                Toast.makeText(ConnectActivity.this, "Peers Changed", Toast.LENGTH_SHORT).show();
            }
            else if(WiFiDirectService.WIFI_DIRECT_CONNECTION_CHANGED.equals(action)) {
                Log.d("BroadcastReceiver", "WIFI_DIRECTION_CONN_CHANGED");
                if(intent.getBooleanExtra(WiFiDirectService.WIFI_DIRECT_CONNECTION_UPDATED_KEY, false)) {
                    Log.d("BroadcastReceiver", "Connection True");
                    mConnected = true;
                    Intent startControlActivity = new Intent(ConnectActivity.this, ControlActivity.class);
                    startActivity(startControlActivity);
                }
                else {
                    mConnected = false;
                }
                // On Succes:
                // mConnected = true
                // connect to controlActivity

                // On Failure:
                // mConnected = false
            }
        }
    };*/

    // Broadcast handler for received Intents.
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                boolean status = intent.getBooleanExtra(WiFiDirectService.WIFI_DIRECT_CONNECTION_UPDATED_KEY, false);
                switch (intent.getAction()) {
                    case WiFiDirectService.WIFI_DIRECT_CONNECTION_CHANGED:
                        Log.d("Receiver",Boolean.toString(status));
                        if(status) {
                            mConnected = true;
                            Intent startControlActivity = new Intent(ConnectActivity.this, ControlActivity.class);
                            startActivity(startControlActivity);
                        }
                        else {
                            mConnected = false;
                        }
                        break;
                    case WiFiDirectService.WIFI_DIRECT_SERVICES_CHANGED:
                        String deviceName = intent.getStringExtra(WiFiDirectService.WIFI_DIRECT_PEER_NAME_KEY);
                        String deviceAddress = intent.getStringExtra(WiFiDirectService.WIFI_DIRECT_PEER_ADDRESS_KEY);
                        if(deviceName != null && deviceAddress != null) {
                            UpdateDeviceList(deviceName, deviceAddress);
                        }
                }
            }
        }
    };

    private void UpdateDeviceList(String deviceName, String deviceAddress) {
        mDeviceObjectAdapter.add(new DeviceObject(deviceName, deviceAddress));
    }

    protected void showConnectProgressSpinner(int msTimeOut) {
        mProgress = (ProgressBar)findViewById(R.id.progressBar);
        mProgress.setIndeterminate(true);
        mProgress.setVisibility(View.VISIBLE);
        //lstViewDevices.setFocusable(false);
        //lstViewDevices.setClickable(false);
        lstViewDevices.setVisibility(View.INVISIBLE);
        new CountDownTimer(msTimeOut, msTimeOut) {
            @Override
            public void onTick(long millisUntilFinished) {
                //Nothing
            }

            @Override
            public void onFinish() {
                Log.d("CountDownTimer", "onFinish");
                mProgress.setVisibility(View.INVISIBLE);
                lstViewDevices.setVisibility(View.VISIBLE);
                if(!mConnected) {
                    Toast.makeText(ConnectActivity.this, "Connection failed", Toast.LENGTH_SHORT).show();
                    mService.disconnectFromDevice();
                }
                else {
                    // Something maybe
                }
                //lstViewDevices.setFocusable(true);
                //lstViewDevices.setClickable(false);
            }
        }.start();
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
        mService.connectToDevice(mSelectedDeviceAddress);
        showConnectProgressSpinner(mTimeout_ms);
    }

}

