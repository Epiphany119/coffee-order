package com.coffee.web.agent;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.web.security.RequestIdentity;

public final class AgentSceneAuthorization {
    private AgentSceneAuthorization() { }

    public static void requireCustomer(RequestIdentity identity) {
        if (identity == null || (identity.kind() != RequestIdentity.Kind.USER
                && identity.kind() != RequestIdentity.Kind.GUEST)) {
            throw new ServiceException(403, "Customer Agent requires USER or GUEST identity");
        }
    }

    public static void requireMerchant(RequestIdentity identity, Long merchantId) {
        if (identity == null || identity.kind() != RequestIdentity.Kind.MERCHANT
                || identity.id() == null || merchantId == null || !identity.id().equals(merchantId)) {
            throw new ServiceException(403, "Merchant Agent identity or ownership check failed");
        }
    }

    public static void requireScene(RequestIdentity identity, String scene, Long merchantId) {
        if ("customer".equals(scene)) {
            requireCustomer(identity);
        } else if ("merchant".equals(scene)) {
            requireMerchant(identity, merchantId);
        } else {
            throw new ServiceException(400, "Unsupported Agent scene");
        }
    }
}
