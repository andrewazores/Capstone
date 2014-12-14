package ca.mcmaster.capstone.networking;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import ca.mcmaster.capstone.R;
import ca.mcmaster.capstone.monitoralgorithm.Monitor;
import ca.mcmaster.capstone.monitoralgorithm.MonitorBinder;
import ca.mcmaster.capstone.networking.structures.DeviceInfo;
import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;
import ca.mcmaster.capstone.networking.util.SensorUpdateCallbackReceiver;
import ca.mcmaster.capstone.networking.util.NsdUpdateCallbackReceiver;
import ca.mcmaster.capstone.networking.util.PeerUpdateCallbackReceiver;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CapstoneActivity extends Activity implements SensorUpdateCallbackReceiver<DeviceInfo>,
                                                                          NsdUpdateCallbackReceiver,
                                                                          PeerUpdateCallbackReceiver<DeviceInfo> {

    private final Gson gson = new Gson();

    protected TextView jsonTextView;
    protected ListView listView;
    private final LocationServiceConnection locationServiceConnection = new LocationServiceConnection();
    private final MonitorServiceConnection monitorServiceConnection = new MonitorServiceConnection();
    private final List<HashableNsdServiceInfo> nsdPeers = new ArrayList<>();
    private Intent networkServiceIntent;
    private Intent monitorServiceIntent;

    public HashableNsdServiceInfo getLocalNsdServiceInfo() {
        return localNsdServiceInfo;
    }

    private HashableNsdServiceInfo localNsdServiceInfo = null;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log("Starting");
        setContentView(R.layout.activity_location);

        networkServiceIntent = new Intent(this, CapstoneService.class);
        monitorServiceIntent = new Intent(this, Monitor.class);

        listView = (ListView) findViewById(R.id.listView);
        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, nsdPeers));
        listView.setOnItemClickListener((adapterView, view, i, l)
                                                -> getPeerUpdate((HashableNsdServiceInfo)listView.getItemAtPosition(i)));

        jsonTextView = (TextView) findViewById(R.id.jsonTextView);

        final Button reconnectButton = (Button) findViewById(R.id.reconnectButton);
        reconnectButton.setOnClickListener(v -> {
            reconnect();
            updateSelfInfo();
            ((ArrayAdapter<DeviceInfo>) listView.getAdapter()).notifyDataSetChanged();
            log("Known peers: " + nsdPeers);
        });

        final Button stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setOnClickListener(view -> {
            disconnect();
            stopLocationService();
            stopMonitorService();
        });

        final Button cubeButton = (Button) findViewById(R.id.cube);
        cubeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(localNsdServiceInfo != null) {
                    Intent i = new Intent(CapstoneActivity.this, CubeActivity.class);
                    startActivity(i);
                }
                else
                    Toast.makeText(CapstoneActivity.this, "NSD Conenction invalid", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getPeerUpdate(final HashableNsdServiceInfo peer) {
        if (locationServiceConnection.isBound()) {
            locationServiceConnection.getService().sendHandshakeToPeer(peer);
            locationServiceConnection.getService().requestUpdateFromPeer(peer, this);
        }
    }

    @Override
    public void update(final DeviceInfo peerInfo) {
        updateSelfInfo();
    }

    @Override
    public void nsdUpdate(final Collection<HashableNsdServiceInfo> nsdPeers) {
        final Handler mainHandler = new Handler(this.getMainLooper());
        mainHandler.post(() -> {
            CapstoneActivity.this.nsdPeers.clear();
            CapstoneActivity.this.nsdPeers.addAll(nsdPeers);
            ((ArrayAdapter<DeviceInfo>) listView.getAdapter()).notifyDataSetChanged();
        });
    }

    @Override
    public void peerUpdate(final DeviceInfo deviceInfo) {
        if (deviceInfo == null) {
            return;
        }
        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(deviceInfo.getIp() + ":" + deviceInfo.getPort())
                .setMessage(gson.toJson(deviceInfo))
                .create().show();
    }

    private void stopLocationService() {
        if (!locationServiceConnection.isBound()) {
            Toast.makeText(this, "Location service not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        jsonTextView.setText("Not connected to Location Service");
        nsdUpdate(Collections.<HashableNsdServiceInfo>emptySet());
        stopService(networkServiceIntent);
        Toast.makeText(this, "Network service stopped", Toast.LENGTH_SHORT).show();
    }

    private void stopMonitorService() {
        if (!monitorServiceConnection.isBound()) {
            Toast.makeText(this, "Monitor service not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        jsonTextView.setText("Not connected to Monitor Service");
        stopService(monitorServiceIntent);
        Toast.makeText(this, "Location service stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        reconnect();
    }

    @Override
    public void onPause() {
        super.onPause();
        disconnect();
    }

    private void reconnect() {
        startService(networkServiceIntent);
        getApplicationContext().bindService(networkServiceIntent, locationServiceConnection, BIND_AUTO_CREATE);
        startService(monitorServiceIntent);
        getApplicationContext().bindService(monitorServiceIntent, monitorServiceConnection, BIND_AUTO_CREATE);
    }

    private void disconnect() {
        if (locationServiceConnection.isBound()) {
            locationServiceConnection.getService().unregisterSensorUpdateCallback(CapstoneActivity.this);
            locationServiceConnection.getService().unregisterNsdUpdateCallback(CapstoneActivity.this);
            getApplicationContext().unbindService(locationServiceConnection);
        } else {
            log("Location service not bound, cannot disconnect");
        }
        if (monitorServiceConnection.isBound()) {
            getApplicationContext().unbindService(monitorServiceConnection);
        } else {
            log("Monitor service not bound, cannot disconnect");
        }
    }

    private void updateSelfInfo() {
        if (!locationServiceConnection.isBound()) {
            Toast.makeText(this, "Service connection not established", Toast.LENGTH_LONG).show();
            log("Service connection not established");
            return;
        }
        final Handler mainHandler = new Handler(this.getMainLooper());
        mainHandler.post(() -> jsonTextView.setText(locationServiceConnection.getService().getStatusAsJson()));
    }

    private static void log(final String message) {
        Log.v("CapstoneActivity", message);
    }

    public class LocationServiceConnection implements ServiceConnection {

        private CapstoneService service;
        private CapstoneService.CapstoneLocationServiceBinder binder;

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(CapstoneActivity.this, "Location service connected", Toast.LENGTH_LONG).show();
            log("Service connected");

            this.binder = (CapstoneService.CapstoneLocationServiceBinder) service;
            this.service = ((CapstoneService.CapstoneLocationServiceBinder) service).getService();
            this.service.registerSensorUpdateCallback(CapstoneActivity.this);
            this.service.registerNsdUpdateCallback(CapstoneActivity.this);
            CapstoneActivity.this.localNsdServiceInfo = HashableNsdServiceInfo.get(this.service.getLocalNsdServiceInfo());
            updateSelfInfo();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Toast.makeText(CapstoneActivity.this, "Location service disconnected", Toast.LENGTH_LONG).show();
            log("Service disconnected");
            this.service = null;
        }

        public boolean isBound() {
            return this.service != null && binder.getClients() > 0;
        }

        public CapstoneService getService() {
            return service;
        }
    }

    public class MonitorServiceConnection implements ServiceConnection {

        private Monitor service;
        private MonitorBinder binder;

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(CapstoneActivity.this, "Monitor service connected", Toast.LENGTH_LONG).show();
            log("Service connected");

            this.binder = (MonitorBinder) service;
            this.service = ((MonitorBinder) service).getMonitor();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Toast.makeText(CapstoneActivity.this, "Monitor service disconnected", Toast.LENGTH_LONG).show();
            log("Service disconnected");
            this.service = null;
        }

        public boolean isBound() {
            return this.service != null;
        }

        public Monitor getService() {
            return service;
        }
    }
}
