package ca.mcmaster.capstone;

import android.util.Log;
import com.google.gson.Gson;
import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CapstoneLocationServer extends NanoHTTPD {

    public static final String KEY_REQUEST_MIME_TYPE_APPLICATION_JSON = "application/json; charset=utf-8";
    public static final String KEY_REQUEST_METHOD = "request_method";
    public static final String REQUEST_METHOD_UPDATE = "update";
    public static final String REQUEST_METHOD_IDENTIFY = "identify";
    public static final String KEY_REQUEST_MIME_TYPE_TEXT_PLAIN = "text/plain; charset=utf-8";
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
            return new Response(Response.Status.METHOD_NOT_ALLOWED, KEY_REQUEST_MIME_TYPE_TEXT_PLAIN, "Only GET and POST requests are supported");
        }

        final Map<String, String> headers = session.getHeaders();
        final String method = headers.get(KEY_REQUEST_METHOD);
        final Map<String, String> contentBody = new HashMap<>();
        try {
            session.parseBody(contentBody);
        } catch (final ResponseException | IOException e) {
            log("Error parsing body: " + contentBody);
            return genericError();
        }
        log("Method: " + method);
        log("Content Body: " + contentBody);

        if (session.getMethod().equals(Method.GET)) {
            if (method != null && method.equals(REQUEST_METHOD_UPDATE)) {
                log("Responding with OK and current DeviceInfo");
                return serveGetRequest();
            }
        } else if (session.getMethod().equals(Method.POST)) {
            if (method != null && method.equals(REQUEST_METHOD_IDENTIFY)) {
                final String postData = contentBody.get("postData");
                final HashableNsdServiceInfo peerNsdServiceInfo = gson.fromJson(postData, HashableNsdServiceInfo.class);
                log("Parsed POST data as: " + peerNsdServiceInfo);
                if (peerNsdServiceInfo == null) {
                    return genericError();
                }
                return servePostIdentify(peerNsdServiceInfo);
            }
        }

        log("No known handler for request, returning generic error");
        return genericError();
    }

    private Response serveGetRequest() {
        return new Response(Response.Status.OK, KEY_REQUEST_MIME_TYPE_APPLICATION_JSON, capstoneLocationService.getStatusAsJson());
    }

    private Response servePostIdentify(final HashableNsdServiceInfo peerNsdServiceInfo) {
        capstoneLocationService.addSelfIdentifiedPeer(peerNsdServiceInfo);
        return new Response(Response.Status.OK, KEY_REQUEST_MIME_TYPE_APPLICATION_JSON, gson.toJson(capstoneLocationService.getLocalNsdServiceInfo()));
    }

    private Response genericError(final String errorMessage) {
        return new Response(Response.Status.BAD_REQUEST, KEY_REQUEST_MIME_TYPE_TEXT_PLAIN, errorMessage);
    }

    private Response genericError() {
        return genericError("Error");
    }

    private static void log(final String message) {
        Log.v("CapstoneHttpServer", message);
    }
}
