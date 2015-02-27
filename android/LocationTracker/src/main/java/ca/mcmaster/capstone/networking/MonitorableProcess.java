package ca.mcmaster.capstone.networking;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import ca.mcmaster.capstone.initializer.Initializer;
import ca.mcmaster.capstone.initializer.InitializerBinder;
import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.monitoralgorithm.Valuation;
import ca.mcmaster.capstone.monitoralgorithm.VectorClock;
import ca.mcmaster.capstone.networking.structures.Message;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import ca.mcmaster.capstone.networking.util.MessageReceiver;
import ca.mcmaster.capstone.networking.util.MonitorSatisfactionStateListener;
import lombok.NonNull;

public abstract class MonitorableProcess extends Activity implements MonitorSatisfactionStateListener, MessageReceiver {

    protected static final int HEARTBEAT_INTERVAL = 3000;

    protected final NetworkServiceConnection networkServiceConnection = new NetworkServiceConnection();
    protected final InitializerServiceConnection initializerServiceConnection = new InitializerServiceConnection();
    protected NetworkPeerIdentifier localPeerIdentifier;
    protected String variableName;
    protected VectorClock messageVectorClock;
    protected final ScheduledExecutorService heartbeatWorker = Executors.newSingleThreadScheduledExecutor();
    protected ScheduledFuture<?> heartbeatJob;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent networkServiceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(networkServiceIntent, networkServiceConnection, BIND_AUTO_CREATE);

        final Intent initializerServiceIntent = new Intent(this, Initializer.class);
        getApplicationContext().bindService(initializerServiceIntent, initializerServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getApplicationContext().unbindService(networkServiceConnection);
        getApplicationContext().unbindService(initializerServiceConnection);
        stopHeartbeat();
    }

    protected void startHeartbeat(Runnable command) {
        heartbeatJob = heartbeatWorker.scheduleAtFixedRate(command, 0, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    protected void stopHeartbeat() {
        if (heartbeatJob != null) {
            heartbeatJob.cancel(false);
        }
    }

    protected void broadcastHeartbeat() {
        waitForNetworkLayer();
        //messageVectorClock.incrementProcess(localPeerIdentifier);
        networkServiceConnection.getService().broadcastMessage(
                new Message(localPeerIdentifier, messageVectorClock, "ticktock")
        );
    }

    protected final void sendEvent(final double value, final Event.EventType type) {
        waitForNetworkLayer();
        final Valuation valuation = new Valuation(new HashMap<String, Double>() {{
            put(MonitorableProcess.this.variableName, value);
        }});
        int eid = messageVectorClock.process(localPeerIdentifier);
        final Event e = new Event(eid, localPeerIdentifier, type, valuation, messageVectorClock);
        if (eid != 0) {
            showToast("Event has left the building");
            networkServiceConnection.getService().sendEventToMonitor(e);
        }
    }

    protected final void waitForNetworkLayer() {
        while (networkServiceConnection.getService() == null) {
            log("waitForNetworkLayer");
            try {
                log("waiting for network layer to appear...");
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
            } catch (final InterruptedException e) {
                log("NetworkLayer connection is not established: " + e.getLocalizedMessage());
            }
        }
    }

    @Override
    public void receiveMessage(@NonNull final Message message) {
        messageVectorClock.incrementProcess(localPeerIdentifier);
        messageVectorClock = messageVectorClock.merge(message.getVectorClock());
        onReceiveMessage(message);
    }

    public abstract void onReceiveMessage(Message message);

    public abstract void onNetworkServiceConnection();

    public abstract void onNetworkServiceDisconnection();

    public abstract void onInitializerServiceConnection();

    public abstract void onInitializerServiceDisconnection();

    protected void log(@NonNull final String message) {
        Log.v(getLogTag(), message);
    }

    protected void showToast(@NonNull final String message) {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    protected abstract String getLogTag();

    public class NetworkServiceConnection implements ServiceConnection {

        private CapstoneService service;

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            showToast("Service connected");

            this.service = ((CapstoneService.CapstoneNetworkServiceBinder) service).getService();
            this.service.registerMonitorStateListener(MonitorableProcess.this);
            this.service.registerMessageReceiver(MonitorableProcess.this);
            onNetworkServiceConnection();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            showToast("Service disconnected");
            this.service = null;
            onNetworkServiceDisconnection();
        }

        public CapstoneService getService() {
            return service;
        }

    }

    public class InitializerServiceConnection implements ServiceConnection {
        private Initializer initializer;

        @Override
        public void onServiceConnected(final ComponentName componentName, final IBinder iBinder) {
            this.initializer = ((InitializerBinder) iBinder).getInitializer();

            MonitorableProcess.this.localPeerIdentifier = initializer.getLocalPID();
            //FIXME: this is for testing out simple test case. More work is needed for more complex variableGlobalText arrangements
            for (final Map.Entry<String, NetworkPeerIdentifier> virtualID : initializer.getVirtualIdentifiers().entrySet()) {
                if (virtualID.getValue().equals(localPeerIdentifier)) {
                    MonitorableProcess.this.variableName = virtualID.getKey();
                    break;
                }
            }
            final Map<NetworkPeerIdentifier, Integer> vec = new HashMap<>();
            for (final NetworkPeerIdentifier peer : initializer.getVirtualIdentifiers().values()) {
                vec.put(peer, 0);
            }
            messageVectorClock = new VectorClock(vec);
            log("I am: " + MonitorableProcess.this.variableName);
            onInitializerServiceConnection();
        }

        @Override
        public void onServiceDisconnected(final ComponentName componentName) {
            this.initializer = null;
            onInitializerServiceDisconnection();
        }

        public Initializer getInitializer() {
            return this.initializer;
        }
    }

}
