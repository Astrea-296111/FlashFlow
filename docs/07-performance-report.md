# 性能报告：入口更快，完成路径更长

全部数据于2026-09-30真实执行。最终验收目录是[20260930T123027Z-paired-final](../benchmark/results/20260930T123027Z-paired-final/)，对应应用业务源码提交56a0620；后续修改为文档、证据和探针打包，不改变该版本的业务Java/Lua。旧轮次、失败记录和低/高结果全部保留，没有挑最漂亮的轮次。

## 环境与方法

Linux6.18.44、Java21.0.12.1、Spring Boot3.5.16、MySQL8.4.9、Redis7.4.2、RocketMQ5.3.3、k6 1.3.0。本环境无Docker daemon，实际使用native官方服务。容器报告宿主CPU型号INTEL XEON PLATINUM8573C，可见CPU数9，cgroup配额8核、内存上限8GiB；这些不是独占物理机器资源。

API与worker各1个，平台线程，-Xms128m/-Xmx384m，Hikari各16连接，MQ消费者4线程，Broker512MiB/SYNC_FLUSH、8队列、单节点，Redis单节点AOF。详细[environment.json](../benchmark/results/20260930T123027Z-paired-final/environment.json)。没有验证生产高可用、外部网络延迟或多实例容量。

每轮3000个不同用户、40VU、100库存；fixed shared-iterations，最长120s，实际为短burst。三组交替顺序：B→O、O→B、B→O。用不同SKU保持模式隔离；50次GET预热HTTP/JWT公共路径，不先消耗库存。两组关闭令牌桶；没有把限流拒绝能力当系统容量。

SQL观察器对两组都每25ms读已经提交的订单ID，从负载机请求开始计时到首次观察到该订单，约有一个轮询周期的采样不确定性。与worker的Micrometer“Redis资格产生→DB提交/确认”计时口径不同。JWT文件仅保存在临时私有文件夹，结束删除，不进入证据。

## 全部最终轮次

| 轮次 | 方案 | RPS | HTTP P50 ms | P95 ms | P99 ms | accepted P99 ms | 完成 P95 ms | 完成 P99 ms | 最终订单 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | baseline | 404.3 | 74.7 | 244.3 | 660.5 | 987.6 | 895.8 | 1013.5 | 100 |
| 1 | optimized | 1270.9 | 15.1 | 63.5 | 342.3 | 998.3 | 3628.8 | 3677.3 | 100 |
| 2 | optimized | 966.6 | 18.5 | 140.3 | 205.8 | 240.0 | 3226.6 | 3257.0 | 100 |
| 2 | baseline | 627.5 | 56.4 | 130.1 | 206.2 | 266.1 | 236.8 | 290.8 | 100 |
| 3 | baseline | 841.3 | 42.0 | 98.7 | 176.9 | 191.7 | 207.6 | 215.8 | 100 |
| 3 | optimized | 3089.5 | 8.5 | 32.7 | 50.6 | 70.7 | 1456.7 | 1460.8 | 100 |

## 汇总

| 三轮轮次值的中位数 | Baseline | Optimized |
|---|---:|---:|
| 入口 RPS | 627.5 | 1270.9 |
| HTTP P50 (ms) | 56.4 | 15.1 |
| HTTP P95 (ms) | 130.1 | 63.5 |
| HTTP P99 (ms) | 206.2 | 205.8 |
| 被接受请求 HTTP P99 (ms) | 266.1 | 240.0 |
| 最终落库完成 P95 (ms) | 236.8 | 3226.6 |
| 最终落库完成 P99 (ms) | 290.8 | 3257.0 |

入口RPS中位数为约2.03倍，HTTP P95下降约51.2%。HTTP P99基本相同，不能写成尾延迟显著降低。accepted请求P99仅略降，而且第一轮optimized仍更慢。完成P99中位数从290.8ms增加到3257.0ms。

