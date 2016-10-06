package bachelorgogo.com.robotcontrolapp;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
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
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;

public class ConnectActivity extends AppCompatActivity {

    // Fragment argument keys
    public final static String DEVICE_NAME = "deviceName";
    public final static String DEVICE_ADDRESS = "deviceAddress";

    //Extra Keys
    public final static String DISCOVER_PEERS = "Discover peers";

    // WifiService stuff
    WifiDirectService mService;
    boolean mBound;

    WifiP2pManager mWifiManager;
    WifiP2pManager.Channel mChannel;
    WifiP2pManager.PeerListListener mPeerListListener;

    IntentFilter mIntentFilter;

    // UI Elements
    Toolbar myToolbar;
    ListView lstViewDevices;

    ArrayList<DeviceObject> mDeviceObjects;
    DeviceObjectAdapter mDeviceObjectAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("onCreate:", "called");
        setContentView(R.layout.activity_connect);

        //initialize WifiManager
        mWifiManager = (WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mWifiManager.initialize(this, getMainLooper(), null);
        mPeerListListener = new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peers) {
                // TODO - DO SHIT WITH PEERS
                Log.d("Connect Activity", "onPeersAvailable");
                for(Iterator<WifiP2pDevice> i = peers.getDeviceList().iterator(); i.hasNext();) {
                    WifiP2pDevice item = i.next();
                    mDeviceObjectAdapter.add(new DeviceObject(item.deviceName, item.deviceAddress));
                    // TODO - also add address
                }
            }
        };

        myToolbar = (Toolbar)findViewById(R.id.toolbarConnActivity);
        setSupportActionBar(myToolbar);

        //Start Wifi Service
        Intent wifiServiceIntent = new Intent(ConnectActivity.this, WifiDirectService.class);
        startService(wifiServiceIntent);

        // Initialize list of devices, adapter and attach adapter to listview
        mDeviceObjects = new ArrayList<DeviceObject>();
        mDeviceObjectAdapter = new DeviceObjectAdapter(ConnectActivity.this, mDeviceObjects);
        lstViewDevices = (ListView)findViewById(R.id.lstViewAvailRobots);
        lstViewDevices.setAdapter(mDeviceObjectAdapter);
        lstViewDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DeviceObject item = (DeviceObject)lstViewDevices.getItemAtPosition(position);
                Toast.makeText(ConnectActivity.this, "Dialog opens here", Toast.LENGTH_SHORT).show();
                // TODO Open dialog and do shit
                // TODO - Set ip address textview
                ConnectDialogFragment dialog = new ConnectDialogFragment();
                Bundle args = new Bundle();
                args.putString(DEVICE_NAME, item.getName());
                args.putString(DEVICE_ADDRESS, item.getDeviceAddress());
                dialog.setArguments(args);
                dialog.show(getFragmentManager(), "dialog");
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
    protected void onResume() {
        Log.d("onResume", "Called");
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiDirectService.WIFI_DIRECT_PEERS_CHANGED);

        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, mIntentFilter);
        //Bind to Wifi Service
        Intent wifiServiceIntent = new Intent(ConnectActivity.this, WifiDirectService.class);
        wifiServiceIntent.putExtra(DISCOVER_PEERS, true);
        bindToService(wifiServiceIntent);
        super.onResume();
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
                // Moved to wifi service
                /*mWifiManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d("Discover peers:", "succes");
                    }
                    @Override
                    public void onFailure(int reasonCode) {
                        Log.d("Discover peers", "failure");
                    }
                });*/
        }
        return super.onOptionsItemSelected(item);
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d("onServiceConnected", "called");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            WifiDirectService.LocalBinder binder = (WifiDirectService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
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
            unbindService(mConnection);
        }
    }

    // TODO Broadcast reciever
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("Broadcast Receiver:", "Broadcast received from WifiDirectService");
            if(WifiDirectService.WIFI_DIRECT_PEERS_CHANGED.equals(action)) {
                Log.d("BroadcastReceiver:", "Peers changed");
                // TODO - Handle changed peers
                if (mWifiManager != null) {
                    mWifiManager.requestPeers(mChannel, mPeerListListener);
                }
                Toast.makeText(ConnectActivity.this, "Peers Changed", Toast.LENGTH_SHORT).show();
            }
        }
    };
}

