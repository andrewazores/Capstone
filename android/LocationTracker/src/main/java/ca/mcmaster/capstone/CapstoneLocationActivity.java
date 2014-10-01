package ca.mcmaster.capstone;

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

public class CapstoneLocationActivity extends Activity implements LocalUpdateCallbackReceiver<DeviceInfo>,
                                                                NsdUpdateCallbackReceiver,
                                                                PeerUpdateCallbackReceiver<DeviceInfo> {

    private final Gson gson = new Gson();

    protected TextView jsonTextView;
    protected ListView listView;
    private CapstoneLocationService capstoneLocationService;
    private volatile boolean serviceBound;
    private final LocationServiceConnection serviceConnection = new LocationServiceConnection();
    public static final Intent SERVICE_INTENT = new Intent("ca.mcmaster.capstone.CapstoneLocationService");
    private final List<HashableNsdServiceInfo> nsdPeers = new ArrayList<>();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log("Starting");
        setContentView(R.layout.activity_location);

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
        if (serviceBound && capstoneLocationService != null) {
            capstoneLocationService.identifySelfToPeer(peer);
            capstoneLocationService.requestUpdateFromPeer(this, peer);
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
            CapstoneLocationActivity.this.nsdPeers.clear();
            CapstoneLocationActivity.this.nsdPeers.addAll(nsdPeers);
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
        if (!serviceBound || capstoneLocationService == null) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        serviceBound = false;
        jsonTextView.setText("Not connected to Capstone Service");
        nsdUpdate(Collections.<HashableNsdServiceInfo>emptySet());
        disconnect();
        stopService(new Intent(this, CapstoneLocationService.class));
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
        if (serviceBound) {
            log("Already bound, cannot reconnect");
            return;
        }
        final Intent startSticky = new Intent(this, CapstoneLocationService.class);
        startService(startSticky);
        bindService(SERVICE_INTENT, serviceConnection, BIND_AUTO_CREATE);
    }

    private void disconnect() {
        if (!serviceBound || capstoneLocationService == null) {
            log("Service not bound, cannot disconnect again");
            return;
        }
        capstoneLocationService.unregisterLocationUpdateCallback(CapstoneLocationActivity.this);
        capstoneLocationService.unregisterNsdUpdateCallback(CapstoneLocationActivity.this);
        unbindService(serviceConnection);
    }

    private void updateSelfInfo() {
        if (!serviceBound || capstoneLocationService == null) {
            Toast.makeText(this, "Service connection not established", Toast.LENGTH_LONG).show();
            log("Service connection not established");
            return;
        }
        final Handler mainHandler = new Handler(this.getMainLooper());
        mainHandler.post(() -> jsonTextView.setText(capstoneLocationService.getStatusAsJson()));
    }

    private static void log(final String message) {
        Log.v("CapstoneActivity", message);
    }

    private class LocationServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(CapstoneLocationActivity.this, "Service connected", Toast.LENGTH_LONG).show();
            log("Service connected");

            if (capstoneLocationService == null) {
                capstoneLocationService = ((CapstoneLocationService.CapstoneLocationServiceBinder) service).getService();
                capstoneLocationService.registerLocationUpdateCallback(CapstoneLocationActivity.this);
                capstoneLocationService.registerNsdUpdateCallback(CapstoneLocationActivity.this);
                updateSelfInfo();
            }
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Toast.makeText(CapstoneLocationActivity.this, "Service disconnected", Toast.LENGTH_LONG).show();
            log("Service disconnected");
            capstoneLocationService = null;
            serviceBound = false;
        }
    }
}
