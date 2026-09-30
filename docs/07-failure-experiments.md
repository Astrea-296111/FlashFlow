# 故障实验

真实 Broker 的最终证据为 [faults.json](../benchmark/results/20260930T124455Z-faults/faults.json)。集成测试的 MQ 使用 mock，不能与真实 Broker 实验混称。

|场景|实际结果|证据/边界|
|---|---|---|
|Worker 停机|20 次 ACK，停机期间 0 单；恢复 20 单，积压差值 20 → 0|12.93 秒含 JVM 启动；consumerProgress 前后原始文件|
|真实重复投递|额外发送 20 次 ACK，同 reservation 仍只有 1 单|数据库唯一约束、reservation 终态与行锁|
|延迟关单|关闭 scheduler 后约 7.62 秒关单，DB 库存回到 1|delay level 2 标称 5 秒；Redis 释放等待维护任务恢复，不能声称单独延迟消息同步了 Redis|
|坏消息|null/格式错误持久化隔离，重复相同内容使用 hash 去重|quarantine 行存在，避免消息无限无效重试|
|发送失败/未知发送结果|真实 Redis 预占后 mock Producer 抛异常；超时补偿幂等回补|ProducerFailureIT；未进行真实 Broker 网络断链注入|
|支付与关单竞争|三轮竞争测试，支付赢或关单赢均满足状态与库存约束|PaymentRaceIT；状态条件更新及锁顺序|

复现脚本：`benchmark/scripts/faults.py`，其 worker 停启默认 Docker，也提供 native 路径；运行前检查独立演示环境。关掉 scheduler 用于证明真实延迟消息触发 DB 关单，维护任务恢复后才能最终推进 Redis release outbox。持续停掉补偿会阻止跨存储收敛。
