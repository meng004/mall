# E3-07 AI 使用记录

本示例为迁移重建，不是历史上的测试先行过程；未找到原始 AI 对话记录。

工具：Cursor 编码代理；JDK 17；JaCoCo 0.8.13。对照用 `SDC/compare.sh E3-07`，本示例不连数据库。旧记录自述 Cursor 子代理 Grok 4.7，未核实；迁移重建的测试、修复提交带 `Co-Authored-By: Claude Opus 5.5 (1M context)` trailer。没有学生录屏，也没有独立人工标注。旧记录写读取 E3-07 计划、评分、业务卡和 `AddressDefaultProbe`；探针在 `sdc-java-migration` 的 `SDC/evidence/target-candidates/AddressDefaultProbe.java` 和 `address-default-observation.log`，已核实存在。

采纳：

- 红灯由 `SDC/compare.sh E3-07` 在基线上回放得到。测试提交 `a65aaaa897d6e0d096e47824697e733df5e1983f`，修复提交 `2b3ab80f2de8f169e072bfffd836b1141f1e1ae1`。
- 修复只在清默认前用现成 Example 做 `selectByExample`，空则返回 0。位置在第 46–48 行，复用第 44–45 行的会员加 ID 条件。
- 保留有效目标的两次写入和接口上的 `@Transactional`。两次写入在第 60、62 行（基线第 57、59 行），由 `:67-86`、`:88-105` 锁定；`@Transactional` 在 `UmsMemberReceiveAddressService.java:29`，本分支没有改接口。

驳回：

- 不把编译失败当红灯。`compare.txt` 改前 Errors 0。
- 不把探针日志当成修复后的测试。探针只是 Mock 观察，输出写着 “Observation matched; service invocation only, mocked database, no transaction/HTTP/concurrency claim”。
- 不靠事务注解推断回滚。返回 0 是正常返回，不会触发回滚。
- 不改写成功路径的“先清后更新”顺序。顺序由 `:80-85` 断言，删掉第 60 行后 `:80`、`:101` 变红。
- 不改生成 Mapper。`exp/E3..exp/e3-07` 在 `src/main` 下只改了服务实现一个文件。

人工核对：

- `compare.sh` 回放时两例都是 `NeverWantedButInvoked`，调用点是基线第 57 行（`defaultStatus=0`，清原默认）和第 59 行（`defaultStatus=1`，目标更新）。这来自 surefire 报告，没有收进证据。`compare.txt` 第 3–4 行只有用例名和行号。
- 绿灯 6 项，见 `evidence/compare.txt` 第 5 行。
- 覆盖率见 `evidence/coverage.txt`：`update` 行 17/17、分支 6/6。
- 只在 `defaultStatus=1` 时检查目标的写法 6 例仍全绿；非默认的无效目标不发写入由第 46 行保证，但没有测试锁定。

未做：没有录屏，没有连接数据库验证事务，没有另一位人员复核。

限制：

没有保存当时的 AI 对话。
