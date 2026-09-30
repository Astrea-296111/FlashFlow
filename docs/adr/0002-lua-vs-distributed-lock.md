# ADR 0002：Lua、数据库约束与分布式锁的职责

日期：2026-09-30；状态：接受。

Redis Lua 在一次调用内检查销售窗口、库存和用户资格，扣减库存并写预占和截止时间索引。不能拆成 GET / 判断 / DECR。SKU 级分布式锁会把热键请求串行化，并引入获取、释放和锁等待；用户级锁也不能代替 SKU 库存的原子操作。MySQL 条件 UPDATE 及 UNIQUE(user_id,sku_id) 是持久化最终防线。

Redisson 的 watchdog 在未指定 leaseTime 的常见锁用法中续期，显式 leaseTime 有限时释放语义；进程暂停、网络分区和锁失效仍需要存储层约束。当前不引入 Redisson：初始化由数据库行锁冻结 SKU 模式并用 Redis 的幂等初始化脚本完成，低频管理操作没有额外锁客户端的必要。

参考：[Redis Lua](https://redis.io/docs/latest/develop/programmability/eval-intro/)、[Redisson locks](https://redisson.pro/docs/data-and-services/locks-and-synchronizers/index.html)。单节点 Redis 实测，Cluster 只讨论同槽约束。
