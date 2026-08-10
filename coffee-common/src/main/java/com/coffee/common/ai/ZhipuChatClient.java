package com.coffee.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/** 轻量的智谱 Chat Completions 客户端；密钥只从运行环境读取。 */
@Component
public class ZhipuChatClient {
    private static final Logger log = LoggerFactory.getLogger(ZhipuChatClient.class);
    private final ObjectMapper json;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String endpoint;
    private final String model;
    private final Duration timeout;
    /** 同一 API Key 串行调用，避免多个页面/连点同时打满平台并发额度。 */
    private final Semaphore requestPermit = new Semaphore(1);
    private volatile long rateLimitedUntilMillis;

    public ZhipuChatClient(ObjectMapper json,
                           @Value("${coffee.ai.zhipu.api-key:}") String apiKey,
                           @Value("${coffee.ai.zhipu.endpoint:https://open.bigmodel.cn/api/paas/v4/chat/completions}") String endpoint,
                           @Value("${coffee.ai.zhipu.model:glm-5.2}") String model,
                           @Value("${coffee.ai.zhipu.timeout-seconds:12}") int timeoutSeconds) {
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.endpoint = endpoint;
        this.model = model;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        if (this.apiKey.isBlank()) log.warn("Zhipu client is disabled: no API key configured");
        else log.info("Zhipu client configured: keyId={}, model={}", keyId(), this.model);
    }

    public Optional<String> chat(String systemPrompt, String userPrompt) {
        if (apiKey.isBlank() || System.currentTimeMillis() < rateLimitedUntilMillis) return Optional.empty();
        if (!requestPermit.tryAcquire()) return Optional.empty();
        try {
            String body = json.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", 0.4,
                    // 流式响应能先建立连接再输出内容，避免模型生成完成前触发 HTTP 超时。
                    "stream", true,
                    // 点单/经营说明不需要长链路推理，直接生成最终文本以确保交互响应速度。
                    "thinking", Map.of("type", "disabled"),
                    "max_tokens", 160,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                if (response.statusCode() == 429) {
                    long retrySeconds = response.headers().firstValue("Retry-After")
                            .flatMap(this::positiveLong).orElse(30L);
                    rateLimitedUntilMillis = System.currentTimeMillis() + retrySeconds * 1000;
                    log.warn("Zhipu rate limited; pausing model calls for {} seconds", retrySeconds);
                } else {
                    log.warn("Zhipu chat request failed with HTTP status {}", response.statusCode());
                }
                return Optional.empty();
            }
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) break;
                    JsonNode delta = json.readTree(data).at("/choices/0/delta/content");
                    if (delta.isTextual()) {
                        content.append(delta.asText());
                        // 本项目只需要 1-2 句说明；拿到完整首句就释放 HTTP 连接，避免尾部生成拖慢页面。
                        if (content.length() >= 16 && endsSentence(content)) break;
                    }
                }
            }
            if (!content.isEmpty()) return Optional.of(content.toString().trim());
            log.warn("Zhipu stream completed without displayable content");
        } catch (Exception e) {
            // Agent 能力必须可降级，不能因第三方模型故障影响下单或营销流程。
            if (e instanceof HttpTimeoutException) {
                rateLimitedUntilMillis = System.currentTimeMillis() + 30_000;
                log.warn("Zhipu request timed out; pausing model calls for 30 seconds");
            }
            log.warn("Zhipu chat request failed: {}", e.getClass().getSimpleName());
        } finally {
            requestPermit.release();
        }
        return Optional.empty();
    }

    private boolean endsSentence(StringBuilder text) {
        char last = text.charAt(text.length() - 1);
        return last == '。' || last == '！' || last == '？' || last == '!' || last == '?';
    }

    private Optional<Long> positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private String keyId() {
        int separator = apiKey.indexOf('.');
        return separator > 0 ? apiKey.substring(0, separator) : "configured";
    }
}
