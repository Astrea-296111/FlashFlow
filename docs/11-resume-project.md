# 简历项目条目

**FlashFlow — 高并发限量票务秒杀与交易履约系统**  
Java 21 / Spring Boot / MySQL / Redis Lua / RocketMQ / Micrometer / k6

- 设计并实现限量票务的预占、异步下单、模拟支付、出票、超时关闭与退款流程；以 Redis Lua 原子预占、MySQL 条件扣库存及业务唯一约束保障库存与限购规则，三轮各 3,000 请求、100 张票的对照压测均无超卖、无重复订单。
- 在 8 核 CPU quota、8 GiB 内存、40 VU 的单机实验中，对比纯 MySQL 与 Redis + MQ 路径，三轮入口 RPS 中位数由 627 提升至 1,271；同时测得订单完成 P99 中位数由 291ms 增至 3,257ms，定位热门 SKU 行锁与消费持久化瓶颈，分别记录入口和完成指标。
- 构建 reservation 终态、业务幂等与库存释放 outbox，验证真实 Broker 额外 20 次重复投递只创建 1 单；Worker 停机时积压 20 个请求，恢复后约 12.93 秒全部落库（含 JVM 启动），并通过发送失败补偿及支付/关单竞争测试。
- 建立 42 项单元与真实 MySQL/Redis 集成测试，提供 k6 原始证据、Actuator 指标、Grafana 配置及 CI 工作流；复现并修复外键共享锁升级死锁和测试 Redis 隔离问题。

所有数字仅来自 [压测报告](06-benchmark-report.md) 与 [故障实验](07-failure-experiments.md)。完整 Docker Compose 运行与公网服务部署尚未验证；远端 CI 已通过，不写“已部署生产”或“CI 已通过”。面试主线：RepoPilot 展示 AI Agent 与评测工程，FlashFlow 展示 Java 交易正确性、一致性与性能分析。根据篇幅可保留前三条；若更强调正确性，可将性能条目的完整数字留到面试。
