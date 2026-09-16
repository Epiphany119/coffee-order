package com.coffee.web.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 浏览器与智谱 GLM-ASR-2512 之间的安全代理。
 *
 * <p>浏览器只上传 16kHz 单声道 PCM16 音频帧，API Key 永远留在服务端。
 * GLM-ASR-2512 的 HTTP 接口按音频文件转写，服务端将持续收到的音频滚动切成
 * 不超过 30 秒的 WAV 片段，顺序调用智谱 ASR，并把每段完整文本追加回浏览器。
 * 这样浏览器端不会因为 SpeechRecognition 自动结束而截断用户原话。</p>
 */
@Component
public class CustomerSpeechWebSocketHandler extends AbstractWebSocketHandler {
    private static final String PROXY_ATTRIBUTE = CustomerSpeechWebSocketHandler.class.getName() + ".proxy";
    private static final int SAMPLE_RATE = 16_000;
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int MAX_BROWSER_FRAME_BYTES = 100_000;
    private static final long[] RETRY_DELAYS_MS = {10L, 50L, 150L, 300L, 800L, 1_500L};
    private static final ExecutorService TRANSCRIPTION_EXECUTOR = Executors.newFixedThreadPool(
            4, daemonThreadFactory("fika-asr-"));

    private final ObjectMapper json;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String transcriptionEndpoint;
    private final String transcriptionModel;
    private final int segmentBytes;
    private final List<String> hotwords;

    public CustomerSpeechWebSocketHandler(ObjectMapper json,
                                          @Value("${coffee.ai.zhipu.api-key:}") String apiKey,
                                          @Value("${coffee.ai.zhipu.transcription-endpoint:https://open.bigmodel.cn/api/paas/v4/audio/transcriptions}") String transcriptionEndpoint,
                                          @Value("${coffee.ai.zhipu.transcription-model:glm-asr-2512}") String transcriptionModel,
                                          @Value("${coffee.ai.zhipu.transcription-segment-seconds:20}") int segmentSeconds,
                                          @Value("${coffee.ai.zhipu.transcription-hotwords:}") String hotwords) {
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.transcriptionEndpoint = transcriptionEndpoint;
        this.transcriptionModel = transcriptionModel == null || transcriptionModel.isBlank()
                ? "glm-asr-2512" : transcriptionModel.trim();
        int safeSeconds = Math.max(1, Math.min(29, segmentSeconds));
        this.segmentBytes = safeSeconds * SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8);
        this.hotwords = parseHotwords(hotwords);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        if (apiKey.isBlank()) {
            sendBrowser(session, errorPayload("流式语音识别服务尚未配置", false));
            closeBrowser(session, CloseStatus.SERVER_ERROR);
            return;
        }
        Proxy proxy = new Proxy(session);
        session.getAttributes().put(PROXY_ATTRIBUTE, proxy);
        proxy.ready();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Object value = session.getAttributes().get(PROXY_ATTRIBUTE);
        if (value instanceof Proxy proxy) proxy.handleClientMessage(message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Object value = session.getAttributes().get(PROXY_ATTRIBUTE);
        if (value instanceof Proxy proxy) proxy.close();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object value = session.getAttributes().get(PROXY_ATTRIBUTE);
        if (value instanceof Proxy proxy) proxy.close();
    }

    private final class Proxy {
        private final WebSocketSession browser;
        private final Object lock = new Object();
        /** 已被 Java 服务接收的客户端帧，用于浏览器重发时幂等确认。 */
        private final Set<Long> acceptedSequences = new HashSet<>();
        private final ArrayDeque<AudioChunk> transcriptionQueue = new ArrayDeque<>();
        private final ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();

        private long nextChunkSequence = 1;
        private boolean transcriptionInFlight;
        private boolean commitRequested;
        private boolean closed;
        private String previousTranscript = "";

        private Proxy(WebSocketSession browser) {
            this.browser = browser;
        }

        private void ready() {
            sendBrowser(statusPayload("已连接智谱 GLM-ASR-2512，正在持续接收语音…", true));
        }

        private void sendBrowser(String payload) {
            CustomerSpeechWebSocketHandler.this.sendBrowser(browser, payload);
        }

        private void handleClientMessage(String payload) {
            try {
                JsonNode message = json.readTree(payload);
                if (message == null || !message.isObject()) {
                    sendBrowser(errorPayload("语音事件格式无效", false));
                    return;
                }
                String type = message.path("type").asText("");
                if ("input_audio_buffer.append".equals(type)) {
                    handleAudioFrame(message);
                    return;
                }
                if ("input_audio_buffer.commit".equals(type)) {
                    commitAudio();
                    return;
                }
                if ("close".equals(type)) {
                    close();
                    return;
                }
                sendBrowser(errorPayload("不支持的语音事件", false));
            } catch (Exception error) {
                sendBrowser(errorPayload("语音事件解析失败", false));
            }
        }

