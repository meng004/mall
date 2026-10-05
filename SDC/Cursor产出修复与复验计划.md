# Cursor产出修复与复验计划

> 供Cursor执行：使用`superpowers:executing-plans`逐任务推进；可以按下述互斥范围并行。贯彻Ponytail：复用现有机制，不新建平台、hash、冻结contract或额外gate。此文件是待执行计划，不是修复完成声明。

**目标**：修复本轮有据可查的问题，完成适用的真实环境验证，形成可信的42题验收结果；不以强行让模型放行作为目标。

**架构**：产品修复留在原服务边界，复用Spring事务和现有测试；E7修复现有输入接线、比较器和结果记录。环境复用既有Compose，写测试使用运行专用数据范围。

**技术**：Java17、现有Maven/JUnit/Mockito/Spring/MyBatis/Mongo工具；不新增测试框架。

**依据**：[真实性评审](Cursor产出真实性评审.md)、[原42题计划](Cursor候选靶点产出计划.md)、[评分细则](课程考核方案.md)、[现有结果索引](候选靶点批量验收结果.md)。

## 起点、权限与分工

- 主仓库`/Users/limeng/软件设计与规范AI/mall`含未提交的最新计划，不能从`af284e3`的旧文档猜规格。先阅读这里的计划，再操作集成树。
- 默认产品工作目录`/Users/limeng/软件设计与规范AI/.worktrees/sdc-integrated`，起点应核实为`af284e3`；如已变化先记录新增差异，不重置。
- 主仓库已有修改全部保留。不推送、不发布、不购买模型额度、不操作生产库。授权范围为本计划所列修复、测试和文档；本人录屏、教师人工核对不能伪造。
- 全部shell命令以`rtk`开头。先执行`rtk git status --short`、`rtk git rev-parse HEAD`、`rtk proxy mvn -version`。本机Java17为`/opt/homebrew/opt/openjdk@17`；`/usr/libexec/java_home -v 17`本次返回了Zulu8路径，不能盲信。
- 若需显式Java17，使用`rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17 PATH=/opt/homebrew/opt/openjdk@17/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin mvn ...`。
- 最多3个执行代理：A负责任务1/2（同一属性服务串行）；B负责任务3/4（优惠券与环境测试）；C负责任务5/6/7（E7产品与评测器串行）。主代理最后任务8/9与集成。共享数据库初始化和在线模型调用由主代理统一调度，不多代理同时启停或写库。
- 并行修改使用独立worktree并携带当前规格；同一checkout同一target不能同时运行Maven。不重新实现全部42题，不强行合并E2-02、E4-03、E4-06。
- 每项先保存失败证据，再最小修复、复跑。新证据写相应题`evidence/review-fix/`，旧证据原样保留。方法覆盖率因代码改变须重新计算，旧报告不挪作新报告。

## 任务1：修复属性迁移事务（P1）

**文件**：`mall-admin/src/main/java/com/macro/mall/service/PmsProductAttributeService.java`；原`.../service/impl/PmsProductAttributeServiceImpl.java`；扩展`mall-admin/src/test/java/com/macro/mall/sdc/SDCE304Test.java`，新增同目录`SDCE304TransactionTest.java`。

- [ ] 用评审`SpecProbe`重现当前`update`事务属性为null；再建真实Spring事务代理+真实隔离MySQL测试，不给整个测试方法加会掩盖产品缺失的外层事务。
- [ ] 准备原分类A、目标B、属性X；记录A/B两种计数与X归属。在真实属性更新后，令分类Mapper的目标写抛出运行时异常（可用测试代理包装真实Mapper，仅定点抛错，其余委托真实SQL）。从独立连接读取：修复前能够观察部分写入，修复后所有值等于操作前。测试后只按本次主键清理。
- [ ] 在现有接口`update`上复用与create/delete一致的`@Transactional`，不加自定义回滚器。先确认当前实现经Spring代理调用。
- [ ] 测正常跨分类迁移、同分类改类型、旧分类写失败、新分类写失败。断言不仅是抛异常，还包括持久化状态恢复；统计对象不能只读Mapper返回的可变引用。

