package com.example.thesis;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentTransaction;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.navigation.NavigationView;

/**
 * Main entry activity hosting the navigation drawer and all fragments.
 * Wires up DataManager, BLE managers and navigation between central and
 * per-patient views.
 */
public class MainActivity extends AppCompatActivity
        implements CentralFragment.CentralListener,
        BleDeviceManager.ConnectionStatusListener,
        PatientFragment.PatientFragmentListener {

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private NavigationView navigationView;

    // Each scale has its own BleDeviceManager instance.
    private BleDeviceManager scale1Manager, scale2Manager, scale3Manager;

    // Permission request code for BLE-related permissions.
    private static final int REQ_BLE_PERMISSIONS = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize DataManager once for the entire app.
        DataManager.getInstance(getApplicationContext());

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);

        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout,
                R.string.app_name, R.string.app_name);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_central) {
                showCentralFragment();
            } else if (itemId == R.id.menu_patient0) {
                showPatientFragment(0);
            } else if (itemId == R.id.menu_patient1) {
                showPatientFragment(1);
            } else if (itemId == R.id.menu_patient2) {
                showPatientFragment(2);
            }
            drawerLayout.closeDrawers();
            return true;
        });

        checkBlePermissions();

        // Start on central fragment.
        if (savedInstanceState == null) {
            showCentralFragment();
        }
    }

    // ------------------------------------------------------------------
    // PERMISSIONS
    // ------------------------------------------------------------------
    private void checkBlePermissions() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQ_BLE_PERMISSIONS);
        } else {
            initBluetoothManagers();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BLE_PERMISSIONS) {
            boolean allGranted = true;
            for (int g : grantResults) {
                if (g != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initBluetoothManagers();
            } else {
                Toast.makeText(this, "BLE permissions denied!", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ------------------------------------------------------------------
    // BLE INITIALIZATION
    // ------------------------------------------------------------------
    private void initBluetoothManagers() {
        BluetoothManager bm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_LONG).show();
            return;
        }

        // Create three managers for three scales.
        scale1Manager = new BleDeviceManager(
                this,
                "Scale_Clinical1",
                BleUuids.SCALE1_SERVICE_UUID,
                BleUuids.SCALE1_TX_CHAR_UUID,
                BleUuids.SCALE1_RX_CHAR_UUID,
                0 // patientIndex=0
        );
        scale2Manager = new BleDeviceManager(
                this,
                "Scale_Clinical2",
                BleUuids.SCALE2_SERVICE_UUID,
                BleUuids.SCALE2_TX_CHAR_UUID,
                BleUuids.SCALE2_RX_CHAR_UUID,
                1
        );
        scale3Manager = new BleDeviceManager(
                this,
                "Scale_Clinical3",
                BleUuids.SCALE3_SERVICE_UUID,
                BleUuids.SCALE3_TX_CHAR_UUID,
                BleUuids.SCALE3_RX_CHAR_UUID,
                2
        );

        // Listen for connection status updates so the central fragment can be refreshed.
        scale1Manager.setConnectionStatusListener(this);
        scale2Manager.setConnectionStatusListener(this);
        scale3Manager.setConnectionStatusListener(this);

        // Start scanning for all three devices.
        scale1Manager.startScan();
        scale2Manager.startScan();
        scale3Manager.startScan();
    }

    // ------------------------------------------------------------------
    // CLEANUP when Activity is destroyed
    // ------------------------------------------------------------------
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Ensure all BLE resources are released.
        if (scale1Manager != null) {
            scale1Manager.cleanup();
        }
        if (scale2Manager != null) {
            scale2Manager.cleanup();
        }
        if (scale3Manager != null) {
            scale3Manager.cleanup();
        }
    }

    // ------------------------------------------------------------------
    // Accessor: CentralFragment asks for a manager by index
    // ------------------------------------------------------------------
    public BleDeviceManager getScaleManager(int index) {
        if (index == 0) return scale1Manager;
        if (index == 1) return scale2Manager;
        if (index == 2) return scale3Manager;
        return null;
    }

    // ------------------------------------------------------------------
    // FRAGMENT NAVIGATION
    // ------------------------------------------------------------------
    private void showCentralFragment() {
        CentralFragment fragment = CentralFragment.newInstance();
        fragment.setCentralListener(this);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragment_container, fragment, "CentralFragment");
        ft.commit();
    }

    private void showPatientFragment(int patientIndex) {
        PatientFragment fragment = PatientFragment.newInstance(patientIndex);
        fragment.setPatientFragmentListener(this);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragment_container, fragment, "PatientFragment" + patientIndex);
        ft.commit();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------
    // INTERFACES
    // ------------------------------------------------------------------

    // 1) CentralFragment.CentralListener => user pressed a "Remind" button.
    @Override
    public void onRemindButtonClicked(int scaleIndex) {
        if (scaleIndex == 0 && scale1Manager != null) {
            scale1Manager.sendReminder();
        } else if (scaleIndex == 1 && scale2Manager != null) {
            scale2Manager.sendReminder();
        } else if (scaleIndex == 2 && scale3Manager != null) {
            scale3Manager.sendReminder();
        }
    }

    // 2) BleDeviceManager.ConnectionStatusListener => update central UI.
    @Override
    public void onConnectionStatusChanged(int patientIndex, String status, boolean isConnected) {
        CentralFragment central = (CentralFragment) getSupportFragmentManager()
                .findFragmentByTag("CentralFragment");
        if (central != null) {
            central.updateConnectionStatus(patientIndex, status, isConnected);
        }
    }

    // 3) PatientFragment.PatientFragmentListener => clearing all backups from the menu.
    @Override
    public void onClearAllBackupsSelected() {
        DataManager.getInstance(getApplicationContext()).clearAllBackups();
        Toast.makeText(this, "All backups cleared", Toast.LENGTH_SHORT).show();
    }
}
