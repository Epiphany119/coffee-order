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

1. 管理端调用 `POST /api/business-agent/knowledge/documents`，原始文档先写入 MySQL。
2. Spring Boot 调用智谱 `embedding-3` 生成向量，并以 MySQL 文档 ID 写入 Milvus。
3. 用户提问时，问题会被向量化，Milvus 用 COSINE 检索 Top-K；服务端再回查 MySQL 原文，保证内容和权限均以业务库为准。
4. Milvus、Embedding 服务或网络异常时，自动使用 MySQL 关键词检索，点单和 Agent 主流程不会中断。

> 不要把 API Key 写入 `application.yml` 或提交到 Git。生产环境应设置 `MILVUS_TOKEN` 并启用 Milvus 鉴权。
