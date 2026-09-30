# FlashFlow 简历条目

个人学习/演示项目；模拟支付，无线上用户或真实资金规模。按自己能讲清的内容选择3到4条，详细限定环境保留在项目链接中。

- 构建限量票务交易链路，采用Redis Lua原子预占、MySQL条件扣减与用户/SKU唯一约束，结合终态栅栏及库存回补outbox；三轮每轮3000请求/100库存对照中最终均100单，超卖与重复订单均为0。
- 设计同步MySQL与Redis＋RocketMQ对照，在8核配额/8GiB本机、40VU下三轮入口RPS中位数627→1271、HTTP P95 130→63ms；同时实测订单完成P99 291ms→3.26s，明确异步吞吐与完成延迟取舍。
- 定位真实压测中的外键S→X锁升级死锁，统一SKU→预占锁序并补并发回归；真实Broker重复投递20次仍1单，worker停机积压20条恢复后offset归零，覆盖发送不确定与支付/关单竞争。
- 完成42个Java/MySQL/Redis验收用例及可重复k6、SQL和故障脚本；十万行合成到期扫描通过复合索引将EXPLAIN ANALYZE客户端wall中位数33.68→1.69ms，保留原始计划、失败轮和监控配置。

## 数字与证据对应

| 条目 | 核对入口 |
|---|---|
| 正确性与入口/完成性能 | [最终三轮comparison](../benchmark/results/20260930T123027Z-paired-final/comparison.json)、每轮validation/orders/completion |
| 真实MQ重复与停机 | [最终faults](../benchmark/results/20260930T124455Z-faults/faults.json)、consumerProgress、producer日志 |
| 修复死锁 | [修复前InnoDB](../benchmark/results/20260930T115123Z-paired/innodb-status.txt)、AsyncIT回归、锁序修复提交 |
| 42用例 | [最终测试summary](../benchmark/results/20260930T122848Z-tests-final/summary.json) |
| 合成SQL索引 | [EXPLAIN ANALYZE](../benchmark/results/20260930T114725Z-sql-index/explain-analyze.json) |

不写“持续TPS翻倍”“P99显著下降”“订单更快”“十万真实订单”“绿色GitHub CI”或已使用Redisson/Sentinel/XXL-JOB。对应STAR：有限资格与异步窗口是背景，持久化栅栏/锁序/outbox是行动，完整审计和延迟取舍是结果。RepoPilot主讲Agent工程/评测，FlashFlow主讲Java事务/可靠性。
