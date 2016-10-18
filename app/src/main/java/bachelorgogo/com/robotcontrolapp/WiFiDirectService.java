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
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

public class WiFiDirectService extends Service {
    static final String TAG = "WiFiDirectService";
    // WiFi Direct Local Broadcast intent keys
    static final String WIFI_DIRECT_CONNECTION_UPDATED_KEY = "WiFi_Direct_update";
    static final String WIFI_DIRECT_PEER_NAME_KEY = "WiFi_Direct_peer_name_key";
    static final String WIFI_DIRECT_PEER_ADDRESS_KEY = "WiFi_Direct_peer_address_key";

    // WiFi Direct Local Broadcast actions
    static final String WIFI_DIRECT_STATE_CHANGED = "WiFi_Direct_state_changed";
    static final String WIFI_DIRECT_PEERS_CHANGED = "WiFi_Direct_peers_changed";
    static final String WIFI_DIRECT_SERVICES_CHANGED = "WiFi_Direct_services_changed";
    static final String WIFI_DIRECT_CONNECTION_CHANGED = "WiFi_Direct_connection_changed";
    static final String WIFI_DIRECT_DEVICE_CHANGED = "WiFi_Direct_device_changed";

    // Local Broadcast actions
    static final String ROBOT_STATUS_RECEIVED_KEY = "robot_status";
    static final String ROBOT_STATUS_RECEIVED = "robot_status_received";

    // WiFi Direct related
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;
    WifiP2pManager.PeerListListener mPeerListListener;
    WifiP2pDnsSdServiceRequest mServiceRequest;
    private HashMap<String, String> mDevices = new HashMap<>();
    Handler mServiceDiscoveryHandler;
    Runnable mServiceDiscoveryRunnable;
    private boolean mWiFiDirectEnabled = false;
    private boolean mConnected = false;
    private boolean mCurrentlyDiscoveringPeers = false;
    private boolean mShouldDiscoverPeers = true;
    private int mDiscoverPeersListeners = 0;
    private int mServiceDiscoveryInterval = 120000; //2 min * 60 sec * 1000 msec
    private final String mNetworkServiceName = "GiantsFTW";

    // Broadcast receiver related
    BroadcastReceiver mReceiver;
    IntentFilter mIntentFilter;
    private boolean mCurrentlyBroadcastingStatus = false;
    private int mBroadcastStatusListeners = 0;


    // Network related
    InetAddress mRobotAddress;
    ServerSocket mServerSocket;
    Socket mSocket;

    // Network clients
    ControlClient mControlClient;
    RobotStatusClient mRobotStatusClient;
    SettingsClient mSettingsClient;
    private int mGroupOwnerPort = 9999;
    private int mHostUDPPort = -1;
    private int mHostTCPPort = -1;
    private int mLocalUDPPort = 4999;
    private int mEstablishConnectionTimeout = 30000; //30 sec * 1000 msec

    // Binder given to clients
    private final IBinder mBinder = (IBinder) new LocalBinder();
    public class LocalBinder extends Binder {
        WiFiDirectService getService() {
            // Return this instance of LocalService so clients can call public methods
            return WiFiDirectService.this;
        }
    }

