package bachelorgogo.com.robotcontrolapp;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class ControlClient {
    static final String TAG = "ControlClient";
    private InetAddress mHostAddress;
    private int mPort;
    private String mCommand;
    private DatagramSocket mDatagramSocket;
    private AsyncTask<Void, Void, Void> async_client;

    ControlClient(InetAddress host, int port) {
        mHostAddress = host;
        mPort = port;
        mDatagramSocket = null;
    }

    public void sendCommand(String command)
    {
        mCommand = command;
        async_client = new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                try
                {
                    if(mCommand.length() <= (2^32-1)) {
                        mDatagramSocket = new DatagramSocket();

                        //First send packet size...
                        ByteBuffer dbuf = ByteBuffer.allocate(4);   //max packet size = 2^32
                        dbuf.putInt(mCommand.length());
                        byte[] size = dbuf.array();
                        DatagramPacket sizePacket = new DatagramPacket(size, size.length, mHostAddress, mPort);
                        mDatagramSocket.send(sizePacket);

                        //Then the actual packet
                        Log.d(TAG, "Sending command: " + mCommand);
                        byte[] message = mCommand.getBytes();
                        DatagramPacket dp = new DatagramPacket(message, message.length, mHostAddress, mPort);
                        mDatagramSocket.send(dp);
                    }
                }
                catch (Exception e)
                {
                    Log.d(TAG,"Error sending command");
                    e.printStackTrace();
                }
                finally
                {
                    if (mDatagramSocket != null)
                    {
                        mDatagramSocket.close();
                    }
                }
                return null;
            }

            protected void onPostExecute(Void result)
            {
                Log.d(TAG,"Finished sending command");
                super.onPostExecute(result);
            }
        };
        // http://stackoverflow.com/questions/9119627/android-sdk-asynctask-doinbackground-not-running-subclass
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            async_client.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
        else
            async_client.execute((Void[]) null);
    }
}
