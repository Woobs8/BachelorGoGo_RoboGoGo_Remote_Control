package bachelorgogo.com.robotcontrolapp;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class WiFiDirectService extends Service {
    // WiFi Direct Local Broadcast actions
    static final String TAG = "WiFiDirectService";
    static final String WIFI_DIRECT_CONNECTION_UPDATED_KEY = "WiFi_Direct_update";
    static final String WIFI_DIRECT_STATE_CHANGED = "WiFi_Direct_state_changed";
    static final String WIFI_DIRECT_PEERS_CHANGED = "WiFi_Direct_peers_changed";
    static final String WIFI_DIRECT_CONNECTION_CHANGED = "WiFi_Direct_connection_changed";
    static final String WIFI_DIRECT_DEVICE_CHANGED = "WiFi_Direct_device_changed";

    // Network Local Broadcast actions
    static final String ROBOT_STATUS_RECEIVED_KEY = "robot_status";
    static final String ROBOT_STATUS_RECEIVED = "robot_status_received";

    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;
    BroadcastReceiver mReceiver;
    IntentFilter mIntentFilter;
    WifiP2pManager.PeerListListener mPeerListListener;
    InetAddress mRobotAddress;
    ServerSocket mServerSocket;

    ControlClient mControlClient;
    RobotStatusClient mStatusClient;

    private boolean mWiFiDirectEnabled = false;
    private boolean mConnected = false;
    private boolean mDiscoveringPeers = false;
    private int mDiscoverPeersListeners = 0;
    private int mGroupOwnerPort = 9999;

    // Binder given to clients
    private final IBinder mBinder = (IBinder) new LocalBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        WiFiDirectService getService() {
            // Return this instance of LocalService so clients can call public methods
            return WiFiDirectService.this;
        }
    }

    public WiFiDirectService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        mPeerListListener = new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peers) {
                Log.d(TAG,"peers available");
            }
        };

        //Intent filter with intents receiver checks for
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bound");
        if (intent != null) {
            boolean discoverPeers = intent.getBooleanExtra("key",false);
            if (discoverPeers)
                mDiscoverPeersListeners++;
                if(!mDiscoveringPeers)
                    discoverPeers();
        }
        registerReceiver(mReceiver, mIntentFilter);
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Service unbound");
        unregisterReceiver(mReceiver);
        if (intent != null) {
            boolean discoverPeers = intent.getBooleanExtra("key",false);
            if (!discoverPeers)
                mDiscoverPeersListeners--;
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }

    public void connectToDevice(final String deviceAddress, final int port) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = deviceAddress;
        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                //success logic
                Log.d(TAG,"Successfully connected to " + deviceAddress);

                //Resolve IP addresses
                mManager.requestConnectionInfo(mChannel, new WifiP2pManager.ConnectionInfoListener() {
                    @Override
                    public void onConnectionInfoAvailable(WifiP2pInfo info) {
                        if (info.groupFormed) {
                            Log.d(TAG, "Robot IP resolved to: " + mRobotAddress.toString());
                            if(info.isGroupOwner) {
                                AsyncTask<Void, Void, Void> async_client = new AsyncTask<Void, Void, Void>() {
                                    @Override
                                    protected Void doInBackground(Void... params) {
                                        try {
                                            mServerSocket = new ServerSocket(mGroupOwnerPort);
                                            Socket client = mServerSocket.accept();
                                            mRobotAddress = client.getInetAddress();
                                            if(mRobotAddress != null) {
                                                Log.d(TAG, "Robot IP resolved to: " + mRobotAddress.toString());
                                                mServerSocket.close();
                                                mControlClient = new ControlClient(mRobotAddress,port);
                                                mStatusClient = new RobotStatusClient(port,WiFiDirectService.this);
                                                //Broadcast to inform activities that a connection was established
                                                Intent notifyActivity = new Intent(WIFI_DIRECT_CONNECTION_CHANGED);
                                                notifyActivity.putExtra(WIFI_DIRECT_CONNECTION_UPDATED_KEY, true);
                                                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(notifyActivity);
                                            }
                                        } catch (IOException e) {
                                            Log.e(TAG, "Error listening for client IPs");
                                            e.printStackTrace();
                                        }
                                        return null;
                                    }
                                };
                                async_client.execute();
                            } else {
                                mRobotAddress = info.groupOwnerAddress;
                                if (mRobotAddress != null) {
                                    Log.d(TAG, "Robot IP resolved to: " + mRobotAddress.toString());
                                    mControlClient = new ControlClient(mRobotAddress,port);
                                    mStatusClient = new RobotStatusClient(port,WiFiDirectService.this);
                                    //Broadcast to inform activities that a connection was established
                                    Intent notifyActivity = new Intent(WIFI_DIRECT_CONNECTION_CHANGED);
                                    notifyActivity.putExtra(WIFI_DIRECT_CONNECTION_UPDATED_KEY, true);
                                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(notifyActivity);
                                }
                            }
                        }
                    }
                });
            }

            @Override
            public void onFailure(int reason) {
                //failure logic
                Log.d(TAG,"Connection to " + deviceAddress + " was unsuccessful");
                //Broadcast to inform activities that no connection was established
                Intent notifyActivity = new Intent(WIFI_DIRECT_CONNECTION_CHANGED);
                notifyActivity.putExtra(WIFI_DIRECT_CONNECTION_UPDATED_KEY, false);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(notifyActivity);
            }
        });
    }

    public void disconnectFromDevice() {
        Log.d(TAG,"Disconnecting from device");
        mManager.cancelConnect(mChannel,null);
        mControlClient = null;
        mStatusClient = null;
    }

    public void sendControlCommand(String command) {
        if (mControlClient != null) {
            mControlClient.sendCommand(command);
        }
    }

    private void discoverPeers() {
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG,"Peers successfully discovered");
            }

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG,"Failed to discover peers");
            }
        });
    }

    public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
        static final String TAG = "WiFiDirectBroadcast";

        private WifiP2pManager mManager;
        private WifiP2pManager.Channel mChannel;
        private WiFiDirectService mService;

        public WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel,
                                           WiFiDirectService service) {
            super();
            this.mManager = manager;
            this.mChannel = channel;
            this.mService = service;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            LocalBroadcastManager localBroadcast = LocalBroadcastManager.getInstance(mService.getApplicationContext());
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                // Check to see if Wi-Fi is enabled and notify appropriate activity
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    mWiFiDirectEnabled = true;
                } else {
                    mWiFiDirectEnabled = false;
                }
                Intent notifyActivity = new Intent(WIFI_DIRECT_STATE_CHANGED);
                notifyActivity.putExtra(WIFI_DIRECT_CONNECTION_UPDATED_KEY, mWiFiDirectEnabled);
                localBroadcast.sendBroadcast(notifyActivity);
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                // The peer list has changed.
                Intent notifyActivity = new Intent(WIFI_DIRECT_PEERS_CHANGED);
                localBroadcast.sendBroadcast(notifyActivity);
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                // Respond to new connections or disconnections
                NetworkInfo networkState = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                // Check if we connected or disconnected.
                if (networkState.isConnected()) {
                    mConnected = true;
                }
                else {
                    mConnected = false;
                    mManager.cancelConnect(mChannel, null);

                    //Broadcast to inform activities that the connection has been lost
                    Intent notifyActivity = new Intent(WIFI_DIRECT_CONNECTION_CHANGED);
                    notifyActivity.putExtra(WIFI_DIRECT_CONNECTION_UPDATED_KEY, mConnected);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(notifyActivity);
                }
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                // Respond to this device's wifi state changing
                Intent notifyActivity = new Intent(WIFI_DIRECT_DEVICE_CHANGED);
                localBroadcast.sendBroadcast(notifyActivity);
            }
            else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
                // Peer discovery stopped or started
                int discovery = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1);
                if (discovery == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    Log.d(TAG, "Peer discovery started");
                    mDiscoveringPeers = true;
                }
                //Continuously discover peers if there are listeners
                else if (discovery == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                    Log.d(TAG, "Peer discovery stopped");
                    mDiscoveringPeers = false;
                    if (mDiscoverPeersListeners > 0)
                        discoverPeers();
                }
            }
        }
    }
}

