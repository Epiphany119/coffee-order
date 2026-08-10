package com.coffee.web.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Milvus 只保存 MySQL 文档主键、门店隔离字段和向量；原文始终以 MySQL 为准。
 * 所有异常由调用方降级处理，向量库不可用不能影响点单主流程。
 */
@Component
public class MilvusKnowledgeVectorStore {
    private static final Logger log = LoggerFactory.getLogger(MilvusKnowledgeVectorStore.class);
    private static final String ID_FIELD = "document_id";
    private static final String STORE_FIELD = "store_id";
    private static final String VECTOR_FIELD = "embedding";
    private static final long GLOBAL_STORE_ID = -1L;

    private final boolean enabled;
    private final String collection;
    private final int dimensions;
    private final MilvusClientV2 client;
    private volatile boolean collectionReady;

    public MilvusKnowledgeVectorStore(
            @Value("${coffee.ai.milvus.enabled:true}") boolean enabled,
            @Value("${coffee.ai.milvus.host:localhost}") String host,
            @Value("${coffee.ai.milvus.port:19530}") int port,
            @Value("${coffee.ai.milvus.collection:agent_knowledge_vector}") String collection,
            @Value("${coffee.ai.milvus.token:}") String token,
            @Value("${coffee.ai.milvus.connect-timeout-seconds:5}") int timeoutSeconds,
            @Value("${coffee.ai.zhipu.embedding-dimensions:512}") int dimensions) {
        this.enabled = enabled;
        this.collection = collection;
        this.dimensions = dimensions;
        this.client = enabled ? new MilvusClientV2(ConnectConfig.builder()
                .uri("http://" + host + ":" + port)
                .token(token == null || token.isBlank() ? null : token.trim())
                .connectTimeoutMs(Math.max(1, timeoutSeconds) * 1000L)
                .rpcDeadlineMs(Math.max(1, timeoutSeconds) * 1000L)
                .build()) : null;
    }

    public boolean upsert(long documentId, Long storeId, List<Double> vector) {
        if (!enabled || !valid(vector)) return false;
        try {
            ensureCollection();
            JsonObject row = new JsonObject();
            row.addProperty(ID_FIELD, documentId);
            row.addProperty(STORE_FIELD, storeId == null ? GLOBAL_STORE_ID : storeId);
            JsonArray embedding = new JsonArray();
            vector.forEach(value -> embedding.add(value.floatValue()));
            row.add(VECTOR_FIELD, embedding);
            client.upsert(UpsertReq.builder().collectionName(collection).data(List.of(row)).build());
            return true;
        } catch (Exception ex) {
            log.warn("Milvus upsert skipped: {}", ex.getClass().getSimpleName());
            return false;
        }
    }

    public Optional<List<VectorHit>> search(List<Double> vector, Long storeId, int limit) {
        if (!enabled || !valid(vector)) return Optional.empty();
        try {
            ensureCollection();
            List<Float> queryVector = vector.stream().map(Double::floatValue).toList();
            String filter = storeId == null ? STORE_FIELD + " == " + GLOBAL_STORE_ID
                    : "(" + STORE_FIELD + " == " + GLOBAL_STORE_ID + " || " + STORE_FIELD + " == " + storeId + ")";
            SearchResp response = client.search(SearchReq.builder()
                    .collectionName(collection)
                    .annsField(VECTOR_FIELD)
                    .data(List.of(new FloatVec(queryVector)))
                    .metricType(IndexParam.MetricType.COSINE)
                    .filter(filter)
                    .topK(Math.max(1, Math.min(limit, 10)))
                    .build());
            List<VectorHit> hits = new ArrayList<>();
            if (!response.getSearchResults().isEmpty()) {
                for (SearchResp.SearchResult hit : response.getSearchResults().get(0)) {
                    hits.add(new VectorHit(((Number) hit.getId()).longValue(), hit.getScore()));
                }
            }
            return Optional.of(hits);
        } catch (Exception ex) {
            log.warn("Milvus search skipped: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private synchronized void ensureCollection() {
        if (collectionReady) return;
        if (Boolean.TRUE.equals(client.hasCollection(HasCollectionReq.builder().collectionName(collection).build()))) {
            collectionReady = true;
            return;
        }
        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        schema.addField(AddFieldReq.builder().fieldName(ID_FIELD).dataType(DataType.Int64)
                .isPrimaryKey(true).autoID(false).build());
        schema.addField(AddFieldReq.builder().fieldName(STORE_FIELD).dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName(VECTOR_FIELD).dataType(DataType.FloatVector)
                .dimension(dimensions).build());
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collection)
                .description("FIKA RAG knowledge document embeddings")
                .collectionSchema(schema)
                .indexParams(List.of(IndexParam.builder().fieldName(VECTOR_FIELD)
                        .indexType(IndexParam.IndexType.AUTOINDEX)
                        .metricType(IndexParam.MetricType.COSINE).build()))
                .build());
        collectionReady = true;
        log.info("Milvus collection {} is ready, dimension={}", collection, dimensions);
    }

    private boolean valid(List<Double> vector) {
        return vector != null && vector.size() == dimensions && vector.stream().allMatch(value -> value != null && Double.isFinite(value));
    }

    public record VectorHit(long documentId, float score) { }
}
