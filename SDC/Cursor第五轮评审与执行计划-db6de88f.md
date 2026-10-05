# Cursor 第五轮评审与执行计划

评审对象：`sdc/integrated` HEAD `db6de88ff798308a9ac8064180da1102abfe0d62`。本轮只复核提交、日志和索引，并制定后续执行计划；未修改集成 worktree，未推送。

## 结论

**B2、B3 的修复真实且有回归证据，当前正式结论保持“未完成”正确。** 隔离库计分条件、退出码语义和相应反例已经落到代码与测试；专项最终汇总 70/0/0，E1–E7 汇总 145/0/0，admin/portal 123/0/0，合计 268/0/0，Mongo 另 2/0/0。没有人工核对和新的 32 题在线结果，不能放行。

## 可核查事实

- `db6de88f` 位于 `74b88b19` 之后，集成 worktree 干净。
- `EvaluationSupport.modelScored` 要求 `isolated=true`、非传输、非缺库；库可达但 facts 未装载时不计分。`CandidateEvaluationTest` 覆盖该情形。
- `processExit/exitIfRejected` 只让“驳回”返回 1；未完成、不放行、降级和传输未完成均返回 0。六个评测入口已改，`QueryEvaluation` 未改，报告同时保存 `processExit` 和 `exitMeaning`。
- `targeted-mvn.log` 最终结果为 70/0/0/0，`EXIT:0`；`full-offline-mvn.log` 的模块末汇总为 E1 3、E2 3、E3 21、E4 6、E5 3、E6 25、E7 84，即 145；`admin-portal-mvn.log` 为 admin 78、portal 45，即 123；145+123=268。不要把这些日志中 Maven reactor 的子模块行再次相加。
- Mongo 日志为 SDCE604/605 各 1/0/0。Java 17.0.19、25 个索引链接从集成 `SDC/` 解析均存在。
- `timeout-probe.txt` 保存了同一 CLI 基线 15.8 秒，以及一次公开 N12：输入 89 字、35.92 秒、235 字、有效 envelope、60 秒内返回。它只说明当前一次运行成功，不能重建历史 60 秒超时。

## 仍需处理的事项

### B1：正式人工与在线证据阻塞（外部阻塞）

没有独立人工核对记录，也没有新的 32 题在线结果。技术阈值可以单独记录，正式结论必须保持未完成。不能把回放、CLI 探针或 268 项代码回归提升为模型放行证据。

### F1：索引的受测版本仍然过时（P2）

索引当前正文开头仍写“代码受测版本 `206f2664`”，随后才追加第四轮 `db6de88f` 的结果。读者从当前入口无法确定 268 项日志究竟针对哪个代码提交；这是追溯缺陷，不是数字本身错误。应把当前版本改成 `db6de88f`，并把 `74b88b19`、`db6de88f` 的变更链列在当前结果旁；旧 `206f2664` 明确标历史基线。每条当前日志应标 evidence commit `db6de88f`。

### F2：索引把最终汇总与模块行混在一起（P3）

当前说明写法已经给出正确合计，但原始日志中存在多条 `Tests run`（reactor 子模块和最终模块摘要）。若后续复制数字，容易得到 536 或 140 之类的错误总和。索引应只引用每份日志的最终 `Results` 汇总，并明确 268 的算式；在证据说明里保留“不要加总 reactor 子模块行”的注释。第一次 `full-offline-exclude-failed.log` 只作为失败命令记录，不纳入通过计数，这一点继续保留。

### F3：N12/N05 超时根因仍未定位（P2）

N12 当前一次 35.92 秒成功，不能解释历史 N12/N05 为什么超过 60 秒；N05 本轮未重跑。公开失败仍可分类为历史传输未完成、协议或业务结果，但不能写成已修复或环境根因已知。

## 交给 Cursor 的执行计划

1. **固定版本和状态。** 在集成 worktree 执行 `rtk git status --short`、`rtk git rev-parse HEAD`，确认 `db6de88f`；不 reset、不覆盖主目录未提交改动、不推送。
2. **修正唯一当前索引入口。** 将索引顶部当前结果改为受测提交 `db6de88f`，注明证据提交同为该提交；把 `206f2664` 移至历史基线。保留 25 个现有链接并再次从 `SDC/` 解析。当前表补齐专项 70/0/0、E1–E7 145、admin/portal 123、合计 268、Mongo 2，并将每个计数指向最终 `Results` 行。
3. **加入最小退出码反例回归。** 复用已有 `processExit` 测试，明确断言：技术阈值“放行”但正式 `decision=未完成` 返回 0；传输未完成加未执行返回 0；安全驳回返回 1；报告写出且 `processExit` 与实际退出码一致。不要让 QueryEvaluation 或其他调用处获得新语义。
4. **保持隔离计分反例。** 复用 `modelScored`，覆盖可达共享库、facts 未装载、隔离且夹具完整、传输失败四种输入；检查模型行 `scored`、分母和报告 `isolatedDatabase` 一致。不得通过设置 `isolated=true` 或改 expected 绕过夹具核对。
5. **有界定位 N12/N05。** 先用现有 CLI 探针确认版本、模型、timeout、envelope 和脱敏 stderr；不把新输出写入旧历史报告。对 N12 与 N05 各最多做一次公开输入运行，记录输入长度、单次耗时、输出长度、退出码、envelope 和错误摘要。若均成功，只能得出“当前运行未复现”，不能修改 60 秒限时；若失败，按实际鉴权、额度、启动、网络、进程等待或解析错误归类。不要无限重试、购买额度或重跑整套六题。
6. **继续区分外部材料。** 新题集由独立准备会话按 20 正常、8 对抗、4 分布外生成并标记待人工；实现会话不读取未公开 expected。教师核对、本人录屏和新的在线 32 题结果分别记录，缺任一项不改变正式未完成。
7. **复验交付。** 运行现有 E7 专项、E1–E7、admin/portal、Mongo 命令；保存 Java 版本、cwd、受测提交、退出码、最终测试汇总和前后 Git 状态。普通测试不得改写历史回放；不把测试成功写成模型成功。

专项命令：

```sh
rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17 PATH=/opt/homebrew/opt/openjdk@17/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin mvn -o -pl SDC/E7 -am -DskipTests=false '-Dtest=CandidateEvaluationTest,SDCE701Test,SDCE702Test,SDCE703Test,SDCE704Test,SDCE705Test,SDCE706Test,CursorCliLlmClientTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

收工条件：索引当前版本与证据提交一致，268 的算式可从最终汇总复核；退出码和隔离计分反例通过；N12/N05 得到有界诊断或明确“当前未复现”；人工和新在线材料仍独立列为未完成。
