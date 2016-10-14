package bachelorgogo.com.robotcontrolapp;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Created by rasmus on 10/13/2016.
 */

public class SettingsClient {static final String TAG = "SettingsClient";
    private InetAddress mHostAddress;
    private int mPort;
    private String mCommand;
    private ServerSocket mServerSocket;
    private Socket mSocket;
    private AsyncTask<Void, Void, Void> async_client;

    private final int SO_TIMEOUT = 1000;

    SettingsClient(InetAddress host, int port) {
        mHostAddress = host;
        mPort = port;
    }

    public void sendCommand(String command)
    {
        mCommand = command;
        async_client = new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params) {
                String dataStr = "";
                try {
                    mServerSocket = new ServerSocket(mPort);
                    Log.d(TAG,"Transmitting TCP Packet " + mCommand);
                    mSocket = mServerSocket.accept();
                    mSocket.setSoTimeout(SO_TIMEOUT);
                    DataOutputStream out = new DataOutputStream(mSocket.getOutputStream());
                    DataInputStream in = new DataInputStream(mSocket.getInputStream());

                    //Send port to client
                    out.writeUTF(mCommand);

                    //read client port
                    dataStr = in.readUTF();
                    Log.d(TAG, "Logging Data Command Message : " + dataStr);

                } catch (SocketTimeoutException se) {
                    Log.d(TAG, "Receiving socket on port " + mPort + " timed out");
                    se.printStackTrace();

                } catch (Exception e) {
                    Log.e(TAG, "Error occurred while sending/receiving Command ");
                    e.printStackTrace();
                } finally {
                    Log.d(TAG,"Checking data is Good ");

                    try{
                        mSocket.close();
                        mServerSocket.close();
                    }catch (IOException e){
                        Log.d(TAG, "Error Closing Socket on port " + mPort );
                        e.printStackTrace();
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
