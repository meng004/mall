# E3-03 AI 使用记录

本示例为迁移重建，不是历史上的测试先行过程；未找到原始 AI 对话记录。

工具：Cursor 编码代理；JDK 17；JaCoCo 0.8.13。对照用 `SDC/compare.sh E3-03`，MySQL 例需要 `SDC/environment/compose.yml` 的 13306。旧记录自述 Cursor 中的 Grok 4.7，未核实。没有学生录屏，也没有独立人工标注。

采纳：

- 红灯由 `SDC/compare.sh E3-03` 在基线上回放得到。测试提交 `3e35d7c59abfb95bd43c333fd30425913995a483`，修复提交 `d8ca32cb37e23d9cb85fb731b3f8d59a75a66da1`。Mock 例只替换两个 Mapper，另有一例经 13306 走真实 Mapper XML（`:190-207`）。分类计数和删除 ID 在 Mapper 接收时复制（`:67-78`、`:79-83`）。
- 修复只改 `PmsProductAttributeServiceImpl.delete`（第 73–111 行）。按实际存在的唯一 ID 分组，规格和参数分开扣，计数不低于 0，返回 `deleteByExample` 的行数。第 84–86 行在全部 ID 都不存在时返回 0，不拼空的 `id in ()`。这一条在 13306 上用注入改动验证过。
- 同组删除两项规格返回 2，规格计数 2→0，参数计数 4 不变（`:137-150`）；只删参数时只动 `paramCount`（`:153-165`）。

驳回：

- 不把跨分类请求改成写入前拒绝。本题规格允许混合删除，与候选库 E3-03「按实际存在的唯一 ID 分别维护所属计数」一致。
- 不按入参个数或总删除行数去减第一个分类。这就是修复前 `[0, 2]` 的原因，见 `evidence/compare.txt` 的 `:97`、`:131`。
- 不抽出与 E3-04 共用的计数函数，示例彼此独立（指南第一节第 2 条）。diff 只动了 `delete` 和两个 import，可以印证。
- 3 例 Failures 是计数断言（首个 ID 存在，`AssertionFailedError`）；2 例 Errors 是全不存在时基线 `delete` 第 74 行 `pmsProductAttribute.getType()` 的空指针，即 Issue 所列缺陷，发生在产品代码里，不是编译或环境错误；改后返回 0。

人工核对：

- 对照基线 `create` 42–54 行和修复前 `delete`：101/202 各从 2 扣到 1（`:87-102`），同分类规格与参数分别变为 1 和 1（`:105-118`），重复 ID `11` 与不存在 ID `999` 不额外扣减（`:121-134`）。
- `evidence/coverage.txt`：统计对象是 `PmsProductAttributeServiceImpl.delete`，行 32/32、分支 21/22，未覆盖第 102 行 `type == 1` 假分支。
- `evidence/compare.txt`：Errors=2 的来源同上，Skipped=0，结论「符合」。
- 没有另一位人员复核，没有录屏。

限制：

没有保存当时的 AI 对话。
