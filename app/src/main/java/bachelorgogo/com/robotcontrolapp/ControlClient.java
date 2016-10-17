package bachelorgogo.com.robotcontrolapp;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/*
    ControlClient transmits the settings stored in the supplied SettingsObject through a UDP socket
    to the host as specified by the host address and port.
 */
public class ControlClient {
    static final String TAG = "ControlClient";
    private InetAddress mHostAddress;
    private int mHostPort;
    private String mCommand;
    private DatagramSocket mDatagramSocket;
    private AsyncTask<Void, Void, Void> async_client;
    private boolean FAILURE = false;

    ControlClient(InetAddress host, int port) {
        mHostAddress = host;
        mHostPort = port;
        mDatagramSocket = null;
    }

    /*
        This function sends the commands stored in the supplied CommandObject through a UDP
        socket. The data transmission is done asynchronously.
    */
    public void sendCommand(final CommandObject command)
    {
        mCommand = command.getDataCommandString();

        async_client = new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                try
                {
                    if(mCommand.length() <= (Math.pow(2,32)-1)) {
                        mDatagramSocket = new DatagramSocket();

                        //First send packet size...
                        ByteBuffer dbuf = ByteBuffer.allocate(4);   //max packet size = 2^32
                        dbuf.putInt(mCommand.length());
                        byte[] size = dbuf.array();
                        DatagramPacket sizePacket = new DatagramPacket(size, size.length, mHostAddress, mHostPort);
                        mDatagramSocket.send(sizePacket);

                        //Then the actual packet
                        Log.d(TAG, "Sending command: " + mCommand);
                        byte[] message = mCommand.getBytes();
                        DatagramPacket dp = new DatagramPacket(message, message.length, mHostAddress, mHostPort);
                        mDatagramSocket.send(dp);
                        FAILURE = false;
                    }
                }
                catch (Exception e)
                {
                    Log.d(TAG,"Error sending command");
                    e.printStackTrace();
                    FAILURE = true;

                }
                finally
                {
                    publishProgress();
                    if (mDatagramSocket != null)
                    {
                        mDatagramSocket.close();
                    }
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(Void... values) {
                // Invoke callbacks
                if(FAILURE)
                    command.onFailure(mCommand);
                else
                    command.onSuccess(mCommand);

                super.onProgressUpdate(values);
            }

            @Override
            protected void onPostExecute(Void result)
            {
                Log.d(TAG,"Finished sending command");
                super.onPostExecute(result);
            }
        };

        /*
            In order to run multiple AsyncTask in parallel, the call to execute them is dependent on
            build version
            @ http://stackoverflow.com/questions/9119627/android-sdk-asynctask-doinbackground-not-running-subclass
        */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            async_client.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
        else
            async_client.execute((Void[]) null);
    }
}
