# 压测与验证报告

## 测试环境和方法

2026-09-30，Linux x86_64，CPU quota 8 核、内存上限 8 GiB；Java 21.0.12.1、Spring Boot 3.5.16、MySQL 8.4.9、Redis 7.4.2、RocketMQ 5.3.3、k6 1.3.0。原生进程，同一主机上运行负载发生器与中间件，单 API、单 Worker。限流关闭，以对照容量；这不代表默认生产配置。每轮 40 VU、3,000 个请求、100 张票，固定迭代而非稳态持续负载，三轮交替执行顺序。JWT 用户 fixture 不计入压测。

HTTP 入口 RPS 包括售罄拒绝，不能称为成功订单 TPS。入口 P95/P99 包含全部请求；另列成功请求入口 P99。端到端延迟从负载端请求开始到 SQL 观察者首次看见已提交订单，两组均以 25ms 轮询，包含观察开销与轮询误差。每组成功样本只有 100，不能推断生产尾延迟。

## 全部通过审计的三轮结果

|方案/轮次|入口 RPS|入口 P95 ms|入口 P99 ms|成功请求入口 P99 ms|完成 P95 ms|完成 P99 ms|订单|
|---|---:|---:|---:|---:|---:|---:|---:|
|baseline/1|404.33|244.27|660.52|987.63|895.76|1013.46|100|
|optimized/1|1270.93|63.45|342.28|998.26|3628.77|3677.29|100|
|optimized/2|966.58|140.34|205.77|239.99|3226.60|3256.96|100|
|baseline/2|627.46|130.06|206.19|266.08|236.78|290.84|100|
|baseline/3|841.33|98.71|176.88|191.70|207.57|215.85|100|
|optimized/3|3089.52|32.71|50.56|70.72|1456.67|1460.76|100|

三轮每组均有 100 个订单，超卖 0、重复订单 0，数据库非负与库存守恒成立，异步组 Redis/DB 库存一致，k6 非预期错误率 0。来源：[comparison.json](../benchmark/results/20260930T123027Z-paired-final/comparison.json)，[environment.json](../benchmark/results/20260930T123027Z-paired-final/environment.json)。各轮保留 accepted.jsonl、completion.json、orders.csv、validation.json、k6-summary.json、Actuator 快照和资源采样。

三轮入口 RPS 中位数：baseline 627.46，optimized 1270.93（2.03 倍）；这是各轮汇总值的中位数，不是合并样本。入口 P99 中位数 206.19 → 205.77ms，差异很小；不能宣称显著降低 P99。订单完成 P99 中位数 290.84 → 3256.96ms，异步方案更慢。入口吞吐获益主要来自库存快速拒绝和解耦，并不说明消费者持久化容量更大。

## 资源与瓶颈

resource-samples.json 每 0.5s 采样 API/Worker CPU 比例、heap、Hikari active/pending、线程和 GC。process_cpu_usage 为 JVM 最近窗口比例，并非主机 CPU 峰值；短测试与采样粒度限制峰值解释。所有实例与采样共享 CPU，结果存在预热和竞争波动。单热门 SKU 的数据库行锁仍串行化持久化，Worker/数据库路径是端到端瓶颈；更多消费者不必然改善它。

## 修复和失败证据

早期 MQ 压测出现外键共享锁升级死锁。消费者与补偿统一先锁 SKU，再处理 reservation，并用无状态变化 UPSERT 避免 INSERT IGNORE 后的共享锁升级。另一次资源实验误用测试 Redis DB，现已隔离为测试 DB1，业务 DB0，并拒绝有残留状态的初次预热。早期目录完整保留，不用于简历数字。20260930T123027Z-paired-final 是最终有效对照。

## 复现

按 [运行手册](08-api-and-runbook.md) 启动，使用独立演示数据库，安装 benchmark/requirements.txt 和 k6。设置 JWT_SECRET 与应用一致、RATE_LIMIT_ENABLED=false、PAYMENT_TTL 足够大后运行 `python benchmark/scripts/run.py --requests 3000 --v-us 40 --stock 100 --repeats 3`。脚本自动保存审计与 JSON，失败立即退出。环境变量的数据库和 Redis 地址须与应用一致。Docker Compose 路径尚未在本环境实际运行，当前结果来自 native 服务。

## 本轮复验状态

2026-09-30：spotless 和 3 项单元测试通过。旧验收 42 项通过证据仍为 20260930T122848Z-tests-final。当前执行隔离环境无法连接此前启动的 localhost MySQL。尝试独立临时数据库时，MySQL 创建 UNIX socket 被运行限制拒绝。因此本轮完整 verify 未通过环境启动，不能声称已再次通过全部集成测试。

初始 Mockito 动态 self-attach 失败已通过 Maven 显式 test javaagent 配置修复；再次运行推进到数据库连接阶段。提供失败日志用于区分业务失败与环境阻断，不替代原始成功验收。
