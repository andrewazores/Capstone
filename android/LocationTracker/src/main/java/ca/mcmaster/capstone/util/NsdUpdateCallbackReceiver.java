package ca.mcmaster.capstone.util;

import ca.mcmaster.capstone.structures.HashableNsdServiceInfo;

import java.util.Collection;

public interface NsdUpdateCallbackReceiver {

    public void nsdUpdate(final Collection<HashableNsdServiceInfo> nsdPeers);

}
