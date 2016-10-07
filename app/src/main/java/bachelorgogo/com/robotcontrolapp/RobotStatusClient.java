package bachelorgogo.com.robotcontrolapp;

import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Created by THP on 06-10-2016.
 */

public class RobotStatusClient {
    static final String TAG = "RobotStatusClient";
    private final int packetSize = 1024;
    private int mPort;
    private String mReceivedString;
    private DatagramSocket mDatagramSocket;
    private AsyncTask<Void, Void, Void> async_cient;
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
        async_cient = new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                receive();
                return null;
            }

            protected void onPostExecute(Void result)
            {
                Log.d(TAG,"Finished receiving data");
                super.onPostExecute(result);
            }
        };
        async_cient.execute();

    }
    public void stop() {
        mReceiveData = false;
    }

    private void receive() {
        try {
            mDatagramSocket = new DatagramSocket(mPort);
            byte[] receiveData = new byte[packetSize];
            while (mReceiveData) {
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
    }
}
