# ADR 0003：预占终态栅栏与库存回补 outbox

日期：2026-09-30；状态：接受。

只“查询订单不存在 → Redis 加回库存”存在竞态：迟到 consumer 可在查询之后创建订单。采用同一个 reservation_id 的 MySQL 行作为串行化点。消费和补偿统一先锁 SKU，再 no-op UPSERT 预占记录，随后 SELECT FOR UPDATE；补偿写入 ROLLED_BACK/EXPIRED 终态后提交，迟到消息读取终态后不能下单。终态记录不删除。实际重复消息并发测试发现 INSERT IGNORE 的共享锁升级可能死锁，因此改用获取排他锁的 ON DUPLICATE KEY UPDATE 自赋值，不重置状态。真实 MQ 压测又发现不同 reservation 的外键检查会先获取同一 SKU 的共享锁，消费者随后同时升级为排他锁；先锁父行再插入子行消除了本次复现的循环等待。代价是同一 SKU 的数据库事务串行化，不能声称消除了所有 MySQL 死锁。

Redis reserve 到发送 MQ 之间没有跨系统原子事务。发送超时含义不确定，返回 QUEUED/UNCERTAIN，保留预占直到截止时间；不立即回补。Worker 创建订单时在同一事务扣 MySQL 库存、写订单、确认预占并写超时消息 outbox。支付与超时通过条件更新决定胜者；关单在同一事务回补 MySQL，并写 Redis 回补 outbox，Lua 保证外部副作用只做一次。

数据库扫描负责兜底超时与 outbox 重试。Redis 的截止索引负责发现“只有 Redis 预占、没有发送消息”的窗口。Redis 完全丢数据时停止抢购，不能在活跃活动中盲目重新预热。AOF 单节点和单 Broker 不是高可用承诺。

业务限购口径：每用户每 SKU 一张持久化订单，关单后也不能重新购买；未创建订单的失败预占回补后可以重试。baseline 与 async 使用不同 SKU，预热后禁止 baseline 混用。
