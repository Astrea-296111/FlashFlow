# 市场与参考资料调研

访问日期：2026-09-30。目的：确定个人 Java 后端作品应体现什么能力，不能用这个小样本推断整个市场的岗位占比。

## 样本与边界

读取 8 个当前可访问的岗位入口、10 个面经页面的公开内容、5 个 Java 简历点评页面，并核对任务书指定的 7 个开源仓库。岗位入口来自牛客平台，发布者可能是招募者或汇编者；并非全部公司官网 JD。岗位页未明确显示发布日期，不能保证全部为近期新增。面经以 2026 春秋季内容为主，混有汇编和作者回顾；按页面计数，不按独立面试场次计数。字节/携程汇编部分受订阅墙限制，只分析公开可见部分，没有声称读过付费全文。腾讯后端15无法访问，保留失败记录，补充浪潮一面作为第10个可读样本。

同一作者、招募账号和平台的内容可能相关，因此频次只对下列8个JD计算，面经与简历点评仅作定性证据。导航、推荐区和评论不计入技能词；提到某词不等于要求熟练，也不等于面试真的考了它。公开简历和点评的数字没有转移到本项目。

## 岗位入口

| 样本 | URL | 正文技能词 |
|---|---|---|
| 快手 Java | [原页面](https://www.nowcoder.com/jobs/detail/451858) | Spring Boot、MySQL、Redis、MQ、Java并发、JVM、Docker、Spring Cloud |
| 字节后端 | [原页面](https://www.nowcoder.com/jobs/detail/447229) | Spring Boot、MySQL、Redis、MQ、Java并发、JVM、Spring Cloud |
| 携程 Java | [原页面](https://www.nowcoder.com/jobs/detail/463591) | MySQL、Java并发、JVM |
| 思朗 Java | [原页面](https://www.nowcoder.com/jobs/detail/462971) | Spring Boot、MySQL、Redis、Java并发、JVM、Docker、Spring Cloud、AI/RAG |
| 贝壳研发 | [原页面](https://www.nowcoder.com/jobs/detail/462880) | Spring Boot、MQ、Java并发、AI/RAG |
| 携程全栈 | [原页面](https://www.nowcoder.com/jobs/detail/463589) | MySQL、Java并发、AI/RAG |
| 中国电子云后端 | [原页面](https://www.nowcoder.com/jobs/detail/463602) | 未命中所列词 |
| 同花顺 Java 实习 | [原页面](https://www.nowcoder.com/jobs/detail/431595) | Spring Boot、MySQL、Redis、Java并发 |

## JD技能提及频次

| 技能词组 | 页面数 / 8 | 匹配口径 |
|---|---:|---|
| Spring Boot | 5/8 | 正文大小写不敏感关键词匹配 |
| MySQL | 6/8 | 正文大小写不敏感关键词匹配 |
| Redis | 4/8 | 正文大小写不敏感关键词匹配 |
| MQ | 3/8 | 正文大小写不敏感关键词匹配 |
| Java并发 | 7/8 | 线程/并发/CAS/AQS/ThreadLocal |
| JVM | 4/8 | 正文大小写不敏感关键词匹配 |
| Docker | 2/8 | 正文大小写不敏感关键词匹配 |
| Prometheus | 0/8 | 正文大小写不敏感关键词匹配 |
| Spring Cloud | 3/8 | 正文大小写不敏感关键词匹配 |
| AI/RAG | 3/8 | 正文大小写不敏感关键词匹配 |

Prometheus 0/8 是这组抽样的结果，没有推断其市场价值为零。它在本项目用于可核验的资源/延迟证据。Spring Cloud 有提及，但本项目的单 DB 交易不需要为关键词拆微服务。AI/RAG 的 3/8 说明这组样本存在相关方向，不代表所有 Java 后端都要求做第二个 AI 项目。

## 面经公开样本

| 页面 | 明确日期或可见范围 | 对本项目的启发 | 来源 |
|---|---|---|---|
| 字节后端15 | 2026 年 9 月 3 日、2026 年 9 月 2 日；公开片段，付费部分未读 | 缓存与双写一致性、令牌桶、补偿失败后的处理 | [页面](https://www.nowcoder.com/discuss/930296009521889280) |
| 携程后端01 | 2026 年 9 月 11 日、2026 年 8 月 18 日；公开片段，付费部分未读 | 项目深挖、SQL/JVM 与中间件取舍 | [页面](https://www.nowcoder.com/discuss/929911457448230912) |
| 后端全栈复盘 | 页面显示月日，年份未明确；以原链接为准 | 项目真实性与线程/索引，不只背八股 | [页面](https://www.nowcoder.com/discuss/932058602422956032) |
| 深轻二面 | 页面显示月日，年份未明确；以原链接为准 | 自己做了什么以及设计选择 | [页面](https://www.nowcoder.com/discuss/931897300308226048) |
| 实在智能 Java | 2026.7.9 | 并发基础与部署排错 | [页面](https://www.nowcoder.com/feed/main/detail/06595dc27127476c9b544d20eb99cfe8) |
| 小米 Java | 页面显示月日，年份未明确；以原链接为准 | 订单消息、数据库与业务追问 | [页面](https://www.nowcoder.com/feed/main/detail/ec9340e947aa4613a284688e00ec4f5d) |
| 字节实习 Java | 页面显示月日，年份未明确；以原链接为准 | 索引与 Redis 原理 | [页面](https://www.nowcoder.com/feed/main/detail/c1448c65b644471e9db872852b4978ce) |
| 趣链 Java | 页面显示月日，年份未明确；以原链接为准 | 线程池、缓存与消息可靠性 | [页面](https://www.nowcoder.com/discuss/929042072709918720) |
| 字节后端08 | 2026 年 4 月 26 日、2026 年 4 月 24 日；公开片段，付费部分未读 | 汇编中状态竞争与幂等追问 | [页面](https://www.nowcoder.com/discuss/922664655749648384) |
| 浪潮 27届 Java 一面 | 09-21 16:16 (year not printed) | 分工与真实性；作者准备的Spring/MySQL并未被问到 | [页面](https://www.nowcoder.com/discuss/931582652442824704) |

## 简历点评样本

| 页面 | 启发（本项目的归纳，非原文引用） | 来源 |
|---|---|---|
| 点评42 | 避免把所有流行组件都写成亮点，给出业务链路与验证 | [页面](https://www.nowcoder.com/feed/main/detail/59cf37e96a094e5298081a8f67fb9772) |
| 点评2 | AI项目与Java交易项目要有主线，每个设计能追问 | [页面](https://www.nowcoder.com/feed/main/detail/18b2a788c36b4f708ad206522da0f0a3) |
| 简历怎么写 | 把实现步骤、技术选择与结果连在同一条描述 | [页面](https://www.nowcoder.com/feed/main/detail/4634de77e8bf4e55ad15dbe46da07611) |
| 点评11 | 明确环境与贡献，删掉无法解释的复杂名词 | [页面](https://www.nowcoder.com/feed/main/detail/59239fbe7d69479c887b2f00c9cece81) |
| 点评9 | 压测数字需要口径，课程功能清单不能代替证据 | [页面](https://www.nowcoder.com/feed/main/detail/6ba4e496c91e41ff88f75bee9380b6f9) |

## 决策

保留 Java21、Spring事务、MySQL条件更新/唯一键、Redis Lua、MQ重复处理、可重现测试和性能口径。业务选择限量票务，能够完整解释资格、订单、支付、关单与退款；不扩展成通用商城。

RepoPilot 的 README、docs/16-项目STAR与简历.md、docs/17-外部历史Bug实测结果.md 已读取。它展示 Agent 工程、评测与工具链；FlashFlow 展示 Java 交易一致性、SQL与消息故障处理。沿用其“先给原始结果再写结论”的表达方式，不把 RepoPilot 的外部Bug指标归到本项目。

来源：[RepoPilot](https://github.com/Astrea-296111/RepoPilot)、[STAR与简历](https://github.com/Astrea-296111/RepoPilot/blob/main/docs/16-项目STAR与简历.md)、[历史Bug实测](https://github.com/Astrea-296111/RepoPilot/blob/main/docs/17-外部历史Bug实测结果.md)。上游路径以任务书给出的分支页面为参考；本地未修改该仓库。

机器可核对的来源、日期、公开范围与正文校验值：[research-samples.json](research-samples.json)、[retrieval-samples.json](retrieval-samples.json)。两种读取渠道的可访问性可能不同，状态差异已保留，不把 blocked 页面计为已读全文。原始第三方全文没有复制进项目，报告仅含短归纳和链接。
