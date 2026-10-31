package com.bcra.gateway.outbound;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;

import com.twilio.http.NetworkHttpClient;
import com.twilio.http.Request;
import com.twilio.http.Response;

public class SingleAttemptHttpClient extends NetworkHttpClient {
    public SingleAttemptHttpClient() {
        super(HttpClients.custom().disableAutomaticRetries()
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(3000)
                        .setSocketTimeout(5000).setConnectionRequestTimeout(3000).build()));
    }

    @Override
    public Response reliableRequest(Request request) {
        // Only the Kafka handler owns retry policy, including the durable attempt budget.
        return makeRequest(request);
    }
}
