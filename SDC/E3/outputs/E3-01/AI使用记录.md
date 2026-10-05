# E3-01 AI 使用记录

本示例为迁移重建，不是历史上的测试先行过程；未找到原始 AI 对话记录。

工具：Cursor 编码代理；JDK 17；JaCoCo 0.8.13。对照用 `SDC/compare.sh E3-01`。没有学生录屏，也没有独立人工标注。

取舍（由 diff 印证）：

- 红灯由 `SDC/compare.sh E3-01` 在基线上回放得到：未修复代码上 `listCart` 返回 1。测试提交 `2f21d5f75172056d53b6247b945778a54ad8d437`，修复提交 `b108e02242d23e148e27443f8a9d7cd7feb9b734`。
- 全场、分类、商品三处门槛都改为 `compareTo`，分别在第 132、145、158 行。
- 覆盖率只摘 `listCart`，行 36/36、分支 30/30，不使用模块平均值。
- 不把第 145、158 行的 `intValue() > 0` 改成 `compareTo(BigDecimal.ZERO)`。那会改变不足 1 元的匹配额语义，与 Issue 的不可变约束一致。
- 不只修全场。`evidence/compare.txt` 里分类、商品各有一个红灯，都是同样的金额断言。
- 不把环境或编译失败写成业务红灯。`evidence/compare.txt` 里失败是 `expected: <0> but was: <1>`，Errors=0、Skipped=0。

人工核对：

- `evidence/compare.txt`：改前失败是 `expected: <0> but was: <1>`，Errors=0、Skipped=0；改后通过，结论「符合」。
- `evidence/coverage.txt`：统计对象是 `UmsMemberCouponServiceImpl.listCart`。
- 测试提交 `2f21d5f75172056d53b6247b945778a54ad8d437`，修复提交 `b108e02242d23e148e27443f8a9d7cd7feb9b734`。
- 核对了红灯失败名和三处源码。没有另一位人员复核，也没有录屏。

限制：

没有保存当时的 AI 对话。
