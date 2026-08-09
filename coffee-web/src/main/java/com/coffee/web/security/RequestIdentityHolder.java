package com.coffee.web.security;

/** 当前请求身份的线程本地容器。 */
public final class RequestIdentityHolder {
    private static final ThreadLocal<RequestIdentity> HOLDER = new ThreadLocal<>();

    private RequestIdentityHolder() {}

    public static RequestIdentity get() { return HOLDER.get(); }
    public static void set(RequestIdentity identity) { HOLDER.set(identity); }
    public static void clear() { HOLDER.remove(); }
}
