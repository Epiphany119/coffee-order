package com.coffee.common.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/** GLM embedding-3 HTTP 适配器；模型不可用时返回空，由业务检索降级到关键词排序。 */
@Component
public class ZhipuEmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(ZhipuEmbeddingClient.class);
    private final ObjectMapper json;
    private final HttpClient http;
    private final String apiKey, endpoint, model;
    private final int dimensions;
    private final Duration timeout;
    private final Semaphore permit = new Semaphore(2);

    public ZhipuEmbeddingClient(ObjectMapper json,
                                @Value("${coffee.ai.zhipu.api-key:}") String apiKey,
                                @Value("${coffee.ai.zhipu.embedding-endpoint:https://open.bigmodel.cn/api/paas/v4/embeddings}") String endpoint,
                                @Value("${coffee.ai.zhipu.embedding-model:embedding-3}") String model,
                                @Value("${coffee.ai.zhipu.embedding-dimensions:512}") int dimensions,
                                @Value("${coffee.ai.zhipu.timeout-seconds:5}") int timeoutSeconds) {
        this.json=json; this.apiKey=apiKey == null ? "" : apiKey.trim(); this.endpoint=endpoint; this.model=model;
        this.dimensions = List.of(256,512,1024,2048).contains(dimensions) ? dimensions : 512;
        this.timeout=Duration.ofSeconds(Math.max(1, timeoutSeconds)); this.http=HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    /** 每批最多 64 条，和 GLM embedding-3 的 API 限制保持一致。 */
    public Optional<List<List<Double>>> embed(List<String> inputs) {
        if (apiKey.isBlank() || inputs == null || inputs.isEmpty() || inputs.size() > 64 || !permit.tryAcquire()) return Optional.empty();
        try {
            String body=json.writeValueAsString(Map.of("model",model,"input",inputs,"dimensions",dimensions));
            HttpRequest request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeout)
                    .header("Authorization","Bearer "+apiKey).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()/100 != 2) { log.warn("Zhipu embedding request failed with HTTP status {}",response.statusCode()); return Optional.empty(); }
            JsonNode data=json.readTree(response.body()).path("data");
            List<JsonNode> ordered=new ArrayList<>(); data.forEach(ordered::add); ordered.sort(Comparator.comparingInt(node -> node.path("index").asInt()));
            List<List<Double>> vectors=new ArrayList<>();
            for(JsonNode item:ordered) vectors.add(json.convertValue(item.path("embedding"), new TypeReference<List<Double>>(){}));
            return vectors.size()==inputs.size() ? Optional.of(vectors) : Optional.empty();
        } catch(Exception e) { log.warn("Zhipu embedding request failed: {}",e.getClass().getSimpleName()); return Optional.empty(); }
        finally { permit.release(); }
    }
}
