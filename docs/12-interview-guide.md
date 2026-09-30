# 围绕自己代码的面试指南

共75题。回答先给实现事实，再解释代价；不背不存在的组件能力。每题源码是复习入口，性能数字和故障结果以原始证据为准。

## Java与并发

### 1. 为什么HTTP输入和消息命令用record？

固定字段与不可变载体减少共享可变状态；不可变不等于业务合法，ReservationCommand仍显式validate，HTTP仍用Bean Validation。

代码/证据：[ReservationCommand.java](../src/main/java/com/astrea/flashflow/seckill/ReservationCommand.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 2. 为什么金额不用double？

价格从SKU的BigDecimal读取并持久化到DECIMAL，避免二进制浮点金额误差；本地模拟没有实际支付渠道对账。

代码/证据：[OrderService.java](../src/main/java/com/astrea/flashflow/order/OrderService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 3. 为什么注入Clock？

让支付、关单和补偿采用同一时间抽象；ProducerFailureIT推进逻辑时钟测试到期。Redis资格检查用Redis TIME，不能说所有系统共用一个时钟。

代码/证据：[RuntimeConfig.java](../src/main/java/com/astrea/flashflow/infrastructure/RuntimeConfig.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 4. 线程池实际用于哪里？

缓存Pub/Sub失效处理使用ThreadPoolExecutor；集成测试用有界线程池制造并发，RocketMQ消费池也固定为4线程。没有额外HTTP异步线程池。

代码/证据：[CacheInvalidationConfig.java](../src/main/java/com/astrea/flashflow/event/CacheInvalidationConfig.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 5. 为什么选择有界队列与CallerRunsPolicy？

缓存失效池2到4线程、队列256，饱和时调用方执行来反压，避免无限堆积。它可能拖慢监听线程，需要负载观察，不能保证永不阻塞。

代码/证据：[CacheInvalidationConfig.java](../src/main/java/com/astrea/flashflow/event/CacheInvalidationConfig.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 6. 测试里的并发任务异常会不会被吞掉？

IntegrationSupport保存Future并逐个get，异常通过ExecutionException导致测试失败；结束时shutdownNow和awaitTermination。

代码/证据：[IntegrationSupport.java](../src/test/java/com/astrea/flashflow/IntegrationSupport.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 7. 为什么不把库存存在ConcurrentHashMap？

本地容器无法跨API实例裁决，重启也丢失；本地Caffeine售罄标记只优化拒绝流量，不是库存来源。

代码/证据：[SeckillService.java](../src/main/java/com/astrea/flashflow/seckill/SeckillService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 8. 已经用了Java21虚拟线程提速吗？

只提供VIRTUAL_THREADS开关，默认false，本次未跑虚拟线程对照。阻塞数据库连接和单SKU写锁仍会限制并发。

代码/证据：[application.yaml](../src/main/resources/application.yaml)。练习：指出失败窗口、运行关联用例，解释一次反例。

## Spring与权限

### 9. 为什么@Transactional放Service？

一个业务动作的库存、预占、订单等SQL必须共同提交；Controller通过Spring代理调用Service，事务不跨Redis和MQ。

代码/证据：[OrderService.java](../src/main/java/com/astrea/flashflow/order/OrderService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 10. 同类调用会影响事务代理吗？

普通自调用不会重新进入代理。insertOrder是已有process/baseline事务的内部步骤，外层入口才@Transactional；不能靠给内部方法加注解补救。

代码/证据：[AsyncOrderService.java](../src/main/java/com/astrea/flashflow/order/AsyncOrderService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 11. 为什么消费者先调用process再操作Redis？

process的代理方法返回时DB已经提交，再执行Redis确认；提交前失败会回滚，提交后失败靠重复消息修复。

代码/证据：[DeliveryHandler.java](../src/main/java/com/astrea/flashflow/messaging/DeliveryHandler.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 12. 公开注册能伪造ADMIN吗？

Credentials不接收角色；register固定传USER，管理路径统一要求ADMIN。HttpIT验证额外role字段不能升级权限。

代码/证据：[AuthController.java](../src/main/java/com/astrea/flashflow/auth/AuthController.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 13. JWT为什么能防客户端改userId？

HS256签名和issuer校验后，用户身份从sub读取，购买接口不接收请求体userId。持有签名密钥者属于可信边界。

代码/证据：[SecurityConfig.java](../src/main/java/com/astrea/flashflow/auth/SecurityConfig.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 14. 401、403、404分别表示什么？

无认证/签名失败是401；普通用户访问管理员路径是403；其他用户的订单与预占返回404以隐藏存在性。

代码/证据：[SecurityConfig.java](../src/main/java/com/astrea/flashflow/auth/SecurityConfig.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 15. 如何防止启动使用空JWT密钥？

SecurityConfig检查secret的UTF-8字节长度至少32；示例密钥仅为本地开发，未实现在线密钥轮转和JWT撤销列表。

代码/证据：[SecurityConfig.java](../src/main/java/com/astrea/flashflow/auth/SecurityConfig.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 16. 缓存删除为什么放Controller事务调用之后？

EventService代理返回后DB提交，再invalidate，减少先删缓存后事务回滚的错误。仍可能回填旧数据，不声称强缓存一致。

代码/证据：[EventController.java](../src/main/java/com/astrea/flashflow/event/EventController.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

## MySQL与事务

### 17. baseline怎样防超卖？

带available_stock>0条件的UPDATE影响行数必须为1；数据库CHECK也约束0到总库存；随后插订单在同一事务内。

代码/证据：[OrderService.java](../src/main/java/com/astrea/flashflow/order/OrderService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 18. 唯一键失败发生在扣库存之后怎么办？

异常冒出事务代理导致扣减、预占与订单全部回滚；BaselineIT验证重复用户不会再次扣库存。

代码/证据：[OrderService.java](../src/main/java/com/astrea/flashflow/order/OrderService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 19. version字段是乐观锁吗？

不是。它只递增记录版本，没有WHERE version=expected；库存裁决实际靠条件UPDATE和InnoDB行锁。

代码/证据：[InventoryRepository.java](../src/main/java/com/astrea/flashflow/inventory/InventoryRepository.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 20. 选择READ COMMITTED的原因？

需要当前提交状态与显式锁，减少不必要范围锁；它不替代唯一键、状态条件或一致锁顺序，外键仍会取得共享锁。

代码/证据：[application.yaml](../src/main/resources/application.yaml)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 21. 重复INSERT IGNORE后再FOR UPDATE有什么问题？

重复插入可能取得共享重复键锁，再同时升级X锁死锁；改成no-op UPSERT取得排他重复键锁且不重置终态。

代码/证据：[AsyncOrderService.java](../src/main/java/com/astrea/flashflow/order/AsyncOrderService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 22. 不同消息为什么也曾死锁？

reservation外键先取得同一SKU的S锁，消费者随后都要X锁。先锁SKU再插reservation解决本次循环等待，保留失败InnoDB证据。

代码/证据：[AsyncOrderService.java](../src/main/java/com/astrea/flashflow/order/AsyncOrderService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 23. 为什么到期索引列序是status,expire_at,id？

先等值过滤WAIT_PAY，再截止范围并按时间LIMIT；id给稳定内部标识。实际10万合成行从全扫排序变为覆盖范围扫描。

代码/证据：[V1__domain.sql](../src/main/resources/db/migration/V1__domain.sql)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 24. SKIP LOCKED有什么作用？

并行relay跳过其他事务已经领取的PENDING行，不都等待同一批；发送成功但提交失败仍可能重发，所以不能单靠领取保证exactly once。

代码/证据：[OutboxRelay.java](../src/main/java/com/astrea/flashflow/messaging/OutboxRelay.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 25. 为什么每用户SKU唯一包括历史关单？

当前政策限制一张持久化订单，不允许关单反复占资格。若要允许重新购买，需要重新设计活跃资格唯一约束，不能简单删索引。

代码/证据：[V1__domain.sql](../src/main/resources/db/migration/V1__domain.sql)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 26. 查询订单总数等于总库存就能证明不超卖吗？

不能。历史CLOSED/REFUNDED不占库存，应该审计available_stock+active_orders=total_stock，同时检查用户SKU重复和非负库存。

代码/证据：[common.py](../benchmark/scripts/common.py)。练习：指出失败窗口、运行关联用例，解释一次反例。

## Redis与缓存

### 27. Lua为什么比get再decr安全？

一次脚本中检查窗口、重复资格、stock>0并写全部预占状态，没有其他Redis命令穿插；数据库仍作持久化兜底。

代码/证据：[reserve.lua](../src/main/resources/lua/reserve.lua)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 28. Lua报错会自动回滚吗？

不会撤销之前已执行命令，所以脚本先校验键类型和库存值再写。OOM等无法用这一检查完全覆盖。

代码/证据：[reserve.lua](../src/main/resources/lua/reserve.lua)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 29. Redis Cluster多键Lua如何同槽？

所有键使用flashflow:{sku:N}:前缀；结果ID带skuId以避免跨槽路由索引。只设计了键约束，本次未运行Cluster测试。

代码/证据：[RedisReservations.java](../src/main/java/com/astrea/flashflow/seckill/RedisReservations.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 30. 为什么用Redis TIME？

资格窗口由服务端统一时间裁决，避免API本地时钟各自判断；扫描deadline仍是应用Clock，需要机器时钟同步。

代码/证据：[reserve.lua](../src/main/resources/lua/reserve.lua)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 31. 活跃reservation为什么没有自动TTL删除？

删除会丢失身份和截止发现信息，无法可靠回补。扫描ZSet并写DB终态后再执行状态迁移，终态保留7天。

代码/证据：[transition.lua](../src/main/resources/lua/transition.lua)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 32. 为什么ROLLBACK删除buyer要检查owner？

旧预占回补不能误删后来该用户的资格；仅当buyer映射仍指向当前reservation才HDEL。

代码/证据：[transition.lua](../src/main/resources/lua/transition.lua)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 33. 关单为什么保留buyer？

关单已是持久化订单，业务永久一单政策要求禁止同一用户SKU再买；只有未创建订单的失败预占释放buyer。

代码/证据：[transition.lua](../src/main/resources/lua/transition.lua)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 34. 为什么Redis丢库存不能重新warm？

DB不能区分尚在Redis/MQ途中的全部资格，重新覆盖会产生额外可售名额；活跃ASYNC库存缺失则拒绝，完整恢复尚未实现。

代码/证据：[InventoryWarmup.java](../src/main/java/com/astrea/flashflow/inventory/InventoryWarmup.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 35. 初次warm为什么检查旧键？

MySQL数据代际或测试环境改变后同SKU编号可能对应旧Redis状态；拒绝残留并回滚DB模式，避免把旧状态当作新库存。

代码/证据：[initialize.lua](../src/main/resources/lua/initialize.lua)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 36. 缓存穿透、击穿、雪崩如何处理？

不存在活动缓存5秒；Caffeine单实例合并同键加载；Redis TTL45到60秒抖动。本地容量1000和2秒TTL，未实现跨实例全局single-flight。

代码/证据：[EventCache.java](../src/main/java/com/astrea/flashflow/event/EventCache.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 37. Pub/Sub能保证缓存一致吗？

不能，离线消息会丢失，读线程也可能删后回填旧值。短本地TTL与Redis TTL限制陈旧窗口，但仍属最终缓存一致。

代码/证据：[EventCache.java](../src/main/java/com/astrea/flashflow/event/EventCache.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 38. 250ms售罄标记的代价？

拒绝流量更便宜，但恢复库存和旧用户重复查询可能暂时被SOLD_OUT挡住；标记很快到期，不改变持久化库存。

代码/证据：[SeckillService.java](../src/main/java/com/astrea/flashflow/seckill/SeckillService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

## RocketMQ

### 39. 这项目使用哪个SDK模型？

Broker5.3.3与client5.3.3经典remoting API，不是5.x新gRPC API；超时使用18级固定延迟，不能写任意时间戳投递。

代码/证据：[RocketMessageBus.java](../src/main/java/com/astrea/flashflow/messaging/RocketMessageBus.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 40. SEND_OK等于订单完成吗？

只表示发送满足Broker当前成功条件；最终订单需consumer事务提交并核对查询。HTTP202仅有资格预占结果。

代码/证据：[RocketMessageBus.java](../src/main/java/com/astrea/flashflow/messaging/RocketMessageBus.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 41. 发送超时为什么不立即回补？

Broker可能已接收而ACK迟到，立刻回补会与有效consumer竞争库存。返回UNCERTAIN，由同一持久化栅栏在消费与截止补偿间裁决。

代码/证据：[SeckillService.java](../src/main/java/com/astrea/flashflow/seckill/SeckillService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 42. 如何应对consumer重复投递？

reservation身份校验、终态行锁、订单reservation唯一与用户SKU唯一共同兜底；重复返回已有订单而不再次扣库存。

代码/证据：[AsyncOrderService.java](../src/main/java/com/astrea/flashflow/order/AsyncOrderService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 43. 什么时候consumer ACK？

DB事务及对应Redis状态处理成功后；临时异常RECONSUME_LATER。无效schema先持久化隔离hash再ACK。

代码/证据：[RocketConsumers.java](../src/main/java/com/astrea/flashflow/messaging/RocketConsumers.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 44. 为什么限制最大重消费3次？

演示环境避免坏消息永久重试；达到上限由Broker进入DLQ。真实运行需要监控和人工重放程序，本项目没有自动DLQ恢复服务。

代码/证据：[RocketConsumers.java](../src/main/java/com/astrea/flashflow/messaging/RocketConsumers.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 45. 毒消息为什么不直接重试？

无效JSON、null、超大消息或身份不匹配通常无法靠重试变有效，记录哈希隔离；隔离DB写失败仍作为暂时异常重试。

代码/证据：[RocketConsumers.java](../src/main/java/com/astrea/flashflow/messaging/RocketConsumers.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 46. default consumer有序消费吗？

使用MessageListenerConcurrently，4线程、单消息批次，没有承诺全局或SKU内投递顺序；状态和行锁应对重放/迟到。

代码/证据：[RocketConsumers.java](../src/main/java/com/astrea/flashflow/messaging/RocketConsumers.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 47. 付款TTL不是固定等级时怎么办？

DelayLevels向上选择相邻等级，避免关单过早；DB仍检查真实expire_at，并由2秒扫描兜底，支持最多7200秒。

代码/证据：[DelayLevels.java](../src/main/java/com/astrea/flashflow/messaging/DelayLevels.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 48. 如何证明不是扫描器关单冒充延迟消息？

真实faults实验关闭worker所有定时维护，人工fixture缩短截止，发送level2消息，观察CLOSED；再恢复扫描完成Redis回补。

代码/证据：[faults.py](../benchmark/scripts/faults.py)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 49. worker停机的积压如何证明？

停机时20个Broker ACK而订单0，并保存官方consumerProgress的offset差；恢复后20订单和offset归零分别验证，不能只看HTTP成功。

代码/证据：[faults.py](../benchmark/scripts/faults.py)。练习：指出失败窗口、运行关联用例，解释一次反例。

## 一致性与履约

### 50. 为什么只查订单不存在还不够？

查完到Redis回补之间迟到consumer可能创建订单。必须持久化ROLLED_BACK栅栏，让消费与补偿在同一锁序下决定终态。

代码/证据：[AsyncOrderService.java](../src/main/java/com/astrea/flashflow/order/AsyncOrderService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 51. 只在Redis放幂等标记够吗？

Redis可丢数据、键过期和被绕过，不能替代MySQL唯一约束；Redis只减少无效流量，DB决定持久化业务结果。

代码/证据：[V1__domain.sql](../src/main/resources/db/migration/V1__domain.sql)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 52. DB提交后Redis确认失败怎么办？

消息返回重试，或到期扫描发现已有订单，执行CONFIRM而非ROLLBACK；AsyncIT覆盖该窗口。

代码/证据：[DeliveryHandler.java](../src/main/java/com/astrea/flashflow/messaging/DeliveryHandler.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 53. 两个outbox为什么不能合成一个口号？

message_outbox记录超时MQ发送，stock_release_outbox记录Redis库存释放，外部副作用、业务键和重试目标不同。

代码/证据：[OutboxRelay.java](../src/main/java/com/astrea/flashflow/messaging/OutboxRelay.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 54. 为什么关单Redis必须在DB提交之后？

DB状态迁移决定唯一胜者；事务中写释放意图，提交后relay执行Lua。先释放Redis会把尚未关单的库存卖给别人。

代码/证据：[OrderService.java](../src/main/java/com/astrea/flashflow/order/OrderService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 55. 支付和超时争用时谁赢？

都先锁订单，并通过WAIT_PAY与截止条件更新，合法迁移只执行一个。重复关单影响行数0，无库存副作用。

代码/证据：[OrderService.java](../src/main/java/com/astrea/flashflow/order/OrderService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 56. 模拟支付如何幂等？

paymentKey唯一且order_no也唯一；同key同订单PAID/ISSUED返回已有结果，另订单复用key冲突，不多扣或多插记录。

代码/证据：[OrderService.java](../src/main/java/com/astrea/flashflow/order/OrderService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 57. 退款是实际资金退款吗？

不是。只演示REFUNDING→REFUNDED条件迁移和库存释放，payment_record仍是模拟支付凭证，没有渠道回调/对账。

代码/证据：[OrderService.java](../src/main/java/com/astrea/flashflow/order/OrderService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 58. 有没有跨系统exactly once？

没有。MQ至少一次、outbox可能重发、Lua状态迁移与DB幂等让业务副作用尽量一次；不承诺单节点丢数据后的无损恢复。

代码/证据：[04-consistency-model.md](04-consistency-model.md)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 59. 终态为什么不立即删除？

很迟的重放仍需要看见tombstone。清理需明确最大保留和重放窗口，当前DB终态不自动清理，代价是数据增长。

代码/证据：[AsyncOrderService.java](../src/main/java/com/astrea/flashflow/order/AsyncOrderService.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

## 系统设计与工程

### 60. 为什么模块化单体而不是微服务？

订单库存同DB事务最易验证，api/worker角色共享代码与数据库；避免注册中心、RPC和跨服务事务扩展故障面，明确不称微服务。

代码/证据：[0001-modular-monolith.md](adr/0001-modular-monolith.md)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 61. api与worker分别负责什么？

api接HTTP、预占与发布；worker消费、超时、补偿、relay和模拟出票。默认all方便开发，Compose分别设置角色。

代码/证据：[MaintenanceJobs.java](../src/main/java/com/astrea/flashflow/infrastructure/MaintenanceJobs.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 62. 为什么没有集成Redisson？

热路径能用Lua一步完成资格处理，SQL提供持久化裁决；锁租期不解决跨系统幂等，当前没有必须持有多步分布式锁的业务。

代码/证据：[0002-lua-vs-distributed-lock.md](adr/0002-lua-vs-distributed-lock.md)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 63. 为什么不用Sentinel或XXL-JOB？

目前Redis令牌桶和Spring有界扫描满足明确范围；没有动态资源治理或独立运维调度需求，未集成就不写用过。

代码/证据：[01-open-source-study.md](01-open-source-study.md)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 64. Redis故障哪些功能可以降级？

活动元数据可以回源DB，资格库存必须失败关闭；不能在ASYNC SKU把入口自动转为baseline，否则两份资格库存竞争。

代码/证据：[EventCache.java](../src/main/java/com/astrea/flashflow/event/EventCache.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 65. 后台列表怎么分页？

当前活动列表LIMIT100、用户订单有限条，未提供完整游标分页；大数据生产场景要改查询和API，不宣称无限列表能力。

代码/证据：[OrderRepository.java](../src/main/java/com/astrea/flashflow/order/OrderRepository.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 66. 测试为什么既有Testcontainers又有external？

默认CI用独立容器便于重现；本环境无Docker，使用专用native schema和Redis DB1并明确标注，不能把它描述成容器验收。

代码/证据：[IntegrationSupport.java](../src/test/java/com/astrea/flashflow/IntegrationSupport.java)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 67. 已验证多实例高可用了吗？

没有。本次各1个API/worker、单MySQL/Redis/Broker；Lua和唯一键设计跨进程仍成立，但多实例容量、切换和数据恢复未实测。

代码/证据：[07-performance-report.md](07-performance-report.md)。练习：指出失败窗口、运行关联用例，解释一次反例。

## 性能与证据

### 68. k6总RPS等于成功订单TPS吗？

不是。每组3000请求只有100库存，大多数为409；optimized廉价拒绝也提高RPS。报告同时列accepted=100和最终100订单。

代码/证据：[entry.js](../benchmark/k6/entry.js)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 69. 为什么两组用不同SKU？

模式互斥，不能同SKU同时BASELINE与ASYNC；各自初始100库存、相同3000用户和40VU，避免共享库存污染对照。

代码/证据：[run.py](../benchmark/scripts/run.py)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 70. P99如何聚合？

先计算每轮k6 P99，再取3个轮次的中位数；不是合并全部请求的总体P99，报告保留每轮值和范围。

代码/证据：[07-performance-report.md](07-performance-report.md)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 71. 最终完成时延怎样测？

两组均从load-generator请求开始到首次SQL观察到已提交订单，25ms轮询有额外不确定性；不把202耗时当完成耗时。

代码/证据：[run.py](../benchmark/scripts/run.py)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 72. 为什么optimized完成P99更慢？

引入Broker、排队、消费者和串行SKU事务后总路径更长。当前实验观察到更慢，未做JFR/细分排队剖析，不把原因百分比分摊当事实。

代码/证据：[07-performance-report.md](07-performance-report.md)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 73. CPU下降了吗？

采样是近期进程CPU ratio且短burst受指标缓存滞后，不足以证明因果下降；报告保存heap/GC/Hikari原始样本，不用它宣传资源节省。

代码/证据：[run.py](../benchmark/scripts/run.py)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 74. 你保留了什么失败实验？

锁升级死锁未收敛轮、错误时区的重复消息fixture、Redis环境污染导致0 accepted轮；均注明不可作为最终性能成果。

代码/证据：[06-failure-injection.md](06-failure-injection.md)。练习：指出失败窗口、运行关联用例，解释一次反例。

### 75. 下一次最有价值的压测是什么？

持续负载和更大库存比例、多实例、多消费者线程及Broker延迟分解。现有固定3000迭代短burst不能外推持续稳定TPS。

代码/证据：[13-learning-roadmap.md](13-learning-roadmap.md)。练习：指出失败窗口、运行关联用例，解释一次反例。
