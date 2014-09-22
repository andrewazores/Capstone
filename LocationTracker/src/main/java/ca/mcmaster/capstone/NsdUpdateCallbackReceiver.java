package ca.mcmaster.capstone;

import android.net.nsd.NsdServiceInfo;

import java.util.Collection;

public interface NsdUpdateCallbackReceiver {

    public void nsdUpdate(final Collection<HashableNsdServiceInfo> nsdPeers);

}
