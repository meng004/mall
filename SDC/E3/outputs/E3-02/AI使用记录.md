# E3-02 AI 使用记录

本示例为迁移重建，不是历史上的测试先行过程；未找到原始 AI 对话记录。

工具：Cursor 编码代理；JDK 17；JaCoCo 0.8.13。对照用 `SDC/compare.sh E3-02`。旧记录自述 Cursor 子代理（Grok 4.7）、`rtk codegraph query`、`rtk proxy mvn`，未核实。没有学生录屏，也没有独立人工标注。

取舍（由 diff 印证）：

- 红灯由 `SDC/compare.sh E3-02` 在基线上回放得到：失败是 `saved modifyDate=1000`。测试提交 `dbfd3616ca079f772d1ce846032518014370150a`，修复提交 `f1701c209c889c474252761a6421aa7f91edec02`。
- 采纳：只把第 50 行 `existCartItem.setModifyDate` 设为调用当时的时间。测试在 `updateByPrimaryKey` 接收参数当时复制字段（`:39-48`），并用 `assertSame(existing, argument)` 锁住保存的是已有项；`insert` 接收当时复制字段（`:72-83`）。时间用调用前后的界限。
- 驳回：不改 `quantity` 相加；不把入参上的 `setModifyDate` 留着冒充已保存；不用 `sleep`；不另写一套合并算法。diff 只改第 50 行，第 51 行数量公式未改，与 Issue 的不可变约束一致。
- 覆盖率只摘 `OmsCartItemServiceImpl.add`，行 12/12、分支 2/2，不使用模块平均值。
- 不把环境或编译失败写成业务红灯。`evidence/compare.txt` 里失败是 `saved modifyDate=1000`，Errors=0、Skipped=0。

人工核对：

- `evidence/compare.txt`：改前失败是 `saved modifyDate=1000`（`expected: <true> but was: <false>`），Errors=0、Skipped=0；改后通过，结论「符合」。
- `evidence/coverage.txt`：统计对象是 `OmsCartItemServiceImpl.add`。
- 测试提交 `dbfd3616ca079f772d1ce846032518014370150a`，修复提交 `f1701c209c889c474252761a6421aa7f91edec02`。
- 没有录屏、没有另一位人员复核。

限制：

没有保存当时的 AI 对话。