```java
assertThrows(RuntimeException.class, () -> proxiedService.update(attributeId, moveParam));
assertEquals(beforeAttribute, readAttributeWithNewConnection(attributeId));
assertEquals(beforeCounts, readCategoryCountsWithNewConnection(sourceId, targetId));
```

上述变量/读取助手在新增测试内定义，表示数据库实际投影，不是新增产品接口。

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false '-Dtest=SDCE304Test,SDCE304TransactionTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

**通过条件**：事务属性不为空、真实回滚断言通过、原迁移功能通过。数据库不可用只能完成单测部分。

## 任务2：空存在集合停止删除（P2，与任务1同一代理）

**文件**：`PmsProductAttributeServiceImpl.delete`、`mall-admin/src/test/java/com/macro/mall/sdc/SDCE303Test.java`。

- [ ] 加`[999]`与`[999,999]`全部不存在用例，期望返回0、无`deleteByExample`、无分类更新；保留混合有效/无效/重复ID用例。
- [ ] 在查完现存对象后、构造Example之前加`if (existingById.isEmpty()) return 0;`。不修改`mall-mbg`生成代码。
- [ ] 运行测试与真实隔离库的全不存在ID调用，确认没有非法SQL、计数不变。

```java
assertEquals(0, service.delete(List.of(999L)));
verify(productAttributeMapper, never()).deleteByExample(any());
verify(productAttributeCategoryMapper, never()).updateByPrimaryKey(any());
```

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE303Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**通过条件**：全不存在正常返回0，混合删除仍按现存唯一ID分组扣数。

## 任务3：修复小额优惠券（P2）

**文件**：`mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberCouponServiceImpl.java`；`SDC/E3/src/test/java/com/macro/mall/sdc/e3/SDCE301Test.java`；回归`SDC/E4/.../SDCE407Test.java`。

- [ ] 用现有真实`listCart`构造分类券/商品券：匹配金额0.90、门槛0.50应可用；先记录失败。
- [ ] 分别覆盖匹配金额0、0.01、0.49、0.50、0.90、1.00，门槛0.50时只有后三者可用；另0.01门槛0.01可用、完全未匹配且门槛0仍不可用。与全场券、失效券、不可用列表对照。
- [ ] 两处`eligibleTotal.intValue()>0`改为`eligibleTotal.compareTo(BigDecimal.ZERO)>0`，复用现有门槛比较，不引入新的金额类型或策略层。
- [ ] 运行E3-01及E4-07。E4历史重构前后证据保留，新增日志标记是在金额修复版本上回归，不回写历史行为。

```sh
rtk proxy mvn -pl SDC/E3,SDC/E4 -am -DskipTests=false '-Dtest=SDCE301Test,SDCE407Test' -Dsurefire.failIfNoSpecifiedTests=false test
```

**通过条件**：两种范围的小额判断正确，原范围选择与重构特征无回归。

## 任务4：安全恢复教学环境与缺失的数据库证明（P1先修测试范围）

**文件**：`SDC/E6/src/test/java/com/macro/mall/sdc/e6/SDCE604MongoTest.java`、`SDCE605MongoTest.java`；按需新增`mall-admin/src/test/java/com/macro/mall/sdc/SDCE602DatabaseTest.java`；复用`SDC/environment/compose.yml`、`application-sdc.yml`及教师环境页。

- [ ] Mongo两测试只使用本次运行专用数据库名，例如`sdc_review_`加本次UUID，而不是直接用配置中的共享`mall-port`；保留按插入ID清理，不加`dropDatabase`或全库删除。服务内部可使用固定测试会员，因为数据库本身已隔离。
- [ ] 在该专用库准备本次持有的“其他会员旧记录、当前会员等于截止时间/晚于截止时间记录”，检查全部保留；当前会员早于截止时间记录仅该条删除。不要在共享库插旧记录试验误删。
- [ ] 检查教学容器后复用启动命令，不删卷、不重复向已有库导入含DROP的mall.sql。

