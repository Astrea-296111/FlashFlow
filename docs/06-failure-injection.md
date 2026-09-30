# 故障实验：结果、注入方式与保留记录

最终真实Broker验收：[faults.json](../benchmark/results/20260930T124455Z-faults/faults.json)。集成验收：[42个用例](../benchmark/results/20260930T122848Z-tests-final/summary.json)。MQ集成用例中的MessageBus是受控mock；下面明确哪些使用真实Broker。

| 场景 | 注入方式 | 实测结果 |
|---|---|---|
| A：重复MQ | 对一个已成功预占实际再发20次，全部SEND_OK | 该reservation仍1个订单；20张总库存仍只占20单，重复订单0 |
| B：worker停机 | 停止指定worker，20个不同用户走HTTP资格并真实Broker确认 | 停机订单0，Broker offset diff=20；恢复后20订单、diff=0；含JVM启动至SQL恢复约12.928s |
| C1：send超时但已收到 | MessageBus抛受控IOException，再实际调用consumer业务处理 | 保留预占、delivery UNCERTAIN，随后SUCCESS，不提前回补 |
| C2：send失败且没有送达 | MessageBus受控mock＋逻辑Clock推进到预占截止 | 截止前不回补，截止后只回补一次，迟到消息无订单；同用户可重新预占 |
| consumer DB提交后进程窗口 | 先process提交，不执行Redis确认，然后reconcile | 已有订单被确认，不加回库存 |
| timeout outbox失败 | 受控mock首次发送异常再恢复 | PENDING、attempts+1，恢复后SENT；没有伪称为Broker真实故障 |
| 支付与关单竞争 | 20订单、早/边界/晚支付与并发close，重复3次，真实MySQL | 每轮5 PAID/15 CLOSED；支付记录5、释放库存15，总数与守恒通过 |
| 真实延迟关单 | 禁用全部worker维护扫描；fixture截止缩短到3s，实际发送level2/5s消息 | CLOSED，DB库存1；从helper启动至观察关单7.624s，含helper启动；恢复维护后Redis释放outbox DONE |
| 毒消息 | 实际发送无效JSON和null | 两种hash均存在隔离记录；相同null重复仅保留1行，非重复插入 |
| Redis活跃库存丢失 | RedisIT删除一个活跃stock键 | live rewarm拒绝；未宣称完整恢复成功 |

重复消息同时有20路重复集成测试，以及40个不同消息同SKU的锁顺序回归；它们调用真实MySQL/Redis但不走Broker。实际A/B/延迟/毒消息实验另有producer ACK日志、worker日志和官方consumerProgress原始输出。

## 可重复脚本

Docker模式：`python benchmark/scripts/faults.py`，使用镜像内编译好的MqProbe并在Compose网络内发布，避免宿主机无法解析broker的问题。此路径已设计，完整容器执行尚未验证。native模式需要显式PID、Java路径、runtime home与ROCKETMQ_HOME；脚本只允许停止指定FlashFlow JAR，不搜索或kill其他进程。native需先生成`.local/classpath.txt`。

```bash
./mvnw dependency:build-classpath -Dmdep.outputFile=.local/classpath.txt
# native：额外提供 --native-pid、--native-java、--native-home
python benchmark/scripts/faults.py
```

在隔离演示环境执行，会创建新的测试活动并短暂停止worker。生产环境没有公开HTTP故障注入入口。

## 失败证据也属于交付

| 记录 | 失败原因及如何处理 |
|---|---|
| [115123Z-paired](../benchmark/results/20260930T115123Z-paired/) | 初版外键锁升级，100 accepted仅92订单于60s内收敛；保存InnoDB STATUS、worker日志、修复前回归测试 |
| [121527Z-faults](../benchmark/results/20260930T121527Z-faults/) | 脚本SQL会话+07当作UTC重建消息，身份不匹配而被正确隔离；修复实验会话时区 |
| [122xxx observed采样组](../benchmark/results/) | 本地IT和演示Redis数据库未隔离，第三组0 accepted；审计失败，保留全组与孤立测试键清理清单 |
| [123330Z-faults](../benchmark/results/20260930T123330Z-faults/) | 脚本错误要求相同null再次增加隔离行；实现按hash幂等，修复断言为行存在，并等待Broker持久化offset归零 |

这些记录没有删除、截断成成功截图，也没有并入最终成功指标。首次纯重复INSERT IGNORE锁升级观察的完整旧日志曾被后续Maven报告覆盖；不声称保留了那份已覆盖日志，外键锁升级的完整后续复现记录已保存。

未注入Broker进程/磁盘损坏、Redis完整丢数据后的自动恢复、多主/网络分区和真实支付渠道故障。本次一致性结论只覆盖表内实际注入的范围。
