package ca.mcmaster.capstone.networking.structures;

import android.net.nsd.NsdServiceInfo;
import android.os.Parcel;
import android.os.Parcelable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.net.InetAddress;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class HashableNsdServiceInfo implements Parcelable {

    private static final ConcurrentMap<Identifier, HashableNsdServiceInfo> cache = new ConcurrentHashMap<>();

    private final NsdServiceInfo nsdServiceInfo;

    private HashableNsdServiceInfo(final NsdServiceInfo nsdServiceInfo) {
        Objects.requireNonNull(nsdServiceInfo);
        Objects.requireNonNull(nsdServiceInfo.getHost());
        Objects.requireNonNull(nsdServiceInfo.getServiceName());
        Objects.requireNonNull(nsdServiceInfo.getServiceType());
        this.nsdServiceInfo = nsdServiceInfo;
    }

    public static HashableNsdServiceInfo get(final NsdServiceInfo nsdServiceInfo) {
        final Identifier identifier = Identifier.getIdentifier(nsdServiceInfo);
        cache.put(identifier, new HashableNsdServiceInfo(nsdServiceInfo));
        return cache.get(identifier);
    }

    public NsdServiceInfo getNsdServiceInfo() {
        return nsdServiceInfo;
    }

    public String getServiceName() {
        return nsdServiceInfo.getServiceName();
    }

    public String getServiceType() {
        return nsdServiceInfo.getServiceType();
    }

    public void setHost(final InetAddress s) {
        nsdServiceInfo.setHost(s);
    }

    public int getPort() {
        return nsdServiceInfo.getPort();
    }

    public void setPort(final int p) {
        nsdServiceInfo.setPort(p);
    }

    public void setServiceName(final String s) {
        nsdServiceInfo.setServiceName(s);
    }

    public void setServiceType(final String s) {
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

        return (nsdServiceInfo.getHost().getHostAddress().contains(that.getHost().getHostAddress())
                        || that.getHost().getHostAddress().contains(nsdServiceInfo.getHost().getHostAddress()))
                       && nsdServiceInfo.getPort() == that.getPort();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(getHost())
                .append(getPort())
                .toHashCode();
    }

    private static class Identifier {
        private final InetAddress host;
        private final int port;

        private Identifier(final InetAddress host, final int port) {
            Objects.requireNonNull(host);
            this.host = host;
            this.port = port;
        }

        public InetAddress getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Identifier that = (Identifier) o;

            return new EqualsBuilder()
                    .append(port, that.port)
                    .append(host, that.getHost())
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                    .append(getHost())
                    .append(getPort())
                    .toHashCode();
        }

        public static Identifier getIdentifier(final NsdServiceInfo nsdServiceInfo) {
            return new Identifier(nsdServiceInfo.getHost(), nsdServiceInfo.getPort());
        }
    }

}