```sh
rtk proxy docker compose -f SDC/environment/compose.yml ps -a
rtk proxy docker compose -f SDC/environment/compose.yml up -d
rtk proxy docker compose -f SDC/environment/compose.yml ps
rtk proxy mvn -pl SDC/E6 -am -DskipTests=false '-Dtest=SDCE604MongoTest,SDCE605MongoTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 检查Surefire：两项必须实际执行、失败0、错误0、跳过0。环境不可用时保留Skipped/阻塞，不以BUILD SUCCESS当通过。
- [ ] E6-05验证先筛选后分页：同会员多条匹配跨两页、另一会员同名、名称中`.`和`*`按字面匹配、空关键词兼容；当前只有一条匹配的测试不足以独立证明跨页结果。
- [ ] E6-02在隔离MySQL中建品牌数据，两种factoryStatus、共同关键词、跨页数据；验证过滤后total、页内容及旧四参数接口兼容。用主键清理，不更改教学已有品牌。
- [ ] 打包并启动portal，复用教师配置，运行E1真实只读查询。记录HTTP及业务code=200和商品26；失败逐层查容器健康、数据库、应用启动日志、监听端口，不跳过认证来修接口。

```sh
rtk proxy mvn -pl mall-portal -am -Ddocker.skip=true -DskipTests=true package
rtk proxy java -jar mall-portal/target/mall-portal-1.0-SNAPSHOT-exec.jar --spring.config.additional-location=file:./SDC/environment/application-sdc.yml --server.port=18085
```

另一个终端：

```sh
rtk proxy curl --noproxy '*' 'http://127.0.0.1:18085/product/search?keyword=HUAWEI%20P20&pageNum=1&pageSize=5'
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE602DatabaseTest -Dsurefire.failIfNoSpecifiedTests=false test
```

打包命令跳过测试只为生成运行包，不计验收。**通过条件**：E1查询与三项数据库测试有真实结果，任务1事务回滚也在隔离MySQL完成。其他E2/E4不无故扩为端到端任务。

## 任务5：E7属性证据与唯一性修复（P1）

**文件**：`mall-portal/src/main/java/com/macro/mall/portal/ai/attributes/ExistingAttributeExtractionService.java`、`LlmAttributeExtractionService.java`（仅必要时）；`SDC/E7/src/test/java/com/macro/mall/sdc/e7/SDCE705Test.java`。

- [ ] 用固定模型替身返回以下JSON，调用真实LLM服务，确认现状错误地返回OK：

```json
{"action":"extract","values":[{"attributeId":21,"value":"红","evidence":"蓝色"}],"unknownIds":[]}
```

- [ ] 测试输入白名单21颜色红/蓝、text=蓝色，必须拒绝该不支持值；蓝/蓝色应通过。容量白名单128GB/256GB，text=128G而值256GB必须拒绝，128GB可通过。空引文、同ID重复、同ID同时known/unknown也不得返回成功。
- [ ] 在现有`check`复用`literal`/`canonicalize`及已规定别名/单位规则，从证据得到可支持的规范化值，再与提议值比对；无法支持时按既定合同未知或拒绝，不能“只要引用出现在原文就认可”。不要创建语义验证模型。
- [ ] 使用一个局部Set拒绝重复ID，检查known/unknown互斥；保证每个属性最终唯一且状态与数据一致。校验仍位于现有共同边界。

```sh
rtk proxy mvn -pl SDC/E7 -am -DskipTests=false -Dtest=SDCE705Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**通过条件**：评审三个反例不再通过，合法别名/单位/未知项仍正确，产品不调用写Mapper。

## 任务6：修正E7比较器、评分归因和报告（P2）

**文件**：`SDC/E7/src/main/java/com/macro/mall/sdc/e7/CandidateE704Evaluation.java`、`CandidateE705Evaluation.java`、`CandidateE706Evaluation.java`、`EvaluationSupport.java`；新增同包测试`SDC/E7/src/test/java/com/macro/mall/sdc/e7/CandidateEvaluationTest.java`。

