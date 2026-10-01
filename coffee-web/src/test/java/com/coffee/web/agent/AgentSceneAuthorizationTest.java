package com.coffee.web.agent;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.web.security.RequestIdentity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentSceneAuthorizationTest {
    @Test
    void customerSceneAcceptsOnlyUserAndGuest() {
        assertDoesNotThrow(() -> AgentSceneAuthorization.requireCustomer(
                new RequestIdentity(RequestIdentity.Kind.USER, 10L, null)));
        assertDoesNotThrow(() -> AgentSceneAuthorization.requireCustomer(
                new RequestIdentity(RequestIdentity.Kind.GUEST, null, "guest-id")));
        assertThrows(ServiceException.class, () -> AgentSceneAuthorization.requireCustomer(
                new RequestIdentity(RequestIdentity.Kind.MERCHANT, 10L, null)));
        assertThrows(ServiceException.class, () -> AgentSceneAuthorization.requireCustomer(
                new RequestIdentity(RequestIdentity.Kind.RIDER, 10L, null)));
    }

    @Test
    void merchantSceneBindsIdentityToPathMerchantId() {
        assertDoesNotThrow(() -> AgentSceneAuthorization.requireMerchant(
                new RequestIdentity(RequestIdentity.Kind.MERCHANT, 10L, null), 10L));
        assertThrows(ServiceException.class, () -> AgentSceneAuthorization.requireMerchant(
                new RequestIdentity(RequestIdentity.Kind.MERCHANT, 11L, null), 10L));
        assertThrows(ServiceException.class, () -> AgentSceneAuthorization.requireMerchant(
                new RequestIdentity(RequestIdentity.Kind.USER, 10L, null), 10L));
    }
    @Test
    void rejectsUnknownAgentScene() {
        assertThrows(ServiceException.class, () -> AgentSceneAuthorization.requireScene(
                new RequestIdentity(RequestIdentity.Kind.USER, 10L, null), "admin", null));
    }}
