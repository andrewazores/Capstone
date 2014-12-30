package ca.mcmaster.capstone.initializer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import ca.mcmaster.capstone.monitoralgorithm.NetworkServiceConnection;
import ca.mcmaster.capstone.networking.CapstoneService;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import ca.mcmaster.capstone.networking.util.NpiUpdateCallbackReceiver;

public class Initializer extends Service {
    private static class NetworkInitializer implements Runnable, NpiUpdateCallbackReceiver {
        private final NetworkServiceConnection serviceConnection;
        private int numPeers;
        private NetworkPeerIdentifier localPID;
        private final Map<String, NetworkPeerIdentifier> virtualIdentifiers = new HashMap<>();
        private final CountDownLatch initializationLatch = new CountDownLatch(1);
        private final CountDownLatch peerCountLatch = new CountDownLatch(1);

        public NetworkInitializer(final int numPeers, final NetworkServiceConnection serviceConnection) {
            this.numPeers = numPeers;
            this.serviceConnection = serviceConnection;
        }

        @Override
        public void npiUpdate(final Collection<NetworkPeerIdentifier> npiPeers) {
            if (npiPeers.size() == numPeers) {
                peerCountLatch.countDown();
            }
        }

        @Override
        public void run() {
            while (serviceConnection.getNetworkLayer() == null) {
                try {
                    Thread.sleep(1000);
                } catch (final InterruptedException e) {
                    Log.d("initializer", "NetworkLayer connection is not established: " + e.getLocalizedMessage());
                }
            }

            localPID = serviceConnection.getNetworkLayer().getLocalNetworkPeerIdentifier();
            this.virtualIdentifiers.putAll(generateVirtualIdentifiers());

            for (final Map.Entry<String, NetworkPeerIdentifier> entry : virtualIdentifiers.entrySet()) {
                Log.v("initializer", entry.getKey() + " - " + entry.getValue());
                if (entry.getValue().equals(localPID)) {
                    Log.v("initializer", "I am " + entry.getKey()  + "!");
                }
            }
            initializationLatch.countDown();
        }

        private void waitForLatch(final CountDownLatch latch) {
            while (latch.getCount() > 0) {
                try {
                    latch.await();
                } catch (final InterruptedException ie) {
                    // don't really care, just need to try again
                }
            }
        }

        private Map<String, NetworkPeerIdentifier> generateVirtualIdentifiers() {
            waitForLatch(peerCountLatch);

            final Map<String, NetworkPeerIdentifier> virtualIdentifiers = new HashMap<>();
            final List<NetworkPeerIdentifier> sortedIdentifiers = new ArrayList<>(serviceConnection.getNetworkLayer().getAllNetworkDevices());
            Collections.sort(sortedIdentifiers, (f, s) -> Integer.compare(f.hashCode(), s.hashCode()));
            for (final NetworkPeerIdentifier networkPeerIdentifier : sortedIdentifiers) {
                final String virtualIdentifier = "x" + (sortedIdentifiers.indexOf(networkPeerIdentifier) + 1);
                virtualIdentifiers.put(virtualIdentifier, networkPeerIdentifier);
            }
            return virtualIdentifiers;
        }

        public NetworkPeerIdentifier getLocalPID() {
            waitForLatch(initializationLatch);
            return localPID;
        }

        public Map<String, NetworkPeerIdentifier> getVirtualIdentifiers() {
            waitForLatch(initializationLatch);
            return virtualIdentifiers;
        }
    }

    private final NetworkServiceConnection networkServiceConnection = new NetworkServiceConnection();
    private Intent networkServiceIntent;

    // FIXME: the magic number will be read in from the input file, but for now is hard coded
    private NetworkInitializer network = new NetworkInitializer(2, networkServiceConnection);

    @Override
    public IBinder onBind(Intent intent) {
        return new InitializerBinder(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        networkServiceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(networkServiceIntent, networkServiceConnection, BIND_AUTO_CREATE);

        new Thread(network).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getApplicationContext().unbindService(networkServiceConnection);
    }

    public NetworkPeerIdentifier getLocalPID() {
        return network.getLocalPID();
    }

    public Map<String, NetworkPeerIdentifier> getVirtualIdentifiers() {
        return network.getVirtualIdentifiers();
    }
}
