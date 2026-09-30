# 简历写法研究

访问日期：2026-09-30。读取MIT CAPD两份公开指导、Arun Veligatla网页简历、Ahmed Doghri两页PDF，以及市场调研中的5篇Java简历点评。本项目不复用这些人的项目数字，也不把公开自述当成经过独立审计的业绩。

| 来源 | 学习的表达方法 | 应用于FlashFlow |
|---|---|---|
| [MIT：Writing about your skills](https://capd.mit.edu/resources/resumes-writing-about-your-skills/) | PAR：行动、背景、结果；有明确作用的量化 | 先说限量交易约束，再写实现与审计 |
| [MIT：Crafting an effective resume](https://capd.mit.edu/resources/career-toolkit-crafting-an-effective-resume/) | 内容围绕目标岗位，写清与岗位有关的成果 | Java基本功、事务与可靠性是主线 |
| [Arun Veligatla](https://arunveligatla.com/resume) | 将系统工作和可评价的结果连起来 | 不写“熟悉RedisMQ”，给代码和原始数据 |
| [Ahmed Doghri PDF](https://adoghri.com/assets/pdf/ADoghri%20Resume.pdf) | 动作、工程方法、规模与评价口径相连 | 写本机实验规模；不借用其线上/收入/准确率数字 |
| [5篇Java点评清单](00-market-and-reference-research.md) | 技术堆叠和真实性会被追问 | 每条都能落到自己的事务、Lua、用例和失败记录 |

好的项目条目能够回答：为什么这样做、自己实现了什么、用什么输入验证、结果何时不成立。FlashFlow最大的真实亮点是发现并修复锁升级死锁、区分202和最终订单、在故障后审计库存。单机入口RPS、合成SQL样本和模拟支付都应写出限定范围。

不写“生产级”“千万用户”“100%可靠”“彻底消除死锁”“零丢失”“分布式exactly once”。只在当前已执行的有限测试范围内写超卖/重复订单为0。不写Redis version为乐观锁，不写用过未集成的Redisson/Sentinel/XXL-JOB。GitHub CI第一次远程成功前，不写已通过绿色CI。

最终可用条目和证据映射：[11-resume.md](11-resume.md)。RepoPilot作为Agent工程与评测项目，FlashFlow作为Java交易与可靠性项目，避免两项都成为只有组件列表的展示。
