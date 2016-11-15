package bachelorgogo.com.robotcontrolapp;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;

/*
    RobotStatusClient class is used to listen for status messages from the robot on the port
    specified by the constructor port parameter. When a status is received, a local broadcast is
    made with the received status string included as an extra in the intent.
 */
public class RobotStatusClient {
    private final String TAG = "RobotStatusClient";
    private int mPacketSize = 255;
    private int mPort;
    private String mReceivedString;
    private DatagramSocket mDatagramSocket;
    private AsyncTask<Void, Void, Void> async_client;
    private WiFiDirectService mService;
    private boolean mManualStop;
    private boolean mRunning;

    RobotStatusClient(int port, WiFiDirectService service) {
        mPort = port;
        mDatagramSocket = null;
        mService = service;
        mManualStop = false;
    }

    /*
        This function will listen continuously for status messages asynchronously until stopped by
        calling the class method stop().
     */
    public void start() {
        mManualStop = false;
        async_client = new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                mRunning = true;
                Log.d(TAG,"Started listening for robot status messages");
                // cancel() is invoked by the class method stop()
                while (!isCancelled())
                    listenOnSocket();

                mRunning = false;
                return null;
            }

            @Override
            protected void onPostExecute(Void result)
            {
                Log.d(TAG,"Stopped listening for robot status messages");
                super.onPostExecute(result);
            }
        };
        if(!mRunning) {
            /*
                In order to run multiple AsyncTask in parallel, the call to execute them is dependent on
                build version
                @ http://stackoverflow.com/questions/9119627/android-sdk-asynctask-doinbackground-not-running-subclass
            */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                async_client.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
            else
                async_client.execute((Void[]) null);
        } else {
            Log.d(TAG,"Status receiver already running");
        }

    }

    /*
        This function stops the continuous listening for status messages by invoking the AsyncTask
        class method cancel(). close() is invoked on the DatagramSocket in order to stop the
        otherwise blocking read operation.
     */
    public void stop() {
        mManualStop = true;
        if(async_client != null) {
            async_client.cancel(true);
            mDatagramSocket.close();
        }
    }

    /*
        This function will block and listen on a UDP socket until a status message is received or
        the socket is closed.
        Received status messages are broadcast.
     */
    private void listenOnSocket() {
        try {
            Log.d(TAG,"Opening socket on port " + mPort);
            mDatagramSocket = new DatagramSocket(mPort);

            byte[] receiveData = new byte[mPacketSize];
            DatagramPacket recv_packet = new DatagramPacket(receiveData, receiveData.length);
            Log.d(TAG, "Receiving data");
            mDatagramSocket.receive(recv_packet);
            mReceivedString = new String(recv_packet.getData());
            Log.d(TAG, "Received status: " + mReceivedString);

            //Broadcast received data
            Intent notifyActivity = new Intent(WiFiDirectService.ROBOT_STATUS_RECEIVED);
            notifyActivity.putExtra(WiFiDirectService.ROBOT_STATUS_RECEIVED_KEY, mReceivedString);
                LocalBroadcastManager.getInstance(mService.getApplicationContext()).sendBroadcast(notifyActivity);
        } catch (IOException e) {
            if (!mManualStop) {
                Log.e(TAG, "Error occurred while listening on port " + mPort);
                e.printStackTrace();
            } else {
                Log.d(TAG, "Socket on port " + mPort + " closed manually");
            }
        // When testing we occasionally reached the application memory limit, when receiving
        // many packets in a row - presumably due to the system being unable to handle the
        // processing in time, and the packets accumulating as a result thereof.
        // In order to handle this we catch the exception, and simply ignore the received packet to
        // allow the system to recover. Ignoring the packet is not critical as a status message
        // will be transmitted regularly.
        } catch (OutOfMemoryError me) {
            Log.d(TAG,"Out of memory, received data ignored");
        } finally {
            Log.d(TAG,"Closing socket on port " + mPort);
            if (mDatagramSocket != null)
            {
                mDatagramSocket.close();
            }
        }
    }
}