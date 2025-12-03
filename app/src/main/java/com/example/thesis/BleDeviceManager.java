package com.example.thesis;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Manages BLE discovery, connection and communication for one scale.
 *
 * Responsibilities:
 * - Scan for devices advertising a specific service UUID.
 * - Connect and automatically retry when disconnected.
 * - Subscribe once to the TX characteristic (notifications from scale).
 * - Debounce duplicate notifications that arrive within a short time window.
 * - Handle Bluetooth OFF/ON transitions via BroadcastReceiver.
 * - Expose connection state callbacks for the UI and a helper to send reminders.
 */
public class BleDeviceManager {
    private static final String TAG = "BleDeviceManager";

    private final Activity activity;
    private final String deviceName;
    private final java.util.UUID serviceUuid;
    private final java.util.UUID txUuid; // Notify => phone
    private final java.util.UUID rxUuid; // Write => from phone
    private final int patientIndex;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanCallback leScanCallback;

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic txCharacteristic; // for notifications
    private BluetoothGattCharacteristic rxCharacteristic; // for writes

    private Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isReconnecting = false;

    // Used to filter out duplicate payloads arriving in quick succession.
    private long lastDataTime = 0;
    private String lastDataValue = "";

    // Ensures we only subscribe to notifications once per connection.
    private boolean didSubscribeTx = false;

    // Last known connection status, used by the UI.
    private String lastKnownStatus = "Disconnected";
    private boolean currentlyConnected = false;

    /**
     * Listener for connection status changes so the UI can be updated.
     */
    public interface ConnectionStatusListener {
        void onConnectionStatusChanged(int patientIndex, String status, boolean isConnected);
    }

    private ConnectionStatusListener connectionStatusListener;

    public void setConnectionStatusListener(ConnectionStatusListener listener) {
        this.connectionStatusListener = listener;
    }

    public BleDeviceManager(Activity activity,
                            String deviceName,
                            java.util.UUID serviceUuid,
                            java.util.UUID txUuid,
                            java.util.UUID rxUuid,
                            int patientIndex) {
        this.activity = activity;
        this.deviceName = deviceName;
        this.serviceUuid = serviceUuid;
        this.txUuid = txUuid;
        this.rxUuid = rxUuid;
        this.patientIndex = patientIndex;

        BluetoothManager bm = (BluetoothManager) activity.getSystemService(Activity.BLUETOOTH_SERVICE);
        if (bm != null) {
            bluetoothAdapter = bm.getAdapter();
        }

        // Listen for global Bluetooth state changes (OFF/ON).
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        activity.registerReceiver(bluetoothReceiver, filter);
    }

