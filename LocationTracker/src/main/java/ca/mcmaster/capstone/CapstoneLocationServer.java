package ca.mcmaster.capstone;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;

public class CapstoneLocationServer extends NanoHTTPD {

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
        if (!session.getMethod().equals(Method.GET)) {
            return new Response(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Only GET requests are supported");
        }
        return new Response(Response.Status.OK, "application/json", capstoneLocationService.getStatusAsJson());
    }

    private static void log(final String message) {
        Log.v("CapstoneHttpServer", message);
    }
}
