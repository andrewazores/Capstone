package ca.mcmaster.capstone.initializer;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class Initializer extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return new InitializerBinder(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("initializer", "Initializer started.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
