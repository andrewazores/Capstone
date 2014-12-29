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
import java.util.concurrent.CountDownLatch;

import ca.mcmaster.capstone.monitoralgorithm.NetworkServiceConnection;
import ca.mcmaster.capstone.networking.CapstoneService;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;

public class Initializer extends Service {
    private static class NetworkInitializer implements Runnable {
        private static int numPeers;
        private static NetworkPeerIdentifier localPID;
        private static final Map<String, NetworkPeerIdentifier> virtualIdentifiers = new HashMap<>();
        private static final CountDownLatch latch = new CountDownLatch(1);

        public NetworkInitializer(final int numPeers){
            this.numPeers = numPeers;
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
            latch.countDown();
        }

        private static Map<String, NetworkPeerIdentifier> generateVirtualIdentifiers() {
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

        public static NetworkPeerIdentifier getLocalPID() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Log.d("initializer", "Await failed.");
            }
            return localPID;
        }

        public static Map<String, NetworkPeerIdentifier> getVirtualIdentifiers() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Log.d("initializer", "Await failed.");
            }
            return virtualIdentifiers;
        }
    }

    // FIXME: the magic number will be read in from the input file, but for now is hard coded
    NetworkInitializer network = new NetworkInitializer(2);

    private Intent networkServiceIntent;
    private static final NetworkServiceConnection serviceConnection = new NetworkServiceConnection();
    private Thread thread;

    @Override
    public IBinder onBind(Intent intent) {
        return new InitializerBinder(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        networkServiceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(networkServiceIntent, serviceConnection, BIND_AUTO_CREATE);

        thread = new Thread(network);
        thread.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getApplicationContext().unbindService(serviceConnection);
    }

    public NetworkPeerIdentifier getLocalPID() {
        return network.getLocalPID();
    }

    public Map<String, NetworkPeerIdentifier> getVirtualIdentifiers() {
        return network.getVirtualIdentifiers();
    }
}
