# Third-party notices

本项目独立实现票务业务模型、SQL、Lua、API 与实验。下列项目仅作为设计和工程参考，未复制其源码；GPL 项目源码未纳入本仓库。完整读取记录、版本与许可证快照见 docs/01-open-source-study.md 与 docs/open-source-snapshot.json。

- macrozheng/mall：订单边界与文档工程参考。
- qiurunze123/miaosha、hzcforever/Seconds-Kill：baseline 与预减库存演进参考。
- Apache RocketMQ：官方消息机制与 client 依赖。
- Redisson：分布式锁与 watchdog 设计比较，未依赖。
- Alibaba Sentinel：流控思想，未依赖。
- XXL-JOB：补偿调度思想，未依赖。

通过 Maven 正常使用 Spring Boot/Security、MyBatis-Plus、Flyway、MySQL Connector/J、RocketMQ client、Caffeine、Micrometer、springdoc、JUnit、Mockito、Testcontainers、JaCoCo、Spotless 及传递依赖；各依赖保留上游许可证，版本以 pom.xml/依赖树为准。部署镜像与 k6 使用其原有许可证。本项目 MIT 许可证不覆盖第三方组件。
