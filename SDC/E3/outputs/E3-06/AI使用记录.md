# E3-06 AI 使用记录

本示例为迁移重建，不是历史上的测试先行过程；未找到原始 AI 对话记录。

工具：Cursor 编码代理；JDK 17；JaCoCo 0.8.13。对照用 `SDC/compare.sh E3-06`，本示例不连数据库。旧记录自述 Cursor 中的 Grok 4.7，未核实；迁移重建的测试、修复提交带 `Co-Authored-By: Claude Opus 5.5 (1M context)` trailer。没有学生录屏，也没有独立人工标注。

采纳：

- 红灯由 `SDC/compare.sh E3-06` 在基线上回放得到。测试提交 `95fd87775f1e04a031e4675fc2707ff99933e323`，修复提交 `ddb65fa4633c5e59516b81298bfd67c50024df51`。合法对象用真实 Validator（`SDCE306Test.java:53-54`）；请求用 standalone MockMvc、真实 `GlobalExceptionHandler` 和 `SpringValidatorAdapter`（`:58-61`）。服务是 subclass mock（`:56`）。
- `productAttributeCategoryId` 从 `@NotEmpty` 改为 `@NotNull`（DTO 第 18 行，基线第 17 行）。create、update 请求体加上项目已有的 `@Validated`（控制器第 48、60 行，基线第 47、59 行）。写法同 `PmsBrandController.java:38`、`:53`。
- 缺分类 ID 和空 name 都要求返回 `VALIDATE_FAILED`（404），并用 `verifyNoInteractions` 证明服务没被调用（`:128-134`、`ResultCode.java:10`）。合法请求仍到达服务，成功/失败由服务返回值决定（`:97-126`）；`update` 第 62 行改成 `count >= 0` 后 `:124` 变红。

驳回：

- 不改 `PmsProductAttributeServiceImpl` 来拦截空参数。那样请求已经进了业务层。本分支服务层与 `exp/E3` 没有差异。
- 不只加 `@Validated`。修复前合法 Long 会抛 HV000030；入口一旦启用，这个类型异常不会变成参数检验失败，而会漏到未处理异常。DTO 改回 `@NotEmpty`、保留 `@Validated` 时 9 例全红，其中 8 例是 MockMvc 抛出的 `ServletException`（内层 HV000030），合法请求也变成服务端错误。
- 不把 standalone MockMvc 写成完整 Spring Boot 认证链。安全过滤器和登录态没有加载。

人工核对：

- 品牌控制器已把 `@Validated` 放在请求体上（`PmsBrandController.java:38`、`:53`）。全局异常处理器把字段错误收成 404（`GlobalExceptionHandler.java:32-42`）。
- `evidence/compare.txt`：Skipped=0，结论「符合」；4 例拒绝用例在基线上的两处失败分别是服务被调用和 `expected: <404> but was: <500>`。
- `evidence/coverage.txt`：只统计控制器 `create` 和 `update`，各行 4/4、分支 2/2。DTO 的注解改动记「不适用」。
- 启用 `@Validated` 同时激活 DTO 第 24–47 行的 7 个 `@FlagValidator`；库里 53 条属性没有越界取值，也没有分类 ID 为空或名称为空的行。
- 没有另一位人员复核，没有录屏。

限制：

没有保存当时的 AI 对话。
