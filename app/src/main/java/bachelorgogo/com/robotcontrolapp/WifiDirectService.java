package bachelorgogo.com.robotcontrolapp;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class WifiDirectService extends Service {
    public static final String WIFI_DIRECT_UPDATE_KEY = "WiFi_Direct_update";
    public static final String WIFI_DIRECT_STATE_CHANGED = "WiFi_Direct_state_changed";
    public static final String WIFI_DIRECT_PEERS_CHANGED = "WiFi_Direct_peers_changed";
    public static final String WIFI_DIRECT_CONNECTION_CHANGED = "WiFi_Direct_connection_changed";
    public static final String WIFI_DIRECT_DEVICE_CHANGED = "WiFi_Direct_device_changed";

    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;
    BroadcastReceiver mReceiver;
    IntentFilter mIntentFilter;
    WifiP2pManager.PeerListListener mPeerListListener;

    boolean mWiFiDirectEnabled = false;
    boolean mConnected = false;
    boolean mDiscoveringPeers = false;
    int mDiscoverPeersListeners = 0;

    // Binder given to clients
    private final IBinder mBinder = (IBinder) new LocalBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        WifiDirectService getService() {
            // Return this instance of LocalService so clients can call public methods
            return WifiDirectService.this;
        }
    }

    public WifiDirectService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("Service", "onCreate called");
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        /*mPeerListListener = new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peers) {
                Log.d("Peer listener:","peers available");
            }
        };*/

        //Intent filter with intents receiver checks for
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("WiFiDirectService", "Service started");

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d("WiFiDirectService", "Service bound");
        if(intent != null) {
            boolean discoverPeers = intent.getBooleanExtra(ConnectActivity.DISCOVER_PEERS, false);
            if(discoverPeers) {
                mDiscoverPeersListeners++;
                if(!mDiscoveringPeers) {
                    discoverPeers();
                }
            }
        }
        registerReceiver(mReceiver, mIntentFilter);
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d("WiFiDirectService", "Service unbound");
        unregisterReceiver(mReceiver);

        if(intent != null) {
            boolean discoverPeers = intent.getBooleanExtra(ConnectActivity.DISCOVER_PEERS, false);
            if(discoverPeers) {
                mDiscoverPeersListeners--;
            }
        }
        //stopSelf();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("WiFiDirectService", "Service destroyed");
    }

    private void discoverPeers() {
        Log.d("Service", "discoverPeers called");
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d("WifiDirectService:", "Peers successfully discovered");
            }
            @Override
            public void onFailure(int reasonCode) {
                Log.d("WifiDirectService:", "Failed to discover peers");
            }
        });
    }

    public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
        private WifiP2pManager mManager;
        private WifiP2pManager.Channel mChannel;
        private WifiDirectService mService;

        public WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel,
                                           WifiDirectService service) {
            super();
            this.mManager = manager;
            this.mChannel = channel;
            this.mService = service;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                // Check to see if Wi-Fi is enabled and notify appropriate activity
                Intent notifyActivity = new Intent(WIFI_DIRECT_STATE_CHANGED);
                notifyActivity.putExtra(WIFI_DIRECT_UPDATE_KEY, "state changed");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(notifyActivity);
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                // Call WifiP2pManager.requestPeers() to get a list of current peers
                Intent notifyActivity = new Intent(WIFI_DIRECT_PEERS_CHANGED);
                notifyActivity.putExtra(WIFI_DIRECT_UPDATE_KEY, "peers changed");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(notifyActivity);

                // request available peers from the wifi p2p manager. This is an
                // asynchronous call and the calling activity is notified with a
                // callback on PeerListListener.onPeersAvailable()
                /*if (mManager != null) {
                    mManager.requestPeers(mChannel, mPeerListListener);
                }*/
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                // Respond to new connection or disconnections
                Intent notifyActivity = new Intent(WIFI_DIRECT_CONNECTION_CHANGED);
                notifyActivity.putExtra(WIFI_DIRECT_UPDATE_KEY, "connection changed");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(notifyActivity);
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                // Respond to this device's wifi state changing
                Intent notifyActivity = new Intent(WIFI_DIRECT_DEVICE_CHANGED);
                notifyActivity.putExtra(WIFI_DIRECT_UPDATE_KEY, "device changed");
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(notifyActivity);
            }
        }
    }
}
