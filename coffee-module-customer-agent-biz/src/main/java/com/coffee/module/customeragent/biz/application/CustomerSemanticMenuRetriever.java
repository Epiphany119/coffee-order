package com.coffee.module.customeragent.biz.application;

import com.coffee.common.ai.ZhipuEmbeddingClient;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Component
class CustomerSemanticMenuRetriever {
    private static final Logger log = LoggerFactory.getLogger(CustomerSemanticMenuRetriever.class);
    private final JdbcTemplate jdbc; private final ZhipuEmbeddingClient embeddings; private final ObjectMapper json;
    CustomerSemanticMenuRetriever(JdbcTemplate jdbc, ZhipuEmbeddingClient embeddings, ObjectMapper json) { this.jdbc=jdbc;this.embeddings=embeddings;this.json=json; }

    Map<String,Double> retrieve(Long storeId,String query,List<MenuItemDTO> products) {
        if (storeId==null || products.isEmpty()) return Map.of();
        try {
            List<String> texts=products.stream().map(this::document).toList(); List<String> hashes=texts.stream().map(this::hash).toList();
            Map<String,Row> cached=rows(storeId);
            List<Integer> stale=new ArrayList<>(); for(int i=0;i<products.size();i++){Row row=cached.get(products.get(i).getCode());if(row==null||!hashes.get(i).equals(row.hash))stale.add(i);}
            if(!stale.isEmpty()) {
                List<String> refresh=stale.stream().map(texts::get).toList();
                List<List<Double>> vectors = new ArrayList<>(Collections.nCopies(refresh.size(), null));
                for (int offset = 0; offset < refresh.size(); offset += 32) {
                    List<String> batch = refresh.subList(offset, Math.min(offset + 32, refresh.size()));
                    Optional<List<List<Double>>> batchResult = embeddings.embed(batch);
                    if (batchResult.isPresent()) {
                        List<List<Double>> batchVectors = batchResult.get();
                        for (int j = 0; j < batchVectors.size() && (offset + j) < vectors.size(); j++) {
                            vectors.set(offset + j, batchVectors.get(j));
                        }
                    } else {
                        log.warn("Embedding batch failed at offset {}, skipping {} items", offset, batch.size());
                    }
                }
                for(int i=0;i<stale.size();i++){
                    int at=stale.get(i);
                    List<Double> vec = vectors.get(i);
                    if (vec != null) {
                        jdbc.update("INSERT INTO agent_menu_embedding(store_id,product_code,text_hash,vector_json,updated_at) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE text_hash=VALUES(text_hash),vector_json=VALUES(vector_json),updated_at=VALUES(updated_at)",storeId,products.get(at).getCode(),hashes.get(at),json.writeValueAsString(vec),LocalDateTime.now());
                    }
                }
                cached=rows(storeId);
            }
            Optional<List<List<Double>>> queryVector=embeddings.embed(List.of(query)); if(queryVector.isEmpty()) return Map.of();
            Map<String,Double> scores=new HashMap<>(); for(MenuItemDTO product:products){Row row=cached.get(product.getCode());if(row!=null)scores.put(product.getCode(),cosine(queryVector.get().get(0),row.vector));} return scores;
        } catch(Exception e) { log.warn("Semantic retrieval failed: {}", e.getMessage()); return Map.of(); }
    }
    private Map<String,Row> rows(Long storeId) throws Exception {Map<String,Row> out=new HashMap<>();for(Map<String,Object> r:jdbc.queryForList("SELECT product_code,text_hash,vector_json FROM agent_menu_embedding WHERE store_id=?",storeId)){out.put(String.valueOf(r.get("product_code")),new Row(String.valueOf(r.get("text_hash")),json.readValue(String.valueOf(r.get("vector_json")),new TypeReference<List<Double>>(){})));}return out;}
    private String document(MenuItemDTO p){return "商品："+safe(p.getName())+"；品类："+safe(p.getCategoryCode())+"；描述："+safe(p.getDescription())+"；冷热："+safe(p.getTemperature());}
    private double cosine(List<Double>a,List<Double>b){if(a.size()!=b.size())return 0;double dot=0,na=0,nb=0;for(int i=0;i<a.size();i++){dot+=a.get(i)*b.get(i);na+=a.get(i)*a.get(i);nb+=b.get(i)*b.get(i);}return na==0||nb==0?0:dot/Math.sqrt(na*nb);}
    private String hash(String text){try{return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){return text;}}
    private String safe(String s){return s==null?"":s;}
    private record Row(String hash,List<Double> vector){}
}
