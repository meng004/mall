# Cursor 第四轮评审与执行计划

评审对象：`sdc/integrated` HEAD `74b88b19`。本轮只复核提交和证据，并制订后续计划；未修改集成 worktree、未推送。

## 结论

**本轮四项修复基本真实、可追溯，当前“正式结论未完成”是正确的。** 68 项专项测试、266 项无在线模型回归、2 项 Mongo 回归均有持久日志；索引 20 个链接从 `SDC/` 解析全部存在。没有新的 32 题在线结果，也没有独立人工核对，不能宣称模型放行。

### 已核实

- `74b88b19` 位于 `5d4920fc` 之后，集成 worktree 干净；主目录索引未提交，报告已明确披露。
- `targeted-mvn.log`：68/0/0，`EXIT:0`。`red-cli.log` 保存了修复前 JSON 引号哨兵泄漏的红灯，随后绿灯可信。
- `full-offline-mvn.log` 的 E1–E7 计数为 3/3/21/6/3/25/82，合计 143；`admin-portal-mvn.log` 为 admin 78、portal 45，合计 123；总计 266/0/0。两份日志共享 Maven reactor，但索引按最终模块汇总，没有把 reactor 子模块重复相加。
- `mongo-mvn.log` 是 2/0/0。`cli-probe.txt` 记录了 CLI 版本、模型、有效 envelope、16373 ms 和 `ping`；它不能追溯已遗失的 14 秒 pong，也不能替代公开题重跑。
- E7-05 已补当前回放口径：32 题，执行 5，通过 1、失败 3、传输未完成 1、未执行 27；这与“1/4 只是局部比例”的限定一致。
- `CandidateDatabaseTest=1` 是真实数据库核对；`SDCE701Test=14`、`SDCE702Test=9` 是离线/替身/断库测试。该分类已修正。

## 仍然阻塞或需要处理的问题

### B1 / P1：正式放行仍缺人工记录和新的在线题集

这是外部证据阻塞，不是代码修复阻塞。当前报告中的技术阈值与正式 `decision=未完成` 分开记录，符合要求；历史公开回放也没有被冒称为新盲测。需要教师人工核对记录和独立准备的新 32 题在线结果后，才能重新判断正式结论。

### B2 / P2：E7-01/02 的在线评分就绪条件仍偏弱

`CandidateE701Evaluation.java`、`CandidateE702Evaluation.java` 用 `isolated || !missingDatabase` 决定是否计分。教学库可连接但未装载本题 facts 时，`missingDatabase=false`，仍可能把共享库查询结果计入模型分数；`isolated=false` 只写入报告，未阻止计分。当前没有新的在线运行，因此没有证据表明本轮已错误计分，但下一次在线执行会有风险。

触发顺序：数据库端口可达 → facts 未装载或非隔离 → 普通查询返回 → `scored=true` → 结果进入正常题分母。现有 Bean 检查和 `CandidateDatabaseTest` 只能证明本次夹具核对，不证明在线评测环境已经就绪。

### B3 / P2：技术阈值达到时进程退出码语义需明确

`EvaluationSupport.decision` 将技术结果“放行”转换为正式“未完成”，而各评测入口仍以 `if (!"放行".equals(decision)) System.exit(1)` 收尾。因此即使技术阈值满足、正式结论因缺人工保持未完成，进程仍退出 1。当前没有在线运行产出，尚未造成错误验收，但会让自动化调用者把“证据未完成”误读为测试崩溃。需要在现有报告字段和调用约定中明确退出码含义，或只在异常/安全驳回时非零；不得把正式未完成改成放行。

### B4 / P3：超时根因仍未定位

16373 ms 的最小 CLI 探针成功，只能证明当前登录、模型和 envelope 可用；历史 N12/N05 仍只有“当时超过 60 秒”的客户端现象。不能据此断言网络、额度、模型服务或输入规模是根因。公开失败目前可如实分类为协议/业务结论，暂不需要为修复而重跑在线题。

## 交给 Cursor 的执行计划

1. **先固定当前状态。** 在集成 worktree 执行 `rtk git status --short`、`rtk git rev-parse HEAD`，确认仍为 `74b88b19`；不 reset，不覆盖主目录既有改动，不推送。
2. **修 E7-01/02 就绪判定。** 复用现有 `CandidateDatabaseTest` 和 facts 夹具，不新增平台或 gate。在线入口只有在本题 facts 已事务性装载、查询核对通过且环境标记为隔离时才计分；可达但未装载、共享库或只取得 Bean 时写清阻塞并将相关模型行标为“未完成/未评分”。增加一个确定性反例：可连接、`isolated=false`、facts 未装载时不得进入正常分母。继续保留 7→9007 映射和逐主键清理。
3. **明确退出码契约。** 先查现有脚本和调用者如何解释 `System.exit(1)`。最小修改应让报告中的正式 `decision=未完成`、`technicalThreshold=放行` 可被机器区分；保留安全违规和真正运行异常的非零信号。为“技术通过但无人工”与“超时/未执行”各加一个入口级断言，避免只测 `decision` 辅助函数。
4. **定位超时但不伪造历史。** 只做一个当前最小请求作为基线，记录 CLI 版本、模型、参数摘要、退出码、耗时、脱敏 stderr 和 envelope。若成功，使用一个公开题的有界单变量诊断，区分输入规模、单次调用、解析和客户端等待；若失败，按实际鉴权/额度/启动/网络错误继续定位。不得把新探针当作 N12/N05 原始记录，不得无限重试、购买额度或直接放宽 60 秒。
5. **人工与新题集分离推进。** 新独立准备会话只读取公开规格/facts，生成 20 正常、8 对抗、4 分布外并标记待人工；实现会话不得读取未公开 expected。教师核对和本人录屏单独列为外部材料，没有记录就维持正式未完成。
6. **复验并更新索引。** 运行 E7 专项、E1–E7、admin/portal、Mongo；分别保存命令、cwd、Java 版本、提交号、退出码和测试计数。普通测试不得改写已提交回放。索引只引用实际存在的日志，继续区分代码受测版本、证据提交、技术阈值和正式结论。

建议专项命令：

```sh
rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17 PATH=/opt/homebrew/opt/openjdk@17/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin mvn -o -pl SDC/E7 -am -DskipTests=false '-Dtest=CandidateEvaluationTest,SDCE701Test,SDCE702Test,SDCE703Test,SDCE704Test,SDCE705Test,SDCE706Test,CursorCliLlmClientTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

收工条件：B2、B3 有失败反例和通过回归；B4 有真实诊断证据或明确保留的未定位结论；新在线题集和人工核对状态分开记录。没有这些材料时，只能报告技术阈值和正式未完成，不能报告模型放行。
