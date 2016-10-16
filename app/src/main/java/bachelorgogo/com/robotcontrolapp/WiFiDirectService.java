package bachelorgogo.com.robotcontrolapp;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

public class WiFiDirectService extends Service {
    static final String TAG = "WiFiDirectService";
    // WiFi Direct Local Broadcast actions
    static final String WIFI_DIRECT_CONNECTION_UPDATED_KEY = "WiFi_Direct_update";
    static final String WIFI_DIRECT_PEER_NAME_KEY = "WiFi_Direct_peer_name_key";
    static final String WIFI_DIRECT_PEER_ADDRESS_KEY = "WiFi_Direct_peer_address_key";
    static final String WIFI_DIRECT_STATE_CHANGED = "WiFi_Direct_state_changed";
    static final String WIFI_DIRECT_PEERS_CHANGED = "WiFi_Direct_peers_changed";
    static final String WIFI_DIRECT_SERVICES_CHANGED = "WiFi_Direct_services_changed";
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
    Socket mSocket;
    WifiP2pDnsSdServiceRequest mServiceRequest;
    private HashMap<String, String> mDevices = new HashMap<>();
    Handler mServiceDiscoveryHandler;
    Runnable mServiceDiscoveryRunnable;


    ControlClient mCommandClient;
    RobotStatusClient mStatusClient;
    SettingsClient mSettingsClient;

