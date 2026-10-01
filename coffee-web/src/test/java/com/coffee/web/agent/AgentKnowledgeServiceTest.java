package com.coffee.web.agent;

import com.coffee.common.ai.ZhipuEmbeddingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentKnowledgeServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private ZhipuEmbeddingClient embeddingClient;
    @Mock private MilvusKnowledgeVectorStore vectorStore;
    @Mock private KnowledgeEmbeddingSyncService embeddingSync;

    private AgentKnowledgeService service;

    @BeforeEach
    void setUp() {
        service = new AgentKnowledgeService(jdbc, embeddingClient, vectorStore, embeddingSync);
        when(embeddingClient.embed(anyList())).thenReturn(Optional.empty());
    }

    @Test
    void customerKeywordFallbackRestrictsResultsToPublicKnowledgeInCurrentStore() {
        service.retrieve("refund policy", 42L, 4);

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(query.capture(), any(Object[].class));

        assertTrue(query.getValue().contains("visibility = 'CUSTOMER_PUBLIC'"));
        assertTrue(query.getValue().contains("(store_id IS NULL OR store_id = ?)"));
        assertTrue(!query.getValue().contains("MERCHANT_INTERNAL"));
    }

    @Test
    void merchantKeywordFallbackRestrictsResultsToOwnedStore() {
        service.retrieveForMerchant("promotion policy", 42L, 4);

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(query.capture(), any(Object[].class));

        assertTrue(query.getValue().contains("store_id = ?"));
        assertTrue(query.getValue().contains("visibility IN ('CUSTOMER_PUBLIC','MERCHANT_INTERNAL')"));
    }
}