package com.example.simplebluetoothchat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.example.simplebluetoothchat.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    Button listen, send, listDevices, discover;
    ListView listView;
    TextView msgBox, status;
    EditText write;
    SendReceive sendReceive;
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    Set<BluetoothDevice> boundedDevices = bluetoothAdapter.getBondedDevices();
    ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();
    ArrayList<String> devicesName = new ArrayList<>();

    private static final int REQUEST_CODE_PERMISSION = 0 ;
    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;

    int REQUEST_ENABLE_BLUETOOTH = 1;

    private  final String APP_NAME = "TEST";
    private  final UUID MY_UUID = UUID.fromString("6c25f1ae-9cb4-4358-9030-43c1839ed425");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(myReceiver, intentFilter);

        checkPermission();
        findViewByIdeas();

        if (!bluetoothAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        }
        implementListeners();
    }

    final BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            status.setText("Discovering");
            String action = intent.getAction();
            BluetoothDevice device;
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                bluetoothDevices.add(device);
                devicesName.add(device.getName());
                listView.deferNotifyDataSetChanged();

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                status.setText("Stop Discovering");

            }
            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>
                    (getApplicationContext(),android.R.layout.simple_list_item_1, devicesName);
            listView.setAdapter(arrayAdapter);
        }
    };

    private void implementListeners() {
        listDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bluetoothDevices.clear();
                devicesName.clear();
                if (boundedDevices.size() > 0) {
                    for (BluetoothDevice device : boundedDevices) {
                        bluetoothDevices.add(device);
                        devicesName.add(device.getName());
                    }
                }
                bluetoothAdapter.startDiscovery();
            }
        });

        discover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent discoverableIntent =
                        new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 60);
                startActivity(discoverableIntent);
            }
        });


        listen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ServerClass serverClass = new ServerClass();
                serverClass.start();
            }
        });
       listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
           @Override
           public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
               ClientClass clientClass = new ClientClass(bluetoothDevices.get(position));
               clientClass.start();

               status.setText("Connecting");
           }
       });

       send.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View v) {
               String string = String.valueOf(write.getText());
               if (status.equals("Connected")) {
                   sendReceive.write(string.getBytes());
               }
               write.setText("");
           }
       });
    }
    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what)
            {
                case STATE_LISTENING:
                    status.setText("Listening");
                    break;
                case STATE_CONNECTING:
                    status.setText("Connecting");
                    break;
                case STATE_CONNECTED:
                    status.setText("Connected");
                    break;
                case STATE_CONNECTION_FAILED:
                    status.setText("Connection Failed");
                    break;
                case STATE_MESSAGE_RECEIVED:
                    byte[] readBuffer = (byte[]) msg.obj;
                    String tempMsg = new String(readBuffer, 0, msg.arg1);
                    msgBox.setText(tempMsg);
                    break;
            }
            return true;
        }
    });

    private void findViewByIdeas() {
        listen = findViewById(R.id.listen);
        send = findViewById(R.id.send);
        listDevices = findViewById(R.id.devicesList);
        listView = findViewById(R.id.list_item);
        msgBox = findViewById(R.id.msgBox);
        status = findViewById(R.id.status);
        write = findViewById(R.id.senMes);
        discover = findViewById(R.id.discoverable);

    }
    
    private class ServerClass extends Thread {
        private BluetoothServerSocket serverSocket;
        
        public ServerClass() {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void run() {
         BluetoothSocket socket = null;

         while(socket == null)
         {
                 try {
                     Message message = Message.obtain();
                     message.what = STATE_CONNECTING;
                     handler.sendMessage(message);

                     socket = serverSocket.accept();
                 } catch (IOException e) {
                     e.printStackTrace();
                     Message message = Message.obtain();
                     message.what = STATE_CONNECTION_FAILED;
                     handler.sendMessage(message);
                 }
                 if (socket != null){
                     Message message = Message.obtain();
                     message.what = STATE_CONNECTED;
                     handler.sendMessage(message);

                     sendReceive = new SendReceive(socket);
                     sendReceive.start();
                     break;
                 }
         }
        }
    }
    private class ClientClass extends Thread {
        private BluetoothDevice device;
        private BluetoothSocket socket;

        public ClientClass(BluetoothDevice currentDevice) {
            device = currentDevice;
            try {
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                socket.connect();
                Message message = Message.obtain();
                message.what = STATE_CONNECTED;
                handler.sendMessage(message);

                sendReceive = new SendReceive(socket);
                sendReceive.start();

            } catch (IOException e) {
                e.printStackTrace();
                Message message = Message.obtain();
                message.what = STATE_CONNECTION_FAILED;
                handler.sendMessage(message);

            }
        }
    }
    private  class SendReceive extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public  SendReceive(BluetoothSocket socet) {
            bluetoothSocket = socet;
            InputStream tempInput = null;
            OutputStream tempOutput = null;

            try {
                tempInput = bluetoothSocket.getInputStream();
                tempOutput = bluetoothSocket.getOutputStream();

            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream = tempInput;
            outputStream = tempOutput;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while(true) {
                try {
                    bytes = inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void checkPermission() {
        int permissionStatus = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);

        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {

        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_CODE_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    checkPermission();
                }
                return;
        }
    }
}
