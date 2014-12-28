package ca.mcmaster.capstone.initializer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.mcmaster.capstone.monitoralgorithm.NetworkServiceConnection;
import ca.mcmaster.capstone.networking.CapstoneService;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;

public class Initializer extends Service {
    // FIXME: numPeers will be read in from the input file, but for now is hard coded
    private final int numPeers = 2;
    private NetworkPeerIdentifier localPID;
    private final Map<String, NetworkPeerIdentifier> virtualIdentifiers = new HashMap<>();

    private Intent networkServiceIntent;
    private static final NetworkServiceConnection serviceConnection = new NetworkServiceConnection();

    @Override
    public IBinder onBind(Intent intent) {
        return new InitializerBinder(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        networkServiceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(networkServiceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getApplicationContext().unbindService(serviceConnection);
    }

    public Map<String, NetworkPeerIdentifier> networkInitialization() {
        while (serviceConnection.getNetworkLayer() == null) {
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                Log.d("initializer", "NetworkLayer connection is not established: " + e.getLocalizedMessage());
            }
        }
        localPID = serviceConnection.getNetworkLayer().getLocalNetworkPeerIdentifier();

        Map<String, NetworkPeerIdentifier> virtualIdentifiers = generateVirtualIdentifiers();

        for (final Map.Entry<String, NetworkPeerIdentifier> entry : virtualIdentifiers.entrySet()) {
            Log.v("initializer", entry.getKey() + " - " + entry.getValue());
            if (entry.getValue().equals(localPID)) {
                Log.v("initializer", "I am " + entry.getKey()  + "!");
            }
        }

        return virtualIdentifiers;
    }

    private Map<String, NetworkPeerIdentifier> generateVirtualIdentifiers() {
        final Map<String, NetworkPeerIdentifier> virtualIdentifiers = new HashMap<>();
        while (true) {
            if (serviceConnection.getNetworkLayer().getAllNetworkDevices().size() == numPeers) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                //Don't care
            }
        }
        final List<NetworkPeerIdentifier> sortedIdentifiers = new ArrayList<>(serviceConnection.getNetworkLayer().getAllNetworkDevices());
        Collections.sort(sortedIdentifiers, (f, s) -> Integer.compare(f.hashCode(), s.hashCode()));
        for (final NetworkPeerIdentifier networkPeerIdentifier : sortedIdentifiers) {
            final String virtualIdentifier = "x" + (sortedIdentifiers.indexOf(networkPeerIdentifier) + 1);
            virtualIdentifiers.put(virtualIdentifier, networkPeerIdentifier);
        }
        return virtualIdentifiers;
    }
}