    // --------------------------------------------------------------------
    // BroadcastReceiver: reacts to Bluetooth being turned OFF/ON
    // --------------------------------------------------------------------
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) return;

            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_ON) {
                Log.d(TAG, "Bluetooth ON => re-scan for " + deviceName);
                startScan();
            } else if (state == BluetoothAdapter.STATE_OFF) {
                Log.d(TAG, "Bluetooth OFF => close GATT for " + deviceName);

                // Defensive check: only stop scan if adapter is still reported as enabled.
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                    stopScan();
                }

                // Close any active GATT connection so we do not keep stale references.
                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }
            }
        }
    };

    // --------------------------------------------------------------------
    // SCANNING
    // --------------------------------------------------------------------
    public void startScan() {
        if (bluetoothAdapter == null) return;
        if (!bluetoothAdapter.isEnabled()) return;

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        leScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                if (result.getScanRecord() != null) {
                    List<android.os.ParcelUuid> uuids = result.getScanRecord().getServiceUuids();
                    if (uuids != null && uuids.contains(new android.os.ParcelUuid(serviceUuid))) {
                        BluetoothDevice device = result.getDevice();
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity,
                                    "Found device: " + deviceName, Toast.LENGTH_SHORT).show();
                            stopScan();
                            connectGatt(device);
                        });
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e(TAG, "Scan failed with error=" + errorCode);
            }
        };

        // Simple feedback that scanning has started.
        activity.runOnUiThread(() ->
                Toast.makeText(activity, "Scanning for: " + deviceName, Toast.LENGTH_SHORT).show()
        );

        // Restrict scan to the service we care about.
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new android.os.ParcelUuid(serviceUuid))
                .build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(filter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        bluetoothLeScanner.startScan(filters, settings, leScanCallback);
    }

    public void stopScan() {
        if (bluetoothLeScanner != null && leScanCallback != null) {
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    // --------------------------------------------------------------------
    // GATT CONNECTION
    // --------------------------------------------------------------------
    private void connectGatt(BluetoothDevice device) {
        if (device == null) return;

        // Close any previous connection before opening a new one.
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        isReconnecting = false;
        bluetoothGatt = device.connectGatt(activity, false, gattCallback,
                BluetoothDevice.TRANSPORT_LE);
    }

    /**
     * Schedules a re-scan and reconnect attempt a few seconds after a disconnect.
     */
    private void scheduleReconnect() {
        lastKnownStatus = "Reconnecting";
        currentlyConnected = false;
        if (connectionStatusListener != null) {
            activity.runOnUiThread(() ->
                    connectionStatusListener.onConnectionStatusChanged(
                            patientIndex, "Reconnecting", false
                    )
            );
        }
        isReconnecting = true;

        reconnectHandler.postDelayed(() -> {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                // Re-scan to find the device again.
                startScan();
            }
        }, 5000);
    }

    // --------------------------------------------------------------------
    // GATT CALLBACK
    // --------------------------------------------------------------------
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            String tmpStatus;
            boolean tmpIsConnected = false;

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                tmpStatus = "Connected";
                tmpIsConnected = true;
                isReconnecting = false;
                reconnectHandler.removeCallbacksAndMessages(null);
                didSubscribeTx = false; // always resubscribe on a fresh connection

                activity.runOnUiThread(() ->
                        Toast.makeText(activity, deviceName + " Connected", Toast.LENGTH_SHORT).show()
                );

                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                tmpStatus = "Disconnected";
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, deviceName + " Disconnected", Toast.LENGTH_SHORT).show()
                );

                if (!isReconnecting) {
                    scheduleReconnect();
                }
            } else {
                tmpStatus = "Unknown State " + newState;
            }

            lastKnownStatus = tmpStatus;
            currentlyConnected = tmpIsConnected;

            // Push status to UI.
            if (connectionStatusListener != null) {
                final String finalMsg = tmpStatus;
                final boolean finalConnected = tmpIsConnected;
                activity.runOnUiThread(() ->
                        connectionStatusListener.onConnectionStatusChanged(patientIndex, finalMsg, finalConnected)
                );
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) return;

            BluetoothGattService service = gatt.getService(serviceUuid);
            if (service == null) return;

            txCharacteristic = service.getCharacteristic(txUuid); // NOTIFY => app
            rxCharacteristic = service.getCharacteristic(rxUuid); // WRITE => scale

            // Subscribe to notifications from the TX characteristic once per connection.
            if (txCharacteristic != null && !didSubscribeTx) {
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                gatt.setCharacteristicNotification(txCharacteristic, true);

                BluetoothGattDescriptor cccDesc = txCharacteristic.getDescriptor(
                        java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                if (cccDesc != null) {
                    cccDesc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(cccDesc);
                    didSubscribeTx = true;
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(txUuid)) {
                final String val = new String(characteristic.getValue(),
                        StandardCharsets.UTF_8);
                Log.d(TAG, deviceName + " => " + val);

                // Basic debounce: drop identical packets within 500 ms.
                long now = System.currentTimeMillis();
                if (val.equals(lastDataValue) && (now - lastDataTime < 500)) {
                    Log.w(TAG, "Skipping duplicate data: " + val);
                    return;
                }
                lastDataValue = val;
                lastDataTime = now;

                parseScaleData(val);
            }
        }
    };

    // --------------------------------------------------------------------
    // Parse payload from the scale and update DataManager
    // --------------------------------------------------------------------
    private void parseScaleData(String data) {
        // expected format: "I 45.23 x"
        String[] tokens = data.split("\\s+");
        if (tokens.length < 3) return;

        String type = tokens[0];  // "I" or "R"
        String amtStr = tokens[1];
        String cup = tokens[2];

        try {
            float amt = Float.parseFloat(amtStr);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()).format(new Date());

            // Add event to DataManager => triggers backup and UI refresh.
            WaterEvent ev = new WaterEvent(timestamp, type, amt, cup);
            DataManager.getInstance(activity).addWaterEvent(patientIndex, ev);

        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    // --------------------------------------------------------------------
    // SEND REMINDER => write a single byte (1) to RX characteristic
    // --------------------------------------------------------------------
    public void sendReminder() {
        if (rxCharacteristic == null || bluetoothGatt == null) return;
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        rxCharacteristic.setValue(new byte[]{1});
        bluetoothGatt.writeCharacteristic(rxCharacteristic);
    }

    // --------------------------------------------------------------------
    // Public accessors and cleanup
    // --------------------------------------------------------------------
    public String getLastKnownStatus() {
        return lastKnownStatus;
    }

    public boolean isCurrentlyConnected() {
        return currentlyConnected;
    }

    /**
     * Releases BLE resources. Should be called from MainActivity.onDestroy().
     */
    public void cleanup() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
        }
        try {
            activity.unregisterReceiver(bluetoothReceiver);
        } catch (Exception ignored) {
        }
    }
}