        private void handleAudioFrame(JsonNode message) {
            long sequence = message.path("seq").asLong(-1);
            String encodedAudio = message.path("audio").asText("");
            if (sequence < 0 || encodedAudio.isBlank() || encodedAudio.length() > 140_000) {
                sendBrowser(errorPayload("语音音频帧格式无效", false));
                return;
            }

            byte[] audio;
            try {
                audio = Base64.getDecoder().decode(encodedAudio);
            } catch (IllegalArgumentException error) {
                sendBrowser(errorPayload("语音音频帧不是有效的 Base64", false));
                return;
            }
            if (audio.length == 0 || audio.length > MAX_BROWSER_FRAME_BYTES
                    || audio.length % (CHANNELS * (BITS_PER_SAMPLE / 8)) != 0) {
                sendBrowser(errorPayload("语音音频帧大小无效", false));
                return;
            }

            synchronized (lock) {
                if (closed) return;
                // 浏览器可能因为 WebSocket 重连重复发送同一序号，不能重复计入音频。
                if (acceptedSequences.add(sequence)) {
                    pcmBuffer.write(audio, 0, audio.length);
                    enqueueFullSegments();
                }
            }

            ObjectNode ack = json.createObjectNode();
            ack.put("type", "audio.ack");
            ack.put("seq", sequence);
            sendBrowser(ack.toString());
            startNextTranscription();
        }

        private void commitAudio() {
            boolean finished;
            synchronized (lock) {
                if (closed) return;
                commitRequested = true;
                if (pcmBuffer.size() > 0) {
                    transcriptionQueue.addLast(new AudioChunk(nextChunkSequence++, takePcm(pcmBuffer.size())));
                }
                finished = !transcriptionInFlight && transcriptionQueue.isEmpty();
            }
            sendBrowser(statusPayload("音频已完整接收，正在提交最后一段识别…", false));
            if (finished) sendBrowser(completedPayload());
            startNextTranscription();
        }

        /** 将完整的滚动片段移入队列，剩余不足一段的音频等待 commit。 */
        private void enqueueFullSegments() {
            while (pcmBuffer.size() >= segmentBytes) {
                transcriptionQueue.addLast(new AudioChunk(nextChunkSequence++, takePcm(segmentBytes)));
            }
        }

        private byte[] takePcm(int length) {
            byte[] all = pcmBuffer.toByteArray();
            byte[] chunk = Arrays.copyOfRange(all, 0, length);
            pcmBuffer.reset();
            if (all.length > length) pcmBuffer.write(all, length, all.length - length);
            return chunk;
        }

        private void startNextTranscription() {
            AudioChunk chunk;
            String prompt;
            synchronized (lock) {
                if (closed || transcriptionInFlight || transcriptionQueue.isEmpty()) return;
                transcriptionInFlight = true;
                chunk = transcriptionQueue.removeFirst();
                prompt = previousTranscript;
            }
            sendBrowser(statusPayload("正在使用智谱 ASR 识别第 " + chunk.sequence() + " 段完整语音…", false));
            String previous = prompt;
            TRANSCRIPTION_EXECUTOR.execute(() -> transcribeChunk(chunk, previous));
        }

        private void transcribeChunk(AudioChunk chunk, String prompt) {
            try {
                String transcript = transcribeWithRetry(chunk.pcm(), prompt);
                if (!transcript.isBlank()) {
                    synchronized (lock) {
                        previousTranscript = appendPrompt(previousTranscript, transcript);
                    }
                    ObjectNode event = json.createObjectNode();
                    event.put("type", "conversation.item.input_audio_transcription.completed");
                    event.put("event_id", UUID.randomUUID().toString());
                    event.put("item_id", "fika-asr-chunk-" + chunk.sequence());
                    event.put("content_index", 0);
                    event.put("transcript", transcript);
                    sendBrowser(event.toString());
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                fail("智谱语音识别被中断，请稍后重试");
            } catch (Exception error) {
                fail("智谱语音识别失败：" + safeMessage(error));
            } finally {
                boolean finished;
                synchronized (lock) {
                    transcriptionInFlight = false;
                    finished = !closed && commitRequested
                            && pcmBuffer.size() == 0 && transcriptionQueue.isEmpty();
                }
                if (finished) {
                    sendBrowser(statusPayload("语音识别完成，请检查文字后再发送", true));
                    sendBrowser(completedPayload());
                }
                startNextTranscription();
            }
        }

        private String transcribeWithRetry(byte[] pcm, String prompt) throws Exception {
            Exception last = null;
            for (int attempt = 0; attempt < RETRY_DELAYS_MS.length; attempt++) {
                if (attempt > 0) Thread.sleep(RETRY_DELAYS_MS[attempt - 1]);
                try {
                    return requestAsr(pcm, prompt);
                } catch (AsrHttpException error) {
                    if (!error.retryable()) throw error;
                    last = error;
                } catch (IOException error) {
                    last = error;
                }
            }
            throw last == null ? new IOException("智谱语音识别请求失败") : last;
        }

        private String requestAsr(byte[] pcm, String prompt) throws IOException, InterruptedException {
            String boundary = "----FikaAsr" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = multipartBody(boundary, wavBytes(pcm), prompt);
            HttpRequest request = HttpRequest.newBuilder(URI.create(transcriptionEndpoint))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                String message = response.body();
                try {
                    JsonNode root = json.readTree(response.body());
                    message = root.path("error").path("message").asText(message);
                } catch (Exception ignored) { }
                throw new AsrHttpException(response.statusCode(), message);
            }

            JsonNode root = json.readTree(response.body());
            // 一段音频可能主要是停顿或环境声；空文本是合法结果，不能把整次语音会话判成失败。
            return root.path("text").asText("").trim();
        }

