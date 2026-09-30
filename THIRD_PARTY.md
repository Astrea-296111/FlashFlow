# Third-party attribution

访问日期：2026-09-30。原创业务代码采用MIT；该许可不覆盖第三方依赖和运行环境。未复制参考项目业务源码、第三方性能截图、简历数字或付费文章。

| 项目 | 用途 | 来源 |
|---|---|---|
| Spring Boot / Security / JDBC / Data Redis | 实际依赖 | [Spring](https://spring.io/projects/spring-boot) |
| MyBatis-Plus | 实际用户CRUD依赖 | [上游](https://github.com/baomidou/mybatis-plus) |
| Apache RocketMQ | 实际客户端与实验Broker | [上游](https://github.com/apache/rocketmq) |
| Caffeine / Micrometer / Flyway / springdoc | 实际依赖 | 各Maven POM中的上游许可证与URL |
| JUnit / Testcontainers / Mockito / JaCoCo / Spotless | 测试、覆盖率与格式 | 各依赖的Maven POM及生成报告 |
| Apache Maven Wrapper | 自动生成的构建入口 | [上游](https://github.com/apache/maven-wrapper)，保留Apache许可证注释 |
| MySQL / Redis / Eclipse Temurin | 外部运行环境 | 官方发行版，二进制不包含在源码包 |
| k6 / Prometheus / Grafana / Docker Compose | 外部测试或监控环境 | 官方发行版/容器，二进制不包含在源码包 |

设计参考：macrozheng/mall、qiurunze123/miaosha、hzcforever/Seconds-Kill、RocketMQ、Redisson、Sentinel、XXL-JOB。阅读版本、文件哈希与许可证API识别快照见[开源研究](docs/01-open-source-study.md)。未识别许可证的参考仓库只研究设计，不做代码移植。Redisson/Sentinel/XXL-JOB没有加入运行依赖。

Redis7.4与MySQL等软件的上游条款应由实际运行版本的发行文件确定，不能用本项目MIT替代。项目只提供版本配置与原创脚本，没有把上述发行包一起重新分发。第三方文字仅作短归纳与链接，完整下载内容未提交。
