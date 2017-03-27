package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import static android.R.attr.key;
import static android.R.attr.port;
import static android.R.attr.type;
import static android.R.attr.x;
import static android.R.attr.y;
import static android.R.id.input;
import static android.R.id.message;
import static android.util.Log.d;
import static edu.buffalo.cse.cse486586.groupmessenger2.GroupMessengerActivity.TAG;
import static java.security.AccessController.getContext;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    // ************** From PA 2B **********************
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String[] RemotePorts = { "11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    int sequence = 0;

    //************* PA 2B *****************************
    int [] proposed_seq = {0, 0, 0, 0, 0};                  // Proposed Sequence List for all the avds
    int [] agreed_seq = {0, 0, 0, 0, 0};                    // Agreed Sequence List for all the avds
    int messageID = 0;                                      // Unique Identifier for any message
    String sendPort;                                        // Send Port i.e. the initiator of a message
    String receiver;                                        // Remote Port i.e. the receiver of a message

    Queue<Message> holding_queue = new PriorityQueue();     // Holding Queue for all the messages to be delivered

    List<String> dead = new ArrayList<String>();                 // List for all the dead avds at any point of time

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        // ************** From PA 1 *******************
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        // ************** PA2B ************************
        sendPort = myPort;

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

        // **************** From PA 2A **********************************
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
            Log.d("Server", "In Server");

                try {
                    while(true) {
                        Socket clientSocket = serverSocket.accept();
                        Log.d("Server", "Accept Socket");

                        DataInputStream din = new DataInputStream(clientSocket.getInputStream());
                        DataOutputStream dout = new DataOutputStream(clientSocket.getOutputStream());
                        String input1 = null;
                        Message message = new Message();
                        Message toSend = null;

                        // Failure Handling for incoming initial input message
                        try{
                            input1 = din.readUTF();
                            toSend = message.breakMessage(input1);

                            Log.d("Server Msg", input1);
                        }catch (Exception e){
                            Log.d("Server", "No initial message");

                        }

                        // Choosing the proposed sequence to be suggested for the message received
                        if (toSend.msgType.equals("Initial")) {
                            int i = toSend.remotePort;

                            if (agreed_seq[i] > proposed_seq[i]) {
                                proposed_seq[i] = agreed_seq[i] + 1;
                            } else if (agreed_seq[i] <= proposed_seq[i]) {
                                proposed_seq[i]++;
                            }

                            // Adding the message to holding queue
                            toSend.proposed.add(proposed_seq[i]);
                            holding_queue.add(toSend);

                            String p_seq = "Proposed~#~" + Integer.toString(proposed_seq[i]);
                            Log.d("Server Proposed ", p_seq);
                            dout.writeUTF(p_seq);
                        }

                        // Failure Handling for incoming agreed message
                        String input2 = null;
                        Message agreed_msg = null;
                        try {
                            input2 = din.readUTF();
                            agreed_msg = message.breakMessage(input2);
                        }catch (Exception e) {
                            Log.d("SendPort "+sendPort, "Remote Port " + receiver);
                            Log.d("SendPort "+toSend.senderPort, "Remote Port " + RemotePorts[toSend.remotePort]);

                            if(!dead.contains(toSend.senderPort)){
                                dead.add(toSend.senderPort);
                            }

                            Log.d("Dead Update in Server", dead.toString());
                        }


                        // Removing the messages for all dead ports
                        if (!dead.isEmpty()) {
                            for (String deadport : dead) {
                                for (Message msg : holding_queue) {
                                    if (RemotePorts[msg.remotePort].equals(deadport))
                                        holding_queue.remove(msg);
                                }
                            }

                            Log.d("Update to holding queue", dead.toString());

                        }

                        // Updating the holding queue with deliverable messages
                        if (agreed_msg != null && agreed_msg.msgType.equals("Agreed") && agreed_msg.deliverable) {

                            Log.d("Receive & Add msg", agreed_msg.msgText);

                            for(Message msg : holding_queue){
                                if(msg.msgID == agreed_msg.msgID) {
                                    holding_queue.remove(msg);
                                }

                                holding_queue.add(agreed_msg);
                            }

                            int i = agreed_msg.remotePort;
                            agreed_seq[i] = agreed_msg.agreed_sequence;

                        }

                        // Pritning all the messages in holding queue
                        while(!holding_queue.isEmpty()) {

                            Message final_msg = holding_queue.poll();

                            Log.d("Final Printing", final_msg.msgText);

                            publishProgress(final_msg.msgText);
                            try {
                                dout.writeUTF("PA2B OK");
                            }catch (Exception e){
                                Log.d("Exception in Printing", final_msg.msgText);
                            }
                        }

                        din.close();
                        dout.close();
                    }

                } catch (Exception e) {
                    Log.e(TAG, "No input to ServerSocket");

                }
            return null;
        }

        protected void onProgressUpdate(String... strings) {

            Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
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
                    //Initial message origination point
                    messageID++;
                    String msgToSend = msgs[0];
                    Message message = new Message(messageID, "Initial", msgToSend, sendPort);


                    // To save all sockets for future use
                    Socket[] all_sockets = new Socket[5];

                    // B-multicast initial message to all the ports (AVDs)
                    for (int i = 0; i < 5; i++) {
                        if (dead.contains(RemotePorts[i])) {
                            continue;

                        }else {

                            receiver = RemotePorts[i];
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(RemotePorts[i]));
                            all_sockets[i] = socket;

                            message.remotePort = i;
                            String final_msg = message.formMessage();
                            Log.d("Client",final_msg);
                            Log.d("Client - dead list", dead.toString());

                            DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
                            dout.writeUTF(final_msg);

                            DataInputStream din = new DataInputStream(socket.getInputStream());
                            String ack1 = null;

                            // Failure Handling for proposal messages received
                            try {
                                ack1 = din.readUTF();
                                if (ack1.contains("Proposed~#~")) {
                                    String[] p = ack1.split("~#~");
                                    message.proposed.add(Integer.parseInt(p[1]));
                                }
                            }catch (Exception e){
                                Log.d("Client", "Dead in Proposed");
                                Log.d("SendPort "+sendPort, "Remote Port " + receiver);
                                Log.d("SendPort "+message.senderPort, "Remote Port " + RemotePorts[message.remotePort]);

                                if(!dead.contains(receiver)) {
                                    dead.add(receiver);

                                }

                                Log.d("Dead Update in Client", dead.toString());
                            }
                        }
                    }

                    //Selecting the agreed sequence for the message
                    message.agreed_sequence = Collections.max(message.proposed);
                    message.deliverable = true;
                    message.msgType = "Agreed";


                    // B-multicast message with agreed sequence to all the ports
                    for (int i = 0; i < 5; i++) {
                        if (dead.contains(RemotePorts[i]))
                            continue;
                        else {
                            message.remotePort = i;
                            receiver = RemotePorts[i];

                            String final_msg = message.formMessage();
                            Log.d("Client - Agreed", final_msg);

                            DataOutputStream dout = new DataOutputStream(all_sockets[i].getOutputStream());

                            dout.writeUTF(final_msg);

                            DataInputStream din = new DataInputStream(all_sockets[i].getInputStream());

                            String ack2 = null;
                            try {
                                ack2 = din.readUTF();
                                if (ack2.equals("PA2B OK")) {
                                    dout.close();
                                    din.close();
                                    all_sockets[i].close();
                                }
                            }catch (Exception e){
                                Log.d("Client", "Dead in Agreed");
                                Log.d("SendPort "+sendPort, "Remote Port " + receiver);
                                Log.d("SendPort "+message.senderPort, "Remote Port " + RemotePorts[message.remotePort]);

                                if(!dead.contains(receiver)) {
                                    dead.add(receiver);
                                    Log.d("Update Dead", dead.toString());
                                }
                            }

                        }

                    }

            } catch (Exception e) {
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }


    // Message object for all the message packets to be sent

    public class Message implements Comparable<Message>{

        int msgID;                                      // Unique Identifier for each message
        String msgType;                                 // Message Type - Initial or Agreed
        String msgText;                                 // Message text to be sent
        String senderPort;                              // Sender port of the message
        int remotePort;                                 // Remote port of the message
        boolean deliverable;                            // True if message is deliverable
        List<Integer> proposed = new ArrayList<Integer>();    // List of proposed priorities from all remote ports
        int agreed_sequence;                            // final agreed sequence for the message

        public Message(){
            deliverable = false;
        }

        public Message(int ID, String type, String msg, String sender){
            msgID = ID;
            msgType =  type;
            msgText = msg;
            deliverable = false;
            senderPort = sender;
        }

        public String formMessage(){

            String str = Integer.toString(msgID)+"~#~"+msgType+"~#~"+msgText+"~#~"+Boolean.toString(deliverable)+"~#~"+senderPort+"~#~"+Integer.toString(remotePort)+"~#~"+Integer.toString(agreed_sequence);

            return str;
        }

        public Message breakMessage(String str){

            String[] msg = str.split("~#~");
            Message message = new Message();

            message.msgID = Integer.parseInt(msg[0]);
            message.msgType = msg[1];
            message.msgText = msg[2];
            message.deliverable = Boolean.parseBoolean(msg[3]);
            message.senderPort = msg[4];
            message.remotePort = Integer.parseInt(msg[5]);
            message.agreed_sequence = Integer.parseInt(msg[6]);

            return message;

        }

        @Override
        public int compareTo(Message msg) {
            if (this.agreed_sequence < msg.agreed_sequence)
            {
                return -1;
            }
            if (this.agreed_sequence > msg.agreed_sequence)
            {
                return 1;
            }
            return 0;
        }

    }
}
