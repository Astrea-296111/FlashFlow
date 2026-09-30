# MySQL：事务、锁与索引实验

数据库为真实 MySQL 8.4.9/InnoDB，应用连接隔离级别 READ COMMITTED。迁移由 Flyway 执行；该依赖启动时提示 MySQL 8.4 超过其声明已验证的 8.1 范围，本次迁移、集成测试和 EXPLAIN ANALYZE 均实际成功，不能把兼容性警告描述成从未存在。

## baseline

`OrderService.baseline` 校验活动后执行：

```sql
UPDATE ticket_sku SET available_stock=available_stock-1,version=version+1
WHERE id=? AND available_stock>0 AND status='ACTIVE' AND mode='BASELINE';
```

影响行数不是 1 即拒绝。预占与订单随后在同事务插入；`uk_order_buyer(user_id,sku_id)` 失败会回滚之前的扣库存。version 当前只记录库存版本，不参与 `WHERE version=?` 的乐观锁 CAS，不能将它写成“实现乐观锁”。售罄后所有 baseline 请求仍访问 DB，这是有限库存 burst 下的主要开销来源。

## 唯一键与索引对应的访问模式

| 访问模式 | 使用的结构 | 原因 |
|---|---|---|
| 每用户每票档限购 | uk_order_buyer(user_id,sku_id) | 跨进程持久化裁决，Redis 绕过也无法重复下单 |
| 预占结果找订单 | UNIQUE reservation_id | 重复投递返回已有订单 |
| 支付幂等 | payment_key / order_no UNIQUE | 防同 key 多订单、同订单多支付记录 |
| 用户历史 | idx_order_user(user_id,created_at,id) | 固定用户的倒序列表 |
| 到期扫描 | idx_order_expire(status,expire_at,id) | 等值状态＋截止范围＋有序 LIMIT |
| outbox 批次 | idx_outbox_status(status,id) | PENDING 有序批次和 SKIP LOCKED |

程序代码用显式 SQL 参数绑定，无字符串拼接用户查询条件；核心 SQL 留在 Repository/Service，便于面试按事务逐句解释。

## 十万行合成订单索引实验

原始证据：[explain-analyze.json](../benchmark/results/20260930T114725Z-sql-index/explain-analyze.json)。`sql_index.py` 在专用临时 fixture 表生成 100000 行，前后各 3 次；完成后删除该表。它们不是 100000 张真实用户订单。

```sql
SELECT id FROM benchmark_order_scan
WHERE status='WAIT_PAY' AND expire_at<='2026-09-30 12:00:00'
ORDER BY expire_at LIMIT 100;
CREATE INDEX idx_status_expire ON benchmark_order_scan(status,expire_at,id);
```

| 指标 | 无复合索引 | 有复合索引 |
|---|---:|---:|
| 客户端 EXPLAIN ANALYZE wall 中位数 | 33.684 ms | 1.688 ms |
| 三次 wall 范围 | 27.767..46.937 ms | 0.734..2.144 ms |
| 实际访问 | 扫描 100000 行再过滤排序 | 覆盖索引范围扫描，LIMIT 100 |
| 第一轮顶层 actual time 结束值 | 27.7 ms | 0.127 ms |

客户端 wall 包含命令与结果返回，不能与执行计划内部 actual time 混用。该小表、热缓存、本机实验说明了访问路径收益，不能据此声称线上慢 SQL 普遍提升 20 倍。

## 本次真实死锁诊断

[失败轮 InnoDB STATUS](../benchmark/results/20260930T115123Z-paired/innodb-status.txt) 中两个事务均持有 ticket_sku 主键的 S 锁，并等待升级为 X 锁。结合外键 DDL、调用顺序与 40 个不同预占的复现测试，定位到 reservation FK 先锁父 SKU。修复后先获取 SKU X 锁，再创建预占。

MySQL 官方说明外键检查会取得共享记录锁，以及 ON DUPLICATE KEY UPDATE 的重复键分支取得排他锁：[官方锁说明](https://dev.mysql.com/doc/refman/8.4/en/innodb-locks-set.html)，访问 2026-09-30。修复解决本次复现，不保证任意新 SQL 与事务组合都永不死锁；consumer 仍保留重试。
