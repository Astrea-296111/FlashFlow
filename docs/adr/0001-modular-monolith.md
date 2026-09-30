# ADR 0001：模块化单体，两个运行角色

日期：2026-09-30；状态：接受。

个人学习项目最需要验证的是订单、库存和消息的正确性。注册中心、RPC、分布式事务会扩大故障面，且并不帮助解释库存为什么不会超卖。因此按 auth/event/inventory/seckill/order/payment/messaging 分包，同一 Maven 工程提供 api、worker、all 三个运行角色。api 负责 HTTP 与资格预占；worker 负责消费和补偿。订单与持久化库存保留在同一 MySQL 事务内。

两个角色共享代码和数据库，不称微服务。未来只有当订单写入、独立部署或团队所有权形成实际需求时才拆订单服务。缓存失效使用 Redis Pub/Sub 加短本地 TTL，不能声称强缓存一致。
