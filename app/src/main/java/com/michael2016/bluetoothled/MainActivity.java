package com.michael2016.bluetoothled;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final String TAG = "DEBUG_TAG";
    private static final int TURN_LED_ON = 1;
    private static final int TURN_LED_OFF = 0;

    private static boolean connectionState = false;

    private ConnectThread mConnectThread;
    private BluetoothSocket mBluetoothSocket;
    private boolean socketWasConnected = false;

    private OutputStream out;

    private Button mOnButton;
    private Button mOffButton;
    private Button mShowBTDevices;

    private ListView mList;

    private TextView mConnectedDevice;

    private Handler mHandler;
    private BluetoothAdapter mBluetoothAdapter;
    private Set<BluetoothDevice> devices;
    private ArrayList<String> names;
    private BluetoothDevice chosenDevice;

    // Defines several constants used when transmitting messages between the
    // service and the UI.
    private interface MessageConstants {
        int MESSAGE_READ = 0;
        int MESSAGE_WRITE = 1;
        int MESSAGE_TOAST = 2;

        // ... (Add other message types here as needed.)
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar myToolbar = (Toolbar)findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        myToolbar.setTitle("Bluetooth LED");
        myToolbar.setBackgroundColor(Color.rgb(6, 6, 105));
        myToolbar.setTitleTextColor(Color.rgb(255, 255, 255));


        mConnectedDevice = (TextView)findViewById(R.id.connectedDevice_textview);
        mConnectedDevice.setVisibility(View.INVISIBLE);

        mList = (ListView) findViewById(R.id.devices_list);
        mList.setVisibility(View.INVISIBLE);

        mOnButton = (Button)findViewById(R.id.on_button);
        mOffButton = (Button)findViewById(R.id.off_button);
        mShowBTDevices = (Button)findViewById(R.id.BTDevices_button);
        mShowBTDevices.setText(R.string.showBTDevices);
        // get the bluetooth adapter.
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();


        isEnabled();
        createList();



        mShowBTDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mList.getVisibility() == View.INVISIBLE) {
                    mList.setVisibility(View.VISIBLE);
                    mShowBTDevices.setText(R.string.hideBTDevices);
                }
                else {
                    mList.setVisibility(View.INVISIBLE);
                    mShowBTDevices.setText(R.string.showBTDevices);
                }
            }
        });

        mList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String name = (String) mList.getItemAtPosition(position);
                chooseDevice(name);
                mConnectedDevice.setText(chosenDevice.getName());
                mConnectedDevice.setVisibility(View.VISIBLE);

                if(!connectionState)
                    connect(chosenDevice);
                else{
                    try{
                        out.close();
                        mBluetoothSocket.close();
                        pushToast("Disconnected.", 0);
                    }catch(IOException e){
                        Log.e(TAG, "Could not close output stream.", e);
                    }
                }

                if(mBluetoothSocket != null)
                    connectionState = mBluetoothSocket.isConnected();
            }
        });

        mOnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Sending data via Turn On Button...");

                if(mBluetoothSocket != null && mBluetoothSocket.isConnected()) {
                    try {
                        OutputStream tmpOut = mBluetoothSocket.getOutputStream();
                        tmpOut.write(TURN_LED_ON);
                    } catch (IOException e) {
                        Log.e(TAG, "Error occurred while creating output stream.", e);
                    }
                }else{
                    pushToast("You must connect to device first.", 0);
                }
            }
        });

        mOffButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mBluetoothSocket != null && mBluetoothSocket.isConnected()) {
                    try {
                        Log.d(TAG, "Sending data via Turn Off Button...");
                        out = mBluetoothSocket.getOutputStream();
                        out.write(TURN_LED_OFF);
                    } catch (IOException e) {
                        Log.e(TAG, "Error occurred while creating output stream.", e);
                    }
                }
                else{
                    pushToast("You must connect to device first.", 0);
                }
            }
        });

    } // end onCreate

    /*
        Simple method to show a toast.
     */
    private void pushToast(String msg, int length){
        Toast.makeText(getApplicationContext(),
                msg, length == 0 ? Toast.LENGTH_SHORT:Toast.LENGTH_LONG).show();
    }

    public synchronized void connect(BluetoothDevice d){

        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }

        mConnectThread = new ConnectThread(d);
        mConnectThread.start();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

    }

    private void chooseDevice(String name){
        for(BluetoothDevice d: devices){
            if(name.equals(d.getName()))
                chosenDevice = d;
        }
    }

    /*
        asks permission to make device visible
     */
    private void visible(){
        Intent getVisible = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        startActivityForResult(getVisible, 0);
    }

    /*
        checks if bluetooth is enabled.
     */
    private boolean isEnabled(){
        if(!mBluetoothAdapter.isEnabled()){
            // This intent starts an activity to enable bluetooth.
            Intent turnOnBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnOnBluetooth, 0);
            return false;
        }
        else{
            pushToast("Bluetooth is already on.", 0);
            return true;
        }
    }

    private void createList(){
        // get all the current;y paired devices and store them in a set.
        devices = mBluetoothAdapter.getBondedDevices();
        names = new ArrayList<>();

        for(BluetoothDevice d: devices){
            names.add(d.getName());
        }

        final ArrayAdapter adapter =
                new ArrayAdapter(this, R.layout.listview_components, R.id.text1, names);

        mList.setAdapter(adapter);

    }



    private class ConnectThread extends Thread{

        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;


        public ConnectThread(BluetoothDevice device){
            Log.d(TAG, "Connect Thread...");

            // Going to use a temp obj that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket temp = null;
            mmDevice = device;

            try{
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                temp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            }catch (IOException e){
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = temp;
        }// end constructor

        public void run(){
            // Cancel discovery since it slows down the connection.
            mBluetoothAdapter.cancelDiscovery();

            try{
                Log.d(TAG, "Trying to connect...");
                // Connect to the remote device through the socket.
                // This call blocks until it succeeds or throws an exception.
                mmSocket.connect();
                Log.d(TAG, "Connection Successful!");
                MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        pushToast("Connection successful!", 0);
                        socketWasConnected = true;
                    }
                });
            }catch(IOException connectException){
                // Unable to connect; close the socket and return.
                Log.d(TAG, "Unable to connect...");
                MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        pushToast("Connection failed.", 0);
                    }
                });
                try{
                    mmSocket.close();
                }catch(IOException closeException){
                    Log.e(TAG, "Could not close the client socket.", closeException);
                }
                return;
            }// end catching connectException

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            mBluetoothSocket = mmSocket;
        } // end run

        // Closes the client socket and causes the thread to finish
        public void cancel(){
            try{
                mmSocket.close();
            }catch(IOException closeException){
                Log.e(TAG, "Could not close the client socket.", closeException);
            }
        }
    }  // end ConnectThread

    /*
    private class ConnectedThread extends Thread{

        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream.

        public ConnectedThread(BluetoothSocket socket){
            Log.d(TAG, "Connected Thread...");
            if(socket == null)
                Log.d(TAG, "socket == null");

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects
            // because member streams are final./
            try{
                tmpIn = socket.getInputStream();
            }catch(IOException e){
                Log.e(TAG, "Error occurred while creating input stream.", e);
            }

            try{
                tmpOut = socket.getOutputStream();
            }catch(IOException e){
                Log.e(TAG, "Error occurred while creating output stream.", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        } // end constructor


        public void run(){
            mmBuffer = new byte[1024];
            int numBytes; // bytes returned from read().

            // Keep listening to the InputStream until an exception occurs.
            while(true){
                try{
                    // Read from the InputStream
                    numBytes = mmInStream.read(mmBuffer);
                    Message readMsg = mHandler.obtainMessage(
                            MessageConstants.MESSAGE_READ, numBytes, -1, mmBuffer);
                    readMsg.sendToTarget();
                }catch(IOException e){
                    Log.d(TAG, "InputStream was disconnected.", e);
                    break;
                }
            }// end while
        }// end run

        // Call this from the main activity to send data to remote device.
        public void write(byte[] bytes){
            try{
                mmOutStream.write(bytes);

                // Share the sent message with the UI activity.
                Message writtenMsg = mHandler.obtainMessage(
                        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
                writtenMsg.sendToTarget();
            }catch(IOException e){
                Log.e(TAG, "Error occurred while sending data.", e);

                // Send a failure message back to the activity.
                Message writeErrorMsg =
                        mHandler.obtainMessage(MessageConstants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast", "Couldn't send data to other device.");
                writeErrorMsg.setData(bundle);
                mHandler.sendMessage(writeErrorMsg);
            }
        }// end write

        public void cancel(){
            try{
                mmSocket.close();
            }catch(IOException e){
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }   // end ConnectedThread
    */


} // end MainActivity

