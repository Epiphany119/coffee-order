package com.coffee.common.internal;

/** Header names shared by service-to-service authentication. */
public final class InternalAuthHeaders {
    public static final String SERVICE_NAME = "X-Service-Name";
    public static final String KEY_ID = "X-Key-Id";
    public static final String TIMESTAMP = "X-Timestamp";
    public static final String NONCE = "X-Nonce";
    public static final String CONTENT_SHA256 = "X-Content-SHA256";
    public static final String SIGNATURE = "X-Service-Signature";

    private InternalAuthHeaders() {
    }
}
