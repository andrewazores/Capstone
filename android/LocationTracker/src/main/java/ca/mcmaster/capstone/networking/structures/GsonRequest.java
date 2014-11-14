package ca.mcmaster.capstone.networking.structures;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import fi.iki.elonen.NanoHTTPD;

import java.io.UnsupportedEncodingException;
import java.util.Map;

// From https://developer.android.com/training/volley/request-custom.html
public class GsonRequest<T> extends Request<T> {
    private final Gson gson = new Gson();
    private final Class<T> clazz;
    private final Map<String, String> headers;
    private final Map<String, String> params;
    private final Response.Listener<T> listener;
    private final String contentBody;

    /**
     * Make a GET request and return a parsed object from JSON.
     *
     * @param url URL of the request to make
     * @param clazz Relevant class object, for Gson's reflection
     * @param headers Map of request headers
     */
    public GsonRequest(final int method, final String url, final Class<T> clazz,
                       final Map<String, String> headers, final Map<String, String> params, final String contentBody,
                       final Response.Listener<T> listener, final Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        this.clazz = clazz;
        this.headers = headers;
        this.params = params;
        this.contentBody = contentBody;
        this.listener = listener;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return headers != null ? headers : super.getHeaders();
    }

    @Override
    public Map<String, String> getParams() throws AuthFailureError {
        return params != null ? params : super.getParams();
    }

    @Override
    public byte[] getBody() {
        return this.contentBody.getBytes();
    }

    @Override
    protected void deliverResponse(final T response) {
        listener.onResponse(response);
    }

    @Override
    protected Response<T> parseNetworkResponse(final NetworkResponse response) {
        if (response.statusCode != NanoHTTPD.Response.Status.OK.getRequestStatus()) {
            return Response.error(new VolleyError(response));
        }
        try {
            final String json = new String(
                response.data,
                HttpHeaderParser.parseCharset(response.headers));
            return Response.success(
                gson.fromJson(json, clazz),
                HttpHeaderParser.parseCacheHeaders(response));
        } catch (final UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        } catch (final JsonSyntaxException e) {
            return Response.error(new ParseError(e));
        }
    }
}