# 可观测性

Micrometer 导出 HTTP、JVM、GC、进程 CPU、Hikari 连接池与业务计数器。Prometheus 抓取 api:8080 与 worker:8080；Grafana 自动加载 [flashflow.json](../deploy/grafana/dashboards/flashflow.json)。监控 profile 配置已生成，但本环境没有运行 Prometheus/Grafana 容器，没有伪造仪表盘截图。

| 指标 | 含义与注意点 |
|---|---|
| flashflow_seckill_total{outcome} | 异步资格判断次数；包含被拒绝请求 |
| flashflow_order_created_total | worker 新建订单；baseline 当前没有该专用计数器 |
| flashflow_message_duplicate_total | 重放后返回已有订单的次数 |
| flashflow_order_completion_seconds_bucket | Redis 资格产生到 DB 提交/Redis 确认，区别于 k6 SQL 观察时延 |
| flashflow_producer_failure_total | 发送结果不确定次数，不等于消息必然丢失 |
| flashflow_compensation_total | 实际发生 Redis 补偿/修复的次数 |
| flashflow_outbox_retry_total | 超时 outbox 发送异常次数 |
| flashflow_stock_released_total | Redis 释放 relay 成功处理次数 |
| http_server_requests_seconds_* | API 总响应计时；不能证明订单持久化完成 |
| hikaricp_connections_active / pending | 连接使用与等待；短 burst 有采样缓存滞后 |

业务状态验收依赖 SQL 与 Redis 审计，计数器不代替库存事实。k6 运行每 0.5s 取 API/worker 的 CPU ratio、heap used、live threads、GC count 和连接池样本，保留 resource-samples.json；结束时存完整 Prometheus 快照。这是稀疏采样和近期平均 CPU ratio，不是瞬时峰值、宿主机总利用率或独立资源归因。

Broker backlog 当前通过官方 mqadmin consumerProgress 保存原始 queue offset 证据，应用没有伪装成实现了 MQ lag Prometheus exporter。Redis INFO、Broker JMX、数据库慢查询持续采样和分布式 tracing 留在后续计划。

默认 Actuator health/prometheus 公开用于本机监测。部署到公网需要网络层隔离管理端点；不在指标标签中加入 userId、reservationId、orderNo，避免高基数与交易明细外泄。日志仅记录失败类别和隔离消息 ID，不打印 JWT、密码和原始支付请求。
