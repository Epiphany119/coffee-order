# Milvus RAG 知识库

本项目的知识库采用“双存储”模型：MySQL 保存原始业务文本、标题、来源和门店权限；Milvus 仅保存 `document_id`、`store_id` 与 Embedding 向量，用于语义近邻检索。

## 启动

```bash
docker compose -f docker-compose.milvus.yml up -d
docker compose -f docker-compose.milvus.yml ps
curl http://localhost:9091/healthz
```

启动后可在 http://localhost:8000 打开 Attu 管理界面。首次写入知识文档时，应用会自动创建 `agent_knowledge_vector` collection、COSINE AUTOINDEX 和加载该 collection。

## 配置

```bash
export ZHIPU_API_KEY='你的智谱 API Key'
export ZHIPU_MODEL='glm-4.5-air'
export ZHIPU_EMBEDDING_MODEL='embedding-3'
export ZHIPU_EMBEDDING_DIMENSIONS=512
export MILVUS_HOST=localhost
export MILVUS_PORT=19530
```

Embedding 维度必须与 Milvus collection 的向量维度完全一致。调整 `ZHIPU_EMBEDDING_DIMENSIONS` 后，应先删除旧 collection 再重新导入知识文档。

## RAG 流程

1. 商家调用 `POST /api/merchant/{merchantId}/growth-agent/knowledge/documents`，原始文档和可见范围先写入 MySQL。
2. Spring Boot 调用智谱 `embedding-3` 生成向量，并以 MySQL 文档 ID 写入 Milvus。
3. 顾客检索只回查 `CUSTOMER_PUBLIC` 且属于全局或当前门店的文档；店长检索只回查其所属门店的公开与内部文档。Milvus 候选 ID 不作为授权依据，最终内容与权限均以 MySQL 为准。
4. Milvus、Embedding 服务或网络异常时，自动使用 MySQL 关键词检索，并执行相同的可见范围过滤。

> 不要把 API Key 写入 `application.yml` 或提交到 Git。生产环境应设置 `MILVUS_TOKEN` 并启用 Milvus 鉴权。

新建知识默认为 `MERCHANT_INTERNAL`；只有明确标注 `CUSTOMER_PUBLIC` 的文档会进入顾客回答。执行 `sql/migrations/V20261001_27_ai_agent_consolidation.sql` 后可使用该字段。