package ca.mcmaster.capstone.networking.structures;

import com.google.gson.Gson;

import java.io.Serializable;
import java.util.Set;

public class PayloadObject<T> implements Serializable {

    public enum Status {
        OK,
        ERROR,
    }

    private final T payload;
    private final long wallClockCreationTime;
    private final int nsdPeersSetHash;
    private final int nsdPeersSetCount;
    private final Status status;

    public PayloadObject(final T payload, final Set<HashableNsdServiceInfo> nsdPeers, final Status status) {
        this.payload = payload;
        this.nsdPeersSetCount = nsdPeers.size();
        this.nsdPeersSetHash = nsdPeers.hashCode();
        this.status = status;
        this.wallClockCreationTime = System.nanoTime();
    }

    public T getPayload() {
        return payload;
    }

    public long getWallClockCreationTime() {
        return wallClockCreationTime;
    }

    public int getNsdPeersSetHash() {
        return nsdPeersSetHash;
    }

    public int getNsdPeersSetCount() {
        return nsdPeersSetCount;
    }

    public Status getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
