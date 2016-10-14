package bachelorgogo.com.robotcontrolapp;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
    private Socket mSocket;
    private AsyncTask<Void, Void, Void> async_client;
    private boolean mSettinsTransmitted = false;

    private final int SO_TIMEOUT = 1000;

    SettingsClient(InetAddress host, int port) {
        mHostAddress = host;
        mPort = port;
    }

    public void sendSettings(final SettingsObject command)
    {
        mCommand = command.getDataString();
        async_client = new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params) {
                String dataStr = "";
                try {
                    mSocket = new Socket();
                    mSocket.setSoTimeout(SO_TIMEOUT);
                    mSocket.bind(null);
                    mSocket.connect((new InetSocketAddress(mHostAddress, mPort)));
                    DataInputStream in = new DataInputStream(mSocket.getInputStream());
                    DataOutputStream out = new DataOutputStream(mSocket.getOutputStream());

                    //Send port to client
                    out.writeUTF(mCommand);

                    //Wait for ACK
                    String recv = in.readUTF();
                    if(recv.equals(command.getAckString())) {
                        mSettinsTransmitted = true;
                    } else {
                        mSettinsTransmitted = false;
                    }

                } catch (SocketTimeoutException se) {
                    Log.d(TAG, "Receiving socket on port " + mPort + " timed out");
                    mSettinsTransmitted = false;
                } catch (Exception e) {
                    mSettinsTransmitted = false;
                    Log.e(TAG, "Error occurred while sending settings ");
                    e.printStackTrace();
                } finally {
                    publishProgress();
                    try{
                        mSocket.close();
                    }catch (IOException e){
                        Log.d(TAG, "Error closing socket on port " + mPort );
                        e.printStackTrace();
                    }
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(Void... values) {
                if(mSettinsTransmitted)
                    command.onSuccess(mCommand);
                else
                    command.onFailure(mCommand);

                super.onProgressUpdate(values);
            }

            protected void onPostExecute(Void result)
            {
                Log.d(TAG,"Finished sending settings");
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
