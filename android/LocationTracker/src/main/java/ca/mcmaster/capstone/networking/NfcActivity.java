package ca.mcmaster.capstone.networking;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.mcmaster.capstone.R;
import ca.mcmaster.capstone.initializer.Initializer;
import ca.mcmaster.capstone.initializer.InitializerBinder;
import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.monitoralgorithm.Valuation;
import ca.mcmaster.capstone.monitoralgorithm.VectorClock;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import ca.mcmaster.capstone.networking.util.MonitorSatisfactionStateListener;
import lombok.Getter;
import lombok.NonNull;

public class NfcActivity extends Activity implements MonitorSatisfactionStateListener {
    protected NfcAdapter nfcAdapter;
    protected PendingIntent nfcPendingIntent;
    private NetworkPeerIdentifier NSD;
    private List<NfcDestinations> destinations = new ArrayList<>();

    private int eventCounter = 0;
    private String variableName;

    private final LocationServiceConnection serviceConnection = new LocationServiceConnection();
    private final InitializerServiceConnection initializerServiceConnection = new InitializerServiceConnection();

    @Override
    public void onMonitorSatisfied() {
        destinations.remove(0);
        final TextView text = (TextView) findViewById(R.id.next_destination);
        text.setText(destinations.get(0).name());
        updateUI();
    }

    @Override
    public void onMonitorViolated() {

    }

    private static enum NfcDestinations {
        A("041AB3329A3D80", 1),
        B("0414B3329A3D80", 2),
        C("0417B3329A3D80", 3),
        D("0412B3329A3D80", 4),
        E("041DB3329A3D80", 5);

        @Getter private final String text;
        @Getter private final double value;

        private NfcDestinations(final String text, final double value) {
            this.text = text;
            this.value = value;
        }

        public static NfcDestinations fromUUID(@NonNull final String uuid) {
            for (final NfcDestinations nfcDestinations : NfcDestinations.values()) {
                if (nfcDestinations.getText().equals(uuid)) {
                    return nfcDestinations;
                }
            }
            return null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        enableForegroundMode();
    }

    @Override
    protected void onPause() {
        super.onPause();

        disableForegroundMode();
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, this.getClass())
                                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);


        try {
            initializeDestinations(Environment.getExternalStorageDirectory().getPath() + "/nfcInit/destinationList.txt");
        } catch (final FileNotFoundException fnfe) {
            Toast.makeText(getApplicationContext(), "Destination list config file missing!", Toast.LENGTH_LONG).show();
        } catch (final IOException ioe) {
            Toast.makeText(getApplicationContext(), "Destination list config file could not be read!", Toast.LENGTH_LONG).show();
        }
        updateUI();

        final Intent serviceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);

        final Intent initializerServiceIntent = new Intent(this, Initializer.class);
        getApplicationContext().bindService(initializerServiceIntent,
                initializerServiceConnection,
                BIND_AUTO_CREATE);
    }

    public void initializeDestinations(@NonNull final String path) throws FileNotFoundException, IOException {
        try (final BufferedReader bufferedReader = new BufferedReader(new FileReader(path))) {
            String line = bufferedReader.readLine();
            while (line != null) {
                destinations.add(NfcDestinations.valueOf(line.trim()));
                line = bufferedReader.readLine();
            }

        }
    }

    public void updateUI() {
        final TextView text = (TextView) findViewById(R.id.next_destination);

        if (destinations.isEmpty()) {
            text.setText("You're done!");
        } else {
            text.setText(destinations.get(0).name());
        }
    }

    public void sendEvent(final double value) {
        //Block until network is set up... I am a failure.
        initializerServiceConnection.getInitializer().getLocalPID();
        final Valuation valuation = new Valuation(new HashMap<String, Double>() {{
            put(NfcActivity.this.variableName, value);
        }});
        ++eventCounter;
        final Event e = new Event(eventCounter, NSD, Event.EventType.INTERNAL, valuation,
                new VectorClock(new HashMap<NetworkPeerIdentifier, Integer>() {{
                    put(serviceConnection.getService().getLocalNetworkPeerIdentifier(), eventCounter);
                    for (final NetworkPeerIdentifier peer : serviceConnection.getService().getKnownPeers()) {
                        put(peer, 0);
                    }
                }}));
        Toast.makeText(NfcActivity.this, "Event has left the building", Toast.LENGTH_SHORT).show();
        serviceConnection.getService().sendEventToMonitor(e);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        if (intent.getAction().equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
            final String uid = byteArrayToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID));

            if (uid.equals(destinations.get(0).getText())) {
                destinations.remove(0);
            }

            sendEvent(NfcDestinations.fromUUID(uid).getValue());
        }
    }

    public void enableForegroundMode() {
        Log.d("NfcActivity", "enableForegroundMode");

        final IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        final IntentFilter[] writeTagFilters = new IntentFilter[] {tagDetected};
        nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, writeTagFilters, null);
    }

    public void disableForegroundMode() {
        Log.d("NfcActivity", "disableForegroundMode");

        nfcAdapter.disableForegroundDispatch(this);
    }

    static String byteArrayToHexString(final byte[] bytes) {
        BigInteger bi = new BigInteger(1, bytes);
        return String.format("%0" + (bytes.length << 1) + "X", bi);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return super.onOptionsItemSelected(item);
    }

    public class LocationServiceConnection implements ServiceConnection {

        private CapstoneService service;

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(NfcActivity.this, "Service connected", Toast.LENGTH_LONG).show();

            this.service = ((CapstoneService.CapstoneNetworkServiceBinder) service).getService();
            this.service.registerMonitorStateListener(NfcActivity.this);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Toast.makeText(NfcActivity.this, "Service disconnected", Toast.LENGTH_LONG).show();
            this.service = null;
        }

        public CapstoneService getService() {
            return service;
        }
    }

    public class InitializerServiceConnection implements ServiceConnection{
        private Initializer initializer;

        @Override
        public void onServiceConnected(final ComponentName componentName, final IBinder iBinder) {
            this.initializer = ((InitializerBinder) iBinder).getInitializer();

            NfcActivity.this.NSD = initializer.getLocalPID();
            //FIXME: this is for testing out simple test case. More work is needed for more complex variableGlobalText arrangements
            for (Map.Entry<String, NetworkPeerIdentifier> virtualID : initializer.getVirtualIdentifiers().entrySet()) {
                if (virtualID.getValue() == NSD) {
                    NfcActivity.this.variableName = virtualID.getKey();
                    break;
                }
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName componentName) {
            this.initializer = null;
        }

        public Initializer getInitializer() {
            return this.initializer;
        }
    }

}
