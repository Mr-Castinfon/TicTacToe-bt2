package com.example.esp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private Button btnStartServer, btnStartClient, btnSendData;
    private TextView tvOutput;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothServerSocket serverSocket;
    private OutputStream outputStream;
    private InputStream inputStream;

    private boolean isConnected = false;
    private final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStartServer = findViewById(R.id.btnStartServer);
        btnStartClient = findViewById(R.id.btnStartClient);
        btnSendData = findViewById(R.id.btnSendData);
        tvOutput = findViewById(R.id.tvOutput);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        btnStartServer.setOnClickListener(v -> startAsServer());
        btnStartClient.setOnClickListener(v -> showDevicePicker());
        btnSendData.setOnClickListener(v -> sendData("Hello from device!\n"));
    }

    private void startAsServer() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 1);
            return;
        }

        // Make discoverable
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);

        new Thread(() -> {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("MyBluetoothApp", SPP_UUID);
                runOnUiThread(() -> Toast.makeText(this, "Waiting for client...", Toast.LENGTH_SHORT).show());

                bluetoothSocket = serverSocket.accept(); // Blocking until client connects
                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();
                isConnected = true;

                runOnUiThread(() -> Toast.makeText(this, "Client connected!", Toast.LENGTH_SHORT).show());
                listenForIncomingData();

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Server error", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showDevicePicker() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.ACCESS_FINE_LOCATION},
                    1);
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "No paired devices found", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> deviceNames = new ArrayList<>();
        for (BluetoothDevice device : pairedDevices) {
            deviceNames.add(device.getName());
        }

        new AlertDialog.Builder(this)
                .setTitle("Select device to connect")
                .setItems(deviceNames.toArray(new String[0]), (dialog, which) -> {
                    String selectedDeviceName = deviceNames.get(which);
                    connectToDevice(selectedDeviceName);
                })
                .show();
    }

    private void connectToDevice(String deviceName) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        BluetoothDevice device = findDeviceByName(bluetoothAdapter.getBondedDevices(), deviceName);
        if (device == null) {
            Toast.makeText(this, "Device not found", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();
                isConnected = true;

                runOnUiThread(() -> Toast.makeText(this, "Connected to server!", Toast.LENGTH_SHORT).show());
                listenForIncomingData();

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void listenForIncomingData() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;

            while (isConnected) {
                try {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        String incoming = new String(buffer, 0, bytes);
                        runOnUiThread(() -> tvOutput.setText("Received: " + incoming.trim()));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }).start();
    }

    private void sendData(String data) {
        if (!isConnected) return;
        try {
            outputStream.write(data.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private BluetoothDevice findDeviceByName(Set<BluetoothDevice> pairedDevices, String name) {
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().equals(name)) return device;
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (bluetoothSocket != null) bluetoothSocket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