        private byte[] multipartBody(String boundary, byte[] wav, String prompt) throws IOException {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeTextPart(body, boundary, "model", transcriptionModel);
            writeTextPart(body, boundary, "stream", "false");
            writeTextPart(body, boundary, "request_id", UUID.randomUUID().toString());
            if (prompt != null && !prompt.isBlank()) {
                writeTextPart(body, boundary, "prompt", prompt);
            }
            if (!hotwords.isEmpty()) {
                writeTextPart(body, boundary, "hotwords", json.writeValueAsString(hotwords));
            }
            body.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"fika-speech.wav\"\r\n"
                    + "Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(wav);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
            body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return body.toByteArray();
        }

        private void writeTextPart(ByteArrayOutputStream body, String boundary,
                                   String name, String value) throws IOException {
            body.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                    + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }

        private byte[] wavBytes(byte[] pcm) throws IOException {
            ByteArrayOutputStream wav = new ByteArrayOutputStream(44 + pcm.length);
            wav.write("RIFF".getBytes(StandardCharsets.US_ASCII));
            writeLittleEndianInt(wav, 36 + pcm.length);
            wav.write("WAVE".getBytes(StandardCharsets.US_ASCII));
            wav.write("fmt ".getBytes(StandardCharsets.US_ASCII));
            writeLittleEndianInt(wav, 16);
            writeLittleEndianShort(wav, (short) 1);
            writeLittleEndianShort(wav, (short) CHANNELS);
            writeLittleEndianInt(wav, SAMPLE_RATE);
            writeLittleEndianInt(wav, SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8));
            writeLittleEndianShort(wav, (short) (CHANNELS * (BITS_PER_SAMPLE / 8)));
            writeLittleEndianShort(wav, (short) BITS_PER_SAMPLE);
            wav.write("data".getBytes(StandardCharsets.US_ASCII));
            writeLittleEndianInt(wav, pcm.length);
            wav.write(pcm);
            return wav.toByteArray();
        }

        private void writeLittleEndianInt(ByteArrayOutputStream output, int value) {
            output.write(value & 0xff);
            output.write((value >>> 8) & 0xff);
            output.write((value >>> 16) & 0xff);
            output.write((value >>> 24) & 0xff);
        }

        private void writeLittleEndianShort(ByteArrayOutputStream output, short value) {
            output.write(value & 0xff);
            output.write((value >>> 8) & 0xff);
        }

        private void fail(String message) {
            synchronized (lock) {
                if (closed) return;
                closed = true;
                pcmBuffer.reset();
                transcriptionQueue.clear();
            }
            sendBrowser(errorPayload(message, false));
        }

        private void close() {
            synchronized (lock) {
                if (closed) return;
                closed = true;
                pcmBuffer.reset();
                transcriptionQueue.clear();
                acceptedSequences.clear();
            }
        }

        private String appendPrompt(String current, String transcript) {
            String combined = (current + " " + transcript).trim();
            int maxPromptLength = 7_900;
            return combined.length() <= maxPromptLength
                    ? combined : combined.substring(combined.length() - maxPromptLength);
        }

        private String safeMessage(Exception error) {
            String message = error.getMessage();
            return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
        }

        private record AudioChunk(long sequence, byte[] pcm) { }
    }

    private static final class AsrHttpException extends IOException {
        private final int status;

        private AsrHttpException(int status, String message) {
            super("HTTP " + status + (message == null || message.isBlank() ? "" : ": " + message));
            this.status = status;
        }

        private boolean retryable() {
            return status == 429 || status >= 500;
        }
    }

    private List<String> parseHotwords(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .limit(100)
                .toList();
    }

    private String statusPayload(String message, boolean ready) {
        ObjectNode payload = json.createObjectNode();
        payload.put("type", "speech.status");
        payload.put("message", message);
        payload.put("ready", ready);
        return payload.toString();
    }

    private String errorPayload(String message, boolean recoverable) {
        ObjectNode payload = json.createObjectNode();
        payload.put("type", "speech.error");
        payload.put("message", message);
        payload.put("recoverable", recoverable);
        return payload.toString();
    }

    private String completedPayload() {
        ObjectNode payload = json.createObjectNode();
        payload.put("type", "speech.completed");
        payload.put("message", "所有语音片段已完成识别");
        return payload.toString();
    }

    private void sendBrowser(WebSocketSession session, String payload) {
        synchronized (session) {
            if (!session.isOpen()) return;
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException ignored) { }
        }
    }

    private void closeBrowser(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) session.close(status);
        } catch (IOException ignored) { }
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + UUID.randomUUID());
            thread.setDaemon(true);
            return thread;
        };
    }
}
