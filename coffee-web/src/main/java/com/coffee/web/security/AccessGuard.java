package com.coffee.web.security;

import com.coffee.common.core.exception.ServiceException;

/** Controller 使用的资源归属校验。 */
public final class AccessGuard {
    private AccessGuard() {}

    public static void requireUser(Long userId) {
        RequestIdentity identity = requireIdentity();
        if (identity.kind() != RequestIdentity.Kind.USER || !identity.id().equals(userId)) {
            throw new ServiceException(403, "无权访问其他用户的数据");
        }
    }

    public static void requireMerchant(Long merchantId) {
        RequestIdentity identity = requireIdentity();
        if (identity.kind() != RequestIdentity.Kind.MERCHANT || !identity.id().equals(merchantId)) {
            throw new ServiceException(403, "无权访问其他商家的数据");
        }
    }

    public static Long currentMerchantId() {
        RequestIdentity identity = requireIdentity();
        if (identity.kind() != RequestIdentity.Kind.MERCHANT) {
            throw new ServiceException(403, "需要商家身份");
        }
        return identity.id();
    }

    public static RequestIdentity currentIdentity() {
        return requireIdentity();
    }

    public static void requireGuest(String guestId) {
        RequestIdentity identity = requireIdentity();
        if (identity.kind() != RequestIdentity.Kind.GUEST || !identity.guestId().equals(guestId)) {
            throw new ServiceException(403, "无权访问其他游客的数据");
        }
    }

    private static RequestIdentity requireIdentity() {
        RequestIdentity identity = RequestIdentityHolder.get();
        if (identity == null) {
            throw new ServiceException(401, "请先登录");
        }
        return identity;
    }
}
