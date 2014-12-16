package ca.mcmaster.capstone.networking.structures;

import com.google.gson.Gson;

import java.io.Serializable;
import java.util.Set;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Builder;

@Builder @Value
public class PayloadObject<T> implements Serializable {

    public enum Status {
        OK,
        ERROR,
    }

    @NonNull T payload;
    long wallClockCreationTime;
    int nsdPeersSetHash;
    int nsdPeersSetCount;
    @NonNull Status status;

    public PayloadObject(@NonNull final T payload, @NonNull final Set<HashableNsdServiceInfo> nsdPeers, @NonNull final Status status) {
        this.payload = payload;
        this.nsdPeersSetCount = nsdPeers.size();
        this.nsdPeersSetHash = nsdPeers.hashCode();
        this.status = status;
        this.wallClockCreationTime = System.nanoTime();
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
