# Cursor候选靶点产出计划

> **历史文件，不得按本文件执行现行编号。**写于 2026-10-05 前后；此后 E2 整体替换（现 E2-01 为订单状态流转，#43），原 E4-07 现为 E4-00（#20），原 E4-00 现为 E6-00（会员价，#14），原 E6-00 现为 E6-07（#26），候选总数与'42 项'口径已失效。现行选题以《教师候选靶点库》与 GitHub Issue 为准。

> **供Cursor执行**：按 `superpowers:executing-plans` 逐题执行；本文件是实施计划，不是实现完成记录。用户一次指定一个编号，默认只执行该题，不运行42题全库，不要求另建代理团队。复用当前仓库的AGENTS.md、RTK和已有测试工具。

**目标**：每个候选形成满足对应实验目标的可核查作品：实际代码／分析、自己的测试或观察、评分证据索引；不能保证模型放行或代替学生本人讲解。

**结构**：产品变化留在原业务模块；Portal的E3/E4/E6/E7测试留在相应SDC模块。E1/E2/E5的业务观察及后台题在原模块测试目录运行，避免给源码分析工具增加应用依赖。每题只有一份说明，候选业务范围只在教师候选靶点库维护。

**技术**：JDK17、Maven、Java、JUnit5、Mockito及Spring Test；复用项目已有依赖。E7复用 `LlmClient.generate(String systemInstruction, String userInput)`，不另建Agent或评测平台。

