# FlashFlow v1.0.0 发布说明草稿

限量票务交易演示：保留MySQL同步baseline，增加Redis Lua资格预占、RocketMQ经典客户端、持久化终态栅栏、超时与回补outbox，以及模拟支付/关单履约。42个用例与三组最终真实对照均通过库存审计，原始失败与成功数据保留。

入口RPS中位数627→1271、HTTP P95 130→63ms，HTTP P99基本相同；最终落库P99 291ms→3.26s，明确排队入口与交易完成的区别。单机native、40VU/3000请求/100库存，不代表生产TPS。真实MQ重复投递、停机offset20→0和关闭扫描后的延迟关单已验证。

模拟支付，无生产高可用或Redis全量恢复。Compose解析已验证，镜像构建与 GitHub CI 已通过，完整容器业务链路尚未验证。公开仓库已发布，Release 附件包含完整原始开发历史；最终状态以 GitHub Releases 为准。
