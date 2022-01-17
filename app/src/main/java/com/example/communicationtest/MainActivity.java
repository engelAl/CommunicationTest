package com.example.communicationtest;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.icu.text.IDNA;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import in.unicodelabs.kdgaugeview.KdGaugeView;


public class MainActivity extends AppCompatActivity {

    //Variable declaration
    private String deviceAddress = null;
    public static Handler handler;
    public static BluetoothSocket mmSocket;
    public static ConnectedThread connectedThread;
    public static CreateConnectThread createConnectThread;

    // The following variables used in bluetooth handler to identify message status
    private final static int CONNECTION_STATUS = 1;
    private final static int Multi_READ = 2;
    private final static int Single_READ = 3;
    private final static int Error_READ = 4;

    private long millis1 = 0;
    private long millis2 = 0;
    private long timeDiff = 0;
    private int count = 0;
    double rotations1 = 0;
    double rotations2 = 0;
    double value;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Instantiate UI
        final KdGaugeView speedMeterView = (KdGaugeView) findViewById(R.id.speedMeter);
        final TextView bluetoothStatus = findViewById(R.id.textBluetoothStatus);
        Button btnConnect = findViewById(R.id.btnConnect);
        Button btnDisconnect= findViewById(R.id.btnDisconnect);
        final TextView txtMultiValue = findViewById(R.id.txtMultiValue);
        final TextView txtSingleValue = findViewById(R.id.txtSingleValue);

