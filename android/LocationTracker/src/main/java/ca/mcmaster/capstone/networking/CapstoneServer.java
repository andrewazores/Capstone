package ca.mcmaster.capstone.networking;

import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.monitoralgorithm.Token;
import ca.mcmaster.capstone.networking.structures.DeviceInfo;
import ca.mcmaster.capstone.networking.structures.HashableNsdServiceInfo;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import ca.mcmaster.capstone.networking.structures.PayloadObject;
import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CapstoneServer extends NanoHTTPD {

    public enum RequestMethod {
        UPDATE,
        IDENTIFY,
        SEND_TOKEN,
        SEND_EVENT,
    }

    public enum MimeType {
        APPLICATION_JSON("application/json; charset=utf-8"),
        TEXT_PLAIN("text/plain; charset=utf-8");

        private final String contentType;

        private MimeType(final String contentType) {
            this.contentType = contentType;
        }

        public String getContentType() {
            return contentType;
        }

        @Override
        public String toString() {
            return getContentType();
        }
    }

    public static final String KEY_REQUEST_METHOD = "request_method";
    private final Gson gson = new Gson();
    private final CapstoneService capstoneService;

    public CapstoneServer(final CapstoneService capstoneService) {
        super(0);
        this.capstoneService = capstoneService;
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
            log(e.getLocalizedMessage());
            return genericError();
        }
        log("Method: " + method);
        log("Content Body: " + contentBody);

        if (session.getMethod().equals(Method.GET)) {
            if (method != null && method.equals(RequestMethod.UPDATE)) {
                log("Responding with OK and current DeviceInfo");
                return serveGetRequest();
            }
        } else if (session.getMethod().equals(Method.POST)) {
            if (method != null && method.equals(RequestMethod.IDENTIFY)) {
                final String postData = contentBody.get("postData");
                final HashableNsdServiceInfo peerNsdServiceInfo = gson.fromJson(postData, HashableNsdServiceInfo.class);
                log("Parsed POST data as: " + peerNsdServiceInfo);
                if (peerNsdServiceInfo == null) {
                    return genericError();
                }
                return servePostIdentify(peerNsdServiceInfo);
            } else if (method != null && method.equals(RequestMethod.SEND_TOKEN)) {
                final String postData = contentBody.get("postData");
                final Token token = gson.fromJson(postData, Token.class);
                log("Parsed POST data as: " + token);
                if (token == null) {
                    return genericError();
                }
                return servePostReceiveToken(token);
            } else if (method != null && method.equals(RequestMethod.SEND_EVENT)) {
                final String postData = contentBody.get("postData");
                final Event event = gson.fromJson(postData, Event.class);
                log("Parsed POST data as: " + event);
                if (event == null) {
                    return genericError();
                }
                return servePostReceiveEvent(event);
            }
        }

        log("No known handler for request, returning generic error");
        return genericError();
    }

    private Response serveGetRequest() {
        final PayloadObject<DeviceInfo> getRequestResponse = new PayloadObject<>(capstoneService.getStatus(), capstoneService.getNsdPeers(), PayloadObject.Status.OK);
        return JSONResponse(Response.Status.OK, getRequestResponse);
    }

    private Response servePostIdentify(final HashableNsdServiceInfo peerNsdServiceInfo) {
        capstoneService.addSelfIdentifiedPeer(peerNsdServiceInfo);
        final PayloadObject<NsdServiceInfo> postIdentifyResponse = new PayloadObject<>(capstoneService.getLocalNsdServiceInfo(), capstoneService.getNsdPeers(), PayloadObject.Status.OK);
        return JSONResponse(Response.Status.OK, postIdentifyResponse);
    }

    private Response servePostReceiveToken(final Token token) {
        capstoneService.receiveTokenInternal(token);
        return genericSuccess();
    }

    private Response servePostReceiveEvent(final Event event) {
        capstoneService.receiveEventExternal(event);
        return genericSuccess();
    }

    private Response errorResponse(final String errorMessage) {
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

    private static Response JSONResponse(final Response.Status status, final Object object) {
        return new Response(status, MimeType.APPLICATION_JSON.getContentType(), object.toString());
    }

    private static void log(final String message) {
        Log.v("CapstoneHttpServer", message);
    }
}
