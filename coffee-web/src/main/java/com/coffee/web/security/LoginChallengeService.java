package com.coffee.web.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 短时、一次性登录验证码。验证码正文只以 SVG 图片返回，服务端保存校验值。 */
@Service
public class LoginChallengeService {
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 5;
    private static final long TTL_SECONDS = 120;
    private static final int MAX_ATTEMPTS = 5;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    public LoginChallenge issue() {
        purgeExpired();
        String id = UUID.randomUUID().toString().replace("-", "");
        String code = randomCode();
        challenges.put(id, new Challenge(code, Instant.now().getEpochSecond() + TTL_SECONDS, MAX_ATTEMPTS));
        return new LoginChallenge(id, toSvgDataUri(code), TTL_SECONDS);
    }

    /** 成功即销毁；失败最多允许五次，过期或不存在均按失败处理。 */
    public boolean verify(String id, String input) {
        if (id == null || input == null) return false;
        Challenge challenge = challenges.get(id);
        if (challenge == null || challenge.expiresAt < Instant.now().getEpochSecond()) {
            challenges.remove(id);
            return false;
        }
        if (challenge.code.equalsIgnoreCase(input.trim())) {
            challenges.remove(id);
            return true;
        }
        if (--challenge.remainingAttempts <= 0) challenges.remove(id);
        return false;
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        return code.toString();
    }

    private String toSvgDataUri(String code) {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            int y = 10 + random.nextInt(38);
            lines.append("<path d='M0 ").append(y).append(" L160 ").append(10 + random.nextInt(38))
                    .append("' stroke='#b68b69' stroke-width='1' opacity='.35'/>");
        }
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            int rotation = random.nextInt(21) - 10;
            int y = 35 + random.nextInt(12);
            letters.append("<text x='").append(14 + i * 27).append("' y='").append(y)
                    .append("' transform='rotate(").append(rotation).append(' ').append(14 + i * 27).append(' ').append(y)
                    .append(")' font-family='monospace' font-size='30' font-weight='700' fill='#173d31'>")
                    .append(code.charAt(i)).append("</text>");
        }
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='160' height='56' viewBox='0 0 160 56'>"
                + "<rect width='160' height='56' rx='8' fill='#f6eee5'/>" + lines + letters + "</svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private void purgeExpired() {
        long now = Instant.now().getEpochSecond();
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
    }

    private static final class Challenge {
        private final String code;
        private final long expiresAt;
        private int remainingAttempts;
        private Challenge(String code, long expiresAt, int remainingAttempts) {
            this.code = code; this.expiresAt = expiresAt; this.remainingAttempts = remainingAttempts;
        }
    }

    public record LoginChallenge(String challengeId, String imageDataUrl, long expiresInSeconds) { }
}