        // Code for the "Connect" button
        btnConnect.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // This is the code to move to another screen
                Intent intent = new Intent(MainActivity.this, SelectDeviceActivity.class);
                startActivity(intent);
            }
        });

        // Get Device Address from SelectDeviceActivity.java to create connection
        deviceAddress = getIntent().getStringExtra("deviceAddress");

        // If  device is found
        if (deviceAddress != null){
            bluetoothStatus.setText(
                    "Connecting...");
            /*
            This is the most important piece of code.
            When "deviceAddress" is found, the code will call the create connection thread
            to create bluetooth connection to the selected device using the device Address
             */
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            createConnectThread = new CreateConnectThread(bluetoothAdapter,deviceAddress);
            createConnectThread.start();
        }

        /*
        Second most important piece of Code.
        This handler is used to update the UI whenever a Thread produces a new output
        and passes through the values to Main Thread
         */
        handler = new Handler(Looper.getMainLooper()) {

            @Override
            public void handleMessage(Message msg){
                String statusText;
                switch (msg.what){
                    // If the updates come from the Thread to Create Connection
                    case CONNECTION_STATUS:
                        switch(msg.arg1){
                            case 1:
                                bluetoothStatus.setText("Bluetooth Connected");
                                break;
                            case -1:
                                bluetoothStatus.setText("Connection Failed");
                                break;
                        }
                        break;
                    // If the updates come from the Thread for Data Exchange
                    case Multi_READ:
                        statusText = msg.obj.toString().replace("~","");
                        int multiRead = (int) Double.parseDouble(statusText);
                        txtMultiValue.setText(Integer.toString(multiRead));
                        break;
                    case Single_READ:
                        statusText = msg.obj.toString().replace("#","");
                        String[] staText = statusText.split("-");
//                        double singleRead = Double.parseDouble(statusText);

                        double singleRead = Double.parseDouble(staText[0]);
                        int rotDirection = (int)Double.parseDouble(staText[1]);

                        // After starting the app current System Time in milliseconds is being recorded
                        if(millis1 == 0){
                            millis1 = System.currentTimeMillis();
                            // Speed is set to 0 rpm
                            value = 0;
                            speedMeterView.setSpeed((float)value);
                            // first received single turn value is saved
                            rotations1 = singleRead;
                        } else {
                            // count is used to reduce the update speed of gauge
                            if (count == 100) {
                                millis2 = millis1;
                                // new current System Time is recorded
                                millis1 = System.currentTimeMillis();
                                // time difference between new and old System Time is calculated
                                if(millis1 >= millis2) {
                                    //timeDiff = (Math.abs(millis1 - millis2));
                                    timeDiff = millis1 - millis2;
                                } else {
                                    long max = 9223372036854775807L;
                                    timeDiff = (max - millis2) + millis1;
                                }
                                rotations2 = rotations1;
                                // New single turn value is saved
                                rotations1 = singleRead;
                                // difference between new angle and old angle are calculated
                                // difference is calculated into rotations


                                double  rotationsDiff;
                                if(((rotations1 - rotations2) > 0) && (rotDirection == 1)){
                                  rotationsDiff = (rotations1 - rotations2)/ 360.0;
                                } else if(((rotations1 - rotations2) < 0) && (rotDirection == 1)){
                                    rotationsDiff = (rotations1 + (rotations2 - 360.0))/ 360.0;
                                } else if(((rotations1 - rotations2) < 0) && (rotDirection == 0)){
                                    rotationsDiff = (rotations2 - rotations1)/360.0;
                                } else if(((rotations1 - rotations2) > 0) && (rotDirection == 0)){
                                    rotationsDiff = (rotations2 + (360.0 - rotations1))/360.0;
                                } else {
                                    rotationsDiff = 0;
                                }


//                                double  rotationsDiff = Math.abs((rotations1 - rotations2)) / 360.0;
                                // calculation of rpm
                                double sec2min = 1.0/60.0;
                                value = rotationsDiff/(timeDiff*0.001*sec2min);
                                count = 1;
                            }
                            count = count + 1;
                        }

                        txtSingleValue.setText(Double.toString(singleRead)+"°");
                        //txtSingleValue.setText(Integer.toString(rotDirection)+"°");
                        speedMeterView.setSpeed((float)value);
                        break;
                    case Error_READ:
                        statusText = "Restart Arduino!";
                        txtMultiValue.setText(statusText);
                        txtSingleValue.setText(statusText);
                }
            }
        };

        // Code for the disconnect button
        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (createConnectThread != null){
                    createConnectThread.cancel();
                    bluetoothStatus.setText("Bluetooth is Disconnected");
                }
            }
        });

    }

    /* ============================ Thread to Create Connection ================================= */
    public static class CreateConnectThread extends Thread {

        public CreateConnectThread(BluetoothAdapter bluetoothAdapter, String address) {
            // Opening connection socket with the Arduino board
            BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
            BluetoothSocket tmp = null;
            UUID uuid = bluetoothDevice.getUuids()[0].getUuid();
            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                Log.e("Create() method failed", e.toString());
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            bluetoothAdapter.cancelDiscovery();
            try {
                // Connect to the Arduino board through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
                handler.obtainMessage(CONNECTION_STATUS, 1, -1).sendToTarget();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                    handler.obtainMessage(CONNECTION_STATUS, -1, -1).sendToTarget();
                } catch (IOException closeException) { }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            // Calling for the Thread for Data Exchange (see below)
            connectedThread = new ConnectedThread(mmSocket);
            connectedThread.run();
        }

        // Closes the client socket and causes the thread to finish.
        // Disconnect from Arduino board
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    /* =============================== Thread for Data Exchange ================================= */
    public static class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        // Getting Input and Output Stream when connected to Arduino Board
        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        // Read message from Arduino device and send it to handler in the Main Thread
        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes = 0; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    buffer[bytes] = (byte) mmInStream.read();
                    String arduinoMsg = null;

                    // Parsing the incoming data stream
                    //if (buffer[bytes] == '~' || buffer[bytes] == '#'){
                    if (buffer[bytes] == '~' || buffer[bytes] == '#' || buffer[bytes] == ']'){
                        arduinoMsg = new String(buffer,0,bytes);
                        Log.e("Arduino Message",arduinoMsg);
                        //if(buffer[bytes] == '~'){
                        //    handler.obtainMessage(Multi_READ,arduinoMsg).sendToTarget();
                        //} else {
                        //    handler.obtainMessage(Single_READ,arduinoMsg).sendToTarget();
                        switch (buffer[bytes]){
                            case '~':
                                handler.obtainMessage(Multi_READ,arduinoMsg).sendToTarget();
                                break;
                            case '#':
                                handler.obtainMessage(Single_READ,arduinoMsg).sendToTarget();
                                break;
                            case ']':
                                handler.obtainMessage(Error_READ).sendToTarget();
                        }
                        bytes = 0;
                    }
                    else {
                        bytes++;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

    }
}