package ca.mcmaster.capstone.networking;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ca.mcmaster.capstone.R;
import ca.mcmaster.capstone.initializer.Initializer;
import ca.mcmaster.capstone.monitoralgorithm.InitializerServiceConnection;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;


public class NfcActivity extends Activity {


    protected NfcAdapter nfcAdapter;
    protected PendingIntent nfcPendingIntent;
    private NetworkPeerIdentifier NSD;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private SensorEventListener mSensorEventListener;

    private List<Destination> destinations = new ArrayList<Destination>();

    private final InitializerServiceConnection initializerServiceConnection = new InitializerServiceConnection();

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

    private class Destination{
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
        nfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);


        destinations.add(new Destination(DestinationEnum.A));
        destinations.add(new Destination(DestinationEnum.B));
        destinations.add(new Destination(DestinationEnum.C));
        destinations.add(new Destination(DestinationEnum.D));
        destinations.add(new Destination(DestinationEnum.E));

        updateViews();

        Intent initializerServiceIntent = new Intent(this, Initializer.class);
        getApplicationContext().bindService(initializerServiceIntent, initializerServiceConnection, BIND_AUTO_CREATE);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
    }

    public void updateViews(){
        TextView list = (TextView) findViewById(R.id.destination_list);
        String s = "";
        for(Destination d : destinations){
            s = s + ", " + d.dest;
        }

        list.setText(s);


    }

    @Override
    protected void onNewIntent(Intent intent) {

        if (intent.getAction().equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {

            String uid = byteArrayToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID));

            if(uid.equals(destinations.get(0).dest.text))
                destinations.remove(0);

            TextView text = (TextView) findViewById(R.id.next_destination);

            if(uid.equals(DestinationEnum.A.text))
                text.setText("You Just Found " + DestinationEnum.valueOf("A"));
            if(uid.equals(DestinationEnum.B.text))
                text.setText("You Just Found " + DestinationEnum.valueOf("B"));
            if(uid.equals(DestinationEnum.C.text))
                text.setText("You Just Found " + DestinationEnum.valueOf("C"));
            if(uid.equals(DestinationEnum.D.text))
                text.setText("You Just Found " + DestinationEnum.valueOf("D"));
            if(uid.equals(DestinationEnum.E.text))
                text.setText("You Just Found " + DestinationEnum.valueOf("E"));

            updateViews();
        }
    }

    public void enableForegroundMode() {
        Log.d("We Have Dicks!!", "enableForegroundMode");

        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED); // filter for all
        IntentFilter[] writeTagFilters = new IntentFilter[] {tagDetected};
        nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, writeTagFilters, null);
    }

    public void disableForegroundMode() {
        Log.d("No More Dicks", "disableForegroundMode");

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
        int id = item.getItemId();


        return super.onOptionsItemSelected(item);
    }
}
