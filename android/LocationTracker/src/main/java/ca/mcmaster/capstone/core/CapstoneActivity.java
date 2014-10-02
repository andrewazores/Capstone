package ca.mcmaster.capstone.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
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
import ca.mcmaster.capstone.R;
import ca.mcmaster.capstone.structures.DeviceInfo;
import ca.mcmaster.capstone.structures.HashableNsdServiceInfo;
import ca.mcmaster.capstone.util.LocalUpdateCallbackReceiver;
import ca.mcmaster.capstone.util.NsdUpdateCallbackReceiver;
import ca.mcmaster.capstone.util.PeerUpdateCallbackReceiver;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CapstoneActivity extends Activity implements LocalUpdateCallbackReceiver<DeviceInfo>,
                                                                          NsdUpdateCallbackReceiver,
                                                                          PeerUpdateCallbackReceiver<DeviceInfo> {

    private final Gson gson = new Gson();

    protected TextView jsonTextView;
    protected ListView listView;
    private final LocationServiceConnection serviceConnection = new LocationServiceConnection();
    private final List<HashableNsdServiceInfo> nsdPeers = new ArrayList<>();
    private Intent serviceIntent;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log("Starting");
        setContentView(R.layout.activity_location);

        serviceIntent = new Intent(this, CapstoneService.class);

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
        stopButton.setOnClickListener(view -> stopLocationService());
    }

    private void getPeerUpdate(final HashableNsdServiceInfo peer) {
        if (serviceConnection.isBound()) {
            serviceConnection.getService().sendHandshakeToPeer(peer);
            serviceConnection.getService().requestUpdateFromPeer(this, peer);
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
        if (!serviceConnection.isBound()) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        jsonTextView.setText("Not connected to Capstone Service");
        nsdUpdate(Collections.<HashableNsdServiceInfo>emptySet());
        stopService(serviceIntent);
        disconnect();
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
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
        startService(serviceIntent);
        getApplicationContext().bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    private void disconnect() {
        if (!serviceConnection.isBound()) {
            log("Service not bound, cannot disconnect again");
            return;
        }
        serviceConnection.getService().unregisterLocationUpdateCallback(CapstoneActivity.this);
        serviceConnection.getService().unregisterNsdUpdateCallback(CapstoneActivity.this);
        getApplicationContext().unbindService(serviceConnection);
    }

    private void updateSelfInfo() {
        if (!serviceConnection.isBound()) {
            Toast.makeText(this, "Service connection not established", Toast.LENGTH_LONG).show();
            log("Service connection not established");
            return;
        }
        final Handler mainHandler = new Handler(this.getMainLooper());
        mainHandler.post(() -> jsonTextView.setText(serviceConnection.getService().getStatusAsJson()));
    }

    private static void log(final String message) {
        Log.v("CapstoneActivity", message);
    }

    private class LocationServiceConnection implements ServiceConnection {

        private CapstoneService service;
        private CapstoneService.CapstoneLocationServiceBinder binder;

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(CapstoneActivity.this, "Service connected", Toast.LENGTH_LONG).show();
            log("Service connected");

            this.binder = (CapstoneService.CapstoneLocationServiceBinder) service;
            this.service = ((CapstoneService.CapstoneLocationServiceBinder) service).getService();
            this.service.registerLocationUpdateCallback(CapstoneActivity.this);
            this.service.registerNsdUpdateCallback(CapstoneActivity.this);
            updateSelfInfo();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Toast.makeText(CapstoneActivity.this, "Service disconnected", Toast.LENGTH_LONG).show();
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
}
