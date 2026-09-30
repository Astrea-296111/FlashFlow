# 学习路线与可验收练习

优先级：事务/唯一键与锁顺序 → Lua与补偿竞态 → MQ投递、延迟及压测口径。先能够不用提示词逐句说明这三个部分，再拓展组件。

| 阶段 | 阅读与动手 | 必须能独立解释的产物 |
|---|---|---|
| 1：Java/Spring基础，约1周 | AuthController、record校验、SecurityConfig、OrderStatus；运行HttpIT；改变角色校验观察403 | 登录与注册流程图，401/403/404的区别，BigDecimal与Clock原因 |
| 2：MySQL交易，约1周 | OrderService.baseline/pay/closeExpired，逐句标事务；重复BaselineIT；读取SHOW ENGINE INNODB STATUS | 解释一次唯一键失败如何回滚扣库存，演示支付和关单只能成功一种 |
| 3：Redis资格，约1周 | reserve/transition/initialize.lua，手动制造WRONGTYPE；运行RedisIT；画三个状态窗口 | 说明Lua不回滚脚本已写命令，为什么不自动expire活跃资格，为什么不能live rewarm |
| 4：MQ与恢复，约1周 | AsyncOrderService、DeliveryHandler、OutboxRelay；运行真实faults；查消费offset与DLQ | 讲清send超时的不确定性、持久化栅栏、两个outbox的不同职责 |
| 5：性能与表达，约1周 | 重跑3组k6、sql_index；按原始数据算median；核对42个用例 | 说明入口P99与完成P99为什么不同，展示失败轮，重写简历每条证据 |

阶段验收不是背出组件定义。尝试去掉 uk_order_buyer、错误地在send异常立刻回补、把锁顺序改回先reservation、删除活动中的Redis库存，然后预测哪个用例失败，再执行验证。练习变更放独立分支，记录失败，不把破坏性练习合并到main。

后续顺序：持续负载与多库存规模 → 多API/worker及容量边界 → Redis恢复与Broker高可用 → 无重复历史订单政策升级 → 真支付回调 → 按实际需求拆服务。虚拟线程当前开关默认false，没有对照数据，不先写到简历。

面试练习：从[12-interview-guide.md](12-interview-guide.md)随机选5题，每题先30秒结论，再2分钟代码/失败例，再说明边界。讲解中能区分事实、推断和未做实验，比讲更多组件更有说服力。
