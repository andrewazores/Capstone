package ca.mcmaster.capstone.networking.structures;

import android.net.nsd.NsdServiceInfo;
import android.os.Parcel;
import android.os.Parcelable;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.net.InetAddress;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

public final class HashableNsdServiceInfo implements Parcelable {

    private static final ConcurrentMap<Identifier, HashableNsdServiceInfo> cache = new ConcurrentHashMap<>();

    @Getter private final NsdServiceInfo nsdServiceInfo;

    private HashableNsdServiceInfo(final NsdServiceInfo nsdServiceInfo) {
        Objects.requireNonNull(nsdServiceInfo);
        Objects.requireNonNull(nsdServiceInfo.getHost());
        Objects.requireNonNull(nsdServiceInfo.getServiceName());
        Objects.requireNonNull(nsdServiceInfo.getServiceType());
        this.nsdServiceInfo = nsdServiceInfo;
    }

    public static HashableNsdServiceInfo get(@NonNull final NsdServiceInfo nsdServiceInfo) {
        final Identifier identifier = Identifier.getIdentifier(nsdServiceInfo.getHost(), nsdServiceInfo.getPort());
        cache.put(identifier, new HashableNsdServiceInfo(nsdServiceInfo));
        return cache.get(identifier);
    }

    public String getServiceName() {
        return nsdServiceInfo.getServiceName();
    }

    public String getServiceType() {
        return nsdServiceInfo.getServiceType();
    }

    public void setHost(@NonNull final InetAddress s) {
        nsdServiceInfo.setHost(s);
    }

    public int getPort() {
        return nsdServiceInfo.getPort();
    }

    public void setPort(final int p) {
        nsdServiceInfo.setPort(p);
    }

    public void setServiceName(@NonNull final String s) {
        nsdServiceInfo.setServiceName(s);
    }

    public void setServiceType(@NonNull final String s) {
        nsdServiceInfo.setServiceType(s);
    }

    public InetAddress getHost() {
        return nsdServiceInfo.getHost();
    }

    @Override
    public int describeContents() {
        return nsdServiceInfo.describeContents();
    }

    @Override
    public String toString() {
        return nsdServiceInfo.toString();
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        nsdServiceInfo.writeToParcel(dest, flags);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (! (o instanceof HashableNsdServiceInfo)) return false;

        final HashableNsdServiceInfo that = (HashableNsdServiceInfo) o;

        return (this.getHost().getHostAddress().contains(that.getHost().getHostAddress())
                        || that.getHost().getHostAddress().contains(this.getHost().getHostAddress()))
                       && this.getPort() == that.getPort();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(getHost())
                .append(getPort())
                .toHashCode();
    }

    @Value @RequiredArgsConstructor(staticName = "getIdentifier")
    private static class Identifier {
        @NonNull InetAddress host;
        int port;
    }

}