    // Empty constructor
    public WiFiDirectService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        setUpWiFiDirectChannel();
        mPeerListListener = new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peers) {
                Log.d("Peer listener:","peers available");
            }
        };

        setUpServiceDiscovery();
        /*
            Setup continuous service discovery.
            This is necessary since service discovery supposedly times out after 2 min.
            without a broadcast or callback
         */
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

        // Intent filter for the broadcast receiver, which listens for Wi-Fi P2P broadcasts
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
        registerReceiver(mReceiver, mIntentFilter);
        return mBinder;
    }

    /*
        This is called by a bound activity to indicate it is listening for discovered peers,
        received status messages or both. The function will call the system API correspondingly.
     */
    public void addListener(boolean broadcastPeers, boolean broadcastStatus) {
        // Indicates the listener is listening for discovered peers (services)
        if(broadcastPeers) {
            Log.d(TAG,"Adding peer discovery listener");
            mDiscoverPeersListeners++;
            if (!mCurrentlyDiscoveringPeers) {
                discoverPeers();
                mServiceDiscoveryHandler.postDelayed(mServiceDiscoveryRunnable, 0);
            }
        }

        // Indicates the listener is listening for received status messages
        if(broadcastStatus) {
            Log.d(TAG,"Adding status listener");
            mBroadcastStatusListeners++;
            if (!mCurrentlyBroadcastingStatus) {
                mRobotStatusClient.start();
                mCurrentlyBroadcastingStatus = true;
            }
        }
    }

    /*
        onUnbind has been overloaded to return true, so onRebind will be called when activities
        bind to the service after onBind has been called.
     */
    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Service unbound");
        try {
            unregisterReceiver(mReceiver);
        } catch(IllegalArgumentException e) {
            Log.d(TAG,"Receiver already unregistered. Do nothing.");
        }
        return true;
    }

    /*
        This is called by a bound activity to indicate it has stopped listening for discovered peers,
        received status messages or both. The function will call the system API correspondingly.
        This function should only be called after addListener() and with the same parameters.
    */
    public void removeListener(boolean broadcastPeers, boolean broadcastStatus) {
        // Indicates the listener has stopped listening for discovered peers (services)
        if(broadcastPeers) {
            Log.d(TAG,"Removing peer discovery listener");
            mDiscoverPeersListeners--;
            if(mDiscoverPeersListeners <= 0) {
                mDiscoverPeersListeners = 0;
                stopDiscoveringPeers();
                mServiceDiscoveryHandler.removeCallbacks(mServiceDiscoveryRunnable);
                stopServiceDiscovery();
            }
        }

        // Indicates the listener has stopped listening for received status messages
        if(broadcastStatus) {
            Log.d(TAG,"Removing status listener");
            mBroadcastStatusListeners--;
            if(mBroadcastStatusListeners <= 0) {
                mBroadcastStatusListeners = 0;
                mRobotStatusClient.stop();
                mCurrentlyBroadcastingStatus = false;
            }
        }
    }

    /*
        Called when activities bind after onBind() has been called
    */
    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "Service rebound");
        registerReceiver(mReceiver, mIntentFilter);
        super.onRebind(intent);
    }

    /*
        Disconnect from Wi-Fi P2P group, stop network service discovery and stop peer discovery.
        If any network clients are running, they will be stopped and their sockets closed.
    */
    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        removeWiFiDirectGroup();
        stopServiceDiscovery();
        stopDiscoveringPeers();
        try {
            unregisterReceiver(mReceiver);
        } catch(IllegalArgumentException e) {
            Log.d(TAG,"Receiver already unregistered. Do nothing.");
        }
        if(mRobotStatusClient != null) {
            mRobotStatusClient.stop();
            mCurrentlyBroadcastingStatus = false;
        }
        super.onDestroy();
    }

    /*
        Initialize the Wi-Fi P2P channel.
    */
    private void setUpWiFiDirectChannel() {
        Log.d(TAG,"Initializing channel");
        mChannel = mManager.initialize(this, getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                setUpWiFiDirectChannel();
            }
        });
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
    }

    /*
        This function disconnects from the connect Wi-Fi P2P group by removing the group.
        The group is removed through nested callbacks to ensure the group exists before
        attempting to remove it.
    */
    private void removeWiFiDirectGroup() {
        Log.d(TAG,"Removing WiFi Direct group");
        mManager.requestGroupInfo(mChannel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(final WifiP2pGroup group) {
                Log.d(TAG,"onGroupInfoAvailable called");
                mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG,"removeGroup successfully called");
                        deletePersistentGroup(group);
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.d(TAG,"Error calling removeGroup");
                    }
                });
            }
        });
    }

    /*
        This is a hack to help with the quirkiness of the Android Wi-Fi P2P API.
        This function searches for the hidden method of the Android WifiP2pManager, which allows
        for the deletion of persistent Wi-Fi P2P groups.

        @http://stackoverflow.com/questions/23653707/forgetting-old-wifi-direct-connections
    */
    private void deletePersistentGroup(WifiP2pGroup wifiP2pGroup) {
        try {

            Method getNetworkId = WifiP2pGroup.class.getMethod("getNetworkId");
            Integer networkId = (Integer) getNetworkId.invoke(wifiP2pGroup);
            Method deletePersistentGroup = WifiP2pManager.class.getMethod("deletePersistentGroup",
                    WifiP2pManager.Channel.class, int.class, WifiP2pManager.ActionListener.class);
            deletePersistentGroup.invoke(mManager, mChannel, networkId, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.e(TAG, "deletePersistentGroup onSuccess");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "deletePersistentGroup failure: " + reason);
                }
            });
        } catch (NoSuchMethodException e) {
            Log.e("WIFI", "Could not delete persistent group", e);
        } catch (InvocationTargetException e) {
            Log.e("WIFI", "Could not delete persistent group", e);
        } catch (IllegalAccessException e) {
            Log.e("WIFI", "Could not delete persistent group", e);
        }
    }

    /*
        This is called by a bound activity to indicate a desire to connect to a device with the
        addresses supplied by @params.
        The function will invoke the appropriate API method.
    */
    public void connectToDevice(final String deviceAddress) {
        if(deviceAddress != null && !deviceAddress.isEmpty()) {
            Log.d(TAG, "Connecting to device: " + deviceAddress);
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = deviceAddress;
            config.groupOwnerIntent = 15;    // Highest possible = device wants to be group owner
            /*
                Invoke connect on WifiP2pManager. A successful connection will trigger a
                WIFI_P2P_CONNECTION_CHANGED_ACTION broadcast from the system.
            */
            mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    //success logic
                    Log.d(TAG, "Successful attempt to connect to " + deviceAddress);
                }

                @Override
                public void onFailure(int reason) {
                    //failure logic
                    Log.d(TAG, "Error attempting to connect to " + deviceAddress);

                    // Broadcast to inform listeners about failure to connect
                    Intent notifyActivity = new Intent(WIFI_DIRECT_CONNECTION_CHANGED);
                    notifyActivity.putExtra(WIFI_DIRECT_CONNECTION_UPDATED_KEY, false);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(notifyActivity);
                }
            });
        } else {
            Log.d(TAG,"deviceAddress parameter was empty");
        }
    }

    /*
        This is called by a bound activity to indicate the service should disconnect from the
        currently connected device.
        The function will remove the Wi-Fi P2P group.
    */
    public void disconnectFromDevice() {
        Log.d(TAG,"Disconnecting from device");
        removeWiFiDirectGroup();
    }

    /*
        This is called by a bound activity to send a control command to the
        currently connected device.
        The function will call an instance of the ControlClient class to transmit the control data
        through a UDP socket.
    */
    public void sendCommandObject(CommandObject command) {
        if (mControlClient != null) {
            mControlClient.sendCommand(command);
        }
    }

    /*
        This is called by a bound activity to send settings changes to the
        currently connected device.
        The function will call an instance of the SettingsClient class to transmit the settings data
        through a TCP socket.
    */
    public void sendSettingsObject(SettingsObject settings) {
        if(mSettingsClient != null) {
            mSettingsClient.sendSettings(settings);
        }
    }

    /*
        This function starts peer discovery through the WifiP2P API.
        This function is necessary since Android requires peer discovery to be active in order to
        register and request services on the network.
     */
    private void discoverPeers() {
        Log.d(TAG,"Starting peer discovery");
        mShouldDiscoverPeers = true;
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG,"discoverPeers successfully called");
            }

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG,"Failed to call discoverPeers");
            }
        });
    }

    /*
        This function stops peer discovery through the WifiP2P API.
    */
    private void stopDiscoveringPeers() {
        Log.d(TAG,"Stopping peer discovery");
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

    /*
        This function prepares the network service discovery by setting up the network service
        request listeners.
    */
    private void setUpServiceDiscovery() {
        // This listener is invoked when the record registered with the network service is available.
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

        // This listener is invoked when a network service matching the service request is registered
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

                // Broadcast with device name and address included in the intent.
                // Only discovered network services of the specified instance type are broadcast
                if(instanceName.equals("_"+ mNetworkServiceName)) {
                    Intent notifyActivity = new Intent(WIFI_DIRECT_SERVICES_CHANGED);
                    notifyActivity.putExtra(WIFI_DIRECT_PEER_NAME_KEY, resourceType.deviceName);
                    notifyActivity.putExtra(WIFI_DIRECT_PEER_ADDRESS_KEY, resourceType.deviceAddress);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(notifyActivity);
                }
            }
        };
        mManager.setDnsSdResponseListeners(mChannel, servListener, txtListener);
    }

    /*
        This function adds a service request to the WifiP2pManager.
        This is done through a nested callback to ensure the old service request was removed, as
        this is another quirky part of the Android API, which seems to improve reliability.
    */
    private void discoverService() {
        Log.d(TAG,"Starting service discovery");
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
                                        Log.d(TAG,"discoverServices successfully called");
                                    }

                                    @Override
                                    public void onFailure(int code) {
                                        Log.d(TAG,"Failed to call discoverServices");
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

    /*
        This function removes a service request to the WifiP2pManager and stops the continuous service
        discovery if it is running.
    */
    private void stopServiceDiscovery() {
        //Stop continuously discovering services
        Log.d(TAG,"Stopping service discovery");
        if(mServiceDiscoveryHandler != null && mServiceDiscoveryRunnable != null)
            mServiceDiscoveryHandler.removeCallbacks(mServiceDiscoveryRunnable);

        //Stop pending service request
        if(mManager != null && mServiceRequest != null)
            mManager.removeServiceRequest(mChannel, mServiceRequest, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG,"Service request successfully removed");
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG,"Error removing service request");
                }
            });
    }

    /*
        This receiver listens for system broadcasts that relate to Wi-Fi P2P hardware and connections.
    */
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
                // Check to see if Wi-Fi is enabled and notify appropriate activit
                Log.d(TAG,"WiFiP2P broadcast: P2P state changed");
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    mWiFiDirectEnabled = true;
                } else {
                    mWiFiDirectEnabled = false;
                }

                // Wi-Fi P2P state is broadcast
                Intent notifyActivity = new Intent(WIFI_DIRECT_STATE_CHANGED);
                notifyActivity.putExtra(WIFI_DIRECT_CONNECTION_UPDATED_KEY, mWiFiDirectEnabled);
                localBroadcast.sendBroadcast(notifyActivity);
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                // The peer list has changed.
                Log.d(TAG,"WiFiP2P broadcast: Peers changed");

                // Wi-Fi P2P peers changed broadcast
                Intent notifyActivity = new Intent(WIFI_DIRECT_PEERS_CHANGED);
                localBroadcast.sendBroadcast(notifyActivity);
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                // Respond to new connections or disconnections
                Log.d(TAG,"WiFiP2P broadcast: Connection changed");
                NetworkInfo networkState = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                // Check if we connected or disconnected.
                if (networkState.isConnected()) {
                    Log.d(TAG,"Connection established");
                    // If connection is established start IP and port exchange procedure
                    if(!mConnected) {
                        mConnected = true;
                        //Resolve IP addresses
                        mManager.requestConnectionInfo(mChannel, new WifiP2pManager.ConnectionInfoListener() {
                            @Override
                            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                                Log.d(TAG,"ConnectionInfo received");
                                if (info.groupFormed) {
                                    Log.d(TAG,"WiFi Direct group is formed");
                                    /*
                                        If device is group owner, it should listen for connections
                                        port 9999, and exchange IP addresses and ports with clients.
                                     */
                                    if (info.isGroupOwner) {
                                        Log.d(TAG,"This device is group owner");
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
                                                        if(mSocket != null)
                                                            mSocket.close();
                                                        if(mServerSocket != null)
                                                            mServerSocket.close();
                                                    } catch (IOException e) {
                                                        Log.d(TAG,"Error closing sockets");
                                                        e.printStackTrace();
                                                    }
                                                    if(!mConnected) {
                                                        removeWiFiDirectGroup();
                                                    }
                                                }
                                                return null;
                                            }

                                            @Override
                                            protected void onPostExecute(Void aVoid) {
                                                //If connection is established, set up the necessary
                                                // sockets and stop service discovery.
                                                if(mConnected) {
                                                    Log.d(TAG,"Connection Established");
                                                    mControlClient = new ControlClient(mRobotAddress, mHostUDPPort);
                                                    mSettingsClient = new SettingsClient(mRobotAddress,mHostTCPPort);
                                                    mRobotStatusClient = new RobotStatusClient(mLocalUDPPort, WiFiDirectService.this);
                                                    stopDiscoveringPeers();
                                                    stopServiceDiscovery();

                                                    // Broadcast a connection changed event
                                                    Intent notifyActivity = new Intent(WIFI_DIRECT_CONNECTION_CHANGED);
                                                    notifyActivity.putExtra(WIFI_DIRECT_CONNECTION_UPDATED_KEY, mConnected);
                                                    localBroadcast.sendBroadcast(notifyActivity);
                                                }
                                            }
                                        };
                                        // http://stackoverflow.com/questions/9119627/android-sdk-asynctask-doinbackground-not-running-subclass
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                                            async_client.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
                                        else
                                            async_client.execute((Void[])null);
                                    /*
                                        If device is not group owner, it should connect to the group
                                        owner on port 9999, and exchange IP addresses and ports.
                                     */
                                    } else {
                                        Log.d(TAG,"This device is a client");
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
                                                        //Prevent race condition when attempting to
                                                        // connect before owner is accepts incoming connections
                                                        SystemClock.sleep(100);
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
                                                            removeWiFiDirectGroup();
                                                        }
                                                    }
                                                    return null;
                                                }

                                                @Override
                                                protected void onPostExecute(Void aVoid) {
                                                    //If connection is established, set up the necessary
                                                    // sockets and stop service discovery.
                                                    if(mConnected) {
                                                        Log.d(TAG,"Connection Established");
                                                        mControlClient = new ControlClient(mRobotAddress, mHostUDPPort);
                                                        mSettingsClient = new SettingsClient(mRobotAddress,mHostTCPPort);
                                                        mRobotStatusClient = new RobotStatusClient(mLocalUDPPort, WiFiDirectService.this);
                                                        stopDiscoveringPeers();
                                                        stopServiceDiscovery();

                                                        // Broadcast a connection changed event
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
                /*
                    For debug purposes. Currently unused.
                 */
                } else if (networkState.isConnectedOrConnecting()) {
                    Log.d(TAG,"Connection being established");
                }
                /*
                    If no connection was established or an existing connection was lost all Wi-Fi P2P
                    connections are closed and network sockets are closed.
                    If there are listeners, service discovery is restarted
                */
                else {
                    Log.d(TAG,"No connection established");
                    mConnected = false;
                    mManager.cancelConnect(mChannel, null);
                    removeWiFiDirectGroup();
                    if(mRobotStatusClient != null) {
                        mRobotStatusClient.stop();
                        mCurrentlyBroadcastingStatus = false;
                    }

                    if(mDiscoverPeersListeners > 0) {
                        Log.d(TAG,"There are peer listeners, restarting peer and service discovery");
                        discoverPeers();
                        discoverService();
                    }

                    // Broadcast connection changed event
                    Intent notifyActivity = new Intent(WIFI_DIRECT_CONNECTION_CHANGED);
                    notifyActivity.putExtra(WIFI_DIRECT_CONNECTION_UPDATED_KEY, mConnected);
                    localBroadcast.sendBroadcast(notifyActivity);
                }
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                // Respond to this device's wifi state changing
                Log.d(TAG,"WiFiP2P broadcast: This device changed");
                // Broadcast Wi-Fi Direct device state changed event
                Intent notifyActivity = new Intent(WIFI_DIRECT_DEVICE_CHANGED);
                localBroadcast.sendBroadcast(notifyActivity);
            }
            else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
                // Peer discovery stopped or started
                Log.d(TAG,"WiFiP2P broadcast: Peer discovery changed");
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
