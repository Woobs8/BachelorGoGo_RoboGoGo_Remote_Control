package bachelorgogo.com.robotcontrolapp;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

/*
    SettingsClient opens a TCP socket to the host as specified by the host address and port
    parameters and transmits the settings stored in the supplied SettingsObject.
 */
public class SettingsClient {static final String TAG = "SettingsClient";
    private InetAddress mHostAddress;
    private int mHostPort;
    private String mCommand;
    private Socket mSocket;
    private AsyncTask<Void, Void, Void> async_client;
    private boolean mSettinsTransmitted = false;
    private final int maxPacketSize = 255;

    private final int SO_TIMEOUT = 1000;

    SettingsClient(InetAddress host, int port) {
        mHostAddress = host;
        mHostPort = port;
    }

    /*
        This function sends the settings stored in the supplied SettingsObject through a TCP
        socket. The data transmission is done asynchronously.
     */
    public void sendSettings(final SettingsObject command)
    {
        mCommand = command.getDataString();
        async_client = new AsyncTask<Void, Void, Void>()
        {
            @Override
            protected Void doInBackground(Void... params) {
                String dataStr = "";
                try {
                    if(mCommand.length() <= (maxPacketSize)) {
                        mSocket = new Socket();
                        mSocket.setSoTimeout(SO_TIMEOUT);
                        mSocket.bind(null);

                        // Connect to host
                        mSocket.connect((new InetSocketAddress(mHostAddress, mHostPort)));
                        DataInputStream in = new DataInputStream(mSocket.getInputStream());
                        DataOutputStream out = new DataOutputStream(mSocket.getOutputStream());

                        //Send settings to host
                        byte[] packet = new byte[maxPacketSize];
                        Arrays.fill(packet, (byte) 0);
                        byte[] message = mCommand.getBytes();
                        System.arraycopy(message, 0, packet, 0, message.length);
                        out.write(packet);

                        //Wait for ACK
                        byte[] rcv = new byte[command.getAckString().length()];
                        in.read(rcv);
                        String recvString = new String(rcv);
                        if (recvString.equals(command.getAckString())) {
                            mSettinsTransmitted = true;
                        } else {
                            mSettinsTransmitted = false;
                        }
                    }

                } catch (SocketTimeoutException se) {
                    Log.d(TAG, "Receiving socket on port " + mHostPort + " timed out");
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
                        Log.d(TAG, "Error closing socket on port " + mHostPort);
                        e.printStackTrace();
                    }
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(Void... values) {
                // Invoke callback methods
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
