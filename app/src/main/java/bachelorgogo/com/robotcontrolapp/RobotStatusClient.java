package bachelorgogo.com.robotcontrolapp;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;

/**
 * Created by THP on 06-10-2016.
 */

public class RobotStatusClient {
    private final String TAG = "RobotStatusClient";
    private int mPacketSize = 0;
    private int mPort;
    private String mReceivedString;
    private DatagramSocket mDatagramSocket;
    private AsyncTask<Void, Void, Void> async_client;
    private boolean mReceiveData;
    private WiFiDirectService mService;

    RobotStatusClient(int port, WiFiDirectService service) {
        mPort = port;
        mDatagramSocket = null;
        mReceiveData = false;
        mService = service;
    }

    public void start() {
        mReceiveData = true;
        async_client = new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                try {
                    mDatagramSocket = new DatagramSocket(mPort);
                    byte[] packetSizeData = new byte[4];    //Max size = 2^32
                    while (mReceiveData) {
                        //First read size of packet...
                        DatagramPacket size_packet = new DatagramPacket(packetSizeData, packetSizeData.length);
                        mDatagramSocket.receive(size_packet);
                        ByteBuffer sizeBuffer = ByteBuffer.wrap(size_packet.getData()); // big-endian by default
                        mPacketSize = sizeBuffer.getInt();
                        Log.d(TAG,"Receiving packet of size: " + mPacketSize);

                        //...Then the actual packet
                        byte[] receiveData = new byte[mPacketSize];
                        DatagramPacket recv_packet = new DatagramPacket(receiveData, receiveData.length);
                        Log.d(TAG, "receiving data");
                        mDatagramSocket.receive(recv_packet);
                        mReceivedString = new String(recv_packet.getData());
                        Log.d(TAG, "Received string: " + mReceivedString);

                        //Broadcast received data
                        Intent notifyActivity = new Intent(WiFiDirectService.ROBOT_STATUS_RECEIVED);
                        notifyActivity.putExtra(WiFiDirectService.ROBOT_STATUS_RECEIVED_KEY, mReceivedString);
                        LocalBroadcastManager.getInstance(mService.getApplicationContext()).sendBroadcast(notifyActivity);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error receiving data");
                    e.printStackTrace();
                } finally {
                    if (mDatagramSocket != null)
                    {
                        mDatagramSocket.close();
                    }
                }
                return null;
            }

            protected void onPostExecute(Void result)
            {
                Log.d(TAG,"Finished receiving data");
                super.onPostExecute(result);
            }
        };
        // http://stackoverflow.com/questions/9119627/android-sdk-asynctask-doinbackground-not-running-subclass
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            async_client.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
        else
            async_client.execute((Void[]) null);

    }
    public void stop() {
        mReceiveData = false;
    }
}
