package bachelorgogo.com.robotcontrolapp;

import android.os.AsyncTask;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Created by THP on 04-10-2016.
 */

public class ControlClient {
    private String mHostAddress;
    private int mPort;
    private String mCommand;
    private DatagramSocket mDatagramSocket;
    private AsyncTask<Void, Void, Void> async_cient;

    ControlClient(String host, int port) {
        mHostAddress = host;
        mPort = port;
        mDatagramSocket = null;
    }

    public void sendCommand(String command)
    {
        mCommand = command;
        async_cient = new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                try
                {
                    Log.d("ControlClient","Sending command: " + mCommand);
                    mDatagramSocket = new DatagramSocket();
                    InetAddress hostAddress = InetAddress.getByName(mHostAddress);
                    int msg_length = mCommand.length();
                    byte[] message = mCommand.getBytes();
                    DatagramPacket dp = new DatagramPacket(message, msg_length, hostAddress, mPort);
                    mDatagramSocket.send(dp);
                }
                catch (Exception e)
                {
                    Log.d("ControlClient","Error sending command");
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
                Log.d("ControlClient","Finished sending command");
                super.onPostExecute(result);
            }
        };
        async_cient.execute();
    }
}