    private boolean mWiFiDirectEnabled = false;
    private boolean mConnected = false;
    private boolean mCurrentlyDiscoveringPeers = false;
    private boolean mShouldDiscoverPeers = true;
    private int mDiscoverPeersListeners = 0;
    private boolean mCurrentlyBroadcastingStatus = false;
    private int mBroadcastStatusListeners = 0;
    private int mGroupOwnerPort = 9999;
    private int mHostUDPPort = -1;
    private int mHostTCPPort = -1;
    private int mLocalUDPPort = 4999;
    private int mEstablishConnectionTimeout = 5000; //5 sec * 1000 msec
    private int mServiceDiscoveryInterval = 120000; //2 min * 60 sec * 1000 msec
    private final String mSystemName = "GiantsFTW";

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
                Log.d("Peer listener:","peers available");
            }
        };

        //Network service related
        setUpServiceDiscovery();
        mServiceDiscoveryHandler = new Handler();
        mServiceDiscoveryRunnable = new Runnable() {
            @Override
            public void run() {
                try{
                    if(!mConnected)
                        discoverService();
                    mServiceDiscoveryHandler.postDelayed(this, mServiceDiscoveryInterval);
                }
                catch (Exception e) {
                    Log.d(TAG,"Error running continuous service discovery");
                    e.printStackTrace();
                }
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
        /*
        if (intent != null) {
            boolean discoverPeers = intent.getBooleanExtra(ConnectActivity.DISCOVER_PEERS, false);
            if (discoverPeers) {
                mDiscoverPeersListeners++;
                if (!mCurrentlyDiscoveringPeers) {
                    discoverPeers();
                    mServiceDiscoveryHandler.postDelayed(mServiceDiscoveryRunnable, 0);
                }
            }
        }
        */
        registerReceiver(mReceiver, mIntentFilter);
        return mBinder;
    }

    public void addListener(boolean broadcastPeers, boolean broadcastStatus) {
        if(broadcastPeers) {
            Log.d(TAG,"Adding peer discovery listener");
            mDiscoverPeersListeners++;
            if (!mCurrentlyDiscoveringPeers) {
                discoverPeers();
                mServiceDiscoveryHandler.postDelayed(mServiceDiscoveryRunnable, 0);
            }
        }

        if(broadcastStatus) {
            Log.d(TAG,"Adding status listener");
            mBroadcastStatusListeners++;
            if (!mCurrentlyBroadcastingStatus) {
                mStatusClient.start();
                mCurrentlyBroadcastingStatus = true;
            }
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Service unbound");
        try {
            unregisterReceiver(mReceiver);
        } catch(IllegalArgumentException e) {
            Log.d(TAG,"Receiver already unregistered. Do nothing.");
        }
        /*
        if (intent != null) {
            boolean discoverPeers = intent.getBooleanExtra(ConnectActivity.DISCOVER_PEERS, false);
            if (discoverPeers)
                mDiscoverPeersListeners--;
            //If no more listeners - stop discovering services
            if(mDiscoverPeersListeners <= 0) {
                stopDiscoveringPeers();
                mServiceDiscoveryHandler.removeCallbacks(mServiceDiscoveryRunnable);
                stopServiceDiscovery();
                if(mStatusClient != null)
                    mStatusClient.stop();
            }
        }
        */
        // return true so onRebind is called
        return true;
    }

    public void removeListener(boolean broadcastPeers, boolean broadcastStatus) {
        if(broadcastPeers) {
            mDiscoverPeersListeners--;
            if(mDiscoverPeersListeners <= 0) {
                mDiscoverPeersListeners = 0;
                stopDiscoveringPeers();
                mServiceDiscoveryHandler.removeCallbacks(mServiceDiscoveryRunnable);
                stopServiceDiscovery();
            }
        }

        if(broadcastStatus) {
            mBroadcastStatusListeners--;
            if(mBroadcastStatusListeners <= 0) {
                mBroadcastStatusListeners = 0;
                mStatusClient.stop();
                mCurrentlyBroadcastingStatus = false;
            }
        }
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "Service rebound");
        registerReceiver(mReceiver, mIntentFilter);
        super.onRebind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mManager.removeGroup(mChannel,null);
        Log.d(TAG, "Service destroyed");
        stopServiceDiscovery();
        stopDiscoveringPeers();
        try {
            unregisterReceiver(mReceiver);
        } catch(IllegalArgumentException e) {
            Log.d(TAG,"Receiver already unregistered. Do nothing.");
        }
        if(mStatusClient != null) {
            mStatusClient.stop();
            mCurrentlyBroadcastingStatus = false;
        }
    }

    public void connectToDevice(final String deviceAddress) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = deviceAddress;
        config.groupOwnerIntent = 0;
        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                //success logic
                Log.d(TAG,"Successful attempt to connect to " + deviceAddress);
            }

            @Override
            public void onFailure(int reason) {
                //failure logic
                Log.d(TAG,"Error attempting to connect to " + deviceAddress);
            }
        });
    }

    public void disconnectFromDevice() {
        mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG,"removeGroup successfully called");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG,"Error calling removeGroup");
            }
        });
        mCommandClient = null;
    }

    public void sendCommandObject(CommandObject command) {
        if (mCommandClient != null) {
            mCommandClient.sendCommand(command);
        }
    }

    public void sendSettingsObject(SettingsObject settings) {
        if(mSettingsClient != null) {
            mSettingsClient.sendSettings(settings);
        }
    }

    private void discoverPeers() {
        mShouldDiscoverPeers = true;
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

    private void stopDiscoveringPeers() {
        mShouldDiscoverPeers = false;
        if(mManager != null) {
            mManager.stopPeerDiscovery(mChannel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Manually stopped discovering peers");
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "Failed to manually stop discovering peers");
                }
            });
        }
    }

    private void setUpServiceDiscovery() {
        WifiP2pManager.DnsSdTxtRecordListener txtListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
        /* Callback includes:
         * fullDomain: full domain name: e.g "printer._ipp._tcp.local."
         * record: TXT record dta as a map of key/value pairs.
         * device: The device running the advertised service.
         */
            public void onDnsSdTxtRecordAvailable(String fullDomain, Map record, WifiP2pDevice device) {
                Log.d(TAG, "DnsSdTxtRecord available -" + record.toString());

                if(mDevices.containsKey(device.deviceAddress))
                    mDevices.remove(device.deviceAddress);

                mDevices.put(device.deviceAddress, record.get("device_name").toString());

            }
        };

        WifiP2pManager.DnsSdServiceResponseListener servListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String registrationType,
                                                WifiP2pDevice resourceType) {

                // Update the device name with the human-friendly version from
                // the DnsTxtRecord, assuming one arrived.
                resourceType.deviceName = mDevices
                        .containsKey(resourceType.deviceAddress) ? mDevices
                        .get(resourceType.deviceAddress) : resourceType.deviceName;

                Log.d(TAG, "onServiceAvailable " + instanceName);

                if(instanceName.equals("_"+mSystemName)) {
                    Intent notifyActivity = new Intent(WIFI_DIRECT_SERVICES_CHANGED);
                    notifyActivity.putExtra(WIFI_DIRECT_PEER_NAME_KEY, resourceType.deviceName);
                    notifyActivity.putExtra(WIFI_DIRECT_PEER_ADDRESS_KEY, resourceType.deviceAddress);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(notifyActivity);
                }
            }
        };
        mManager.setDnsSdResponseListeners(mChannel, servListener, txtListener);
    }

    private void discoverService() {
        mServiceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        mManager.removeServiceRequest(mChannel, mServiceRequest, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                mManager.addServiceRequest(mChannel, mServiceRequest,
                        new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG,"Successfully added service request");
                                mManager.discoverServices(mChannel, new WifiP2pManager.ActionListener() {

                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG,"Successfully discovered services");
                                    }

                                    @Override
                                    public void onFailure(int code) {
                                        Log.d(TAG,"Failed to discover services");
                                    }
                                });
                            }

                            @Override
                            public void onFailure(int code) {
                                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                                Log.d(TAG,"Failed to add service request");
                            }
                        });
            }

            @Override
            public void onFailure(int reason) {

            }
        });
    }

    private void stopServiceDiscovery() {
        //Stop continuously discovering services
        if(mServiceDiscoveryHandler != null && mServiceDiscoveryRunnable != null)
            mServiceDiscoveryHandler.removeCallbacks(mServiceDiscoveryRunnable);

        //Stop pending service request
        if(mManager != null && mServiceRequest != null)
            mManager.removeServiceRequest(mChannel, mServiceRequest, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG,"Service request successfully removed");
                    mServiceRequest = null;
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG,"Error removing service request");
                }
            });
    }

    public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
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
            final LocalBroadcastManager localBroadcast = LocalBroadcastManager.getInstance(mService.getApplicationContext());
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
                    if(!mConnected) {
                        mConnected = true;
                        //Resolve IP addresses
                        mManager.requestConnectionInfo(mChannel, new WifiP2pManager.ConnectionInfoListener() {
                            @Override
                            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                                if (info.groupFormed) {
                                    if (info.isGroupOwner) {
                                        //Listen for clients and exchange ports
                                        AsyncTask<Void, Void, Void> async_client = new AsyncTask<Void, Void, Void>() {
                                            @Override
                                            protected Void doInBackground(Void... params) {
                                                try {
                                                    mServerSocket = new ServerSocket(mGroupOwnerPort);
                                                    mServerSocket.setSoTimeout(mEstablishConnectionTimeout);
                                                    Log.d(TAG, "Listening for clients on port " + Integer.toString(mGroupOwnerPort));
                                                    mSocket = mServerSocket.accept();
                                                    mRobotAddress = mSocket.getInetAddress();
                                                    publishProgress();
                                                    DataOutputStream out = new DataOutputStream(mSocket.getOutputStream());
                                                    DataInputStream in = new DataInputStream(mSocket.getInputStream());

                                                    //read client UDP port
                                                    String dataStr = in.readUTF();
                                                    int hostUDPPort = Integer.valueOf(dataStr);
                                                    if (hostUDPPort > 0 && hostUDPPort < 9999)
                                                        mHostUDPPort = hostUDPPort;
                                                    else
                                                        mConnected = false;
                                                    Log.d(TAG, "UDP client resolved to: " + mRobotAddress + " (port " + mHostUDPPort + ")");

                                                    //read client TCP port
                                                    dataStr = in.readUTF();
                                                    int hostTCPPort = Integer.valueOf(dataStr);
                                                    if (hostTCPPort > 0 && hostTCPPort < 9999)
                                                        mHostTCPPort = hostTCPPort;
                                                    else
                                                        mConnected = false;
                                                    Log.d(TAG, "TCP client resolved to: " + mRobotAddress + " (port " + mHostTCPPort + ")");

                                                    //Send UDP port to client
                                                    out.writeUTF(Integer.toString(mLocalUDPPort));

                                                } catch (SocketTimeoutException st) {
                                                    Log.d(TAG,"Attempt to establish connection timed out");
                                                    mConnected = false;
                                                } catch (IOException e) {
                                                    Log.e(TAG, "Error listening for client IPs");
                                                    e.printStackTrace();
                                                    mConnected = false;
                                                }
                                                finally {
                                                    try {
                                                        Log.d(TAG,"Closing socket " + mGroupOwnerPort);
                                                        mSocket.close();
                                                        mServerSocket.close();
                                                    } catch (IOException e) {
                                                        Log.d(TAG,"Error closing sockets");
                                                        e.printStackTrace();
                                                    }
                                                    if(!mConnected) {
                                                        mManager.removeGroup(mChannel,null);
                                                    }

                                                }
                                                return null;
                                            }

                                            @Override
                                            protected void onPostExecute(Void aVoid) {
                                                if(mConnected) {
                                                    mCommandClient = new ControlClient(mRobotAddress, mHostUDPPort);
                                                    mSettingsClient = new SettingsClient(mRobotAddress,mHostTCPPort);
                                                    mStatusClient = new RobotStatusClient(mLocalUDPPort, WiFiDirectService.this);
                                                    stopDiscoveringPeers();
                                                    stopServiceDiscovery();
                                                }
                                            }
                                        };
                                        // http://stackoverflow.com/questions/9119627/android-sdk-asynctask-doinbackground-not-running-subclass
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                                            async_client.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
                                        else
                                            async_client.execute((Void[])null);
                                    } else {
                                        mRobotAddress = info.groupOwnerAddress;
                                        if (mRobotAddress != null) {
                                            //transmit ip to group owner and exchange ports
                                            AsyncTask<Void, Void, Void> async_transmit_ip = new AsyncTask<Void, Void, Void>() {
                                                @Override
                                                protected Void doInBackground(Void... params) {
                                                    try {
                                                        Log.d(TAG,"Establishing connection with group owner");
                                                        mSocket = new Socket();
                                                        mSocket.setSoTimeout(mEstablishConnectionTimeout);
                                                        mSocket.bind(null);
                                                        mSocket.connect((new InetSocketAddress(mRobotAddress, mGroupOwnerPort)));
                                                        DataInputStream in = new DataInputStream(mSocket.getInputStream());
                                                        DataOutputStream out = new DataOutputStream(mSocket.getOutputStream());

                                                        //Send port to owner
                                                        out.writeUTF(Integer.toString(mLocalUDPPort));

                                                        //read owner UDP port
                                                        String dataStr = in.readUTF();
                                                        int hostUDPPort = Integer.valueOf(dataStr);
                                                        if (hostUDPPort > 0 && hostUDPPort < 9999)
                                                            mHostUDPPort = hostUDPPort;
                                                        Log.d(TAG, "UDP host resolved to: " + mRobotAddress + " (port " + mHostUDPPort + ")");

                                                        //read owner TCP port
                                                        dataStr = in.readUTF();
                                                        int hostTCPPort = Integer.valueOf(dataStr);
                                                        if (hostTCPPort > 0 && hostTCPPort < 9999)
                                                            mHostTCPPort = hostTCPPort;
                                                        else
                                                            mConnected = false;
                                                        Log.d(TAG, "TCP host resolved to: " + mRobotAddress + " (port " + mHostTCPPort + ")");

                                                    } catch (SocketTimeoutException st) {
                                                        Log.d(TAG,"Attempt to establish connection timed out");
                                                        mConnected = false;
                                                    } catch (IOException e) {
                                                        Log.e(TAG, "Error connecting to group owner");
                                                        e.printStackTrace();
                                                        mConnected = false;
                                                    } finally {
                                                        try {
                                                            if (mSocket != null)
                                                                mSocket.close();
                                                        } catch (IOException e) {
                                                            Log.d(TAG,"Error closing sockets");
                                                            e.printStackTrace();
                                                        }
                                                        if(!mConnected) {
                                                            mManager.removeGroup(mChannel,null);
                                                        }
                                                    }
                                                    return null;
                                                }

                                                @Override
                                                protected void onPostExecute(Void aVoid) {
                                                    if(mConnected) {
                                                        mCommandClient = new ControlClient(mRobotAddress, mHostUDPPort);
                                                        mSettingsClient = new SettingsClient(mRobotAddress,mHostTCPPort);
                                                        mStatusClient = new RobotStatusClient(mLocalUDPPort, WiFiDirectService.this);
                                                        stopDiscoveringPeers();
                                                        stopServiceDiscovery();

                                                        Intent notifyActivity = new Intent(WIFI_DIRECT_CONNECTION_CHANGED);
                                                        notifyActivity.putExtra(WIFI_DIRECT_CONNECTION_UPDATED_KEY, mConnected);
                                                        localBroadcast.sendBroadcast(notifyActivity);
                                                    }
                                                }
                                            };
                                            // http://stackoverflow.com/questions/9119627/android-sdk-asynctask-doinbackground-not-running-subclass
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                                                async_transmit_ip.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
                                            else
                                                async_transmit_ip.execute((Void[])null);
                                        }
                                    }
                                }
                            }
                        });
                    }
                }
                else {
                    mConnected = false;
                    mManager.cancelConnect(mChannel, null);
                    if(mStatusClient != null) {
                        mStatusClient.stop();
                        mCurrentlyBroadcastingStatus = false;
                    }

                    Intent notifyActivity = new Intent(WIFI_DIRECT_CONNECTION_CHANGED);
                    notifyActivity.putExtra(WIFI_DIRECT_CONNECTION_UPDATED_KEY, mConnected);
                    localBroadcast.sendBroadcast(notifyActivity);
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
                    mCurrentlyDiscoveringPeers = true;
                }
                //Continuously discover peers if there are listeners
                else if (discovery == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                    Log.d(TAG, "Peer discovery stopped");
                    mCurrentlyDiscoveringPeers = false;
                    if (mDiscoverPeersListeners > 0 && mShouldDiscoverPeers)
                        discoverPeers();
                }
            }
        }
    }
}
