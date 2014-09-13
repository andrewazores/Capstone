package ca.mcmaster.capstone;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

/**
* Created by andrew on 9/13/14.
*/
class BarometerListener implements SensorEventListener {
    private LocationActivity locationActivity;

    public BarometerListener(final LocationActivity locationActivity) {
        this.locationActivity = locationActivity;
    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
        locationActivity.barometerPressure = event.values[0];
    }

    @Override
    public void onAccuracyChanged(final Sensor sensor, final int accuracy) {

    }
}
