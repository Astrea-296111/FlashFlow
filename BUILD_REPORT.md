# FlashFlow 交付报告

## 已完成

模块化单体 API/Worker、认证与权限、活动票档、MySQL baseline、Redis Lua 预占、MQ 异步下单、reservation 终态补偿、幂等 release outbox、模拟支付/出票/退款、延迟关单、缓存/限流、指标、监控配置、测试/压测脚本、研究与学习文档。

## 未完成或未验证

GitHub 公开仓库已创建并上传完整文件；远端 CI 已触发，最终结果以 Actions 为准。原始开发提交历史由 Release 附件 bundle 保留。环境缺少 Docker daemon，Compose 和镜像构建未实测。无真实支付、Cluster、分片、多实例、高可用、真实 Broker 网络断链测试。版本 v1.0.0 仅为预期发布版本。

## 技术栈与验收

Java 21 / Boot 3.5.16 / MySQL 8.4.9 / Redis 7.4.2 / RocketMQ 5.3.3；其余依赖以 pom.xml 为准。42 项测试，失败和错误 0，MQ IT mocked，另有真实 Broker 故障证据。最终测试来源 benchmark/results/20260930T122848Z-tests-final/summary.json。



|方案/轮次|入口 RPS|入口 P95 ms|入口 P99 ms|成功请求入口 P99 ms|完成 P95 ms|完成 P99 ms|订单|
|---|---:|---:|---:|---:|---:|---:|---:|
|baseline/1|404.33|244.27|660.52|987.63|895.76|1013.46|100|
|optimized/1|1270.93|63.45|342.28|998.26|3628.77|3677.29|100|
|optimized/2|966.58|140.34|205.77|239.99|3226.60|3256.96|100|
|baseline/2|627.46|130.06|206.19|266.08|236.78|290.84|100|
|baseline/3|841.33|98.71|176.88|191.70|207.57|215.85|100|
|optimized/3|3089.52|32.71|50.56|70.72|1456.67|1460.76|100|

三轮每组均有 100 个订单，超卖 0、重复订单 0，数据库非负与库存守恒成立，异步组 Redis/DB 库存一致，k6 非预期错误率 0。来源：[comparison.json](benchmark/results/20260930T123027Z-paired-final/comparison.json)，[environment.json](benchmark/results/20260930T123027Z-paired-final/environment.json)。各轮保留 accepted.jsonl、completion.json、orders.csv、validation.json、k6-summary.json、Actuator 快照和资源采样。

三轮入口 RPS 中位数：baseline 627.46，optimized 1270.93（2.03 倍）；这是各轮汇总值的中位数，不是合并样本。入口 P99 中位数 206.19 → 205.77ms，差异很小；不能宣称显著降低 P99。订单完成 P99 中位数 290.84 → 3256.96ms，异步方案更慢。入口吞吐获益主要来自库存快速拒绝和解耦，并不说明消费者持久化容量更大。


## 故障结论

额外 20 次真实投递只 1 单；Worker 积压 20 条约 12.93s 恢复（含 JVM 启动）；禁用 scheduler 后真实延迟消息约 7.62s 关闭订单。发送失败为 mock 注入；支付/关单竞争通过。完整边界见 docs/07-failure-experiments.md。

## 五个设计取舍

1. 模块化单体双运行角色，减少不必要网络调用。
2. Lua 提供快速原子预占，DB 提供持久化约束。
3. MQ 解耦入口与落库，分别度量两类延迟。
4. SKU → reservation 统一锁顺序，阻止死锁与迟到复活。
5. outbox + scheduler 推进跨 Redis/MySQL 库存释放，承认最终一致与故障边界。

## 简历条目

**FlashFlow — 高并发限量票务秒杀与交易履约系统**  
Java 21 / Spring Boot / MySQL / Redis Lua / RocketMQ / Micrometer / k6

- 设计并实现限量票务的预占、异步下单、模拟支付、出票、超时关闭与退款流程；以 Redis Lua 原子预占、MySQL 条件扣库存及业务唯一约束保障库存与限购规则，三轮各 3,000 请求、100 张票的对照压测均无超卖、无重复订单。
- 在 8 核 CPU quota、8 GiB 内存、40 VU 的单机实验中，对比纯 MySQL 与 Redis + MQ 路径，三轮入口 RPS 中位数由 627 提升至 1,271；同时测得订单完成 P99 中位数由 291ms 增至 3,257ms，定位热门 SKU 行锁与消费持久化瓶颈，分别记录入口和完成指标。
- 构建 reservation 终态、业务幂等与库存释放 outbox，验证真实 Broker 额外 20 次重复投递只创建 1 单；Worker 停机时积压 20 个请求，恢复后约 12.93 秒全部落库（含 JVM 启动），并通过发送失败补偿及支付/关单竞争测试。
- 建立 42 项单元与真实 MySQL/Redis 集成测试，提供 k6 原始证据、Actuator 指标、Grafana 配置及 CI 工作流；复现并修复外键共享锁升级死锁和测试 Redis 隔离问题。

所有数字仅来自 [压测报告](docs/06-benchmark-report.md) 与 [故障实验](docs/07-failure-experiments.md)。Docker Compose、远端 CI 和公网部署尚未验证，不写“已部署生产”或“CI 已通过”。面试主线：RepoPilot 展示 AI Agent 与评测工程，FlashFlow 展示 Java 交易正确性、一致性与性能分析。根据篇幅可保留前三条；若更强调正确性，可将性能条目的完整数字留到面试。

## 二十个重点追问

baseline 意义、条件更新、业务唯一键、Lua 原子性、Cluster key、为什么不用分布式锁、预热安全、MQ ACK 语义、未知发送结果、消费重试、迟到消息、终态补偿、锁顺序、外键死锁、支付/关单竞争、outbox、延迟关单、入口 RPS 与 TPS、完成 P99、共享环境限制。答案与代码链接见 docs/12-interview-guide.md。

## Roadmap

完成 GitHub 发布与 CI；实跑 Compose；多实例/网络故障/Redis 恢复；稳态成功订单压测；针对热点 SKU 的持久化容量验证。

## 本轮复验状态

2026-09-30：spotless 和 3 项单元测试通过。旧验收 42 项通过证据仍为 20260930T122848Z-tests-final。当前执行隔离环境无法连接此前启动的 localhost MySQL。尝试独立临时数据库时，MySQL 创建 UNIX socket 被运行限制拒绝。因此本轮完整 verify 未通过环境启动，不能声称已再次通过全部集成测试。

初始 Mockito 动态 self-attach 失败已通过 Maven 显式 test javaagent 配置修复；再次运行推进到数据库连接阶段。提供失败日志用于区分业务失败与环境阻断，不替代原始成功验收。
