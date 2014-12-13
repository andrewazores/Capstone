package ca.mcmaster.capstone.monitoralgorithm;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

import ca.mcmaster.capstone.networking.util.NetworkLayer;

public class NetworkServiceConnection implements ServiceConnection {

    private NetworkLayer networkLayer;

    @Override
    public void onServiceConnected(final ComponentName componentName, final IBinder iBinder) {
        this.networkLayer = (NetworkLayer) iBinder; //FIXME: This cast fails
    }

    @Override
    public void onServiceDisconnected(final ComponentName componentName) {
        this.networkLayer = null;
    }

    public NetworkLayer getNetworkLayer() {
        return networkLayer;
    }
}
