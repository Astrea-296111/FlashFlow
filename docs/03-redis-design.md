# Redis：预占、限流和元数据缓存

所有 SKU 键包含同一 `{sku:N}` hash tag。多键 Lua 的槽位设计适配 Redis Cluster 的键约束；本次运行的是单节点 Redis，没有执行 Cluster、主从切换或重分片实验。

| 键后缀 | 类型 | 用途及生命周期 |
|---|---|---|
| stock | String | 资格库存；活动期间不自动过期 |
| metadata | Hash | eventId、start、finish、enabled；活动售卖快照 |
| buyers | Hash | userId→reservationId，防重复资格；持久化订单关单后保留 |
| reservation:ID | Hash | RESERVED/CONFIRMED/ROLLED_BACK/CLOSED 与消息身份；终态保留 7 天 |
| deadline | ZSet | 截止毫秒→reservationId；每 SKU 每轮扫描最多 100 条 |
| bucket | Hash | Redis TIME 驱动的 SKU 令牌桶 |

[reserve.lua](../src/main/resources/lua/reserve.lua) 将窗口检查、重复资格检查、库存判定、DECR、买家映射、预占记录和截止索引写入放在一次脚本内。时间取 Redis TIME，避免多 API 进程时钟分别决定资格。截止扫描使用应用 Clock，跨机器仍需时钟同步。

脚本先检查所有键类型及库存整数性，再做写操作。Redis Lua 能阻止其他命令穿插，但执行中抛错不会回滚之前已经执行的写入。因此不能把“Lua 原子执行”表述成 SQL 式事务回滚。预检缩小 WRONGTYPE 半写窗口；OOM、运行时故障等仍是运维问题，不能承诺任意故障下完整回滚。

[transition.lua](../src/main/resources/lua/transition.lua) 仅对允许状态转换执行 INCR。重复 ROLLBACK/CLOSE 返回 0。回滚删除 buyers 时额外校验它仍指向自己的 reservationId，避免误删后续资格。CLOSE 接受 RESERVED 和 CONFIRMED，覆盖 MySQL 已关单、Redis 尚未确认的窗口；它保留限购映射。

## 初始化与恢复

`InventoryWarmup` 先锁 MySQL SKU；初次预热发现任何旧 stock/metadata/buyers/deadline 都拒绝，且回滚 BASELINE→ASYNC 的 DB 改动。正常重复调用已 ASYNC 的预热不重置库存；活跃库存键消失则 LIVE_REWARM_FORBIDDEN。Redis 写成功但 DB 提交失败可能留下孤立初始化键，应停售并人工核对，不能用重复预热掩盖它。

单节点 AOF 和 noeviction 是演示配置。Redis 作为资格库存不是可随意丢弃的缓存；丢失 active reservation 会失去补偿发现索引。完整恢复方案尚未实现。测试专用 Redis DB 1 与演示 DB 0 隔离；不能让两个不同 MySQL 实例复用相同的 SKU 键空间。

## 令牌桶与售罄标记

默认每 SKU rate=2000/s、burst=4000，由 Redis Lua 统一计数。限流超额返回 429；这不是容量承诺。对照压测关闭限流，两组参数相同。`RedisIT` 使用显式低速、5 token 突发配置验证 40 次尝试只允许 5 次。

`SeckillService` 的售罄缓存容量 10000、TTL 250ms，用于减少失败流量继续访问 Redis。这段时间内恢复的库存或旧用户重复请求可能仍收到 SOLD_OUT；标记不作为持久化库存事实，后续请求可恢复。

## Caffeine→Redis→MySQL 活动详情

只缓存活动与票档元数据，响应不包含 mutable available_stock。本地 TTL 2s、容量 1000；Redis 正常对象 45..60s 抖动，空值 5s。`Caffeine.get(key, loader)` 合并单实例同键加载。管理员 DB 事务提交后删除 Redis、本地缓存并发送 Pub/Sub 失效；本地订阅处理使用有界线程池。

这不是强一致缓存：Pub/Sub 会丢失离线订阅消息；读线程可能在删除后回填旧数据，旧 Redis 对象最多还会存活一个 TTL，随后也可能进入本地短 TTL。Redis 断连只对活动元数据读取回源 DB，秒杀资格路径失败关闭。没有使用全局分布式锁缓存每一次查询，也没有把 Redis 故障自动切换成另一个库存来源。