- [ ] 先写比较器反例：同语义标签/属性、不同但均支持结论的原文片段应同分；原文内无关片段、错标签/错值、缺编号、错数量仍失败。示例：蓝色→蓝，证据“蓝”或“蓝色”可成立；蓝色→红永远失败。
- [ ] E704按主题集合比较；证据必须非空、来自原文且支持标签。语义证据使用教师事先核对的可接受片段/标注，或标为待人工核对，不用模型自判，也不为当前响应临时补答案。
- [ ] E705按规范化属性ID—值集合和unknown集合；调用任务5同等证据规则，不以expected引文唯一字符串作为答案。E706按匿名ID—标签、计数、比例、分母、失败项比较，保留主因规则，证据同样查支持。
- [ ] 比较器遇到重复ID等非法结构返回明确失败原因，不让`toMap`异常中断整份报告。评测循环记录本题异常并保存已完成部分，不把异常算正确拒绝。
- [ ] 每行补充Java实际结构化结果、比较失败字段、错误类别（传输/协议/业务/比较器/缺环境）及是否计分。期望仅在离线比较器读取，绝不进入模型请求。
- [ ] 移除“离线已通过”的无条件报告断言，关联实际测试证据或写未由本次评测验证；usage缺失原因如实记录为适配器未暴露。不要为此设计新证据框架。
- [ ] 统一`codeVerdict`和中文decision语义：安全违规、质量不足、基础设施未完成分开；有数据支撑的降级允许，已跑不足20正常/12边界不得放行。
- [ ] 用已公开model-eval响应离线回放，不调用在线模型；报告新旧分数差异及原因。无法恢复Java结果/缺事实就标不可重算。不得覆盖首次报告或宣称离线回放为新盲测。

```sh
rtk proxy mvn -pl SDC/E7 -am -DskipTests=false '-Dtest=CandidateEvaluationTest,SDCE704Test,SDCE705Test,SDCE706Test' -Dsurefire.failIfNoSpecifiedTests=false test
```

**通过条件**：合法证据不被精确字符串误杀，错误不能逃过校验；比较器异常不丢结果；报告可以定位得分原因。

## 任务7：E7-01/02接通真实受控依赖（P1）

**文件**：`CandidateE701Evaluation.java`、`CandidateE702Evaluation.java`；复用`QueryEvaluation.java`中的应用启动方式及产品现有Spring配置；扩展现有SDCE701/702测试，必要时新增`SDC/E7/src/test/java/com/macro/mall/sdc/e7/CandidateDatabaseTest.java`。

- [ ] 删除评测入口固定`isolated=false`与unavailable代理作为唯一路径的做法；通过受控教学配置启动Spring，取得真实`OmsPortalOrderService`及Pms相关Mapper。不额外定义一份订单/商品查询算法。
- [ ] 用公开开发facts先完成接线：隔离MySQL、当前会员身份、正常/他人/删除订单、可见/隐藏/删除商品。订单需20条以上测分页total；禁止把expected当数据库查询返回值。
- [ ] `facts.json`负责教学记录及时间基准，验收expected只参与比对。输入案例不得任意覆盖服务端会员身份。测试身份使用既有SecurityContext机制，结束清理上下文。
- [ ] 先运行普通路径及固定模型替身，断言同会员过滤、商品可见性、分页和真实数据库一致；再考虑付费模型。普通路径或数据不就绪时立即报告阻塞，不白跑13次模型。
- [ ] 以实际预检结果决定isolated状态，而非硬改true。启动容器但数据未导入、身份未生效仍不得通过。用本次主键清理开发数据，正式独立数据由准备会话管理。

```sh
rtk proxy mvn -pl SDC/E7 -am -DskipTests=false '-Dtest=CandidateDatabaseTest,SDCE701Test,SDCE702Test' -Dsurefire.failIfNoSpecifiedTests=false test
```

**通过条件**：真实教学库+普通路径通过，缺环境仍诚实阻塞，替身仅限离线测试；模型不接触参考表单答案或expected。

## 任务8：定位CLI超时，重新进行有效模型验收

**文件**：`mall-portal/src/main/java/com/macro/mall/portal/llm/CursorCliLlmClient.java`、`EvaluationSupport.java`；现有测试`SDC/E7/src/test/java/com/macro/mall/portal/llm/CursorCliLlmClientTest.java`。

当前只能证实60秒截止，不先认定供应商故障。按预测区分：①CLI/登录启动问题→最小请求也失败；②请求/模型耗时→最小请求成功、相同代表请求持续接近截止；③输出协议问题→进程成功但结果夹解释；④调用参数/权限模式问题→隔离CLI配置与实际工具行为不符。

