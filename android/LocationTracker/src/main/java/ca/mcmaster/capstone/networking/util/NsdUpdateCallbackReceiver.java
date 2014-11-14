package ca.mcmaster.capstone.networking.util;

import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;

import java.util.Collection;

public interface NsdUpdateCallbackReceiver {

    public void nsdUpdate(final Collection<HashableNsdServiceInfo> nsdPeers);

}
