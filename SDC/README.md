# SDC — software design and conventions

SDC 指 software design and conventions。学生从[学生操作手册](学生操作手册.md)进入：在基线上做首次构建，每次实验从脚手架开个人分支，用 `SDC/compare.sh` 做改前／改后对照。

当前结构：

- 基线标签 `baseline-dcaa93b3`（与分支 `sdc-teaching` 指向同一提交 `dcaa93b3`）：上游原版。E1 直接在基线上做，没有脚手架分支 `exp/E1`。
- 实验脚手架 `exp/E2` … `exp/E7`：`SDC/E<n>/说明.md`、`SDC/compare.sh`、`SDC/environment/`、`.github/workflows/sdc-compare.yml`，不含示例答案。`exp/E2` 另有公共视图图件 `SDC/E2/diagrams/`；`exp/E7` 另有共用的 LLM 访问层（`mall-portal/src/main/java/com/macro/mall/portal/llm/` 与 `langchain4j-open-ai` 依赖）和评测支持类。
- 示例分支 `exp/e<n>-<NN>`：从对应脚手架分出。`NN=00` 是教师演示。E2-00 是 Issue #42，E2-01 是 Issue #43；E5-00 是 Issue #44。
- Issue #2–#44，按标签查看，例如 [E3](https://github.com/meng004/mall/issues?q=label%3AE3)。
- 对照脚本 `SDC/compare.sh`。推送示例分支时，工作流 `.github/workflows/sdc-compare.yml` 自动重跑对照。
- 分支 `sdc-java-migration` 存放课程文档和教师参考材料，含教师参考实现，不是做作业的起点。

教师/助教看[环境准备](教师环境准备.md)。前端与微服务对照只用于已有理论讲解，不增加部署作业。课程边界：[理论章节](课程理论章节.md)、[实验方案](课程实验方案.md)、[考核方案](课程考核方案.md)。课程成绩构成不变，作业使用分实验评分细则；既有测试及安全要求保留。

## 实验导航

| 实验 | 课堂内容 | 入口 |
|---|---|---|
| E1 | 系统理解、依赖图、源码与运行证据 | 基线 `baseline-dcaa93b3`（见[学生操作手册](学生操作手册.md)）。本页 [E1 说明](E1/说明.md) 是迁移前旧结构 |
| E2 | 分层问题、两种解耦方案与架构视图 | [exp/E2 说明](https://github.com/meng004/mall/blob/exp/E2/SDC/E2/说明.md) · [Issue 标签 E2](https://github.com/meng004/mall/issues?q=label%3AE2) |
| E3 | 规格、积分缺陷、插入边界测试 | [exp/E3 说明](https://github.com/meng004/mall/blob/exp/E3/SDC/E3/说明.md) · [Issue 标签 E3](https://github.com/meng004/mall/issues?q=label%3AE3) |
| E4 | 策略重构、金额与行为特征测试 | [exp/E4 说明](https://github.com/meng004/mall/blob/exp/E4/SDC/E4/说明.md) · [Issue 标签 E4](https://github.com/meng004/mall/issues?q=label%3AE4) |
| E5 | 库存需求变更的影响分析 | [exp/E5 说明](https://github.com/meng004/mall/blob/exp/E5/SDC/E5/说明.md) · [Issue 标签 E5](https://github.com/meng004/mall/issues?q=label%3AE5) |
| E6 | 库存预警、日志与交易返回值 | [exp/E6 说明](https://github.com/meng004/mall/blob/exp/E6/SDC/E6/说明.md) · [Issue 标签 E6](https://github.com/meng004/mall/issues?q=label%3AE6) |
| E7 | 双查询实现、LLM 访问封装、只读验收 | [exp/E7 说明](https://github.com/meng004/mall/blob/exp/E7/SDC/E7/说明.md) · [Issue 标签 E7](https://github.com/meng004/mall/issues?q=label%3AE7) |

## 迁移前的旧结构（历史）

实验代码曾放在 `SDC/E1`–`E7` 模块，首次检查用 `mvn -pl SDC/E1 -am test`，教师对照用 `JavaEvidence`。产品改动也曾直接写在 `sdc-java-migration` 上。这些页面、日志和工具保留作历史记录，不再是学生入口。历史日志见 [evidence/java-migration](evidence/java-migration) 与[迁移验收](迁移验收.md)。

## 设计与记录

- [教师候选靶点库](教师候选靶点库.md)：E2 只有扩展题 E2-01，其余实验各 6 项；E7 完整业务设计与筛选理由也统一在本库，含代码依据、考核能力、验收边界与验证状态；学生每次只选一个。
- [靶点库核对记录](靶点库核对记录.md)：课程方案与 E1–E7 产出对照、42 项评分适配性检查及尚缺证据。
- [Cursor候选靶点产出计划](Cursor候选靶点产出计划.md)：42题逐题的源文件、修改范围、输入／期望、验证命令及交付路径；一次只执行所选编号。
- [迁移设计](SDC迁移设计.md)
- [实施计划](实施计划.md)
- [迁移验收](迁移验收.md)
- [历史评审](评审记录.md)

课堂个人讲解、互评、录屏及模型服务凭据由师生准备；历史记录不代替这些活动。
