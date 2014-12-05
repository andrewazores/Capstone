package darren.esgl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.EventLog;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;

public class CubeActivity extends Activity {

    private GLSurfaceView mGLSurfaceView;
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private SensorEventListener mSensorEventListener;
    private int flatnessCounter = 0;
    private boolean isFlat = true;

    private final float[] gravity = new float[3];
    private final float[] linearAcceleration = new float[3];
    private OpenGLRenderer renderer;

    private EventLog.Event event;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        setupGravitySensorService();
        // Go fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        GLSurfaceView view = new GLSurfaceView(this);
        renderer = new OpenGLRenderer();
        view.setRenderer(renderer);
        setContentView(view);

    }

    private void setupGravitySensorService() {
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mSensorEventListener = new GravitySensorEventListener();
        mSensorManager.registerListener(mSensorEventListener, mSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onDestroy() {
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private class GravitySensorEventListener implements SensorEventListener {

        public static final float ALPHA = 0.7f;

        @Override
        public void onSensorChanged(final SensorEvent event) {
            // Isolate the force of gravity with the low-pass filter.
            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];

            // Remove the gravity contribution with the high-pass filter.
            linearAcceleration[0] = event.values[0] - gravity[0];
            linearAcceleration[1] = event.values[1] - gravity[1];
            linearAcceleration[2] = event.values[2] - gravity[2];

            final float[] z_axis = new float[] {0, 0, 1};

            float[] target_dir = normalise( gravity );
            float rot_angle = (float) Math.acos( dot_product(target_dir,z_axis) );

            if( Math.abs(rot_angle) > Double.MIN_VALUE ) {
                float[] rot_axis = normalise(cross_product(target_dir, z_axis));

                renderer.axis = rot_axis;
                renderer.angle = rot_angle;
            }

            boolean previouslyFlat = isFlat;

            isFlat = checkCondition(gravity);

            if (isFlat) {
                renderer.setColor(new float[] {
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f
                });
            } else {
                renderer.setColor(new float[] {
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f
                });
            }
            if (previouslyFlat != isFlat) {
                ++flatnessCounter;
                sendEvent(isFlat);
            }
        }

        public void sendEvent(boolean event){
            final Valuation valuation = new Valuation("isFlat", new Valuation.Value<Boolean>(isFlat));
            Event e = new Event(flatnessCounter, 0, Event.EventType.INTERNAL, valuation, new VectorClock(new ArrayList<Integer>() {{ add(1); add(7); add(42);}}));
            Toast.makeText(CubeActivity.this, "Event has left the building", Toast.LENGTH_SHORT).show();
            //recieveEventInternal(e);
        }

        public void getEvent(){
            Event e = null;//recieveEvent();
            if(e.getVal().getValue("isFlat").evaluate().equals(true)){
                new AlertDialog.Builder(CubeActivity.this)
                    .setTitle("All Phones are Flat!!!")
                    .setPositiveButton("YAY!", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                        }
                    }).show();

            }
        }

        public boolean checkCondition(float[] gravity){
            float dot = dot_product(gravity, new float[] {0, 0, 1});

            return dot < .5;
        }

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        }
    }

    private float[] normalise(float[] gravity) {
        float sum = 0;
        for (float f : gravity) {
            sum += f*f;
        }
        float magnitude = (float) Math.sqrt(sum);
        float[] result = new float[gravity.length];
        for (int i = 0; i < result.length; ++i) {
            result[i] = gravity[i] / magnitude;
        }
        return result;
    }

    public float[] cross_product(float[] v1, float[] v2){
        float[] cross =  new float[3];

        cross[0] = (v1[1] * v2[2]) - (v1[2] * v2[1]);
        cross[1] = (v1[0] * v2[2]) - (v1[2] * v2[0]);
        cross[2] = (v1[0] * v2[1]) - (v1[1] * v2[0]);

        return cross;
    }

    public float dot_product(float[] v1, float[] v2){
        float dot = 0;

        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vector dimensionality mismatch");
        }

        for (int i = 0; i < v1.length; ++i) {
            dot += v1[i] * v2[i];
        }

        return dot;
    }


}
