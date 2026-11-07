package com.example.matchaggregator.poll;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;

@Component
public class HttpProviderBClient implements ProviderBClient {
    private final RestClient http;

    public HttpProviderBClient(PollerProperties props) {
        HttpClient jdk = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.timeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(props.timeout());
        this.http = RestClient.builder()
                .baseUrl(props.providerBBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public ProviderBScore fetchScore(String providerMatchId) {
        return get("/provider/matches/{id}/score", ProviderBScore.class, providerMatchId);
    }

    @Override
    public ProviderBCommentary fetchCommentary(String providerMatchId) {
        return get("/provider/matches/{id}/commentary", ProviderBCommentary.class, providerMatchId);
    }

    /** Connection errors, timeouts, non-2xx statuses and unparseable bodies all become ProviderException. */
    private <T> T get(String path, Class<T> type, String providerMatchId) {
        try {
            T body = http.get().uri(path, providerMatchId).retrieve().body(type);
            if (body == null) throw new ProviderException("empty response body");
            return body;
        } catch (RestClientException e) {
            throw new ProviderException("request failed: " + e.getMessage(), e);
        }
    }
}