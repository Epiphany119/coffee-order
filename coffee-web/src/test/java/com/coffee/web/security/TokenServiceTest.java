package com.coffee.web.security;

import com.coffee.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {
    private final TokenService service = new TokenService("test-secret-at-least-thirty-two-characters-long-2026", 3600);

    @Test
    void issuesAndVerifiesEachIdentityKind() {
        assertEquals(42L, service.verify(service.issueUser(42L)).id());
        assertEquals(RequestIdentity.Kind.MERCHANT, service.verify(service.issueMerchant(8L)).kind());
        assertEquals("g-demo", service.verify(service.issueGuest("g-demo")).guestId());
    }

    @Test
    void rejectsTamperedToken() {
        String token = service.issueUser(42L);
        String tampered = token.substring(0, token.length() - 1) + "x";
        assertThrows(ServiceException.class, () -> service.verify(tampered));
    }
}
