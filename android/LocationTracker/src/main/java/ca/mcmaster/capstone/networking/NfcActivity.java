package ca.mcmaster.capstone.networking;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
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

public class NfcActivity extends Activity implements MonitorSatisfactionStateListener {
    protected NfcAdapter nfcAdapter;
    protected PendingIntent nfcPendingIntent;
    private NetworkPeerIdentifier NSD;
    private List<Destination> destinations = new ArrayList<>();

    private int eventCounter = 0;
    private String variableName;

    private final LocationServiceConnection serviceConnection = new LocationServiceConnection();
    private final InitializerServiceConnection initializerServiceConnection = new InitializerServiceConnection();

    @Override
    public void onMonitorSatisfied() {
        destinations.remove(0);
        TextView text = (TextView) findViewById(R.id.next_destination);
        text.setText(destinations.get(0).dest.name());
        updateUI();
    }

    @Override
    public void onMonitorViolated() {

    }

    private enum DestinationEnum {
        A("041AB3329A3D80"),
        B("0414B3329A3D80"),
        C("0417B3329A3D80"),
        D("0412B3329A3D80"),
        E("041DB3329A3D80");

        private final String text;

        private DestinationEnum(final String text) {
            this.text = text;
        }
    }

    private class Destination {
        DestinationEnum dest;

        public Destination(DestinationEnum dest){
            this.dest = dest;
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, this.getClass())
                                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        destinations.add(new Destination(DestinationEnum.A));
        destinations.add(new Destination(DestinationEnum.B));
        destinations.add(new Destination(DestinationEnum.C));
        destinations.add(new Destination(DestinationEnum.D));
        destinations.add(new Destination(DestinationEnum.E));

        updateUI();

        Intent serviceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);

        Intent initializerServiceIntent = new Intent(this, Initializer.class);
        getApplicationContext().bindService(initializerServiceIntent,
                                            initializerServiceConnection,
                                            BIND_AUTO_CREATE);

    }

    public void updateUI(){
        TextView text = (TextView) findViewById(R.id.next_destination);

        if(destinations.isEmpty()){
            text.setText("Your done!!!");
        }
        else {
            text.setText(destinations.get(0).dest.name());
        }
    }

    public void sendEvent(double value) {
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
    protected void onNewIntent(Intent intent) {
        if (intent.getAction().equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {

            String uid = byteArrayToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID));

            if(uid.equals(destinations.get(0).dest.text)) {
                destinations.remove(0);
            }

            double value = 0;

            if(uid.equals(DestinationEnum.A.text)){
                value = 1;
            }
            else if(uid.equals(DestinationEnum.B.text)){
                value = 2;
            }
            else if(uid.equals(DestinationEnum.C.text)){
                value = 3;
            }
            else if(uid.equals(DestinationEnum.D.text)){
                value = 4;
            }
            else if(uid.equals(DestinationEnum.E.text)){
                value = 5;
            }

            sendEvent(value);
        }
    }

    public void enableForegroundMode() {
        Log.d("NfcActivity", "enableForegroundMode");

        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        IntentFilter[] writeTagFilters = new IntentFilter[] {tagDetected};
        nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, writeTagFilters, null);
    }

    public void disableForegroundMode() {
        Log.d("NfcActivity", "disableForegroundMode");

        nfcAdapter.disableForegroundDispatch(this);
    }

    private String byteArrayToHexString(byte[] array) {
        String[] hex = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"};
        String output = "";
        for (int i = 0; i < array.length; ++i) {
            int input = (int) array[i] & 0xff;
            int temp = (input >> 4) & 0x0f;
            output += hex[temp];
            temp = input & 0x0f;
            output += hex[temp];
        }
        return output;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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
