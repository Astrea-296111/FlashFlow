# 本轮交付复验限制

2026-09-30：spotless 和 3 项单元测试通过。旧验收 42 项通过证据仍为 20260930T122848Z-tests-final。当前执行隔离环境无法连接此前启动的 localhost MySQL。尝试独立临时数据库时，MySQL 创建 UNIX socket 被运行限制拒绝。因此本轮完整 verify 未通过环境启动，不能声称已再次通过全部集成测试。

初始 Mockito 动态 self-attach 失败已通过 Maven 显式 test javaagent 配置修复；再次运行推进到数据库连接阶段。提供失败日志用于区分业务失败与环境阻断，不替代原始成功验收。
