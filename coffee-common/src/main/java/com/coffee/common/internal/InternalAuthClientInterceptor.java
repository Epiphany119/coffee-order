package com.coffee.common.internal;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;

/** Signs an outbound Spring RestClient/RestTemplate request for /internal/** endpoints. */
public final class InternalAuthClientInterceptor implements ClientHttpRequestInterceptor {
    private final String serviceName;
    private final String keyId;
    private final String secret;

    public InternalAuthClientInterceptor(String serviceName, String keyId, String secret) {
        if (serviceName == null || serviceName.isBlank() || keyId == null || keyId.isBlank()
                || secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("Internal service identity requires a name, key id and 32+ byte secret");
        }
        this.serviceName = serviceName;
        this.keyId = keyId;
        this.secret = secret;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        URI uri = request.getURI();
        var signatureHeaders = InternalRequestSigner.headers(request.getMethod().name(), uri.getRawPath(), body,
                serviceName, keyId, secret);
        HttpHeaders headers = request.getHeaders();
        signatureHeaders.forEach(headers::set);
        return execution.execute(request, body);
    }
}
