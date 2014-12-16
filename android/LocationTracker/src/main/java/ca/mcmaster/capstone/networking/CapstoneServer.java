package ca.mcmaster.capstone.networking;

import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.monitoralgorithm.Token;
import ca.mcmaster.capstone.networking.structures.DeviceInfo;
import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;
import ca.mcmaster.capstone.networking.structures.PayloadObject;
import fi.iki.elonen.NanoHTTPD;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

public class CapstoneServer extends NanoHTTPD {

    public enum RequestMethod {
        UPDATE,
        IDENTIFY,
        SEND_TOKEN,
        SEND_EVENT,
    }

    @AllArgsConstructor
    public enum MimeType {
        APPLICATION_JSON("application/json; charset=utf-8"),
        TEXT_PLAIN("text/plain; charset=utf-8");
        @NonNull @Getter private final String contentType;

        @Override
        public String toString() {
            return getContentType();
        }

    }

    public static final String KEY_REQUEST_METHOD = "request_method";
    private final InetAddress inetAddress;
    private final Gson gson = new Gson();
    private final CapstoneService capstoneService;

    public CapstoneServer(@NonNull final InetAddress inetAddress, @NonNull final CapstoneService capstoneService) {
        super(inetAddress, 0);
        this.capstoneService = capstoneService;
        this.inetAddress = inetAddress;
        log("Created");
    }

    @Override
    public void start() throws IOException {
        super.start();
        log("Started on " + this.inetAddress + ": " + getListeningPort());
    }

    @Override
    public Response serve(@NonNull final IHTTPSession session) {
        log("Got " + session.getMethod() + " request on " + session.getUri());
        if (!(session.getMethod().equals(Method.GET) || session.getMethod().equals(Method.POST))) {
            return errorResponse("Only GET and POST requests are supported");
        }

        final Map<String, String> headers = session.getHeaders();
        final RequestMethod method = RequestMethod.valueOf(headers.get(KEY_REQUEST_METHOD));
        final Map<String, String> contentBody = new HashMap<>();
        try {
            session.parseBody(contentBody);
        } catch (final ResponseException | IOException e) {
            log("Error parsing body: " + contentBody);
            log("Stack trace: (" + e.getClass() + ")" + e.getLocalizedMessage());
            return genericError();
        }
        log("Method: " + method);
        log("Content Body: " + contentBody);

        if (method == null) {
            log("Request method is invalid (null), cnanot serve request");
            return genericError();
        }

        if (session.getMethod().equals(Method.GET)) {
            if (method.equals(RequestMethod.UPDATE)) {
                log("Responding with OK and current DeviceInfo");
                return serveGetRequest();
            }
        } else if (session.getMethod().equals(Method.POST)) {
            switch (method) {
                case UPDATE:
                    log("Cannot serve POST with UPDATE method, returning error");
                    return genericError();
                case IDENTIFY:
                    return servePostIdentify(contentBody);
                case SEND_TOKEN:
                    return servePostReceiveToken(contentBody);
                case SEND_EVENT:
                    return servePostReceiveEvent(contentBody);
            }
        }

        log("No known handler for request, returning generic error");
        return genericError();
    }

    private Response serveGetRequest() {
        final PayloadObject<DeviceInfo> getRequestResponse = new PayloadObject<>(capstoneService.getStatus(), capstoneService.getNsdPeers(), PayloadObject.Status.OK);
        return JSONResponse(Response.Status.OK, getRequestResponse);
    }

    private static <T> T parseContentBody(@NonNull final Gson gson, @NonNull final Map<String, String> contentBody, @NonNull final Class<T> type) {
        final String postData = contentBody.get("postData");
        final T t = gson.fromJson(postData, type);
        log("Parsed POST data as: " + t);
        return t;
    }

    private Response servePostIdentify(@NonNull final Map<String, String> contentBody) {
        final HashableNsdServiceInfo peerNsdServiceInfo = parseContentBody(gson, contentBody, HashableNsdServiceInfo.class);
        capstoneService.addSelfIdentifiedPeer(peerNsdServiceInfo);
        final PayloadObject<NsdServiceInfo> postIdentifyResponse = new PayloadObject<>(capstoneService.getLocalNsdServiceInfo(), capstoneService.getNsdPeers(), PayloadObject.Status.OK);
        return JSONResponse(Response.Status.OK, postIdentifyResponse);
    }

    private Response servePostReceiveToken(@NonNull final Map<String, String> contentBody) {
        final Token token = parseContentBody(gson, contentBody, Token.class);
        capstoneService.receiveTokenInternal(token);
        return genericSuccess();
    }

    private Response servePostReceiveEvent(@NonNull final Map<String, String> contentBody) {
        final Event event = parseContentBody(gson, contentBody, Event.class);
        capstoneService.receiveEventExternal(event);
        return genericSuccess();
    }

    private Response errorResponse(@NonNull final String errorMessage) {
        final PayloadObject<String> errorPayload = new PayloadObject<>(errorMessage, capstoneService.getNsdPeers(), PayloadObject.Status.ERROR);
        return JSONResponse(Response.Status.BAD_REQUEST, errorPayload);
    }

    private Response genericError() {
        final PayloadObject<Void> errorPayload = new PayloadObject<>(null, capstoneService.getNsdPeers(), PayloadObject.Status.ERROR);
        return JSONResponse(Response.Status.BAD_REQUEST, errorPayload);
    }

    private Response genericSuccess() {
        final PayloadObject<Void> successPayload = new PayloadObject<>(null, capstoneService.getNsdPeers(), PayloadObject.Status.OK);
        return JSONResponse(Response.Status.OK, successPayload);
    }

    private static Response JSONResponse(@NonNull final Response.Status status, @NonNull final Object object) {
        return new Response(status, MimeType.APPLICATION_JSON.getContentType(), object.toString());
    }

    private static void log(final String message) {
        Log.v("CapstoneHttpServer", message);
    }
}