**依据**：[分实验评分](课程考核方案.md#rubric-e1)、[唯一候选库](教师候选靶点库.md)、[选题评审](靶点库核对记录.md#course-audit)。若规范或实际源码有变化，以当前源码和已明确的业务契约重新核对，不按旧行号盲改。

## 共用执行约束

- 所有命令工作目录为 `mall`，所有shell命令以 `rtk` 开头；先确认IDEA和Maven使用JDK17。
- 每题开始执行 `rtk git status --short` 与 `rtk git rev-parse HEAD`，记录起点提交及相关已有差异。保留用户改动；不得整仓重置、恢复其他实验文件或清空数据库。使用普通Git分支管理起点，不新增hash、冻结副本或并行验收机制。
- 只执行下面该编号列出的范围。读取符号／调用关系优先CodeGraph；无索引按项目约定询问是否初始化，不能用猜测填入调用链。文件:行号在最终说明中取实施后的实际值。
- 不改生成的 `mall-mbg` 类、表结构、认证／敏感数据规则或已有安全措施。涉及新增特殊防御控制时遵守AGENTS.md的具体失败场景及最小控制规则，优先用已有类型、权限、事务和普通测试。
- 若起点中的缺陷已修复，不在当前产品撤销修复来制造红灯；可在独立历史快照中重建同组对照并标明来源，或改选教师认可的未重复题。
- 不将真实会员资料、密钥、Cookie或Token写入报告或发送给模型。E7使用教师配置的模型入口；Cursor登录由官方CLI使用，不提取Token。没有模型或独立题集时完成能验证的部分，并明确在线／独立验收未完成。
- Mockito使用项目现有subclass方式；测试真实业务类，只替换外部依赖，不另写一份业务算法来“验证”原算法。期望从本计划业务规则和人工事实得出，不从被测返回值反推。
- 单元测试只证明其边界。MySQL／Mongo集成使用教师提供的隔离教学库，仅创建本题数据并按本次插入ID清理，不全库删除、不操作已有真实订单。交易回滚须经真实代理／数据库验证，不能凭注解或Mock宣称通过。
- 默认不自动提交／推送，不启动生产服务，不购买模型额度。每一步有具体授权则正常推进，不为日常可逆编辑重复索要许可。

## 一题的最终交付

1. `SDC/Ex/outputs/编号/说明.md`：沿用一页作品格式，含本题目标、当前源码定位、本人完成内容、原则／取舍、运行结论及局限；用“讲清楚30／有依据30／验证过40”三行列出各子项的证据链接，不自行宣布教师已给满分。
2. 该题的Java测试／实现，或E1/E5的观察测试与分析；原始运行输出放在同目录 `evidence/`。原始日志与说明一同属于原作业，不另交报告。测试报告可链接 `target/surefire-reports`，提交前把本题必要前后结果保存在自己的evidence目录，避免clean后丢失。
3. 简短差异说明和AI使用记录：模型／工具、采纳与驳回理由、人工核对内容。Cursor能准备说明和演示步骤，不能伪造学生本人录屏、人工审阅或独立标注者身份。
4. E1/E5正确完成理解／分析即可，不修改产品来凑五问；E7有依据地降级／驳回可以满足课程目标，不以调整阈值或删题“制造成功”。

下文每题的测试类和输出目录都是**计划创建**，并非已存在。断言片段展示本题必须检查的业务值；其中captured／before／after等变量由本题步骤捕获的实际参数或业务投影得到，不是新的生产接口。可在测试内定义小record投影，不为此新建测试框架。

### 统一测试写法

沿用已有真实类＋外部替身模式，测试类放在下文的明确路径。字段名来自该题真实源码，不反射调用另写的业务副本：

```java
private static <T> T dependency(Object service, String field, Class<T> type) {
    T value = org.mockito.Mockito.mock(type,
        org.mockito.Mockito.withSettings().mockMaker("mock-maker-subclass"));
    org.springframework.test.util.ReflectionTestUtils.setField(service, field, value);
    return value;
}
```

比较可变入参时，在Mapper／Repository接收的当时复制所需字段，不能等方法返回后才读取同一对象；否则后续写入可能使错误测试通过。时间测试用固定Clock或调用前后界限，不能靠sleep。涉及PageHelper的题目沿用现有测试做法，在@AfterEach调用PageHelper.clearPage()，避免未实际执行SQL的替身留下分页ThreadLocal。源码分析工具的fixture测试只证明工具，不替代业务观察。

### E1共同环境证据

每个E1候选均保留模块／生成边界要求，并执行一次教师就绪环境的只读查询；这仅证明环境与商品查询，不外推所选链：

```sh
rtk proxy mvn -pl SDC/E1 -am test
rtk proxy curl --noproxy '*' 'http://127.0.0.1:18085/product/search?keyword=HUAWEI%20P20&pageNum=1&pageSize=5'
```

另外运行 `MallFacts.main`（IDEA或已准备的classpath），检查直接依赖图并沿本题服务定位。环境未启动时先做本题受控观察，保留连接失败并明确缺少环境证据，不算全部达标。

### E2共同视图证据

在原一页说明中，用一张本题职责／依赖图和简短视图对应说明覆盖逻辑职责、进程／异步边界、开发依赖、物理节点及一个贯穿场景。未变化的部署或模块可引用既有E1/E2图，注明本题没有新增进程；不要求另画五张大图。两方案以相同场景比较，局部验证只支持对应接缝的结论。

### E3共同覆盖率

逐题步骤提供测试命令；红灯必须来自业务断言而不是编译／依赖错误。先将红灯日志保存到本题evidence目录，再对修复版收集覆盖率。Portal题使用下面命令模板中的实际测试类名（具体名见各题）；后台题同理对 `mall-admin` 采集，目标方法见各题：

```sh
rtk proxy mvn -pl SDC/E3 -am clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test -Dtest=SDCE301Test -Dsurefire.failIfNoSpecifiedTests=false
rtk proxy mvn -pl mall-portal -Djacoco.dataFile=../SDC/E3/target/jacoco.exec org.jacoco:jacoco-maven-plugin:0.8.13:report
```

每题下方会给出自己的完整命令；不得把E3-01报告用于其他题。报告按实际修改方法统计行、分支（各至少80%），无分支项写不适用。生成模型覆盖率和整个模块平均值不是目标方法覆盖率。

### E7课前材料与独立验收

教师或独立Cursor准备会话先只读候选规格与业务事实，不读取实现、提示词或开发测试。为选中编号创建 `SDC/E7/evaluation/编号/acceptance.json`、`facts.json`、`method.md`：20正常＋8对抗＋4分布外，记录真值来源、标注规则及人工核对。题目含 `id/kind/input/expected`，expected使用下文每题的结构化输出语义。不得把模型回答当标注。普通规则如不能理解文本，输入同一语义的表单／人工字段；比较能力和操作成本，不强迫规则版假装自然语言解析。

评测输入分别保存业务文本、普通表单输入和事实引用：订单／比较／退货题给LLM的请求只保留text和必要事实，status／日期／attributeIds／reasonId／quantity等参考表单字段置空，避免模型绕过理解直接使用正确答案；普通实现使用text为空的表单请求。评论／属性／批次题两版使用相同文本与白名单／原始结构化原因，不能把期望标签送进普通规则。实现会话只获得业务规格、facts和自建开发题，不读取未公开acceptance；验收运行时评测器读取期望但只将input和必要业务事实送入产品接口，严禁把expected发给模型。已经看过／调参用过的题只能计回归，需要另一次独立准备才能声明盲测。不新建冻结／哈希机制，来源与版本用Git和普通记录说明。

每题都实现本文指定的普通类、LLM类和小型Evaluation main。Evaluation同一题分别调用两种实现，记录结果、错误、wall-clock耗时、模型调用次数和脱敏原文；供应商不返回usage时成本写“无数据”，不估造费用。每题内部有固定的结构化比较器，不能用LLM判自己对错。正常题至少18/20且权限／只读／关键事实边界全部满足才放行；模型不达质量、普通路径可用则降级；存在未受控越权／写入／事实伪造则驳回。已公开历史31/32不移作本题结果。

离线必须覆盖：合法模型结果、未知／越界字段、非法JSON、模型抛错／超时，以及显式选择普通路径；不能把UNAVAILABLE等同正确拒绝。E7-01/02涉及实际会员／可见商品查询，正式结论须有相应隔离数据库证据；E7-03的已授权订单项／附件上下文可由教师受控数据提供，明确不是正式退款审批。E7-04/05/06可用脱敏或人工业务样本，无需新增后台→portal应用依赖。

## 编号导航

| 实验 | 六个当前候选计划 |
|---|---|
| E1 | [E1-01](#plan-e1-01) · [E1-02](#plan-e1-02) · [E1-03](#plan-e1-03) · [E1-04](#plan-e1-04) · [E1-05](#plan-e1-05) · [E1-06](#plan-e1-06) |
| E2 | [E2-01](#plan-e2-01) · [E2-02](#plan-e2-02) · [E2-03](#plan-e2-03) · [E2-04](#plan-e2-04) · [E2-06](#plan-e2-06) · [E2-07](#plan-e2-07) |
| E3 | [E3-01](#plan-e3-01) · [E3-02](#plan-e3-02) · [E3-03](#plan-e3-03) · [E3-04](#plan-e3-04) · [E3-06](#plan-e3-06) · [E3-07](#plan-e3-07) |
| E4 | [E4-01](#plan-e4-01) · [E4-02](#plan-e4-02) · [E4-03](#plan-e4-03) · [E4-04](#plan-e4-04) · [E4-06](#plan-e4-06) · [E4-07](#plan-e4-07) |
| E5 | [E5-01](#plan-e5-01) · [E5-02](#plan-e5-02) · [E5-03](#plan-e5-03) · [E5-04](#plan-e5-04) · [E5-05](#plan-e5-05) · [E5-06](#plan-e5-06) |
| E6 | [E6-01](#plan-e6-01) · [E6-02](#plan-e6-02) · [E6-03](#plan-e6-03) · [E6-04](#plan-e6-04) · [E6-05](#plan-e6-05) · [E6-06](#plan-e6-06) |
| E7 | [E7-01](#plan-e7-01) · [E7-02](#plan-e7-02) · [E7-03](#plan-e7-03) · [E7-04](#plan-e7-04) · [E7-05](#plan-e7-05) · [E7-06](#plan-e7-06) |

## 按编号执行

<a id="plan-e1-01"></a>

### E1-01 会员领取优惠券

**依据**：[业务范围](教师候选靶点库.md#e1-01) · [E1评分](课程考核方案.md#rubric-e1)。

**读取**：[UmsMemberCouponServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberCouponServiceImpl.java)、[UmsMemberCouponController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/UmsMemberCouponController.java)、[UmsMemberCouponService.java](../mall-portal/src/main/java/com/macro/mall/portal/service/UmsMemberCouponService.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `mall-portal/src/test/java/com/macro/mall/portal/sdc/SDCE101Test.java`；说明 `SDC/E1/outputs/E1-01/说明.md`，本题证据 `SDC/E1/outputs/E1-01/evidence/`。测试包名 `com.macro.mall.portal.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E1/outputs/E1-01/evidence`。
- [ ] 2. 不改产品；追踪当前会员→券资格→历史插入→券数量更新，并读服务接口已有事务声明。
- [ ] 3. 建立下表人工数据，在真实服务上写观察测试或完成等价可重复断点观察；先写自己的预测，再记录实际调用／返回。不要实现计划中的新规则。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 画入口、两次写入及生成Mapper边界；通过捕获参数／调用顺序核实领取行为，说明Mock不证明事务。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 券count=2，perLimit=1，已领0，enableTime早于当前时间 | 历史写入会员101、状态未用；剩余1、领取数增加1 |
| 会员已领1或券数量0 | 资格拒绝，不调用历史插入／数量更新 |

**关键断言／实现片段**

```java
verify(historyMapper).insert(argThat(h -> h.getMemberId().equals(101L) && h.getUseStatus() == 0));
```

**运行**

```sh
rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE101Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e1-02"></a>

### E1-02 会员收货地址

**依据**：[业务范围](教师候选靶点库.md#e1-02) · [E1评分](课程考核方案.md#rubric-e1)。

**读取**：[UmsMemberReceiveAddressServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberReceiveAddressServiceImpl.java)、[UmsMemberReceiveAddressController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/UmsMemberReceiveAddressController.java)、[UmsMemberReceiveAddress.java](../mall-mbg/src/main/java/com/macro/mall/model/UmsMemberReceiveAddress.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `mall-portal/src/test/java/com/macro/mall/portal/sdc/SDCE102Test.java`；说明 `SDC/E1/outputs/E1-02/说明.md`，本题证据 `SDC/E1/outputs/E1-02/evidence/`。测试包名 `com.macro.mall.portal.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E1/outputs/E1-02/evidence`。
- [ ] 2. 不改产品；追踪add/list/getItem/delete的会员条件，并区分update设置默认时的两次写入。
- [ ] 3. 建立下表人工数据，在真实服务上写观察测试或完成等价可重复断点观察；先写自己的预测，再记录实际调用／返回。不要实现计划中的新规则。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 提交会员来源、请求字段、查询条件对照；解释主键查找与成员范围的差别。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 当前会员101，list／getItem(11) | 查询条件同时包含对应会员和必要ID |
| 有效地址11设为默认 | 先清旧默认再更新11；记录顺序，不宣称已验证不存在地址情形 |

**关键断言／实现片段**

```java
assertTrue(where.contains("member_id = 101")); // where来自捕获的Example条件
```

**运行**

```sh
rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE102Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e1-03"></a>

### E1-03 商品收藏

**依据**：[业务范围](教师候选靶点库.md#e1-03) · [E1评分](课程考核方案.md#rubric-e1)。

**读取**：[MemberCollectionServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/MemberCollectionServiceImpl.java)、[MemberProductCollectionController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/MemberProductCollectionController.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `mall-portal/src/test/java/com/macro/mall/portal/sdc/SDCE103Test.java`；说明 `SDC/E1/outputs/E1-03/说明.md`，本题证据 `SDC/E1/outputs/E1-03/evidence/`。测试包名 `com.macro.mall.portal.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E1/outputs/E1-03/evidence`。
- [ ] 2. 不改产品；追踪sqlEnable开／关时商品信息来源，以及Mongo写入和会员＋商品去重查询。
- [ ] 3. 建立下表人工数据，在真实服务上写观察测试或完成等价可重复断点观察；先写自己的预测，再记录实际调用／返回。不要实现计划中的新规则。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 图中明确MySQL查询与Mongo持久化是不同依赖；比较两配置分支和未运行的数据库部分。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| sqlEnable=true，商品表名为DB_NAME，请求名为INPUT_NAME | 写入快照采用DB_NAME |
| sqlEnable=false | 快照来自请求；不能推断每次列表实时联查MySQL |
| 已有会员101与商品7组合 | 按现有返回语义拒绝重复新增 |

**关键断言／实现片段**

```java
assertEquals("DB_NAME", capturedCollection.getProductName());
```

**运行**

```sh
rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE103Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e1-04"></a>

### E1-04 浏览记录

**依据**：[业务范围](教师候选靶点库.md#e1-04) · [E1评分](课程考核方案.md#rubric-e1)。

**读取**：[MemberReadHistoryServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/MemberReadHistoryServiceImpl.java)、[MemberReadHistoryController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/MemberReadHistoryController.java)、[MemberReadHistory.java](../mall-portal/src/main/java/com/macro/mall/portal/domain/MemberReadHistory.java)、[MemberReadHistoryRepository.java](../mall-portal/src/main/java/com/macro/mall/portal/repository/MemberReadHistoryRepository.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `mall-portal/src/test/java/com/macro/mall/portal/sdc/SDCE104Test.java`；说明 `SDC/E1/outputs/E1-04/说明.md`，本题证据 `SDC/E1/outputs/E1-04/evidence/`。测试包名 `com.macro.mall.portal.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E1/outputs/E1-04/evidence`。
- [ ] 2. 不改产品；追踪浏览新增、按会员倒序分页和clear；与收藏去重语义比较。
- [ ] 3. 建立下表人工数据，在真实服务上写观察测试或完成等价可重复断点观察；先写自己的预测，再记录实际调用／返回。不要实现计划中的新规则。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 解释事件记录与收藏状态的区别，记录分页页码换算、排序及清空范围。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 同一会员对商品7连续create两次 | 观察两次save且每次传入ID清空；未运行Mongo时不宣称已生成独立记录ID |
| 会员101执行clear | 调用Repository的按会员删除；不删除全体会员 |

**关键断言／实现片段**

```java
verify(repository).deleteAllByMemberId(101L);
```

**运行**

```sh
rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE104Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e1-05"></a>

### E1-05 品牌改名

**依据**：[业务范围](教师候选靶点库.md#e1-05) · [E1评分](课程考核方案.md#rubric-e1)。

**读取**：[PmsBrandServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsBrandServiceImpl.java)、[PmsProduct.java](../mall-mbg/src/main/java/com/macro/mall/model/PmsProduct.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE105Test.java`；说明 `SDC/E1/outputs/E1-05/说明.md`，本题证据 `SDC/E1/outputs/E1-05/evidence/`。测试包名 `com.macro.mall.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E1/outputs/E1-05/evidence`。
- [ ] 2. 不改产品；用Mapper替身观察updateBrand对品牌及商品冗余品牌名的影响。
- [ ] 3. 建立下表人工数据，在真实服务上写观察测试或完成等价可重复断点观察；先写自己的预测，再记录实际调用／返回。不要实现计划中的新规则。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 给出品牌表／商品表字段关系和两次写入条件；说明只检查品牌表不够。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 品牌7改名NEW_NAME | 品牌更新及商品按brandId=7同步名称 |
| 其他品牌8的商品 | 商品更新条件不能覆盖brandId=8 |

**关键断言／实现片段**

```java
assertEquals("NEW_NAME", capturedProduct.getBrandName());
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE105Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e1-06"></a>

### E1-06 首页内容聚合

**依据**：[业务范围](教师候选靶点库.md#e1-06) · [E1评分](课程考核方案.md#rubric-e1)。

**读取**：[HomeServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/HomeServiceImpl.java)、[HomeController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/HomeController.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `mall-portal/src/test/java/com/macro/mall/portal/sdc/SDCE106Test.java`；说明 `SDC/E1/outputs/E1-06/说明.md`，本题证据 `SDC/E1/outputs/E1-06/evidence/`。测试包名 `com.macro.mall.portal.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E1/outputs/E1-06/evidence`。
- [ ] 2. 不改产品；追踪content的六块聚合，重点核实无活动时秒杀结果，记录其余来源。
- [ ] 3. 建立下表人工数据，在真实服务上写观察测试或完成等价可重复断点观察；先写自己的预测，再记录实际调用／返回。不要实现计划中的新规则。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 在同一张图标六块来源；源码推断与受控运行观察分列，解释聚合服务不等于六个进程。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 所有DAO提供人工小列表 | 广告、品牌、新品、人气、专题、秒杀分别来自记录的依赖 |
| 无当前秒杀活动 | 观察秒杀为空的实际返回结构，不能把空内容当整个首页错误 |

**关键断言／实现片段**

```java
assertNull(result.getHomeFlashPromotion()); // 无活动样例的实际业务字段
```

**运行**

```sh
rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE106Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e2-01"></a>

### E2-01 优惠码生成的隐藏依赖

**依据**：[业务范围](教师候选靶点库.md#e2-01) · [E2评分](课程考核方案.md#rubric-e2)。

**读取**：[UmsMemberCouponServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberCouponServiceImpl.java)、[UmsMemberCouponController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/UmsMemberCouponController.java)。

**修改**：[UmsMemberCouponServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberCouponServiceImpl.java)。

**创建**：测试 `mall-portal/src/test/java/com/macro/mall/portal/sdc/SDCE201Test.java`；说明 `SDC/E2/outputs/E2-01/说明.md`，本题证据 `SDC/E2/outputs/E2-01/evidence/`。测试包名 `com.macro.mall.portal.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E2/outputs/E2-01/evidence`。
- [ ] 2. 仅在优惠码生成边界显式提供时间和随机源，或新增小型CouponCodeGenerator并委托；保留领取资格和写入顺序。
- [ ] 3. 先记录旧结构的可运行行为，再比较两种局部方案；写下表对照测试，实施一个接缝或两个可运行版本。不要把测试工具本身当架构改造。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 比较参数输入与协作者两案；说明隐藏依赖变为显式依赖，不承诺全局唯一或安全随机。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 固定毫秒1700000000123，随机数字1/2/3/4，会员7 | 码按既有后8位＋4随机位＋补齐4位会员规则确定 |
| 会员123456 | 使用会员后4位；长度16 |
| 相同受控时间／随机序列重复执行 | 结果可复现，领取字段不变 |

**关键断言／实现片段**

```java
assertEquals("0000012312340007", generatedCode);
```

**运行**

```sh
rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE201Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e2-02"></a>

### E2-02 商品关联写入的隐含类型契约

**依据**：[业务范围](教师候选靶点库.md#e2-02) · [E2评分](课程考核方案.md#rubric-e2)。

**读取**：[PmsProductServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductServiceImpl.java)、[PmsProductController.java](../mall-admin/src/main/java/com/macro/mall/controller/PmsProductController.java)、[PmsProductAttributeValue.java](../mall-mbg/src/main/java/com/macro/mall/model/PmsProductAttributeValue.java)。

**修改**：[PmsProductServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductServiceImpl.java)。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE202Test.java`；说明 `SDC/E2/outputs/E2-02/说明.md`，本题证据 `SDC/E2/outputs/E2-02/evidence/`。测试包名 `com.macro.mall.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E2/outputs/E2-02/evidence`。
- [ ] 2. 只将商品属性值关联从Object／原始List／字符串反射改为显式类型路径，比较直接写入与有类型的回调；其余关联保持原样。
- [ ] 3. 先记录旧结构的可运行行为，再比较两种局部方案；写下表对照测试，实施一个接缝或两个可运行版本。不要把测试工具本身当架构改造。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 比较可表达的类型约定；错误类型编译拒绝可用临时负例javac展示，不能把编译失败算业务红灯。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 空属性值列表 | 不写DAO |
| 两条属性值、productId=7、原id非空 | 写入前id清空、productId均为7，参数列表完整 |
| DAO抛出指定异常 | 按所选明确错误契约传播，不吞成成功；若改善异常上下文，单独注明行为差异 |

**关键断言／实现片段**

```java
assertTrue(capturedValues.stream().allMatch(v -> v.getId() == null && v.getProductId().equals(7L)));
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE202Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e2-03"></a>

### E2-03 首页聚合中的时间规则职责

**依据**：[业务范围](教师候选靶点库.md#e2-03) · [E2评分](课程考核方案.md#rubric-e2)。

**读取**：[HomeServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/HomeServiceImpl.java)、[HomeController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/HomeController.java)。

**修改**：[HomeServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/HomeServiceImpl.java)。

**创建**：测试 `mall-portal/src/test/java/com/macro/mall/portal/sdc/SDCE203Test.java`；说明 `SDC/E2/outputs/E2-03/说明.md`，本题证据 `SDC/E2/outputs/E2-03/evidence/`。测试包名 `com.macro.mall.portal.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E2/outputs/E2-03/evidence`。
- [ ] 2. 比较显式时间参数＋局部选择函数，与秒杀查询协作者；只实施活动／场次选择接缝，聚合返回结构不改。
- [ ] 3. 先记录旧结构的可运行行为，再比较两种局部方案；写下表对照测试，实施一个接缝或两个可运行版本。不要把测试工具本身当架构改造。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 用首页板块变化与时间规则变化两个场景论证职责分离；指出参数变多或增加协作者的代价。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 固定时刻无活动 | 返回原空结构 |
| 恰等于活动／场次起止时刻 | 按起点代码的比较运算维持原边界 |
| 当前存在、下一场不存在／存在 | 商品查询与组装保持原结果 |

**关键断言／实现片段**

```java
assertEquals(beforeResult, afterResult); // 比较业务投影，排除对象身份差异
```

**运行**

```sh
rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE203Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e2-04"></a>

### E2-04 收藏与浏览记录重复维护商品快照规则

**依据**：[业务范围](教师候选靶点库.md#e2-04) · [E2评分](课程考核方案.md#rubric-e2)。

**读取**：[MemberCollectionServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/MemberCollectionServiceImpl.java)、[MemberReadHistoryServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/MemberReadHistoryServiceImpl.java)、[MemberProductCollectionController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/MemberProductCollectionController.java)、[MemberReadHistoryController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/MemberReadHistoryController.java)。

**修改**：[MemberCollectionServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/MemberCollectionServiceImpl.java)、[MemberReadHistoryServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/MemberReadHistoryServiceImpl.java)。

**创建**：测试 `mall-portal/src/test/java/com/macro/mall/portal/sdc/SDCE204Test.java`；说明 `SDC/E2/outputs/E2-04/说明.md`，本题证据 `SDC/E2/outputs/E2-04/evidence/`。测试包名 `com.macro.mall.portal.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E2/outputs/E2-04/evidence`。
- [ ] 2. 比较纯快照映射与小型读取协作者；只集中真正相同的商品查询／四字段规则，不统一收藏与浏览业务。
- [ ] 3. 先记录旧结构的可运行行为，再比较两种局部方案；写下表对照测试，实施一个接缝或两个可运行版本。不要把测试工具本身当架构改造。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 展示共享知识只有一处及两用例不被错误合并；不得仅凭少四行赋值称整体架构改善。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 两个服务，sqlEnable分别true/false | 快照来源保持原约定 |
| 商品不存在 | 两条路径保留各自原错误行为 |
| 对共享价格文本规则做受控小变化 | 两服务输出一致；去重与新增规则仍独立 |

**关键断言／实现片段**

```java
assertEquals(collectionSnapshot.price(), historySnapshot.price()); // 同一来源商品的对照投影
```

**运行**

```sh
rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE204Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e2-06"></a>

### E2-06 订单查询的会员范围规则不一致

**依据**：[业务范围](教师候选靶点库.md#e2-06) · [E2评分](课程考核方案.md#rubric-e2)。

**读取**：[OmsPortalOrderServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPortalOrderServiceImpl.java)、[OmsPortalOrderController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/OmsPortalOrderController.java)。

**修改**：[OmsPortalOrderServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPortalOrderServiceImpl.java)。

**创建**：测试 `mall-portal/src/test/java/com/macro/mall/portal/sdc/SDCE206Test.java`；说明 `SDC/E2/outputs/E2-06/说明.md`，本题证据 `SDC/E2/outputs/E2-06/evidence/`。测试包名 `com.macro.mall.portal.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E2/outputs/E2-06/evidence`。
- [ ] 2. 比较在现有查询服务集中可读条件，与小型只读查询协作者；使列表／详情一致限定当前会员和未删除记录，详情不存在、已删除或不属于当前会员时，均用既有Asserts.fail("订单不存在")返回业务错误，不泄露差别。
- [ ] 3. 先记录旧结构的可运行行为，再比较两种局部方案；写下表对照测试，实施一个接缝或两个可运行版本。不要把测试工具本身当架构改造。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 指出权限属于确定性业务边界，保留既有认证；Mock结果不宣称完整HTTP漏洞已验证。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 当前会员101、订单属于101且未删除 | 详情成功，明细一致 |
| 订单属于202／已删除／不存在 | 不返回订单或明细 |
| 列表与详情相同订单 | 可见范围一致，旧列表分页语义不变 |

**关键断言／实现片段**

```java
assertThrows(ApiException.class, () -> service.detail(unreadableId));
```

**运行**

```sh
rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE206Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e2-07"></a>

### E2-07 会员读取中的缓存编排边界

**依据**：[业务范围](教师候选靶点库.md#e2-07) · [E2评分](课程考核方案.md#rubric-e2)。

**读取**：[UmsMemberServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberServiceImpl.java)、[UmsMemberCacheService.java](../mall-portal/src/main/java/com/macro/mall/portal/service/UmsMemberCacheService.java)、[UmsMemberService.java](../mall-portal/src/main/java/com/macro/mall/portal/service/UmsMemberService.java)。

**修改**：[UmsMemberServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberServiceImpl.java)。

**创建**：测试 `mall-portal/src/test/java/com/macro/mall/portal/sdc/SDCE207Test.java`；说明 `SDC/E2/outputs/E2-07/说明.md`，本题证据 `SDC/E2/outputs/E2-07/evidence/`。测试包名 `com.macro.mall.portal.sdc`。

**新增协作者（选择该方案时）**：`mall-portal/src/main/java/com/macro/mall/portal/service/impl/MemberLookup.java`，构造器接收 `UmsMemberCacheService cache, UmsMemberMapper mapper`；提供 `UmsMember findByUsername(String username)`，原 `getByUsername` 委托。组合点仍在Spring；不要改密码／Token／认证流程。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E2/outputs/E2-07/evidence`。
- [ ] 2. 比较原服务内私有读取方法与MemberLookup协作者；实施局部委托，保留缓存命中／回填及原异常传播，认证和令牌不改。
- [ ] 3. 先记录旧结构的可运行行为，再比较两种局部方案；写下表对照测试，实施一个接缝或两个可运行版本。不要把测试工具本身当架构改造。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 报告读取顺序／未命中回填编排变化的修改位置；认证类仍保留密码更新等缓存失效职责，不宣称迁出全部缓存规则。原getByUsername本来就可用两依赖单测，不夸大可测试性收益。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 缓存命中会员101 | 返回该会员，不查Mapper不回填 |
| 缓存未命中，数据库查到101 | 按username查询，回填一次，返回101 |
| 缓存及数据库均无会员 | 返回null、不回填 |
| 缓存读取／回填异常 | 维持原异常语义，不新增静默降级 |

**关键断言／实现片段**

```java
public UmsMember findByUsername(String username) {
    UmsMember cached = cache.getMember(username);
    if (cached != null) return cached;
    UmsMemberExample query = new UmsMemberExample();
    query.createCriteria().andUsernameEqualTo(username);
    List<UmsMember> rows = mapper.selectByExample(query);
    if (CollectionUtils.isEmpty(rows)) return null;
    UmsMember member = rows.get(0);
    cache.setMember(member);
    return member;
}
```

**运行**

```sh
rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE207Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e3-01"></a>

### E3-01 优惠券金额门槛精度

**依据**：[业务范围](教师候选靶点库.md#e3-01) · [E3评分](课程考核方案.md#rubric-e3)。

**读取**：[UmsMemberCouponServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberCouponServiceImpl.java)、[UmsMemberCouponController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/UmsMemberCouponController.java)。

**修改**：[UmsMemberCouponServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberCouponServiceImpl.java)。

**创建**：测试 `SDC/E3/src/test/java/com/macro/mall/sdc/e3/SDCE301Test.java`；说明 `SDC/E3/outputs/E3-01/说明.md`，本题证据 `SDC/E3/outputs/E3-01/evidence/`。测试包名 `com.macro.mall.sdc.e3`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E3/outputs/E3-01/evidence`。
- [ ] 2. 用BigDecimal数值比较修复金额门槛截断；检查全场／分类／商品三个相同判断，不改优惠算法、时间语义和持久化模型。
- [ ] 3. 先用下表业务期望写可编译测试，运行并保存旧代码的断言失败；随后按步骤2的范围作最小修复，同组测试重跑。编译失败不是缺陷红灯。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 金额红灯必须来自实际listCart；补相邻范围分支回归及listCart目标覆盖率。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 一件商品、quantity=1、reduceAmount=0，券endTime为远未来、门槛100；金额98.90／99.90／100.00／100.10 | 不可用／不可用／可用／可用 |
| 分类／商品券，仅非匹配项金额很高 | 不能借非匹配项凑门槛 |
| 过期券、零匹配金额、未过期券 | 按明确范围及时间规则判定，回归两种筛选type |

**关键断言／实现片段**

```java
assertEquals(0, service.listCart(cartAt9990, 1).size()); // 99.90低于100.00
```

**运行**

```sh
rtk proxy mvn -pl SDC/E3 -am -Dtest=SDCE301Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**覆盖率对象**：`UmsMemberCouponServiceImpl.listCart`。保存红灯结果后，对修复版执行：

```sh
rtk proxy mvn -pl SDC/E3 -am clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test -Dtest=SDCE301Test -Dsurefire.failIfNoSpecifiedTests=false
rtk proxy mvn -pl mall-portal -Djacoco.dataFile=../SDC/E3/target/jacoco.exec org.jacoco:jacoco-maven-plugin:0.8.13:report
```

<a id="plan-e3-02"></a>

### E3-02 购物车合并的修改时间

**依据**：[业务范围](教师候选靶点库.md#e3-02) · [E3评分](课程考核方案.md#rubric-e3)。

**读取**：[OmsCartItemServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsCartItemServiceImpl.java)。

**修改**：[OmsCartItemServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsCartItemServiceImpl.java)。

**创建**：测试 `SDC/E3/src/test/java/com/macro/mall/sdc/e3/SDCE302Test.java`；说明 `SDC/E3/outputs/E3-02/说明.md`，本题证据 `SDC/E3/outputs/E3-02/evidence/`。测试包名 `com.macro.mall.sdc.e3`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E3/outputs/E3-02/evidence`。
- [ ] 2. 在已有购物车项合并分支更新将要持久化的existCartItem.modifyDate，避免仅修改未保存入参；不改数量计算。
- [ ] 3. 先用下表业务期望写可编译测试，运行并保存旧代码的断言失败；随后按步骤2的范围作最小修复，同组测试重跑。编译失败不是缺陷红灯。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 前后同组断言；时间用调用前后范围或可控时钟，不使用sleep碰运气。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 已有quantity=2、modifyDate=1000ms；新增quantity=3 | 实际更新对象quantity=5且modifyDate为本次调用期间 |
| 购物车无旧项 | 新增分支及初始时间、会员来源保持 |
| 参数与已存对象不同实例 | 断言捕获更新参数而非调用后随意读入参 |

**关键断言／实现片段**

```java
assertEquals(5, saved.getQuantity());
assertTrue(!saved.getModifyDate().before(start) && !saved.getModifyDate().after(end));
```

**运行**

```sh
rtk proxy mvn -pl SDC/E3 -am -Dtest=SDCE302Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**覆盖率对象**：`OmsCartItemServiceImpl.add`。保存红灯结果后，对修复版执行：

```sh
rtk proxy mvn -pl SDC/E3 -am clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test -Dtest=SDCE302Test -Dsurefire.failIfNoSpecifiedTests=false
rtk proxy mvn -pl mall-portal -Djacoco.dataFile=../SDC/E3/target/jacoco.exec org.jacoco:jacoco-maven-plugin:0.8.13:report
```

<a id="plan-e3-03"></a>

### E3-03 跨分类批量删除属性的计数

**依据**：[业务范围](教师候选靶点库.md#e3-03) · [E3评分](课程考核方案.md#rubric-e3)。

**读取**：[PmsProductAttributeServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductAttributeServiceImpl.java)、[PmsProductAttributeController.java](../mall-admin/src/main/java/com/macro/mall/controller/PmsProductAttributeController.java)。

**修改**：[PmsProductAttributeServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductAttributeServiceImpl.java)。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE303Test.java`；说明 `SDC/E3/outputs/E3-03/说明.md`，本题证据 `SDC/E3/outputs/E3-03/evidence/`。测试包名 `com.macro.mall.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E3/outputs/E3-03/evidence`。
- [ ] 2. 正式规则为允许混合分类／类型删除；按实际存在的唯一属性ID分组计数，维护每组，原删除接口保留。
- [ ] 3. 先用下表业务期望写可编译测试，运行并保存旧代码的断言失败；随后按步骤2的范围作最小修复，同组测试重跑。编译失败不是缺陷红灯。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. MockMvc或真实服务前后断言各组计数与删除集合，不能只验证总删除行数；涉及事务效果另给集成证据。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 分类101／202的规格计数各2，各删1 | 结果各1，不得0／2 |
| 同分类规格与参数混合 | 分别扣attributeCount／paramCount |
| 重复ID、不存在ID | 重复只计一次，不存在不扣计数 |
| 同组普通删除 | 返回实际删除行数，原成功路径不变 |

**关键断言／实现片段**

```java
assertEquals(List.of(1, 1), List.of(category101.getAttributeCount(), category202.getAttributeCount()));
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE303Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**覆盖率对象**：`PmsProductAttributeServiceImpl.delete`。保存红灯结果后，对修复版执行：

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test -Dtest=SDCE303Test -Dsurefire.failIfNoSpecifiedTests=false
rtk proxy mvn -pl mall-admin org.jacoco:jacoco-maven-plugin:0.8.13:report
```

<a id="plan-e3-04"></a>

### E3-04 属性迁移后的分类计数

**依据**：[业务范围](教师候选靶点库.md#e3-04) · [E3评分](课程考核方案.md#rubric-e3)。

**读取**：[PmsProductAttributeServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductAttributeServiceImpl.java)、[PmsProductAttributeParam.java](../mall-admin/src/main/java/com/macro/mall/dto/PmsProductAttributeParam.java)、[PmsProductAttributeController.java](../mall-admin/src/main/java/com/macro/mall/controller/PmsProductAttributeController.java)。

**修改**：[PmsProductAttributeServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductAttributeServiceImpl.java)。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE304Test.java`；说明 `SDC/E3/outputs/E3-04/说明.md`，本题证据 `SDC/E3/outputs/E3-04/evidence/`。测试包名 `com.macro.mall.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E3/outputs/E3-04/evidence`。
- [ ] 2. 正式规则允许属性迁移至有效分类／类型；比较旧、新分组，维护双方计数。目标分类不存在在任何写入前拒绝。
- [ ] 3. 先用下表业务期望写可编译测试，运行并保存旧代码的断言失败；随后按步骤2的范围作最小修复，同组测试重跑。编译失败不是缺陷红灯。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 用create与update的不变量对照解释根因，保留接口；与E3-03不重复计同一计数辅助函数的实现。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 101规格→202参数；原两组分别1和0 | 旧规格0、新参数1，属性字段同步 |
| 分类／类型不变 | 不增不减计数 |
| 只换类型或只换分类 | 恰维护受影响两组 |
| 目标分类不存在 | 不更新属性和计数 |

**关键断言／实现片段**

```java
assertEquals(0, oldCategory.getAttributeCount());
assertEquals(1, newCategory.getParamCount());
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE304Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**覆盖率对象**：`PmsProductAttributeServiceImpl.update`。保存红灯结果后，对修复版执行：

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test -Dtest=SDCE304Test -Dsurefire.failIfNoSpecifiedTests=false
rtk proxy mvn -pl mall-admin org.jacoco:jacoco-maven-plugin:0.8.13:report
```

<a id="plan-e3-06"></a>

### E3-06 商品属性分类 ID 的参数校验

**依据**：[业务范围](教师候选靶点库.md#e3-06) · [E3评分](课程考核方案.md#rubric-e3)。

**读取**：[PmsProductAttributeParam.java](../mall-admin/src/main/java/com/macro/mall/dto/PmsProductAttributeParam.java)、[PmsProductAttributeController.java](../mall-admin/src/main/java/com/macro/mall/controller/PmsProductAttributeController.java)。

**修改**：[PmsProductAttributeParam.java](../mall-admin/src/main/java/com/macro/mall/dto/PmsProductAttributeParam.java)、[PmsProductAttributeController.java](../mall-admin/src/main/java/com/macro/mall/controller/PmsProductAttributeController.java)。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE306Test.java`；说明 `SDC/E3/outputs/E3-06/说明.md`，本题证据 `SDC/E3/outputs/E3-06/evidence/`。测试包名 `com.macro.mall.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E3/outputs/E3-06/evidence`。
- [ ] 2. Long分类ID改用适合类型的@NotNull，并在create/update请求入口启用项目现有校验；不只补@Valid使HV000030变成500。
- [ ] 3. 先用下表业务期望写可编译测试，运行并保存旧代码的断言失败；随后按步骤2的范围作最小修复，同组测试重跑。编译失败不是缺陷红灯。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 保留修复前类型异常与控制器未校验两项证据；使用真实Validator和MockMvc，不推称全应用认证链。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 分类ID=7、name=颜色，selectType/inputType/filterType/searchType/relatedStatus/handAddStatus/type均0 | Validator不抛UnexpectedTypeException，请求可到服务 |
| 缺分类ID／空name | 按项目参数错误返回，业务服务不被调用 |
| create和update同一非法字段 | 两入口均阻止非法写入 |

**关键断言／实现片段**

```java
assertDoesNotThrow(() -> validator.validate(validParam));
verifyNoInteractions(serviceForInvalidRequest);
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE306Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**覆盖率对象**：`PmsProductAttributeController.create/update`。保存红灯结果后，对修复版执行：

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test -Dtest=SDCE306Test -Dsurefire.failIfNoSpecifiedTests=false
rtk proxy mvn -pl mall-admin org.jacoco:jacoco-maven-plugin:0.8.13:report
```

<a id="plan-e3-07"></a>

### E3-07 无效地址更新清空已有默认地址

**依据**：[业务范围](教师候选靶点库.md#e3-07) · [E3评分](课程考核方案.md#rubric-e3)。

**读取**：[UmsMemberReceiveAddressServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberReceiveAddressServiceImpl.java)、[UmsMemberReceiveAddressController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/UmsMemberReceiveAddressController.java)、[UmsMemberReceiveAddressService.java](../mall-portal/src/main/java/com/macro/mall/portal/service/UmsMemberReceiveAddressService.java)。

**修改**：[UmsMemberReceiveAddressServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberReceiveAddressServiceImpl.java)。

**创建**：测试 `SDC/E3/src/test/java/com/macro/mall/sdc/e3/SDCE307Test.java`；说明 `SDC/E3/outputs/E3-07/说明.md`，本题证据 `SDC/E3/outputs/E3-07/evidence/`。测试包名 `com.macro.mall.sdc.e3`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E3/outputs/E3-07/evidence`。
- [ ] 2. 在清除旧默认前确认目标属于当前会员且可更新；无效目标返回0且不写。保留既有@Transactional和有效目标默认切换。
- [ ] 3. 先用下表业务期望写可编译测试，运行并保存旧代码的断言失败；随后按步骤2的范围作最小修复，同组测试重跑。编译失败不是缺陷红灯。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 利用已保存的AddressDefaultProbe解释红灯，但新JUnit断言应要求无副作用；不以事务注解自动推断回滚。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 当前会员101，目标999不存在，defaultStatus=1 | 返回0；无清默认、无目标更新 |
| 目标属于202 | 返回0且当前会员原默认不变 |
| 有效非默认地址设默认／已默认地址再次更新 | 保持成功语义和会员范围 |
| 普通字段更新、defaultStatus=0或null | 按既有语义执行，不扩大规则 |

**关键断言／实现片段**

```java
assertEquals(0, service.update(999L, request));
verify(mapper, never()).updateByExampleSelective(any(), any());
```

**运行**

```sh
rtk proxy mvn -pl SDC/E3 -am -Dtest=SDCE307Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**覆盖率对象**：`UmsMemberReceiveAddressServiceImpl.update`。保存红灯结果后，对修复版执行：

```sh
rtk proxy mvn -pl SDC/E3 -am clean org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test -Dtest=SDCE307Test -Dsurefire.failIfNoSpecifiedTests=false
rtk proxy mvn -pl mall-portal -Djacoco.dataFile=../SDC/E3/target/jacoco.exec org.jacoco:jacoco-maven-plugin:0.8.13:report
```

<a id="plan-e4-01"></a>

### E4-01 优惠券关联写入重复

**依据**：[业务范围](教师候选靶点库.md#e4-01) · [E4评分](课程考核方案.md#rubric-e4)。

**读取**：[SmsCouponServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/SmsCouponServiceImpl.java)。

**修改**：[SmsCouponServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/SmsCouponServiceImpl.java)。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE401Test.java`；说明 `SDC/E4/outputs/E4-01/说明.md`，本题证据 `SDC/E4/outputs/E4-01/evidence/`。测试包名 `com.macro.mall.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E4/outputs/E4-01/evidence`。
- [ ] 2. 在create/update内提取真实重复的关联填充／写入，比较普通方法和范围策略；保留更新独有的删除行为。
- [ ] 3. 先将下表现有行为写成特征测试并运行通过；保存实际参数／结果快照，再按步骤2重构；原样重跑特征测试，禁止为迁就重构改期望。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 展示关联规则调整前后需改几处；不能将旧E3-05的数据清理功能夹入重构。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| useType=0/1/2，分别create和update | 券ID回填、插入／删除次数、参数及顺序与改前相同 |
| 关联空集合 | 维持原行为 |
| 同类型更新 | 不顺带改变旧关联清理契约 |

**关键断言／实现片段**

```java
assertEquals(beforeWrites, afterWrites); // 记录操作类型、业务参数、顺序的不可变快照
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE401Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e4-02"></a>

### E4-02 后台商品的多种状态批量更新

**依据**：[业务范围](教师候选靶点库.md#e4-02) · [E4评分](课程考核方案.md#rubric-e4)。

**读取**：[PmsProductServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductServiceImpl.java)、[PmsProductController.java](../mall-admin/src/main/java/com/macro/mall/controller/PmsProductController.java)。

**修改**：[PmsProductServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductServiceImpl.java)。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE402Test.java`；说明 `SDC/E4/outputs/E4-02/说明.md`，本题证据 `SDC/E4/outputs/E4-02/evidence/`。测试包名 `com.macro.mall.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E4/outputs/E4-02/evidence`。
- [ ] 2. 只重组publish/recommend/new/delete四个状态更新的共同编排，保留四个公开方法；比较模板式流程和类型明确的辅助方法。
- [ ] 3. 先将下表现有行为写成特征测试并运行通过；保存实际参数／结果快照，再按步骤2重构；原样重跑特征测试，禁止为迁就重构改期望。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 以第五种状态的小型变化说明修改点与抽象成本，不修改生产接口来演示。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 四种状态分别更新ID[7,8]为1 | 仅目标字段非空、ID集合一致、返回Mapper行数 |
| Mapper返回0 | 原样返回0 |
| 审核状态路径 | 不并入本题，不删审核记录副作用 |

**关键断言／实现片段**

```java
assertEquals(1, saved.getPublishStatus());
assertNull(saved.getVerifyStatus());
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE402Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e4-03"></a>

### E4-03 商品关联写入的反射辅助方法

**依据**：[业务范围](教师候选靶点库.md#e4-03) · [E4评分](课程考核方案.md#rubric-e4)。

**读取**：[PmsProductServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductServiceImpl.java)、[PmsProductController.java](../mall-admin/src/main/java/com/macro/mall/controller/PmsProductController.java)、[PmsProductAttributeValue.java](../mall-mbg/src/main/java/com/macro/mall/model/PmsProductAttributeValue.java)。

**修改**：[PmsProductServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductServiceImpl.java)。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE403Test.java`；说明 `SDC/E4/outputs/E4-03/说明.md`，本题证据 `SDC/E4/outputs/E4-03/evidence/`。测试包名 `com.macro.mall.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E4/outputs/E4-03/evidence`。
- [ ] 2. 仅对一个商品属性值关联改为显式类型化路径，比较适配／回调与直接调用；其他关联继续原实现。
- [ ] 3. 先将下表现有行为写成特征测试并运行通过；保存实际参数／结果快照，再按步骤2重构；原样重跑特征测试，禁止为迁就重构改期望。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 比较类型约定和调用位置；本题与E2-02重叠，不允许同一补丁重复交作业。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 空、多条、带旧ID记录 | ID清空、productId赋值及DAO入参一致 |
| DAO抛出异常 | 保留原外部可观察异常类型、消息／cause约定；不顺便修异常原因 |
| 其余关联路径 | 不被迁移或改写 |

**关键断言／实现片段**

```java
assertEquals(beforeFailure.getClass(), afterFailure.getClass());
assertEquals(beforeWriteProjection, afterWriteProjection);
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE403Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e4-04"></a>

### E4-04 SKU 集合差异计算

**依据**：[业务范围](教师候选靶点库.md#e4-04) · [E4评分](课程考核方案.md#rubric-e4)。

**读取**：[PmsProductServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductServiceImpl.java)。

**修改**：[PmsProductServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductServiceImpl.java)。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE404Test.java`；说明 `SDC/E4/outputs/E4-04/说明.md`，本题证据 `SDC/E4/outputs/E4-04/evidence/`。测试包名 `com.macro.mall.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E4/outputs/E4-04/evidence`。
- [ ] 2. 将handleUpdateSkuStockList的集合划分与数据库写入编排分离；比较纯差异计算与模板式处理，不改库存规则。
- [ ] 3. 先将下表现有行为写成特征测试并运行通过；保存实际参数／结果快照，再按步骤2重构；原样重跑特征测试，禁止为迁就重构改期望。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 给出纯计算接缝如何缩小测试依赖的证据；解释不采用复杂通用Diff框架。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 原ID[1,2]，输入ID[2,null] | 新增null项、更新2、删除1 |
| 全空、仅新增、仅更新、部分删除 | 集合和调用顺序与原版一致 |
| 编码生成、DAO返回值 | 保持现状，不顺带修改库存预警 |

**关键断言／实现片段**

```java
assertEquals(List.of(1L), deletedIds);
assertEquals(List.of(2L), updatedIds);
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE404Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e4-06"></a>

### E4-06 首页秒杀选择与结果组装

**依据**：[业务范围](教师候选靶点库.md#e4-06) · [E4评分](课程考核方案.md#rubric-e4)。

**读取**：[HomeServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/HomeServiceImpl.java)、[HomeController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/HomeController.java)。

**修改**：[HomeServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/HomeServiceImpl.java)。

**创建**：测试 `mall-portal/src/test/java/com/macro/mall/portal/sdc/SDCE406Test.java`；说明 `SDC/E4/outputs/E4-06/说明.md`，本题证据 `SDC/E4/outputs/E4-06/evidence/`。测试包名 `com.macro.mall.portal.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E4/outputs/E4-06/evidence`。
- [ ] 2. 提取秒杀选择的时间输入与结果组装，比较管道式组合／简单方法和查询协作者；保持所有时间比较。
- [ ] 3. 先将下表现有行为写成特征测试并运行通过；保存实际参数／结果快照，再按步骤2重构；原样重跑特征测试，禁止为迁就重构改期望。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 代码和特征测试同时证明变化点；与E2-03不得用同一改动重复计分。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 无活动、无当前场、无下一场、完整场次 | 返回结构、查询条件与原版一致 |
| 起止边界固定时刻 | 严格按原运算符行为 |
| 组装字段变化演示 | 只触及选定职责，说明增加跳转的代价 |

**关键断言／实现片段**

```java
assertEquals(beforeProjection, afterProjection);
```

**运行**

```sh
rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE406Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e4-07"></a>

### E4-07 优惠券适用范围的策略重构

**依据**：[业务范围](教师候选靶点库.md#e4-07) · [E4评分](课程考核方案.md#rubric-e4)。

**读取**：[UmsMemberCouponServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberCouponServiceImpl.java)、[UmsMemberCouponController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/UmsMemberCouponController.java)。

**修改**：[UmsMemberCouponServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberCouponServiceImpl.java)。

**创建**：测试 `SDC/E4/src/test/java/com/macro/mall/sdc/e4/SDCE407Test.java`；说明 `SDC/E4/outputs/E4-07/说明.md`，本题证据 `SDC/E4/outputs/E4-07/evidence/`。测试包名 `com.macro.mall.sdc.e4`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E4/outputs/E4-07/evidence`。
- [ ] 2. 对listCart三种适用范围采用小型策略或提取公共流程；只重组金额选择与可用分类，不修改门槛和过期算法。
- [ ] 3. 先将下表现有行为写成特征测试并运行通过；保存实际参数／结果快照，再按步骤2重构；原样重跑特征测试，禁止为迁就重构改期望。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 比较新增第四种范围的修改点；已知金额缺陷单独列为E3问题，不能换测试期望掩盖行为变化。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 全场／分类／商品，匹配与不匹配，type=1/0 | 可用／不可用对象ID集合及顺序与起点一致 |
| 过期、空购物车、未知范围 | 结果按原行为，不补新功能 |
| 门槛100，99.90／100.00 | 记录起点是否有截断缺陷；纯重构不改变，也不恢复已修复缺陷 |

**关键断言／实现片段**

```java
assertEquals(beforeCouponIds, afterCouponIds);
assertEquals(beforeDisabledIds, afterDisabledIds);
```

**运行**

```sh
rtk proxy mvn -pl SDC/E4 -am -Dtest=SDCE407Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e5-01"></a>

### E5-01 分类删除限制

**依据**：[业务范围](教师候选靶点库.md#e5-01) · [E5评分](课程考核方案.md#rubric-e5)。

**读取**：[PmsProductCategoryServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductCategoryServiceImpl.java)、[PmsProduct.java](../mall-mbg/src/main/java/com/macro/mall/model/PmsProduct.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE501Test.java`；说明 `SDC/E5/outputs/E5-01/说明.md`，本题证据 `SDC/E5/outputs/E5-01/evidence/`。测试包名 `com.macro.mall.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E5/outputs/E5-01/evidence`。
- [ ] 2. 不改产品；规划分类删除的子分类／商品引用检查、错误响应及测试落点，排除级联删除。
- [ ] 3. 建立下表人工数据，在真实服务上写观察测试或完成等价可重复断点观察；先写自己的预测，再记录实际调用／返回。不要实现计划中的新规则。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 影响图含Controller、Service、分类／商品Mapper、错误返回；列出为什么不动搜索与生成代码、并发新增引用的限制。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 现有delete分类101 | 用Mapper调用证明当前仅按主键删除 |
| 拟新增规则：有子分类或商品引用 | 计划期望拒绝、未执行删除；不存在仍0 |
| 逻辑删除商品引用 | 在计划中明确也阻止删除 |

**关键断言／实现片段**

```java
verify(categoryMapper).deleteByPrimaryKey(101L); // 仅核实现状，不是新规则已通过
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE501Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e5-02"></a>

### E5-02 品牌列表的可选厂家筛选

**依据**：[业务范围](教师候选靶点库.md#e5-02) · [E5评分](课程考核方案.md#rubric-e5)。

**读取**：[PmsBrandServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsBrandServiceImpl.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE502Test.java`；说明 `SDC/E5/outputs/E5-02/说明.md`，本题证据 `SDC/E5/outputs/E5-02/evidence/`。测试包名 `com.macro.mall.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E5/outputs/E5-02/evidence`。
- [ ] 2. 不改产品；规划可选factoryStatus如何贯穿控制器、服务和分页前查询，旧四参数服务入口必须兼容。
- [ ] 3. 建立下表人工数据，在真实服务上写观察测试或完成等价可重复断点观察；先写自己的预测，再记录实际调用／返回。不要实现计划中的新规则。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 列出新增重载委托或可选查询对象两方案、无需改表的字段依据，并给出每个调用方处理方式。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 现有listBrand不传厂家条件 | 捕获Example，确认现有过滤与分页调用 |
| 新需求不传／0／1／非法2 | 分别计划旧结果／对应状态／输入错误 |
| 关键词和展示状态组合 | 计划AND组合、分页前筛选 |

**关键断言／实现片段**

```java
assertFalse(existingConditions.contains("factory_status")); // 现状观察
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE502Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e5-03"></a>

### E5-03 收货地址邮政编码规则

**依据**：[业务范围](教师候选靶点库.md#e5-03) · [E5评分](课程考核方案.md#rubric-e5)。

**读取**：[UmsMemberReceiveAddressServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberReceiveAddressServiceImpl.java)、[UmsMemberReceiveAddress.java](../mall-mbg/src/main/java/com/macro/mall/model/UmsMemberReceiveAddress.java)、[UmsMemberReceiveAddressController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/UmsMemberReceiveAddressController.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `mall-portal/src/test/java/com/macro/mall/portal/sdc/SDCE503Test.java`；说明 `SDC/E5/outputs/E5-03/说明.md`，本题证据 `SDC/E5/outputs/E5-03/evidence/`。测试包名 `com.macro.mall.portal.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E5/outputs/E5-03/evidence`。
- [ ] 2. 不改产品；规划add/update共同邮编规则，明确null、空白、前导零及旧数据语义。
- [ ] 3. 建立下表人工数据，在真实服务上写观察测试或完成等价可重复断点观察；先写自己的预测，再记录实际调用／返回。不要实现计划中的新规则。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 证明表和模型已是String，无需改表；指出校验和规范化放在哪里、为何不改生成模型。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 现有请求postCode="001234" | 观察Mapper收到字符串原值 |
| 新需求null／空白／5、6、7位／全角数字 | 列出新增及更新各自期望，不能只写合法／非法 |
| 旧记录已有非规范邮编 | 不迁移；仅约束新写入 |

**关键断言／实现片段**

```java
assertEquals("001234", capturedAddress.getPostCode());
```

**运行**

```sh
rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE503Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e5-04"></a>

### E5-04 只清除指定日期以前的浏览记录

**依据**：[业务范围](教师候选靶点库.md#e5-04) · [E5评分](课程考核方案.md#rubric-e5)。

**读取**：[MemberReadHistoryServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/MemberReadHistoryServiceImpl.java)、[MemberReadHistory.java](../mall-portal/src/main/java/com/macro/mall/portal/domain/MemberReadHistory.java)、[MemberReadHistoryController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/MemberReadHistoryController.java)、[MemberReadHistoryRepository.java](../mall-portal/src/main/java/com/macro/mall/portal/repository/MemberReadHistoryRepository.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `mall-portal/src/test/java/com/macro/mall/portal/sdc/SDCE504Test.java`；说明 `SDC/E5/outputs/E5-04/说明.md`，本题证据 `SDC/E5/outputs/E5-04/evidence/`。测试包名 `com.macro.mall.portal.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E5/outputs/E5-04/evidence`。
- [ ] 2. 不改产品；规划新增按截止时刻删除入口，旧clear保留，成员范围必须进入Repository条件。
- [ ] 3. 建立下表人工数据，在真实服务上写观察测试或完成等价可重复断点观察；先写自己的预测，再记录实际调用／返回。不要实现计划中的新规则。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 列入口／接口／Repository变化与列表不改理由；明确需要Mongo集成来验证条件和删除数量。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 现有clear，会员101 | 确认按会员删除的实际调用 |
| 未来截止t | 计划仅删101且createTime<t；=t及其他会员保留 |
| 带不同时区偏移但同一瞬间 | 计划相同删除结果，毫秒精度 |

**关键断言／实现片段**

```java
verify(repository).deleteAllByMemberId(101L); // 现状；新截止删除尚未实现
```

**运行**

```sh
rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE504Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e5-05"></a>

### E5-05 收藏列表增加商品名筛选

**依据**：[业务范围](教师候选靶点库.md#e5-05) · [E5评分](课程考核方案.md#rubric-e5)。

**读取**：[MemberCollectionServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/MemberCollectionServiceImpl.java)、[MemberProductCollectionController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/MemberProductCollectionController.java)、[MemberProductCollectionRepository.java](../mall-portal/src/main/java/com/macro/mall/portal/repository/MemberProductCollectionRepository.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `mall-portal/src/test/java/com/macro/mall/portal/sdc/SDCE505Test.java`；说明 `SDC/E5/outputs/E5-05/说明.md`，本题证据 `SDC/E5/outputs/E5-05/evidence/`。测试包名 `com.macro.mall.portal.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E5/outputs/E5-05/evidence`。
- [ ] 2. 不改产品；规划收藏快照名称字面过滤及分页总数，排除实时联查商品表与取一页后过滤。
- [ ] 3. 建立下表人工数据，在真实服务上写观察测试或完成等价可重复断点观察；先写自己的预测，再记录实际调用／返回。不要实现计划中的新规则。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 说明快照字段来源、Mongo条件与PageRequest的职责；在计划中给出可运行集成用例的数据分布。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 现有list会员101 | 确认Repository以memberId分页 |
| 新需求空关键字／普通字／.或* | 分别维持旧结果／字面包含，不当正则 |
| 跨页匹配及其他会员 | 计划过滤先于分页且总数／归属正确 |

**关键断言／实现片段**

```java
assertEquals(101L, capturedMemberId); // 捕获现有Repository会员参数
```

**运行**

```sh
rtk proxy mvn -pl mall-portal -am -DskipTests=false -Dtest=SDCE505Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e5-06"></a>

### E5-06 首页人气推荐批次去重

**依据**：[业务范围](教师候选靶点库.md#e5-06) · [E5评分](课程考核方案.md#rubric-e5)。

**读取**：[SmsHomeRecommendProductServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/SmsHomeRecommendProductServiceImpl.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE506Test.java`；说明 `SDC/E5/outputs/E5-06/说明.md`，本题证据 `SDC/E5/outputs/E5-06/evidence/`。测试包名 `com.macro.mall.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E5/outputs/E5-06/evidence`。
- [ ] 2. 不改产品；规划仅本批按productId取首次、默认状态不变及返回真实插入数，排除跨请求幂等。
- [ ] 3. 建立下表人工数据，在真实服务上写观察测试或完成等价可重复断点观察；先写自己的预测，再记录实际调用／返回。不要实现计划中的新规则。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 给出表无productId唯一约束的依据；解释标准集合已足够、不能承诺并发唯一性。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 现有输入商品[7,7,8] | 观察逐条插入3次及默认字段 |
| 新需求同样输入 | 计划只插首个7与8，返回真实成功数 |
| 空列表／Mapper返回0／抛异常 | 分别计划0／不虚增／保留原异常事务行为 |

**关键断言／实现片段**

```java
verify(mapper, times(3)).insert(any());
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE506Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e6-01"></a>

### E6-01 实现分类删除限制

**依据**：[业务范围](教师候选靶点库.md#e6-01) · [E6评分](课程考核方案.md#rubric-e6)。

**读取**：[PmsProductCategoryServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductCategoryServiceImpl.java)、[PmsProduct.java](../mall-mbg/src/main/java/com/macro/mall/model/PmsProduct.java)。

**修改**：[PmsProductCategoryServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductCategoryServiceImpl.java)。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE601Test.java`；说明 `SDC/E6/outputs/E6-01/说明.md`，本题证据 `SDC/E6/outputs/E6-01/evidence/`。测试包名 `com.macro.mall.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E6/outputs/E6-01/evidence`。
- [ ] 2. 在既有分类delete边界增加子分类与任意商品引用检查；不存在返回0，有引用在写入前拒绝，无引用沿用删除。
- [ ] 3. 把下表规格写为测试，先观察未实现时不满足；新入口可先建立可编译的最小签名，再使业务断言红灯，不能以缺方法编译失败冒充功能红灯。实现步骤2后同组转绿并回归旧调用。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 承接E5-01，把每条规格转为测试；用实际字段条件证据，单独说明并发插入引用未保证。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 分类不存在 | 返回0不执行删除 |
| 有直接子分类／活动商品引用／逻辑删除商品引用 | 项目错误方式拒绝且不删除 |
| 空分类、仅其他分类有引用 | 原删除成功；无级联副作用 |

**关键断言／实现片段**

```java
assertThrows(ApiException.class, () -> service.delete(101L));
verify(categoryMapper, never()).deleteByPrimaryKey(any());
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE601Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e6-02"></a>

### E6-02 实现品牌厂家筛选

**依据**：[业务范围](教师候选靶点库.md#e6-02) · [E6评分](课程考核方案.md#rubric-e6)。

**读取**：[PmsBrandServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsBrandServiceImpl.java)、[PmsBrandController.java](../mall-admin/src/main/java/com/macro/mall/controller/PmsBrandController.java)、[PmsBrandService.java](../mall-admin/src/main/java/com/macro/mall/service/PmsBrandService.java)。

**修改**：[PmsBrandServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/PmsBrandServiceImpl.java)、[PmsBrandController.java](../mall-admin/src/main/java/com/macro/mall/controller/PmsBrandController.java)、[PmsBrandService.java](../mall-admin/src/main/java/com/macro/mall/service/PmsBrandService.java)。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE602Test.java`；说明 `SDC/E6/outputs/E6-02/说明.md`，本题证据 `SDC/E6/outputs/E6-02/evidence/`。测试包名 `com.macro.mall.sdc`。

**新旧接口**：旧 `listBrand(String keyword, Integer showStatus, int pageNum, int pageSize)` 保留并委托新增五参数重载 `listBrand(String keyword, Integer showStatus, int pageNum, int pageSize, Integer factoryStatus)`。Controller新增可选同名参数；旧调用传null，不复制查询实现。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E6/outputs/E6-02/evidence`。
- [ ] 2. 在Controller接可选factoryStatus，Service新增兼容入口并共用查询；只允许0/1，过滤在分页前，旧四参数方法继续工作。
- [ ] 3. 把下表规格写为测试，先观察未实现时不满足；新入口可先建立可编译的最小签名，再使业务断言红灯，不能以缺方法编译失败冒充功能红灯。实现步骤2后同组转绿并回归旧调用。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 规格追溯至E5-02；用捕获Example及隔离数据查询区分条件单测与真实分页证据。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 不传厂家状态并使用旧服务方法 | 旧结果和排序分页不变 |
| 状态0／1，叠加keyword/showStatus | 生成AND条件并返回对应数据 |
| 状态2 | 按项目参数错误拒绝，不执行数据库查询 |

**关键断言／实现片段**

```java
assertTrue(conditions.contains("factory_status = 1"));
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE602Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e6-03"></a>

### E6-03 实现收货地址邮编规则

**依据**：[业务范围](教师候选靶点库.md#e6-03) · [E6评分](课程考核方案.md#rubric-e6)。

**读取**：[UmsMemberReceiveAddressServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberReceiveAddressServiceImpl.java)、[UmsMemberReceiveAddressController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/UmsMemberReceiveAddressController.java)、[UmsMemberReceiveAddress.java](../mall-mbg/src/main/java/com/macro/mall/model/UmsMemberReceiveAddress.java)。

**修改**：[UmsMemberReceiveAddressServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/UmsMemberReceiveAddressServiceImpl.java)。

**创建**：测试 `SDC/E6/src/test/java/com/macro/mall/sdc/e6/SDCE603Test.java`；说明 `SDC/E6/outputs/E6-03/说明.md`，本题证据 `SDC/E6/outputs/E6-03/evidence/`。测试包名 `com.macro.mall.sdc.e6`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E6/outputs/E6-03/evidence`。
- [ ] 2. 在手写服务的共同边界实现String.strip及六位ASCII数字规则，不改生成模型。新增null保留，更新null不覆盖旧值，纯空白规范为可清空的空字符串。
- [ ] 3. 把下表规格写为测试，先观察未实现时不满足；新入口可先建立可编译的最小签名，再使业务断言红灯，不能以缺方法编译失败冒充功能红灯。实现步骤2后同组转绿并回归旧调用。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 说明接口兼容、字符类型、同一规范化规则；不得把邮编格式合法说成地址真实。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 新增null／空白／" 001234 " | 分别null／""／"001234" |
| 更新未传或null | 不修改旧邮编 |
| 5位／7位／全角数字 | 拒绝且不写Mapper |
| 合法新增、合法更新 | 前导零及会员条件保持 |

**关键断言／实现片段**

```java
assertEquals("001234", capturedAddress.getPostCode());
verify(mapper, never()).insert(any());
```

**运行**

```sh
rtk proxy mvn -pl SDC/E6 -am -Dtest=SDCE603Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e6-04"></a>

### E6-04 实现浏览记录按截止时刻清理

**依据**：[业务范围](教师候选靶点库.md#e6-04) · [E6评分](课程考核方案.md#rubric-e6)。

**读取**：[MemberReadHistoryServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/MemberReadHistoryServiceImpl.java)、[MemberReadHistory.java](../mall-portal/src/main/java/com/macro/mall/portal/domain/MemberReadHistory.java)、[MemberReadHistoryController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/MemberReadHistoryController.java)、[MemberReadHistoryService.java](../mall-portal/src/main/java/com/macro/mall/portal/service/MemberReadHistoryService.java)、[MemberReadHistoryRepository.java](../mall-portal/src/main/java/com/macro/mall/portal/repository/MemberReadHistoryRepository.java)。

**修改**：[MemberReadHistoryServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/MemberReadHistoryServiceImpl.java)、[MemberReadHistoryController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/MemberReadHistoryController.java)、[MemberReadHistoryService.java](../mall-portal/src/main/java/com/macro/mall/portal/service/MemberReadHistoryService.java)、[MemberReadHistoryRepository.java](../mall-portal/src/main/java/com/macro/mall/portal/repository/MemberReadHistoryRepository.java)。

**创建**：测试 `SDC/E6/src/test/java/com/macro/mall/sdc/e6/SDCE604Test.java`；说明 `SDC/E6/outputs/E6-04/说明.md`，本题证据 `SDC/E6/outputs/E6-04/evidence/`。测试包名 `com.macro.mall.sdc.e6`。

**新增接口**：`POST /member/readHistory/clearBefore?before=2026-10-01T00:00:00%2B08:00`；Controller用OffsetDateTime解析带偏移量输入并转Instant，Service新增 `long clearBefore(Instant cutoff)`，Repository新增 `long deleteByMemberIdAndCreateTimeBefore(Long memberId, Date cutoff)`。超过毫秒的非零小数精度应按题目输入错误拒绝，不能无声截断；旧clear和list保留。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E6/outputs/E6-04/evidence`。
- [ ] 2. 新增独立clearBefore入口及带offset、毫秒精度的时间解析；Repository按当前会员且createTime严格小于截止删除，旧clear不变。
- [ ] 3. 把下表规格写为测试，先观察未实现时不满足；新入口可先建立可编译的最小签名，再使业务断言红灯，不能以缺方法编译失败冒充功能红灯。实现步骤2后同组转绿并回归旧调用。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 必须用隔离Mongo验证条件与删除数量才能声称删除功能验收；离线替身只得相应部分验证分。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 会员101有t-1ms、t、t+1ms，会员202有t-1ms | 只删除101的t-1ms，返回1 |
| 不同时区表达相同瞬间 | 删除集合一致 |
| 无匹配／非法时间 | 0／输入错误，非法时无删除 |

**关键断言／实现片段**

```java
assertEquals(1L, deletedCount);
assertTrue(remainingIds.containsAll(List.of(equalTimeId, laterId, otherMemberId)));
```

**运行**

```sh
rtk proxy mvn -pl SDC/E6 -am -Dtest=SDCE604Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**集成验证**：增加SDCE604MongoTest，在教师隔离Mongo库准备上述四条记录；只清理由本测试插入的_id，不清空整库。

新增 `SDC/E6/src/test/java/com/macro/mall/sdc/e6/SDCE604MongoTest.java`，读取教师指定隔离Mongo配置；不在测试源码硬编码凭据。按选定测试运行，未提供环境时不能计该项已验证：

```sh
rtk proxy mvn -pl SDC/E6 -am -Dtest=SDCE604MongoTest -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e6-05"></a>

### E6-05 实现收藏名称筛选

**依据**：[业务范围](教师候选靶点库.md#e6-05) · [E6评分](课程考核方案.md#rubric-e6)。

**读取**：[MemberCollectionServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/MemberCollectionServiceImpl.java)、[MemberProductCollectionController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/MemberProductCollectionController.java)、[MemberCollectionService.java](../mall-portal/src/main/java/com/macro/mall/portal/service/MemberCollectionService.java)、[MemberProductCollectionRepository.java](../mall-portal/src/main/java/com/macro/mall/portal/repository/MemberProductCollectionRepository.java)。

**修改**：[MemberCollectionServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/MemberCollectionServiceImpl.java)、[MemberProductCollectionController.java](../mall-portal/src/main/java/com/macro/mall/portal/controller/MemberProductCollectionController.java)、[MemberCollectionService.java](../mall-portal/src/main/java/com/macro/mall/portal/service/MemberCollectionService.java)、[MemberProductCollectionRepository.java](../mall-portal/src/main/java/com/macro/mall/portal/repository/MemberProductCollectionRepository.java)。

**创建**：测试 `SDC/E6/src/test/java/com/macro/mall/sdc/e6/SDCE605Test.java`；说明 `SDC/E6/outputs/E6-05/说明.md`，本题证据 `SDC/E6/outputs/E6-05/evidence/`。测试包名 `com.macro.mall.sdc.e6`。

**新旧接口**：旧 `list(Integer pageNum, Integer pageSize)` 保留并委托新增 `list(Integer pageNum, Integer pageSize, String keyword)`。现有GET `/member/productCollection/list` 接可选keyword；Repository新增 `Page<MemberProductCollection> findByMemberIdAndProductNameRegex(Long memberId, String pattern, Pageable pageable)`。pattern由 `Pattern.quote(keyword)` 生成，无大小写忽略标记；空关键字走原方法，查询与count均带会员／名称条件。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E6/outputs/E6-05/evidence`。
- [ ] 2. 新增可选商品快照名条件并保留旧列表方法；Mongo使用转义后的大小写敏感字面包含，条件与会员条件一起在分页前执行。
- [ ] 3. 把下表规格写为测试，先观察未实现时不满足；新入口可先建立可编译的最小签名，再使业务断言红灯，不能以缺方法编译失败冒充功能红灯。实现步骤2后同组转绿并回归旧调用。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 实际Mongo分页和总数要用隔离集成数据核实，不能仅比较Java内过滤列表。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 空关键字 | 旧结果与分页不变 |
| 名称包含"a.b"和"axb"，查"." | 只匹配字面点 |
| 匹配项在原列表第二页、pageSize=1 | 过滤后第一页能返回匹配项，总数正确 |
| 其他会员同名 | 不返回 |

**关键断言／实现片段**

```java
assertEquals(expectedTotal, page.getTotalElements());
assertEquals(expectedFirstId, page.getContent().get(0).getId());
```

**运行**

```sh
rtk proxy mvn -pl SDC/E6 -am -Dtest=SDCE605Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**集成验证**：增加SDCE605MongoTest，隔离Mongo中包含当前／其他会员、特殊字符及跨页数据；结束仅清理本题插入记录。

新增 `SDC/E6/src/test/java/com/macro/mall/sdc/e6/SDCE605MongoTest.java`，读取教师指定隔离Mongo配置；不在测试源码硬编码凭据。按选定测试运行，未提供环境时不能计该项已验证：

```sh
rtk proxy mvn -pl SDC/E6 -am -Dtest=SDCE605MongoTest -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e6-06"></a>

### E6-06 实现人气推荐批次去重

**依据**：[业务范围](教师候选靶点库.md#e6-06) · [E6评分](课程考核方案.md#rubric-e6)。

**读取**：[SmsHomeRecommendProductServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/SmsHomeRecommendProductServiceImpl.java)。

**修改**：[SmsHomeRecommendProductServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/SmsHomeRecommendProductServiceImpl.java)。

**创建**：测试 `mall-admin/src/test/java/com/macro/mall/sdc/SDCE606Test.java`；说明 `SDC/E6/outputs/E6-06/说明.md`，本题证据 `SDC/E6/outputs/E6-06/evidence/`。测试包名 `com.macro.mall.sdc`。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E6/outputs/E6-06/evidence`。
- [ ] 2. 使用保持首次顺序的标准集合按productId去重；保留首次记录和原默认状态／排序，累加Mapper实际插入数。
- [ ] 3. 把下表规格写为测试，先观察未实现时不满足；新入口可先建立可编译的最小签名，再使业务断言红灯，不能以缺方法编译失败冒充功能红灯。实现步骤2后同组转绿并回归旧调用。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 说明行为范围只在单请求、旧默认字段与异常语义兼容；不新增幂等平台／唯一性承诺。
- [ ] 6. 按本实验三个维度填一页说明，列实际源码行号、命令／结果和未验证范围；运行 `rtk git diff --check` 并检查修改范围，准备本人两分钟演示步骤，不自动提交或推送。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 空列表 | 0，无插入 |
| 商品7名称FIRST、7名称SECOND、8 | 只插FIRST的7及8，保持顺序 |
| 两条插入分别返回1、0 | 返回1而不是集合大小2 |
| Mapper抛异常 | 原样传播，不报告成功 |

**关键断言／实现片段**

```java
assertEquals(List.of("FIRST", "EIGHT"), insertedNames);
assertEquals(1, actualInsertedCount);
```

**运行**

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=SDCE606Test -Dsurefire.failIfNoSpecifiedTests=false test
```

<a id="plan-e7-01"></a>

### E7-01 我的订单查询与状态问答

**依据**：[业务范围](教师候选靶点库.md#e7-01) · [E7评分](课程考核方案.md#rubric-e7)。

**读取**：[OmsPortalOrderServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPortalOrderServiceImpl.java)、[OmsOrder.java](../mall-mbg/src/main/java/com/macro/mall/model/OmsOrder.java)、[LlmClient.java](../mall-portal/src/main/java/com/macro/mall/portal/llm/LlmClient.java)、[OmsPortalOrderService.java](../mall-portal/src/main/java/com/macro/mall/portal/service/OmsPortalOrderService.java)。

**修改**：[OmsPortalOrderServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPortalOrderServiceImpl.java)、[OmsPortalOrderService.java](../mall-portal/src/main/java/com/macro/mall/portal/service/OmsPortalOrderService.java)。

**创建**：测试 `SDC/E7/src/test/java/com/macro/mall/sdc/e7/SDCE701Test.java`；说明 `SDC/E7/outputs/E7-01/说明.md`，本题证据 `SDC/E7/outputs/E7-01/evidence/`。测试包名 `com.macro.mall.sdc.e7`。

**新增产品文件**：`mall-portal/src/main/java/com/macro/mall/portal/ai/order/OrderQuestionService.java`、`mall-portal/src/main/java/com/macro/mall/portal/ai/order/ExistingOrderQuestionService.java`、`mall-portal/src/main/java/com/macro/mall/portal/ai/order/LlmOrderQuestionService.java`。只为当前题新增，后续不建立通用平台。

**新增评测入口**：`SDC/E7/src/main/java/com/macro/mall/sdc/e7/CandidateE701Evaluation.java`；读取 `SDC/E7/evaluation/E7-01/acceptance.json` 与 `facts.json`，结果写到本题evidence。不得用ProductQueryService的商品ID比较器直接评本题。

**接口与数据契约（Java17）**：

```java
public interface OrderQuestionService {
    enum State { OK, NEEDS_INPUT, UNSUPPORTED, UNAVAILABLE, PARTIAL }
    Result query(Request input);
    record Request(String text, Integer status, java.time.Instant fromInclusive,
                   java.time.Instant untilExclusive, int pageNum, int pageSize) {}
    record OrderFact(long id, int status, java.time.Instant createdAt,
                     String logisticsCompany, String logisticsCode) {}
    record Result(State state, java.util.List<OrderFact> orders, long total, String message) {}
}
```

普通版接收结构化状态／区间；LLM版解析text并委托普通版。请求不包含memberId，会员来自既有上下文。状态与时间区间同时进入list查询条件，保留旧三参数list入口。

**模型输出协议**：

```json
{"action":"query","status":1,"period":"LAST_WEEK","fromDate":null,"throughDate":null}
```

action仅query/needs_input/unsupported；status为已有0—5或null；period为ANY/TODAY/YESTERDAY/LAST_WEEK/RANGE。RANGE仅提取明确的ISO日期，throughDate含当日；Java按Clock和ZoneId转换为左闭右开Instant，不能让模型计算时区偏移。其他period日期字段为空；不接受memberId或orderId、SQL／写操作。 其余字段、非法JSON和尾随文本均按非法模型输出处理；沿用项目现有Jackson及LLM边界做法，业务错误与模型不可用分开。示例是开发协议说明，不是独立验收题。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E7/outputs/E7-01/evidence`。
- [ ] 2. 实现OrderQuestionService普通／LLM两版；LLM仅解析状态和日期，Java提供Clock/ZoneId、换算周界并在会员过滤内查询，筛选先于分页。保留旧list入口。
- [ ] 3. 在开发题及人工事实上先完成普通实现，再由LLM解析有限字段并委托同一确定性处理；用替身验证合法、非法、不可用状态。独立题集准备与实现按上文分开。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 正常题核对状态、时间区间、订单ID集合及分页总数；普通表单处理相同语义而非把它当语言理解金标。
- [ ] 6. 使用教师配置的真实模型与独立验收集执行评测入口，保存两种实现结果、失败样例及耗时；按预定阈值给结论。离线通过或没有独立题集时只能标记相应部分完成。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 固定周一参考时刻，"上周付过钱还没寄的订单" | 待发货＋上周一至本周一左闭右开；ID集合及total与普通结构化输入一致 |
| 含糊"没发货" | 追问，不扩大到未付款 |
| 其他会员／已删除／要求取消 | 不泄露、不写入 |
| 物流未记录 | 未知，不编到货日期 |

**关键断言／实现片段**

```java
assertEquals(expectedOrderIds, actualOrderIds);
assertEquals(expectedTotal, result.total());
```

**运行**

```sh
rtk proxy mvn -pl SDC/E7 -am -Dtest=SDCE701Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**真实评测**：先按教师环境页配置模型及本题所需隔离数据，再运行；该main必须实现前述双实现／结构化比对，不直接复用商品查询打分：

```sh
rtk proxy mvn -pl SDC/E7 -am test -DskipTests=true -Pe7-run -Dexec.mainClass=com.macro.mall.sdc.e7.CandidateE701Evaluation '-Dexec.args=SDC/E7/evaluation/E7-01/acceptance.json SDC/E7/outputs/E7-01/evidence/acceptance-result.json'
```

完成评测后补三维证据索引、可用范围和降级操作，运行 `rtk git diff --check`；模型未通过不代表可以删日志或改标准。

<a id="plan-e7-02"></a>

### E7-02 商品规格对比问答

**依据**：[业务范围](教师候选靶点库.md#e7-02) · [E7评分](课程考核方案.md#rubric-e7)。

**读取**：[PmsPortalProductServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/PmsPortalProductServiceImpl.java)、[PmsProductAttribute.java](../mall-mbg/src/main/java/com/macro/mall/model/PmsProductAttribute.java)、[PmsProductAttributeValue.java](../mall-mbg/src/main/java/com/macro/mall/model/PmsProductAttributeValue.java)、[LlmClient.java](../mall-portal/src/main/java/com/macro/mall/portal/llm/LlmClient.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `SDC/E7/src/test/java/com/macro/mall/sdc/e7/SDCE702Test.java`；说明 `SDC/E7/outputs/E7-02/说明.md`，本题证据 `SDC/E7/outputs/E7-02/evidence/`。测试包名 `com.macro.mall.sdc.e7`。

**新增产品文件**：`mall-portal/src/main/java/com/macro/mall/portal/ai/comparison/ProductComparisonService.java`、`mall-portal/src/main/java/com/macro/mall/portal/ai/comparison/ExistingProductComparisonService.java`、`mall-portal/src/main/java/com/macro/mall/portal/ai/comparison/LlmProductComparisonService.java`。只为当前题新增，后续不建立通用平台。

**新增评测入口**：`SDC/E7/src/main/java/com/macro/mall/sdc/e7/CandidateE702Evaluation.java`；读取 `SDC/E7/evaluation/E7-02/acceptance.json` 与 `facts.json`，结果写到本题evidence。不得用ProductQueryService的商品ID比较器直接评本题。

**接口与数据契约（Java17）**：

```java
public interface ProductComparisonService {
    enum State { OK, NEEDS_INPUT, UNSUPPORTED, UNAVAILABLE, PARTIAL }
    Result compare(Request input);
    record Request(long leftId, long rightId, Long leftSkuId, Long rightSkuId,
                   String text, java.util.List<Long> attributeIds) {}
    record Value(long productId, Long skuId, long attributeId, String raw,
                 String normalized, String sourceField) {}
    record Row(long attributeId, Value left, Value right, String relation) {}
    record Result(State state, java.util.List<Row> rows, java.util.List<String> questions) {}
}
```

普通版按勾选属性比较，LLM版只将问题映射为候选attributeIds并委托。raw和sourceField来自当前可见快照，normalized按给定规则计算；不能从品牌常识补数据。

**模型输出协议**：

```json
{"action":"compare","attributeIds":[7,8]}
```

action仅compare/needs_input/unsupported；最多三个候选属性ID，不能输出商品ID、SKU选择或比较数值；Java校验当前快照、SKU和单位。比较只基于字段事实，relation由Java确定。 其余字段、非法JSON和尾随文本均按非法模型输出处理；沿用项目现有Jackson及LLM边界做法，业务错误与模型不可用分开。示例是开发协议说明，不是独立验收题。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E7/outputs/E7-02/evidence`。
- [ ] 2. 实现ProductComparisonService两版；先校验两商品当前可见性，确定SKU／属性候选，LLM仅对齐最多3个属性，Java按明确单位规则比较。
- [ ] 3. 在开发题及人工事实上先完成普通实现，再由LLM解析有限字段并委托同一确定性处理；用替身验证合法、非法、不可用状态。独立题集准备与实现按上文分开。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 按属性ID、商品／SKU归属、规范值和未知状态核对；普通勾选属性与LLM语言输入使用同一快照和比较逻辑。
- [ ] 6. 使用教师配置的真实模型与独立验收集执行评测入口，保存两种实现结果、失败样例及耗时；按预定阈值给结论。离线通过或没有独立题集时只能标记相应部分完成。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 两商品容量1024MB与1GB，教师规则1GB=1024MB | 数值等价并附字段依据；不把此教学单位规则说成所有领域约定 |
| 内存含糊、多SKU未选、缺属性 | 追问或未知，不能把缺失说成不支持 |
| 下架／删除ID、名称中含指令 | 不绕过可见性，不改变业务指令 |

**关键断言／实现片段**

```java
assertEquals(expectedAttributeIds, alignedAttributeIds);
assertEquals(expectedNormalizedValues, comparedValues);
```

**运行**

```sh
rtk proxy mvn -pl SDC/E7 -am -Dtest=SDCE702Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**真实评测**：先按教师环境页配置模型及本题所需隔离数据，再运行；该main必须实现前述双实现／结构化比对，不直接复用商品查询打分：

```sh
rtk proxy mvn -pl SDC/E7 -am test -DskipTests=true -Pe7-run -Dexec.mainClass=com.macro.mall.sdc.e7.CandidateE702Evaluation '-Dexec.args=SDC/E7/evaluation/E7-02/acceptance.json SDC/E7/outputs/E7-02/evidence/acceptance-result.json'
```

完成评测后补三维证据索引、可用范围和降级操作，运行 `rtk git diff --check`；模型未通过不代表可以删日志或改标准。

<a id="plan-e7-03"></a>

### E7-03 退货材料整理与缺项提示

**依据**：[业务范围](教师候选靶点库.md#e7-03) · [E7评分](课程考核方案.md#rubric-e7)。

**读取**：[OmsOrderReturnApplyParam.java](../mall-portal/src/main/java/com/macro/mall/portal/domain/OmsOrderReturnApplyParam.java)、[OmsPortalOrderReturnApplyServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPortalOrderReturnApplyServiceImpl.java)、[OmsOrderReturnApply.java](../mall-mbg/src/main/java/com/macro/mall/model/OmsOrderReturnApply.java)、[OmsPortalOrderServiceImpl.java](../mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPortalOrderServiceImpl.java)、[LlmClient.java](../mall-portal/src/main/java/com/macro/mall/portal/llm/LlmClient.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `SDC/E7/src/test/java/com/macro/mall/sdc/e7/SDCE703Test.java`；说明 `SDC/E7/outputs/E7-03/说明.md`，本题证据 `SDC/E7/outputs/E7-03/evidence/`。测试包名 `com.macro.mall.sdc.e7`。

**新增产品文件**：`mall-portal/src/main/java/com/macro/mall/portal/ai/returns/ReturnMaterialService.java`、`mall-portal/src/main/java/com/macro/mall/portal/ai/returns/ExistingReturnMaterialService.java`、`mall-portal/src/main/java/com/macro/mall/portal/ai/returns/LlmReturnMaterialService.java`。只为当前题新增，后续不建立通用平台。

**新增评测入口**：`SDC/E7/src/main/java/com/macro/mall/sdc/e7/CandidateE703Evaluation.java`；读取 `SDC/E7/evaluation/E7-03/acceptance.json` 与 `facts.json`，结果写到本题evidence。不得用ProductQueryService的商品ID比较器直接评本题。

**接口与数据契约（Java17）**：

```java
public interface ReturnMaterialService {
    enum State { OK, NEEDS_INPUT, UNSUPPORTED, UNAVAILABLE, PARTIAL }
    Result suggest(Request input);
    record ItemContext(long orderId, long itemId, int maxQuantity,
                       java.util.Set<Long> allowedReasonIds, boolean proofRequired,
                       java.util.Set<String> uploadedProofIds) {}
    record Request(ItemContext context, String text, Long reasonId, Integer quantity) {}
    record Result(State state, Long reasonId, Integer quantity, String description,
                  java.util.Map<String,String> evidence, java.util.Set<String> missingFields) {}
}
```

ItemContext只能由普通流程对当前会员订单项校验后提供，或来自明确标记的教师夹具；不直接绑定外部HTTP JSON为可信context。uploadedProofIds来自该流程，不从text推断；描述可改写，事实字段及依据必须受原文支持。返回建议，不保存、不审批。

**模型输出协议**：

```json
{"action":"suggest","reasonId":7,"quantity":1,"description":"一双鞋底开胶","evidence":{"reasonId":"鞋底开胶","quantity":"只退这一双"},"missingFields":[]}
```

action仅suggest/needs_input/unsupported；原因必须在上下文白名单，数量未确定为null，不能输出身份、订单、价格／退款额、附件已上传等决定。附件状态来自可信context；model生成的缺项集合要结合确定性检查形成最终结果。 其余字段、非法JSON和尾随文本均按非法模型输出处理；沿用项目现有Jackson及LLM边界做法，业务错误与模型不可用分开。示例是开发协议说明，不是独立验收题。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E7/outputs/E7-03/evidence`。
- [ ] 2. 实现ReturnMaterialService两版；从已授权订单项及真实附件状态形成上下文，LLM只给原因候选、数量、描述依据和缺项，不调用退货create。
- [ ] 3. 在开发题及人工事实上先完成普通实现，再由LLM解析有限字段并委托同一确定性处理；用替身验证合法、非法、不可用状态。独立题集准备与实现按上文分开。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 正常题按原因ID、数量、需追问字段及证据片段是否支持核对，不按润色文本字面相等；附件规则来自教师规格。
- [ ] 6. 使用教师配置的真实模型与独立验收集执行评测入口，保存两种实现结果、失败样例及耗时；按预定阈值给结论。离线通过或没有独立题集时只能标记相应部分完成。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 买两双，只退开胶一双，原因白名单含质量 | quantity=1、原因质量、依据来自原文 |
| 不知道哪件／数量矛盾 | 追问，不猜订单项 |
| 文字说有照片但真实附件为空 | 仍提示附件未上传（本题规则要求凭证时） |
| 要求直接退款／模型给memberId、金额或create指令 | 拒绝越界字段，不调用写服务 |

**关键断言／实现片段**

```java
verifyNoInteractions(returnApplyWriter);
assertEquals(1, result.quantity());
```

**运行**

```sh
rtk proxy mvn -pl SDC/E7 -am -Dtest=SDCE703Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**真实评测**：先按教师环境页配置模型及本题所需隔离数据，再运行；该main必须实现前述双实现／结构化比对，不直接复用商品查询打分：

```sh
rtk proxy mvn -pl SDC/E7 -am test -DskipTests=true -Pe7-run -Dexec.mainClass=com.macro.mall.sdc.e7.CandidateE703Evaluation '-Dexec.args=SDC/E7/evaluation/E7-03/acceptance.json SDC/E7/outputs/E7-03/evidence/acceptance-result.json'
```

完成评测后补三维证据索引、可用范围和降级操作，运行 `rtk git diff --check`；模型未通过不代表可以删日志或改标准。

<a id="plan-e7-04"></a>

### E7-04 评论主题分类

**依据**：[业务范围](教师候选靶点库.md#e7-04) · [E7评分](课程考核方案.md#rubric-e7)。

**读取**：[PmsComment.java](../mall-mbg/src/main/java/com/macro/mall/model/PmsComment.java)、[LlmClient.java](../mall-portal/src/main/java/com/macro/mall/portal/llm/LlmClient.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `SDC/E7/src/test/java/com/macro/mall/sdc/e7/SDCE704Test.java`；说明 `SDC/E7/outputs/E7-04/说明.md`，本题证据 `SDC/E7/outputs/E7-04/evidence/`。测试包名 `com.macro.mall.sdc.e7`。

**新增产品文件**：`mall-portal/src/main/java/com/macro/mall/portal/ai/comments/CommentTopicService.java`、`mall-portal/src/main/java/com/macro/mall/portal/ai/comments/ExistingCommentTopicService.java`、`mall-portal/src/main/java/com/macro/mall/portal/ai/comments/LlmCommentTopicService.java`。只为当前题新增，后续不建立通用平台。

**新增评测入口**：`SDC/E7/src/main/java/com/macro/mall/sdc/e7/CandidateE704Evaluation.java`；读取 `SDC/E7/evaluation/E7-04/acceptance.json` 与 `facts.json`，结果写到本题evidence。不得用ProductQueryService的商品ID比较器直接评本题。

**接口与数据契约（Java17）**：

```java
public interface CommentTopicService {
    enum State { OK, NEEDS_INPUT, UNSUPPORTED, UNAVAILABLE, PARTIAL }
    Result classify(String text);
    enum Topic { QUALITY, LOGISTICS, AFTER_SALES, OTHER }
    record Result(State state, java.util.Map<Topic,String> evidence) {}
}
```

标签按被讨论的主题，而非情感好坏；无可判断主题返回NEEDS_INPUT及空集合，不将OTHER当任意垃圾文本的默认成功。模型和普通规则共用允许标签及片段校验。

**模型输出协议**：

```json
{"action":"classify","topics":[{"label":"QUALITY","evidence":"质量没问题"},{"label":"LOGISTICS","evidence":"快递太慢"}]}
```

action仅classify/needs_input/unsupported；label来自接口Topic，标签不可重复，依据必须是原文连续片段。原文片段存在不代表语义一定正确，主题正确性由独立标签检验。 其余字段、非法JSON和尾随文本均按非法模型输出处理；沿用项目现有Jackson及LLM边界做法，业务错误与模型不可用分开。示例是开发协议说明，不是独立验收题。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E7/outputs/E7-04/evidence`。
- [ ] 2. 实现CommentTopicService两版；普通关键词规则与LLM都返回主题集合及逐主题原文片段，Java统一检查允许标签／依据与明确状态。
- [ ] 3. 在开发题及人工事实上先完成普通实现，再由LLM解析有限字段并委托同一确定性处理；用替身验证合法、非法、不可用状态。独立题集准备与实现按上文分开。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 独立人工多标签为真值，20正常题以标签集合精确匹配且逐标签证据支持计分；额外F1可报告但不替换既定阈值。
- [ ] 6. 使用教师配置的真实模型与独立验收集执行评测入口，保存两种实现结果、失败样例及耗时；按预定阈值给结论。离线通过或没有独立题集时只能标记相应部分完成。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| "质量没问题，但快递太慢" | 按主题是否被讨论标注QUALITY和LOGISTICS；不把主题当情感极性 |
| 同时讨论质量／售后 | 多标签，不强行单选 |
| "好评"等无主题依据、空文本 | 不确定 |
| 未知标签、伪造引文、正文内指令 | 非法结果不进入业务；不删评改分 |

**关键断言／实现片段**

```java
assertEquals(Set.of(Topic.QUALITY, Topic.LOGISTICS), result.evidence().keySet());
```

**运行**

```sh
rtk proxy mvn -pl SDC/E7 -am -Dtest=SDCE704Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**真实评测**：先按教师环境页配置模型及本题所需隔离数据，再运行；该main必须实现前述双实现／结构化比对，不直接复用商品查询打分：

```sh
rtk proxy mvn -pl SDC/E7 -am test -DskipTests=true -Pe7-run -Dexec.mainClass=com.macro.mall.sdc.e7.CandidateE704Evaluation '-Dexec.args=SDC/E7/evaluation/E7-04/acceptance.json SDC/E7/outputs/E7-04/evidence/acceptance-result.json'
```

完成评测后补三维证据索引、可用范围和降级操作，运行 `rtk git diff --check`；模型未通过不代表可以删日志或改标准。

<a id="plan-e7-05"></a>

### E7-05 商品属性结构化提取

**依据**：[业务范围](教师候选靶点库.md#e7-05) · [E7评分](课程考核方案.md#rubric-e7)。

**读取**：[PmsProductAttribute.java](../mall-mbg/src/main/java/com/macro/mall/model/PmsProductAttribute.java)、[PmsProductAttributeValue.java](../mall-mbg/src/main/java/com/macro/mall/model/PmsProductAttributeValue.java)、[LlmClient.java](../mall-portal/src/main/java/com/macro/mall/portal/llm/LlmClient.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `SDC/E7/src/test/java/com/macro/mall/sdc/e7/SDCE705Test.java`；说明 `SDC/E7/outputs/E7-05/说明.md`，本题证据 `SDC/E7/outputs/E7-05/evidence/`。测试包名 `com.macro.mall.sdc.e7`。

**新增产品文件**：`mall-portal/src/main/java/com/macro/mall/portal/ai/attributes/AttributeExtractionService.java`、`mall-portal/src/main/java/com/macro/mall/portal/ai/attributes/ExistingAttributeExtractionService.java`、`mall-portal/src/main/java/com/macro/mall/portal/ai/attributes/LlmAttributeExtractionService.java`。只为当前题新增，后续不建立通用平台。

**新增评测入口**：`SDC/E7/src/main/java/com/macro/mall/sdc/e7/CandidateE705Evaluation.java`；读取 `SDC/E7/evaluation/E7-05/acceptance.json` 与 `facts.json`，结果写到本题evidence。不得用ProductQueryService的商品ID比较器直接评本题。

**接口与数据契约（Java17）**：

```java
public interface AttributeExtractionService {
    enum State { OK, NEEDS_INPUT, UNSUPPORTED, UNAVAILABLE, PARTIAL }
    Result extract(Request input);
    record AttributeSpec(long id, String name, java.util.Set<String> allowedValues, String unit) {}
    record Request(java.util.List<AttributeSpec> attributes, String text) {}
    record Value(long attributeId, String value, String evidence) {}
    record Result(State state, java.util.List<Value> values, java.util.Set<Long> unknownIds) {}
}
```

普通版字面匹配，LLM版语义抽取；属性定义由教学或普通业务上下文提供。输入未给的属性不补造，模型未知ID拒绝，允许值和单位按同一规则。

**模型输出协议**：

```json
{"action":"extract","values":[{"attributeId":7,"value":"128G","evidence":"128G"}],"unknownIds":[8]}
```

action仅extract/needs_input/unsupported；ID来自输入白名单，值经过Java按事先规定的单位／别名规则规范化再查允许值；原文未支持则未知。不能带持久化字段或写入动作。 其余字段、非法JSON和尾随文本均按非法模型输出处理；沿用项目现有Jackson及LLM边界做法，业务错误与模型不可用分开。示例是开发协议说明，不是独立验收题。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E7/outputs/E7-05/evidence`。
- [ ] 2. 实现AttributeExtractionService两版；输入白名单与人工描述，输出属性ID／值／原文依据和未知项；普通字面提取与LLM复用同一允许值检查。
- [ ] 3. 在开发题及人工事实上先完成普通实现，再由LLM解析有限字段并委托同一确定性处理；用替身验证合法、非法、不可用状态。独立题集准备与实现按上文分开。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 20正常题以规范化后的属性ID—值集合、未知集合和依据支持核对；不允许调用生成Mapper保存商品。
- [ ] 6. 使用教师配置的真实模型与独立验收集执行评测入口，保存两种实现结果、失败样例及耗时；按预定阈值给结论。离线通过或没有独立题集时只能标记相应部分完成。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 白名单颜色红/蓝、容量128/256GB；描述"红色，128G" | 按给定G=GB映射得到红与128GB，附原文 |
| 未提容量 | 未知，不按品牌补造 |
| 合法JSON但ID未知／值不在白名单 | 拒绝或明确未知，不保存实体 |
| 不同单位／全角数字 | 只按事先规则规范化，未定义则追问 |

**关键断言／实现片段**

```java
assertEquals(expectedValuesById, valuesById);
verifyNoInteractions(productWriter);
```

**运行**

```sh
rtk proxy mvn -pl SDC/E7 -am -Dtest=SDCE705Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**真实评测**：先按教师环境页配置模型及本题所需隔离数据，再运行；该main必须实现前述双实现／结构化比对，不直接复用商品查询打分：

```sh
rtk proxy mvn -pl SDC/E7 -am test -DskipTests=true -Pe7-run -Dexec.mainClass=com.macro.mall.sdc.e7.CandidateE705Evaluation '-Dexec.args=SDC/E7/evaluation/E7-05/acceptance.json SDC/E7/outputs/E7-05/evidence/acceptance-result.json'
```

完成评测后补三维证据索引、可用范围和降级操作，运行 `rtk git diff --check`；模型未通过不代表可以删日志或改标准。

<a id="plan-e7-06"></a>

### E7-06 退货原因归类与批次分析

**依据**：[业务范围](教师候选靶点库.md#e7-06) · [E7评分](课程考核方案.md#rubric-e7)。

**读取**：[OmsOrderReturnApplyServiceImpl.java](../mall-admin/src/main/java/com/macro/mall/service/impl/OmsOrderReturnApplyServiceImpl.java)、[OmsOrderReturnApply.java](../mall-mbg/src/main/java/com/macro/mall/model/OmsOrderReturnApply.java)、[LlmClient.java](../mall-portal/src/main/java/com/macro/mall/portal/llm/LlmClient.java)。

**修改**：本题没有列出的既有产品文件不得改动；E1/E5只新增观察与分析，E7按下方列出的新业务文件实施。

**创建**：测试 `SDC/E7/src/test/java/com/macro/mall/sdc/e7/SDCE706Test.java`；说明 `SDC/E7/outputs/E7-06/说明.md`，本题证据 `SDC/E7/outputs/E7-06/evidence/`。测试包名 `com.macro.mall.sdc.e7`。

**新增产品文件**：`mall-portal/src/main/java/com/macro/mall/portal/ai/reasons/ReturnReasonBatchService.java`、`mall-portal/src/main/java/com/macro/mall/portal/ai/reasons/ExistingReturnReasonBatchService.java`、`mall-portal/src/main/java/com/macro/mall/portal/ai/reasons/LlmReturnReasonBatchService.java`。只为当前题新增，后续不建立通用平台。

**新增评测入口**：`SDC/E7/src/main/java/com/macro/mall/sdc/e7/CandidateE706Evaluation.java`；读取 `SDC/E7/evaluation/E7-06/acceptance.json` 与 `facts.json`，结果写到本题evidence。不得用ProductQueryService的商品ID比较器直接评本题。

**接口与数据契约（Java17）**：

```java
public interface ReturnReasonBatchService {
    enum State { OK, NEEDS_INPUT, UNSUPPORTED, UNAVAILABLE, PARTIAL }
    Result analyze(java.util.List<Item> input);
    enum Label { QUALITY, LOGISTICS, SIZE_SPEC, PREFERENCE, OTHER, UNCERTAIN }
    record Item(String anonymousId, String category, String reason, String description) {}
    record Classified(String anonymousId, Label label, String evidence, boolean failed) {}
    record Result(State state, java.util.List<Classified> items,
                  java.util.Map<Label,Integer> counts,
                  java.util.Map<Label,java.math.BigDecimal> ratios,
                  int denominator, int failedCount) {}
}
```

分类失败记录failed=true、label和依据可空；计数只含非失败记录，但比例分母始终为输入数。有失败返回PARTIAL并列失败数，空批返回NEEDS_INPUT。数量精确、比例4位小数HALF_UP，不能丢失败或用模型做求和。

**模型输出协议**：

```json
{"action":"classify","anonymousId":"r1","label":"QUALITY","evidence":"鞋底开胶"}
```

首版逐条调用、逐条记录结果，不并发、不自动重试；一次只发送一条脱敏事实。ID必须等于当前输入ID；未知标签、错ID或调用失败只标记该条failed，不能忽略。Java汇总全批，每个输入ID恰有一条结果。输入重复ID在调用模型前明确拒绝；模型回错／重复旧ID使当前条失败。正常评测批次用1—3条人工记录，10条含失败场景可离线验证。不得让模型生成counts/ratios。 其余字段、非法JSON和尾随文本均按非法模型输出处理；沿用项目现有Jackson及LLM边界做法，业务错误与模型不可用分开。示例是开发协议说明，不是独立验收题。

**操作顺序**

- [ ] 1. 读取本题评分、业务卡与上述源码，记录实际入口／调用者／数据流及Git起点；创建 `SDC/E7/outputs/E7-06/evidence`。
- [ ] 2. 实现ReturnReasonBatchService两版；普通结构化原因映射与LLM单主因分类，Java按匿名ID核对一进一出并计算本批数量／比例。
- [ ] 3. 在开发题及人工事实上先完成普通实现，再由LLM解析有限字段并委托同一确定性处理；用替身验证合法、非法、不可用状态。独立题集准备与实现按上文分开。
- [ ] 4. 执行下方本题命令，确认确实执行所选测试且不是0项；将必要日志放入本题evidence，不混用教师历史结果。
- [ ] 5. 20正常批次分别以独立人工标注的编号—单主因集合和统计量严格核对；另测失败批次的总数、分母和四位小数HALF_UP比例，不得用普通原因映射当语义真值。
- [ ] 6. 使用教师配置的真实模型与独立验收集执行评测入口，保存两种实现结果、失败样例及耗时；按预定阈值给结论。离线通过或没有独立题集时只能标记相应部分完成。

**必须覆盖的输入与期望**

| 输入／场景 | 应观察或证明的结果 |
|---|---|
| 10条输入，8条确定＋1条不确定＋1条失败 | 报告本批10，计数9加失败1＝10；分母10，不丢失败项 |
| 多个原因 | 按教师主因规则或不确定，不自行改为多标签 |
| 重复／缺失编号、含注入文本 | 标识不完整则整批未完成，不把小计当全量 |
| 一页样本 | 标题明确本批，不宣称全月／全站 |

**关键断言／实现片段**

```java
assertEquals(inputCount, classifiedCount + failedCount);
assertEquals(inputCount, result.denominator());
```

**运行**

```sh
rtk proxy mvn -pl SDC/E7 -am -Dtest=SDCE706Test -Dsurefire.failIfNoSpecifiedTests=false test
```

**真实评测**：先按教师环境页配置模型及本题所需隔离数据，再运行；该main必须实现前述双实现／结构化比对，不直接复用商品查询打分：

```sh
rtk proxy mvn -pl SDC/E7 -am test -DskipTests=true -Pe7-run -Dexec.mainClass=com.macro.mall.sdc.e7.CandidateE706Evaluation '-Dexec.args=SDC/E7/evaluation/E7-06/acceptance.json SDC/E7/outputs/E7-06/evidence/acceptance-result.json'
```

完成评测后补三维证据索引、可用范围和降级操作，运行 `rtk git diff --check`；模型未通过不代表可以删日志或改标准。

## 执行前后自检

- 编号必须是当前候选：E2-05、E3-05、E4-05已退役；新编号分别为E2-07、E3-07、E4-07，不沿用旧日志。
- 源码说明、测试数据、断言和结论必须指向同一题。E2-02与E4-03、E2-03与E4-06有代码范围交叉，不能用同一个补丁重复提交；E5/E6配对允许复用分析，但产出与评分不同。
- 每题输出说明中的三维证据与本实验子项逐一对应；缺失则标明具体未完成项。记录工具／AI身份与本人工作，不伪造独立性、人工审查或录屏。
- 最终报告区分“源码存在”“设计适配”“已运行观察”“实现通过”“真实环境／模型通过”。只有实际执行的结果能写为通过。

本文件制定的是42项产出计划；本轮未执行这些产品实现。新E3-07只有独立服务观察证据，不能当作其修复已完成。
