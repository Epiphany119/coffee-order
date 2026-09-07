package com.coffee.module.auth.biz.application.service;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.auth.api.dto.AuthResponse;
import com.coffee.module.auth.api.dto.EmailCodePurpose;
import com.coffee.module.auth.api.dto.UserPasswordUpdateRequest;
import com.coffee.module.auth.biz.domain.User;
import com.coffee.module.auth.biz.domain.repository.PasswordResetTokenRepository;
import com.coffee.module.auth.biz.domain.repository.UserEmailRepository;
import com.coffee.module.auth.biz.domain.repository.UserRepository;
import com.coffee.module.auth.biz.infra.security.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthApplicationServicePasswordTest {

    private UserRepository users;
    private UserEmailRepository emails;
    private PasswordResetTokenRepository resetTokens;
    private EmailVerificationService verification;
    private AuthApplicationService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        emails = mock(UserEmailRepository.class);
        resetTokens = mock(PasswordResetTokenRepository.class);
        verification = mock(EmailVerificationService.class);
        service = new AuthApplicationService(users, emails, resetTokens, verification);
    }

    @Test
    void existingPasswordCanBeChangedOnlyAfterCurrentPasswordMatches() {
        User user = user(16L, PasswordEncoder.encode("Old123"));
        when(users.findById(16L)).thenReturn(user);

        UserPasswordUpdateRequest wrong = request("Wrong123", null, null, "New456", "New456");
        AuthResponse rejected = service.updatePassword(16L, wrong);
        assertFalse(rejected.isSuccess());
        assertEquals("原密码错误，请重新输入或使用邮箱验证", rejected.getMessage());
        verify(users, never()).updatePassword(anyLong(), anyString());

        UserPasswordUpdateRequest valid = request("Old123", null, null, "New456", "New456");
        AuthResponse changed = service.updatePassword(16L, valid);
        assertTrue(changed.isSuccess());
        assertTrue(changed.isPasswordSet());
        assertEquals("密码修改成功，请重新登录", changed.getMessage());
        verify(users).updatePassword(eq(16L), startsWith("$2"));
        verify(resetTokens).invalidatePreviousTokens(16L);
    }

    @Test
    void firstPasswordRequiresCodeFromAnEmailBoundToTheSameUser() {
        User user = user(16L, null);
        when(users.findById(16L)).thenReturn(user);
        when(verification.normalizeEmail("amy@qq.com")).thenReturn("amy@qq.com");

        UserPasswordUpdateRequest request = request(null, "amy@qq.com", "123456", "First123", "First123");
        when(emails.findByEmail("amy@qq.com"))
                .thenReturn(new UserEmailRepository.UserEmailBinding(17L, "amy@qq.com", true));
        AuthResponse wrongOwner = service.updatePassword(16L, request);
        assertFalse(wrongOwner.isSuccess());
        assertEquals("只能使用当前账户已绑定的邮箱进行验证", wrongOwner.getMessage());

        when(emails.findByEmail("amy@qq.com"))
                .thenReturn(new UserEmailRepository.UserEmailBinding(16L, "amy@qq.com", true));
        when(verification.verify("amy@qq.com", EmailCodePurpose.PASSWORD_CHANGE, "123456"))
                .thenReturn(true);
        AuthResponse created = service.updatePassword(16L, request);
        assertTrue(created.isSuccess());
        assertEquals("密码设置成功，请重新登录", created.getMessage());
        verify(users).updatePassword(eq(16L), startsWith("$2"));
    }

    @Test
    void refusesToUnbindLastSignInMethodWhenPasswordIsMissing() {
        User user = user(16L, "");
        user.setEmail("amy@qq.com");
        user.setEmails(List.of("amy@qq.com"));
        when(users.findById(16L)).thenReturn(user);
        when(emails.findEmailsByUserId(16L)).thenReturn(List.of("amy@qq.com"));

        AuthResponse response = service.unbindEmail(16L, "amy@qq.com");

        assertFalse(response.isSuccess());
        assertEquals("请先设置登录密码，再解绑最后一个邮箱", response.getMessage());
        verify(emails, never()).delete(anyLong(), anyString());
    }

    @Test
    void sendsPasswordCodeOnlyToCurrentUsersBoundEmail() {
        when(users.findById(16L)).thenReturn(user(16L, null));
        when(verification.normalizeEmail("amy@qq.com")).thenReturn("amy@qq.com");
        when(emails.findByEmail("amy@qq.com"))
                .thenReturn(new UserEmailRepository.UserEmailBinding(17L, "amy@qq.com", true));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.sendPasswordVerificationCode(16L, "amy@qq.com"));

        assertEquals(403, error.getCode());
        verify(verification, never()).send(anyString(), any());
    }

    private User user(Long id, String passwordHash) {
        User user = new User();
        user.setId(id);
        user.setAccountNo("fika0000000016");
        user.setUsername("Amy");
        user.setNickname("Amy");
        user.setPasswordHash(passwordHash);
        user.setTotalSpent(0.0);
        user.setEmails(List.of("amy@qq.com"));
        user.setEmail("amy@qq.com");
        return user;
    }

    private UserPasswordUpdateRequest request(String currentPassword, String email, String code,
                                              String newPassword, String confirmation) {
        UserPasswordUpdateRequest request = new UserPasswordUpdateRequest();
        request.setCurrentPassword(currentPassword);
        request.setEmail(email);
        request.setEmailCode(code);
        request.setNewPassword(newPassword);
        request.setConfirmPassword(confirmation);
        return request;
    }
}
