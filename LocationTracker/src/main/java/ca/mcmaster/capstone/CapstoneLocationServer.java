package ca.mcmaster.capstone;

import android.net.nsd.NsdServiceInfo;
import android.util.Log;
import com.google.gson.Gson;
import fi.iki.elonen.NanoHTTPD;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.Map;

public class CapstoneLocationServer extends NanoHTTPD {

    private final Gson gson = new Gson();
    private final CapstoneLocationService capstoneLocationService;

    public CapstoneLocationServer(final CapstoneLocationService capstoneLocationService) {
        super(0);
        this.capstoneLocationService = capstoneLocationService;
        log("Created");
    }

    @Override
    public void start() throws IOException {
        super.start();
        log("Started on port " + getListeningPort());
    }

    @Override
    public Response serve(final IHTTPSession session) {
        log("Got " + session.getMethod() + " request on " + session.getUri());
        if (!(session.getMethod().equals(Method.GET) || session.getMethod().equals(Method.POST) )) {
            return new Response(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Only GET and POST requests are supported");
        }

        final Map<String, String> headers = session.getHeaders();
        final String method = headers.get("method");
        log("Method: " + method);

        if (session.getMethod().equals(Method.GET)) {
            if (method != null && method.equals("request")) {
                return serveGetRequest();
            }
        } else if (session.getMethod().equals(Method.POST)) {
            if (method != null && method.equals("identify")) {
                return servePostIdentify(headers);
            }
        }

        return new Response(Response.Status.BAD_REQUEST, "application/json", "");
    }

    private Response serveGetRequest() {
        log("Responding with OK and current DeviceInfo");
        return new Response(Response.Status.OK, "application/json", capstoneLocationService.getStatusAsJson());
    }

    private Response servePostIdentify(final Map<String, String> headers) {
        final String serviceType = headers.get("nsd_service_type");
        final String serviceName = headers.get("nsd_service_name");
        final int servicePort = Integer.parseInt(headers.get("nsd_service_port"));
        final InetAddress serviceHost = gson.fromJson(headers.get("nsd_service_host"), InetAddress.class);

        final NsdServiceInfo peerNsdServiceInfo = new NsdServiceInfo();
        peerNsdServiceInfo.setServiceType(serviceType);
        peerNsdServiceInfo.setServiceName(serviceName);
        peerNsdServiceInfo.setPort(servicePort);
        peerNsdServiceInfo.setHost(serviceHost);
        log("Received self-identify for " + peerNsdServiceInfo);

        capstoneLocationService.addSelfIdentifiedPeer(peerNsdServiceInfo);

        return new Response(Response.Status.OK, "application/json", "");
    }

    private static void log(final String message) {
        Log.v("CapstoneHttpServer", message);
    }
}
