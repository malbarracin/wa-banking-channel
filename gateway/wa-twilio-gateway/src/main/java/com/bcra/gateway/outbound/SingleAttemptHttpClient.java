package com.bcra.gateway.outbound;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;

import com.twilio.http.NetworkHttpClient;
import com.twilio.http.Request;
import com.twilio.http.Response;

public class SingleAttemptHttpClient extends NetworkHttpClient {
    private final String localEndpoint;

    public SingleAttemptHttpClient() {
        this("");
    }

    public SingleAttemptHttpClient(String localEndpoint) {
        super(HttpClients.custom().disableAutomaticRetries()
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(3000)
                        .setSocketTimeout(5000).setConnectionRequestTimeout(3000).build()));
        if (!localEndpoint.isEmpty()
                && !localEndpoint.matches("http://(localhost|127\\.0\\.0\\.1|host\\.docker\\.internal):[0-9]+")) {
            throw new IllegalArgumentException("Invalid local Twilio endpoint");
        }
        this.localEndpoint = localEndpoint;
    }

    @Override
    public Response makeRequest(Request original) {
        if (localEndpoint.isEmpty()) {
            return super.makeRequest(original);
        }
        var redirected = new Request(original.getMethod(), localEndpoint
                + java.net.URI.create(original.getUrl()).getRawPath());
        redirected.setAuth(original.getUsername(), original.getPassword());
        original.getPostParams().forEach((key, values) -> values.forEach(value ->
                redirected.addPostParam(key, value)));
        return super.makeRequest(redirected);
    }

    @Override
    public Response reliableRequest(Request request) {
        // Only the Kafka handler owns retry policy, including the durable attempt budget.
        return makeRequest(request);
    }
}