有限库存下多数请求是SOLD_OUT拒绝，故RPS不是成功订单TPS，更不是持续吞吐容量。每轮只有100个accepted，最终均100个订单；六轮共18000请求、600个持久化订单，超卖0、重复订单0、库存非负与守恒全部通过，异步Redis与MySQL库存均为0。后续到期历史状态变化不改变这里在该轮结束时保存的审计快照。

“轮次P99中位数”不是合并18000请求后的总体P99。每轮与完整accepted样本、k6-summary、orders.csv、completion.json、validation.json均保留，不能只展示一个均值或最佳截图。

## 运行期资源

0.5s间隔取CPU ratio、堆、活跃/等待连接、线程和GC count，结束保存Prometheus全量快照。下表是三轮中采样的最大值，不是瞬时峰值；Hikari和进程CPU指标有更新滞后，短轮次还可能包含上一轮余波。

| 采样项 | Baseline API | Optimized API | Baseline worker | Optimized worker |
|---|---:|---:|---:|---:|
| 最近CPU ratio最大样本 | 0.698 | 0.652 | 0.215 | 0.295 |
| heap used 最大样本 MiB | 114.1 | 118.6 | 104.2 | 113.3 |
| Hikari active 最大样本 | 16 | 16 | 3 | 5 |
| Hikari pending 最大样本 | 24 | 21 | 0 | 0 |
| live threads 最大样本 | 90 | 91 | 97 | 97 |

不据此声称CPU/内存成本下降，也不把累计GC次数最大值当每轮GC频率。需要独立持续负载与更细粒度采样才能归因。Broker offset在真实停机实验额外验证20→0；当前未实现Prometheus lag exporter。

## 三个已执行的分析与优化

1. **无效流量进入MySQL热点**：baseline所有请求访问DB；optimized用同槽Lua与250ms售罄标记挡住大部分拒绝流量。最终入口吞吐提升，但异步链路增加完成时间，不能以202完成替代业务完成。
2. **外键共享锁升级死锁**：初轮accepted100、60s窗口内仅92订单，真实InnoDB STATUS显示SKU S→X循环等待。新增40不同预占并发测试复现，再统一SKU→reservation锁序；修复后三轮完整对照与最终三轮均落库100。该失败轮不能计为通过，即使入口RPS漂亮。
3. **过期订单扫描路径**：100000行合成fixture，前后各3次EXPLAIN ANALYZE，从全扫排序改成复合覆盖索引；客户端wall中位数33.684→1.688ms。详细[SQL报告](05-mysql-and-index.md)，不声称100000真实交易或线上普遍20倍收益。

补充采样时，测试曾与演示共用Redis DB0，第三轮optimized得到0 accepted，库存审计拦截了它。已隔离测试DB1、拒绝初次预热残留状态，并加强accepted必须等于min(stock,requests)的校验；整个受污染实验组不用于以上最终指标。旧组的CSV时间字段可能为服务器会话+07显示；最终组统一UTC，计时均来自负载机和观察机epoch，不从CSV时间相减。

## 复现与局限

```bash
python -m pip install -r benchmark/requirements.txt
RATE_LIMIT_ENABLED=false docker compose up --build -d
export JWT_SECRET=local-development-secret-change-this-32-bytes
python benchmark/scripts/run.py --requests 3000 --v-us 40 --stock 100 --repeats 3
python benchmark/scripts/sql_index.py
```

本机native的相同脚本已执行；Compose镜像与端到端启动仍未执行，config解析已单独验证。k6需安装指定1.3.0。测试与压测不要共用业务Redis键空间；脚本创建自己的活动但不删除已有业务订单。

这个结果适用于短burst、单SKU、很低成功比例与共享机器。没有稳态流量、多库存比例、重复用户热度、Broker丢盘、Redis完整恢复、多API、JFR或网络延迟分解实验。异步完成更慢的机制解释属于合理推断，没有分摊排队/GC/锁等待各自百分比的证据。
