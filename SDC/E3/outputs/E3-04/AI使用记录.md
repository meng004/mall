# E3-04 AI 使用记录

本示例为迁移重建，不是历史上的测试先行过程；未找到原始 AI 对话记录。

工具：Cursor 编码代理；JDK 17；JaCoCo 0.8.13。对照用 `SDC/compare.sh E3-04`，事务例需要 `SDC/environment/compose.yml` 的 MySQL 13306。旧记录自述 Cursor 中的 Grok 4.7，未核实。没有学生录屏，也没有独立人工标注。

采纳：

- 红灯由 `SDC/compare.sh E3-04` 在基线上回放得到。测试提交 `6a483a025bd4ce9993e6fea41057d75d6ae97d0a`，修复提交 `34a7732952321c231c933f4a6b591efed217af54`。Mock 例只替换两个 Mapper，并在 `updateByPrimaryKeySelective` 和 `updateByPrimaryKey` 收到对象时复制字段（`SDCE304Test.java:52-61`、`:62-66`）。另有 `SDCE304TransactionTest` 4 例经 13306 走真实 Mapper XML（`:180-190`），事务代理在测试里手工组装（`:247-255`）。
- 修复只加在 `update`（第 59–103 行）。比较旧分类/类型和新分类/类型；目标分类不存在时调用 `Asserts.fail`，校验在第 77–80 行，早于第 81 行的写入。接口 `update` 加 `@Transactional`（`PmsProductAttributeService.java:31`），因为修复后最多要写三次（属性，以及源分类、目标分类）。
- 本次按评审修改了产品代码：入参 `type` 或分类 ID 为 null 时沿用数据库里的旧值（第 69–71 行），不再把 null 当成换了分类或类型。selective 更新本来就会留下这两个字段的旧值。旧记录没有这一条。
- 分类和类型不变时不写分类计数（第 74–76 行，`:91-105`）。只换类型时只写一次分类（第 99–101 行，`:118`）。

驳回：

- 不抽出与 E3-03 共用的计数函数，示例彼此独立（指南第一节第 2 条）；本分支 `delete` 仍是基线原样（第 110–135 行，仍用 `ids.get(0)`）。
- 不在服务里补 `@NotNull` 来冒充 E3-06 的入口校验。分类不存在是跨记录拒绝，不是字段注解。入参分类或类型为 null 时视为不改，沿用 selective 更新的语义。
- Mock 例 `SDCE304Test` 没有事务代理，所以 `:175-176` 要求写入调用本身不发生；注入「先写后校验」后 `:175` 变红。事务代理只在 `SDCE304TransactionTest` 里。
- 不把「期望异常没有抛出」写成环境错误。它是 `AssertionFailedError`。`:169` 是基线不拒绝，属业务红灯。`:75`、`:80` 经 `:96` 报期望异常未抛出，是注入的写分类失败没有触发，因为基线不写分类；不表示基线缺事务。`:52` 是结构断言，只检查接口方法上有事务注解。事务效果另由去掉 `@Transactional` 后 `:75`、`:80` 的快照断言和 `:52` 变红证明。

人工核对：

- 基线 `create` 42–55 行（本分支 43–56）只给目标组加 1。基线 `update` 第 62 行只写一次属性，不写任何计数。
- 101 规格 1、202 参数 0 迁过去后，持久化快照分别是旧规格 0、新参数 1，属性分类 202、类型 1（`:70-89`）。
- `evidence/coverage.txt`：统计对象是 `PmsProductAttributeServiceImpl.update`，行 35/36、分支 24/26。未覆盖第 79 行 `Asserts.fail`，以及第 90、95 行类型既非 0 也非 1 的假分支。
- `evidence/compare.txt`：Skipped=0，结论「符合」。
- 没有另一位人员复核，没有录屏。

限制：

没有保存当时的 AI 对话。
