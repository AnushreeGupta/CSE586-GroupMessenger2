package edu.buffalo.cse.cse486586.groupmessenger1;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import static android.R.id.input;
import static edu.buffalo.cse.cse486586.groupmessenger1.GroupMessengerActivity.TAG;
import static java.security.AccessController.getContext;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    //static final String REMOTE_PORT0 = "11108";

    static final String[] RemotePorts = { "11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    int sequence = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;

        }

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        final EditText editText = (EditText) findViewById(R.id.editText1);

        final Button button = (Button) findViewById(R.id.button4);

        button.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                // Perform action on click

                String msg = editText.getText().toString() + "\n";
                editText.setText(""); // This is one way to reset the input box.
                TextView localTextView = (TextView) findViewById(R.id.textView1);
                localTextView.append("\t" + msg); // This is one way to display a string.
                TextView remoteTextView = (TextView) findViewById(R.id.textView1);
                remoteTextView.append("\n");

                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }



/***
 * ServerTask is an AsyncTask that should handle incoming messages. It is created by
 * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
 * <p>
 * Please make sure you understand how AsyncTask works by reading
 * http://developer.android.com/reference/android/os/AsyncTask.html
 *
 * @author stevko
 */

//Below function is taken from OnPTestClickListener.java class to build URI

private Uri buildUri(String scheme, String authority) {
    Uri.Builder uriBuilder = new Uri.Builder();
    uriBuilder.authority(authority);
    uriBuilder.scheme(scheme);
    return uriBuilder.build();
}

private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

    @Override
    protected Void doInBackground(ServerSocket... sockets) {
        ServerSocket serverSocket = sockets[0];

        try{
            while(true) {
                Socket clientSocket = serverSocket.accept();

                DataInputStream din = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream dout = new DataOutputStream(clientSocket.getOutputStream());

                String input = din.readUTF();
                dout.writeUTF("PA2 OK");

                publishProgress(input);

                din.close();
                dout.close();
            }

        } catch (IOException e) {
            Log.e(TAG, "No input to ServerSocket");
        }

        return null;
    }

    protected void onProgressUpdate(String... strings) {

        Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger1.provider");
        ContentResolver contentResolver = getContentResolver();
        ContentValues mContentValues = new ContentValues();

        String strReceived = strings[0].trim();
        String key = Integer.toString(sequence);

        mContentValues.put("key", key);
        mContentValues.put("value", strReceived);
        sequence++;

        contentResolver.insert( mUri, mContentValues);

        TextView remoteTextView = (TextView) findViewById(R.id.textView1);
        remoteTextView.append(strReceived + "\t\n");
        TextView localTextView = (TextView) findViewById(R.id.textView1);
        localTextView.append("\n");

        return;
    }
}

/***
 * ClientTask is an AsyncTask that should send a string over the network.
 * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
 * an enter key press event.
 *
 * @author stevko
 */
private class ClientTask extends AsyncTask<String, Void, Void> {

    @Override
    protected Void doInBackground(String... msgs) {
        try {

            for(int i = 0; i < 5; i++){

                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(RemotePorts[i]));

                String msgToSend = msgs[0];

                DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
                dout.writeUTF(msgToSend);

                DataInputStream din = new DataInputStream(socket.getInputStream());
                String ack = din.readUTF();

                if(ack.equals("P2 OK")) {
                    dout.close();
                    din.close();
                    socket.close();
                }
            }
        } catch (UnknownHostException e) {
            Log.e(TAG, "ClientTask UnknownHostException");
        } catch (IOException e) {
            Log.e(TAG, "ClientTask socket IOException");
        }

        return null;
    }
}

}
