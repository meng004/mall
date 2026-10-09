# E3-00 AI 使用记录

本示例为迁移重建，不是历史上的测试先行过程；未找到原始 AI 对话记录。

工具：Cursor 编码代理；JDK 17；JaCoCo 0.8.13。对照用 `SDC/compare.sh E3-00`。

取舍（由 diff 印证）：

- 采纳把 `setUseIntegration` 前移到插入之前。
- 采纳删掉被覆盖、从未生效的 `setIntegration` 写入。
- 不改第 143 行“0 视为不用积分”的语义。
- 不改表结构和生成的模型代码。
- 不带入 E6 的 `warnSku`。

人工核对：

- `evidence/compare.txt`：改前失败是插入边界的 `useIntegration` 断言（expected 0 或 30，实际 null），改后通过，结论「符合」，Skipped=0。
- `evidence/coverage.txt`：统计对象是 `OmsPortalOrderServiceImpl.generateOrder`。
- 测试提交 `604b116a36fe4bacee976e3d4743694cd58fb9d4`，修复提交 `a17829a931a0f345555ac049d98f509faa7ce1fc`。

限制：

没有保存当时的 AI 对话。
