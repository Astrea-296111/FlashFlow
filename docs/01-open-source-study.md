# 开源项目研究与取舍

访问日期：2026-09-30。读取上游README，另外阅读 mall 的订单服务和 Sentinel FlowRuleManager；[读取文件清单与哈希](open-source-read-manifest.json)。星数来自GitHub API当日快照，只表示受关注程度。七个参考项目不等于七个都已集成。

| 上游项目 | 当日 stars | API识别许可证 |
|---|---:|---|
| [macrozheng/mall](https://github.com/macrozheng/mall) | 84851 | Apache-2.0 |
| [qiurunze123/miaosha](https://github.com/qiurunze123/miaosha) | 26591 | 未识别，不能当作授权 |
| [hzcforever/Seconds-Kill](https://github.com/hzcforever/Seconds-Kill) | 83 | 未识别，不能当作授权 |
| [apache/rocketmq](https://github.com/apache/rocketmq) | 22624 | Apache-2.0 |
| [redisson/redisson](https://github.com/redisson/redisson) | 24406 | Apache-2.0 |
| [alibaba/Sentinel](https://github.com/alibaba/Sentinel) | 23147 | Apache-2.0 |
| [xuxueli/xxl-job](https://github.com/xuxueli/xxl-job) | 30602 | GPL-3.0 |

## mall

学习清晰的前台/后台安全边界、业务模块与订单履约链路。当前 master README 明示 Spring Boot3.5/JDK17，旧分支另行维护；不能把网上旧教程的 Boot2/JDK8 当作当前主分支事实。阅读 OmsPortalOrderServiceImpl 的下单、库存与取消处理后，FlashFlow 收敛到单张票、条件迁移和两类 outbox，避免搬入商品、优惠券、搜索与商城微服务依赖。没有复制其订单代码。

## miaosha

README 为秒杀学习路线和多组件主题入口。采纳从同步事务基线到缓存/异步逐步实验的学习方式，不把 README 的框架名单全部变成依赖，也不复述其量级为本项目性能。GitHub API 未识别许可证，本项目只研究其设计主题，没有复用代码。

## Seconds-Kill

学习“先保留 SQL 条件扣减与唯一键，再观察 Redis预减/RabbitMQ异步的入口表现”的实验组织。它的 README 使用 Boot2.1.5、RabbitMQ、JMeter 与内存售罄标记。FlashFlow 改为 Java21、Redis多键Lua、RocketMQ、持久化终态栅栏、有界短TTL标记，并测最终落库而非只测排队HTTP。没有照搬截图或QPS数据；未识别许可证，未复用源代码。

## RocketMQ

这是实际依赖。使用 5.3.3 Broker 和 `rocketmq-client:5.3.3` 的经典 remoting API：DefaultMQProducer/DefaultMQPushConsumer，SEND_OK、至少一次消费、RECONSUME_LATER、固定延迟等级。不要写成使用5.x新gRPC SDK的任意投递时间。实际实验关闭数据库定时扫描，用经典level2（5秒）消息验证关单，再启动扫描释放Redis。官方参考：[经典延迟消息](https://rocketmq.apache.org/docs/4.x/producer/04message3/)、[消费重试](https://rocketmq.apache.org/docs/featureBehavior/10consumerretrypolicy/)。

## Redisson

研究锁所有者、续租和watchdog适用边界，理解锁租期并不自动保证数据库业务幂等。没有集成 Redisson：资格校验、库存减少和预占写入已由同槽Lua完成，持久化幂等由MySQL负责。全SKU分布式锁会串行热路径并增加网络往返。未来多步、必须跨线程协调的管理任务才考虑锁，并保留业务条件栅栏。参考：[锁与同步器文档](https://redisson.pro/docs/data-and-services/locks-and-synchronizers/)。

## Sentinel

研究资源、流量规则与动态治理，额外阅读FlowRuleManager。当前只需每SKU统一令牌桶和显式429，Redis已有共享状态，因此未引入控制台和独立规则依赖；也没有声称实现熔断或全套治理。若出现多接口动态规则、运维调参和下游服务熔断需求，再引入。参考：[官方文档](https://github.com/alibaba/Sentinel/wiki)。

## XXL-JOB

研究独立调度、执行器与故障运维的能力边界。当前worker用Spring调度，每轮有数量上限；核心DB事务、SKIP LOCKED与Lua保证重复调度安全，不要求一个分布式全局任务锁。未集成XXL-JOB，没有它的管理后台、任务路由、告警或调度历史。任务数、分片与运营治理扩展后再评估；许可证为当日API识别GPL-3.0，不将其代码合并到本项目。

## 归因

原创业务实现采用项目MIT许可证；参考仅为设计学习。自动生成的Maven wrapper保留上游Apache许可证注释。实际依赖和环境软件遵循各自许可证，项目MIT不能替代它们。没有上传第三方完整README、简历、付费面经或二进制运行环境。[THIRD_PARTY.md](../THIRD_PARTY_NOTICES.md) 列出范围。