- [ ] 先只读`rtk proxy cursor-agent --version`及`rtk proxy cursor-agent --help`，确认本机支持的参数。不要凭记忆添加开关，不提取登录token。
- [ ] 扩展现有进程替身测试，分别模拟退出非0、超时、合法JSON信封、带解释的result，检查子进程和临时目录清理。复用现有package-private构造器，不新建CLI框架。
- [ ] 为非0/超时记录受限长度、脱敏的退出原因/耗时/stderr摘要，避免吞掉全部诊断，也不保存密钥或整个用户环境。这属于补足现有故障路径证据。
- [ ] 通过现有适配器运行一个无业务数据的最小请求和一个公开开发请求；每类至多3次，先记录模型名、CLI版本、超时预算和实际耗时。使用已授权模型入口，无额度则停止在线部分；不要扫模型或无限重试。
- [ ] 若耗时证据支持增加超时，复用现有Duration参数配置一次合理预算并记录变更。若是解说混入，修正现有提示/调用模式；产品仍严格拒绝前后赘文，不靠截JSON或放宽解析制造成功。保留工具deny及临时workspace。
- [ ] 传输稳定、任务5–7通过后，独立准备会话按原计划构造新20+8+4验收集，只读规格/事实，不读实现/提示/开发日志。人工核对缺失就保持“未完成人工核对”。曾公开或调参的题只能回归。
- [ ] 六题分别执行原计划的`CandidateE70xEvaluation`命令（对应文件名和路径），不要复用另一题入口；串行限制调用量。遇到传输故障保存当前行及未执行数量，避免自动不停重跑。恢复运行记录不同attempt，不能挑成功结果拼成首次盲测。

**通过条件**：每题32条执行完整、人工核对有真实来源，正常至少18/20且关键边界全部满足才放行；质量不足可有据降级/驳回。验收流程完成与模型放行分列，不能为了收工调低阈值。

## 任务9：集成回归、证据索引与交付

- [ ] 将兼容修复合入一个集成版本，记录普通Git提交；保留三个互斥分支分别验证。不得为消除互斥而改变教学目标。
- [ ] E3-01/03/04重收目标方法覆盖率，沿用原计划的JaCoCo命令；行/分支各至少80%，无分支注明不适用，不用整个模块平均数。
- [ ] 运行覆盖原有测试及新候选的完整离线回归，不能只运行`SDCE*Test`：

```sh
rtk proxy mvn -pl mall-admin,mall-portal,SDC/E1,SDC/E2,SDC/E3,SDC/E4,SDC/E5,SDC/E6,SDC/E7 -am -DskipTests=false '-Dtest=*Test,!SDCE*MongoTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

新增MySQL测试此时也需要隔离库就绪。两个Mongo测试单独显式运行。检查每模块tests/failures/errors/skipped，不以退出码单项代替计数。若受执行沙箱禁用本机HTTP监听/进程查询，按工具权限流程复跑，不把权限错误误判产品错误。

- [ ] 在各自worktree运行E2-02、E4-03、E4-06原测试，命令分别为`rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE202Test -Dsurefire.failIfNoSpecifiedTests=false test`、同样命令替换为`SDCE403Test`、以及`rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE406Test -Dsurefire.failIfNoSpecifiedTests=false test`。
- [ ] 修正7处少一级的测试链接；E7说明中旧“未运行”段落改成带阶段日期的历史说明，当前结论引用最新原始报告。主索引用正确的可点击相对路径指向集成/独立分支产出。
- [ ] 把最终测试命令、日志、提交和计数存入成果目录；不只引用`/tmp`。原始日志行尾空白保留，分别报告源码/文档差异检查与原日志例外，不全仓取消whitespace检查。
- [ ] 更新原`候选靶点批量验收结果.md`，不复制一套新验收表。每题区分代码与客观测试、真实环境、模型决策、人工教学材料状态。

**收工条件**：本评审可修复缺陷已修复并有回归；42题适用客观要求逐项有据；实际环境/模型/人工条件不满足的项明确保留阻塞。无需强迫六题都获模型放行，不得把“诚实报告未完成”写成“全部通过”。最后仅汇报真实结果及必要外部输入；不让Cursor代造本人录屏或教师签字。
