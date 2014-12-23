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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import ca.mcmaster.capstone.R;
import ca.mcmaster.capstone.monitoralgorithm.Monitor;
import ca.mcmaster.capstone.monitoralgorithm.MonitorBinder;
import ca.mcmaster.capstone.networking.structures.DeviceInfo;
import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;
import ca.mcmaster.capstone.networking.util.NsdUpdateCallbackReceiver;
import ca.mcmaster.capstone.networking.util.PeerUpdateCallbackReceiver;
import ca.mcmaster.capstone.networking.util.SensorUpdateCallbackReceiver;

import static ca.mcmaster.capstone.networking.util.JsonUtil.asJson;

public class CapstoneActivity extends Activity implements SensorUpdateCallbackReceiver<DeviceInfo>,
                                                                          NsdUpdateCallbackReceiver,
                                                                          PeerUpdateCallbackReceiver<DeviceInfo> {

    protected TextView jsonTextView;
    protected ListView listView;
    private final NetworkServiceConnection networkServiceConnection = new NetworkServiceConnection();
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
            stopNetworkService();
            stopMonitorService();
            disconnect();
        });

        final Button cubeButton = (Button) findViewById(R.id.cube);
        cubeButton.setOnClickListener(v -> {
            if(localNsdServiceInfo != null) {
                Intent i = new Intent(CapstoneActivity.this, CubeActivity.class);
                startActivity(i);
            }
            else
                Toast.makeText(CapstoneActivity.this, "NSD Conenction invalid", Toast.LENGTH_SHORT).show();
        });
    }

    private void getPeerUpdate(final HashableNsdServiceInfo peer) {
        if (networkServiceConnection.isBound()) {
            networkServiceConnection.getService().sendHandshakeToPeer(peer);
            networkServiceConnection.getService().requestUpdateFromPeer(peer, this);
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
                .setMessage(asJson(deviceInfo))
                .create().show();
    }

    private void stopNetworkService() {
        if (!networkServiceConnection.isBound()) {
            Toast.makeText(this, "Network service not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        jsonTextView.setText("Not connected to Network Service");
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
        Toast.makeText(this, "Monitor service stopped", Toast.LENGTH_SHORT).show();
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
        getApplicationContext().bindService(networkServiceIntent, networkServiceConnection, BIND_AUTO_CREATE);
        startService(monitorServiceIntent);
        getApplicationContext().bindService(monitorServiceIntent, monitorServiceConnection, BIND_AUTO_CREATE);
    }

    private void disconnect() {
        if (networkServiceConnection.isBound()) {
            networkServiceConnection.getService().unregisterSensorUpdateCallback(CapstoneActivity.this);
            networkServiceConnection.getService().unregisterNsdUpdateCallback(CapstoneActivity.this);
            attemptUnbind(networkServiceConnection);
        } else {
            log("Network service not bound, cannot disconnect");
        }
        if (monitorServiceConnection.isBound()) {
            attemptUnbind(monitorServiceConnection);
        } else {
            log("Monitor service not bound, cannot disconnect");
        }
    }

    private void attemptUnbind(final ServiceConnection serviceConnection) {
        try {
            getApplicationContext().unbindService(serviceConnection);
        } catch (final IllegalArgumentException iae) {
            log("Could not unbind service: " + serviceConnection + "; not currently bound. " + iae.getLocalizedMessage());
        }
    }

    private void updateSelfInfo() {
        if (!networkServiceConnection.isBound()) {
            Toast.makeText(this, "Service connection not established", Toast.LENGTH_LONG).show();
            log("Service connection not established");
            return;
        }
        final Handler mainHandler = new Handler(this.getMainLooper());
        mainHandler.post(() -> jsonTextView.setText(networkServiceConnection.getService().getStatusAsJson()));
    }

    private static void log(final String message) {
        Log.v("CapstoneActivity", message);
    }

    public class NetworkServiceConnection implements ServiceConnection {

        private CapstoneService service;
        private CapstoneService.CapstoneNetworkServiceBinder binder;

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(CapstoneActivity.this, "Network service connected", Toast.LENGTH_LONG).show();
            log("Service connected");

            this.binder = (CapstoneService.CapstoneNetworkServiceBinder) service;
            this.service = ((CapstoneService.CapstoneNetworkServiceBinder) service).getService();
            this.service.registerSensorUpdateCallback(CapstoneActivity.this);
            this.service.registerNsdUpdateCallback(CapstoneActivity.this);
            CapstoneActivity.this.localNsdServiceInfo = HashableNsdServiceInfo.get(this.service.getLocalNsdServiceInfo());
            updateSelfInfo();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Toast.makeText(CapstoneActivity.this, "Network service disconnected", Toast.LENGTH_LONG).show();
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

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(CapstoneActivity.this, "Monitor service connected", Toast.LENGTH_LONG).show();
            log("Service connected");

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
