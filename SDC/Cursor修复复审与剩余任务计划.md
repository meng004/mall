# Cursor修复复审与剩余任务计划

2026-10-04，第二轮复审。比较`af284e3..1dfe421`，集成目录为`/Users/limeng/软件设计与规范AI/.worktrees/sdc-integrated`。执行原则：**Ponytail full，已证实修好的不重做，复用原有代码和测试，不新增平台、hash、冻结contract或并行验收体系。**

## 结论与本次验证

**本轮修复确有实质进展，测试计数基本实事求是；“模型没有放行”是正确结论。仍不能收为全部完成：E7比较器、回放归因和数据接线验证尚有缺口。缺facts导入、未启动portal属于可继续执行的工作，不应全部归为等待用户的外部阻塞。**

| 项目 | 本次核实 | 证据边界 |
|---|---|---|
| 属性事务、空删除、小额优惠券 | 已在`1dfe421`，实现最小且正确。真实MyBatis、Spring事务代理与独立JDBC读回，非Mock伪装持久化 | 未保存“修复前真实库部分提交”红灯；现有红灯证明缺事务声明，修复后数据库回滚有效。不要把二者改写成已有完整数据库前后对照 |
| 回归计数 | 本次复跑257项，失败0、错误0、跳过2，即255项执行通过 | admin78、portal43、E1 3、E2 3、E3 21、E4 6、E5 3、E6 25、E7 75。包含教学MySQL测试，宜称“无在线模型回归”，不是纯无外部依赖单测 |
| Mongo | 两项另跑均1/0/0、跳过0。专用`sdc_review_UUID`库，按本次ID清理 | 原固定会员误删共享库风险已解除；本轮成功日志已持久保存 |
| 覆盖率 | XML与文本一致：delete32/32、21/22；update34/35、20/22；listCart16/16、8/8，分类/商品策略各8/8分支 | 本轮核对已有JaCoCo原件，未另采集覆盖率；覆盖率不证明E7评分逻辑正确 |
| 环境 | 四个教学容器运行；13306/27018连通；18085拒绝连接 | E1缺应用启动，不是中间件无法启动 |
| E7真实库 | CandidateDatabaseTest取得两个真实Bean | 不等于已验证订单、商品、分页、身份和facts一致 |
| 最小模型请求14秒pong | 当前持久化成果目录未找到该次命令、输出及耗时记录；本次未再调用在线模型 | 作为执行者报告保留，未独立证实。检索到的`/tmp/sdc-cursor-smoke.log`属于10月3日旧运行，不能挪作本轮证据 |

本次原始日志、探针与公开案例摘录见[审查证据](evidence/cursor-review-round2/README.md)。未读取未公开验收答案，未调用真实模型；产品和既有证据最终保持评审前内容。

## Standards：诚实性与可追溯性发现

### S1 / P2：回放分母和失败归因仍不准确

- E704 `CandidateE704Evaluation.java:199–205`把无modelOutput都当不可重算。公开A02是空输入、模型调用0次、确定性返回NEEDS_INPUT；本地即可重算。当前24/29少计这一项，在其他判定不变时应为25/30，另2项待人工；这不是新的32题模型成绩。
- E706 `CandidateE706Evaluation.java:312–334`未保留历史传输错误。公开N12的原记录为60秒超时、输出`[""]`，回放按空JSON评为业务失败并计入分母。因此1/3不能解释为业务正确率；9项待人工也不是9项模型错误。
- `CandidateE704/705/706Evaluation.verdict`仍可能给REJECT，而`EvaluationSupport.decision`给“不放行”。实际探针：20正常全过、12边界过11、无安全违规，同次统计输出两种不同语义。未完成/待核对/质量不足不能等同越权驳回。

### S2 / P2：普通测试会覆盖已提交的回放证据

`CandidateEvaluationTest.java:93–119`将回放输出固定写入`outputs/E7-04/05/06/evidence/review-fix/replay.json`。本次常规Maven复跑确实使三个已提交文件变脏，包括时间和耗时变化。测试应写JUnit临时目录；显式评测命令才生成交付证据。

本次已将新生成的三个文件另存审查目录，并只恢复本次测试改动的三个文件为运行前已确认干净的HEAD内容；原证据未被本轮评审永久替换。

### S3 / P2：索引仍主要指向历史分支与状态

主仓库`候选靶点批量验收结果.md`仅在末尾追加复验表，前面的“未完成”仍写小额缺陷未改、Mongo拒绝连接、无事务；路径还是非点击的worktree文字。历史可以保留，但必须标阶段，把最新状态与`1dfe421`产出作为首页入口。E7题目说明里的复验仍缺可直接点击的完整日志/报告映射。索引未提交已经如实披露，不构成伪造，却削弱可移交性。

## Spec：尚未履行的要求与实现问题

### F1 / P2：E7-06比较器接受分类与统计不一致

`CandidateE706Evaluation.java:383–397`只校验counts、ratios、总数之间的自洽，没有从items重新按标签计数。真实编译类探针：唯一item=QUALITY，而counts.LOGISTICS=1、ratio.LOGISTICS=1，`score`返回passed=true。这使错误汇总可能通过验收，须修比较器，而不是通过增加模型重跑来消除。

### F2 / P2：关键词规则仍被当成语义裁判

E704第38–42行、E706第98–107行借用普通路径关键词作最后裁决。探针：`客服确认缝线断开`、人工预期与输出均QUALITY，仍被判失败；`不是质量问题，是物流问题`、预期与输出均LOGISTICS，被判主因不唯一。关键词不能替代教师标注，也不能靠逐条加词逼近已公开答案。无足够证据时应待人工核对。

### F3 / P2：facts接线只有Bean证明，就绪判定过弱

- `CandidateDatabaseTest.java:13–20`只检查取Bean和`isolated=false`，没有真实订单/商品结果断言。
- `CandidateE701Evaluation.java:233–267`只要求当前返回ID与facts有交集。合成探针中facts要求2条，实际仅1条且状态错误，ready仍为true；E702第204行起仅检查可见性，没有核对规格值与SKU。不能将此标为facts已准备好。
- 两个`unreachableTeachingDatabaseStaysBlocked`测试根据真实数据库是否在线来skip。解释是诚实的，但这种条件跳过没有确定性覆盖断库分支；数据库在线时断库路径恰好不被验证。

### F4：剩余工作不是全部外部阻塞

E1打包启动与只读查询、E7公开开发facts导入、实际数据库调用断言、比较器与回放修复，均可在已有教学环境继续完成。真正需外部输入的是教师人工核对与本人录屏；新独立题集可由隔离准备会话先生成，不能伪称已经人工核对。

## 交给Cursor的剩余执行计划

> 使用`superpowers:executing-plans`按下面六项执行。依据还包括[上一轮修复计划](Cursor产出修复与复验计划.md)及[原42题计划](Cursor候选靶点产出计划.md)。以下覆盖上一计划中尚未完成的部分，不重复已经确认有效的属性、优惠券或Mongo修复。

### 共用约束

- 先读主仓库最新文档，再检查集成工作区`rtk git status --short`、`rtk git rev-parse HEAD`。起点应为`1dfe421`，若变化核对新增差异，不重置。
- 全部shell命令以rtk开头；使用Java17。本机可用`rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17 PATH=/opt/homebrew/opt/openjdk@17/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin mvn ...`指定版本。
- 并行最多2条代码线：A做任务1–3（共同修改E7比较器，串行）；B做任务4–5（数据与应用）。不同worktree，不同时操作同一个target；数据准备串行。主代理最后任务6。
- 产品/测试变更局限这些问题；不新增后台、通用评测平台或额外门禁。新证据另存`evidence/review-fix-2/`，不覆盖首次报告。历史版本用Git追溯，不新建冻结副本。
- 不推送、不发布、不购买额度；教学库只创建并清理本任务持有的行。保留用户其他改动。不在真实业务库试验，不全库删除，不为了收工降低阈值。

### 任务1：让回归测试不修改证据

**文件**：`SDC/E7/src/test/java/com/macro/mall/sdc/e7/CandidateEvaluationTest.java`。

- [ ] 用JUnit现有`@TempDir Path temporary`承接三个回放输出，例如`temporary.resolve(task + "-replay.json")`。测试仍读取已公开输入及首次报告，保留“原报告未改变”的断言。
- [ ] 运行本测试后用Git检查，三个已提交replay.json不得发生变化。不要删除时间字段或忽略整个evidence目录来掩盖副作用。

```sh
rtk proxy mvn -pl SDC/E7 -am -DskipTests=false -Dtest=CandidateEvaluationTest -Dsurefire.failIfNoSpecifiedTests=false test
rtk git status --short
```

**验收**：回放测试通过，原始报告与已提交回放都未被普通测试写入。

### 任务2：修复统计和语义评分，不扩充关键词库

**文件**：`SDC/E7/src/main/java/com/macro/mall/sdc/e7/CandidateE704Evaluation.java`、`CandidateE706Evaluation.java`；同包`CandidateEvaluationTest.java`。

- [ ] 将审查`ReviewScoreProbe`三个反例转为失败断言。统计案例期望false；两个语义案例有预先教师标注则通过，否则待核对，不能被关键词直接判业务失败。
- [ ] E706从actual.items中每个非失败项的标签独立计数，与actual.counts逐标签比较；比例由该计数/完整输入分母计算到4位HALF_UP，再核对ratios。不要直接复用产品aggregate生成期望，防止同错通过。

```java
var observedCounts = new java.util.EnumMap<Label, Integer>(Label.class);
for (var item : actual.items()) {
    if (!item.failed()) observedCounts.merge(item.label(), 1, Integer::sum);
}
// 每个Label：counts必须等于observedCounts；ratios必须等于独立按分母计算的值。
```

- [ ] 覆盖：QUALITY项却LOGISTICS计数、比率错误、失败项被丢弃、正常多条与空批。不增加生产计算层。
- [ ] E704/E706的expected仍来自独立标注；标签匹配与非空原文引用先作确定性检查。证据语义使用既有expected中教师认可的片段或其预先声明的可接受集合；遇到不在该集合的不同片段，可以待核对，不能无限加关键词或用LLM自判。
- [ ] 区分“明确错误”（错标签、凭空引文、错数量）与“尚不能判断”（同标签但替代引文未人工核对）。不得因词面含“客服”或否定句提到两个原因就推翻既有语义标注。

**验收**：错统计失败；否定/引用语义不被普通关键词强制判错；原有错属性/重复ID防护不回退。

### 任务3：修复回放归因与两套结论分歧

**文件**：`CandidateE704Evaluation.java`、`CandidateE705Evaluation.java`、`CandidateE706Evaluation.java`、`EvaluationSupport.java`；对应`CandidateEvaluationTest.java`。如统一六题verdict，修改其他三个现有入口，不新建结论框架。

- [ ] 在临时目录造三个公开开发历史行：空输入+0调用+NEEDS_INPUT；1调用+timeout+空响应；1调用+无error+非法JSON。三者必须分别判为可本地重算、传输未完成、协议失败。
- [ ] 回放先看历史调用次数/error，再决定能否重建模型输出；零调用短路走真实产品本地分支，传输错误保留oldError，不把空字符串伪装成一次模型回答。
- [ ] 固定报告分项：总题数、已执行、可评分通过/失败、待人工、传输未完成、未执行、不可重算。各项口径互斥并可加总；展示局部分数时同时显示完整题量，不把1/3说成全套准确率。
- [ ] 复用`EvaluationSupport`的现有结论方法作为单一判定来源：安全违规→驳回；缺环境/传输/必需人工未完成→未完成；完整但质量不足→不放行或有据降级；完整且达到既定阈值→放行。codeVerdict仅映射同一结果，不再独立推导另一结论。
- [ ] 测相同参数两种展示一致；20正常全过但12边界仅过11且无安全违规不得同时出现REJECT与“不放行”。对待核对、未执行、普通可用降级分别测试。
- [ ] 显式生成新的review-fix-2回放报告，保存原模型结果不变。E704公开A02应可重算；E706公开N12应保留传输超时，不能当模型业务错误。旧24/29、1/3不再作为当前有效质量结论。

```sh
rtk proxy mvn -pl SDC/E7 -am -DskipTests=false '-Dtest=CandidateEvaluationTest,SDCE701Test,SDCE702Test,SDCE703Test,SDCE704Test,SDCE705Test,SDCE706Test' -Dsurefire.failIfNoSpecifiedTests=false test
```

**验收**：上述三类历史输入归因正确，分母可查，所有入口结论同义；回放仍明确“非新盲测”。

### 任务4：完成E7-01/02事实数据与真实查询验证

**文件**：`CandidateDatabaseTest.java`、`SDCE701Test.java`、`SDCE702Test.java`、`CandidateE701Evaluation.java`、`CandidateE702Evaluation.java`、`EvaluationSupport.java`；数据沿用`SDC/E7/evaluation/E7-01/facts.json`和`E7-02/facts.json`，不读取expected生成返回值。

- [ ] 先用公开开发facts建立真实测试夹具。E701数据对应orders/memberScope/orderStatus/clock；E702对应catalog/snapshots中的商品、属性值和SKU。生成一份简单SQL或现有JUnit fixture即可，不开发通用导入平台。
- [ ] 导入前核对这些固定教学ID是否被已有数据占用。占用时不REPLACE、不覆盖；用独立教学库或重新分配开发夹具ID。正式独立facts由准备会话管理，不能只改expected迁就数据库。
- [ ] 在已有教学MySQL中事务性插入本次拥有的数据，按所属主键清理。日期按facts时区转时间戳；statusName通过现有状态码映射，deleted落到真实delete_status；商品可见性、属性/规格关系使用真实表。未读到facts字段不得默认填成“正常”。
- [ ] E701普通路径实际验证同会员未删除订单、他人隔离、删除订单排除、状态过滤、日期左闭右开和分页total。开发夹具至少21条本人订单，另有他人/删除/日期边界行；用pageSize=5跨页比对集合与total。只调用真实服务，不把facts包装成Mock返回值。
- [ ] E702实际验证可见/隐藏/删除商品、属性白名单、真实规格值与单位、SKU差异，普通比较结果要与facts人工规则一致；仅能getBean或visible=true不算完成。
- [ ] 更新现有ready方法，不能“一条ID相交即就绪”。使用本任务已建立的普通查询与事实核对结果识别缺行、错状态/时间/规格值；不再增加一层平行gate。`OrderFactsProbe`中的两条仅到一条、状态错误必须不就绪。验证不能仅检查首100条或跳过删除行泄漏。
- [ ] 断库测试使用现有接线函数的最小参数入口，在测试内提供确定失败的连接；不依赖真实服务在线就skip。先找已有可注入配置再改签名，只为本问题加必要参数，不造可插拔架构。
- [ ] `CandidateDatabaseTest`不再止于Bean存在，加入上述真实查询断言。缺教师DB时该集成测试可明确跳过/阻塞，确定性断库测试仍应执行；真实库就绪时二者都必须执行。
- [ ] 把预检异常的脱敏类型/原因保留到现有report，不把鉴权、配置解析、Bean启动、数据不齐都抹成同一句“缺环境”。使用Spring已有配置读取工具，不继续扩展手写YAML解析器。

```sh
rtk proxy mvn -pl SDC/E7 -am -DskipTests=false '-Dtest=CandidateDatabaseTest,SDCE701Test,SDCE702Test' -Dsurefire.failIfNoSpecifiedTests=false test
```

**验收**：真实普通路径全通过、事实对应可查、断库分支确定性覆盖；数据未齐时模型调用0。测试会员SecurityContext在finally清理，不改变产品鉴权策略。

### 任务5：完成E1应用启动与模型运行前的最小准备

依赖目前运行，不需要重建或删卷。工作目录集成树：

```sh
rtk proxy mvn -pl mall-portal -am -Ddocker.skip=true -DskipTests=true package
rtk proxy java -jar mall-portal/target/mall-portal-1.0-SNAPSHOT-exec.jar --spring.config.additional-location=file:./SDC/environment/application-sdc.yml --server.port=18085
```

另一个终端：

```sh
rtk proxy curl --noproxy '*' 'http://127.0.0.1:18085/product/search?keyword=HUAWEI%20P20&pageNum=1&pageSize=5'
```

- [ ] 保存本次启动日志、查询响应与命令；要求HTTP可达、业务code=200、list含商品26。仅打包成功或取Bean不算E1环境证明。记录本次启动进程，验证后仅停止自己启动的进程。
- [ ] 找回本轮14秒pong的实际原始记录；找不到就标未留存。若需重新探测，只走已授权入口的一个非业务最小请求，不批量扫模型，不将重跑冒称原记录。
- [ ] 按上一计划补足CLI失败诊断：现有适配器stderr仍被丢弃，保留脱敏且限长的错误摘要、退出码和耗时；复用现有进程替身测试，不增加日志系统。严格JSON与工具deny限制保留。
- [ ] 任务1–4通过后，再开展在线业务验收。新的独立准备会话只读规格/facts，生成20正常+8对抗+4分布外并标明人工核对状态；实现会话不读取未公开expected。教师缺席时可完成准备、离线与公开回归，正式人工核对仍阻塞。
- [ ] 六题按原计划各自入口执行；结果区分放行、降级、驳回与未完成。遇到模型额度/权限问题集中报告；不自动购买、不无限重试、不删失败样例。

**验收**：E1查询真实通过；每题在线与人工状态如实记录。没有人工核对/真实32题结果时不能宣称模型放行。

### 任务6：合并兼容修复、复验并整理唯一索引

- [ ] 合并A/B兼容改动，保留三个互斥分支。以下命令覆盖已存在测试和新用例；独立库必须就绪。

```sh
rtk proxy mvn -pl mall-admin,mall-portal,SDC/E1,SDC/E2,SDC/E3,SDC/E4,SDC/E5,SDC/E6,SDC/E7 -am -DskipTests=false '-Dtest=*Test,!SDCE*MongoTest' -Dsurefire.failIfNoSpecifiedTests=false test
rtk proxy mvn -pl SDC/E6 -am -DskipTests=false '-Dtest=SDCE604MongoTest,SDCE605MongoTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 记录实际tests/failures/errors/skipped；新的断库测试应执行，而不是保留“有库时跳过”的逻辑。不要求固定仍为257项。
- [ ] 测试前后核对Git，普通测试不能改变交付证据；新显式评测产物与源码分别检查。原始日志空白不修改，源码/文档`diff --check`通过后单独注明历史日志例外。
- [ ] 更新主仓库`SDC/候选靶点批量验收结果.md`为“当前结果在前、历史阶段在后”，当前行链接最新集成版本成果、具体命令和原始日志；旧分支证据只标历史。把本轮Mongo成功日志和E1响应放入持久目录，不仅留/tmp。
- [ ] 在本次专用本地分支提交本次修复与结果索引，逐文件选择，不把主仓库无关改动一起提交；未提交就明确记录，未推送不影响本地可追溯性。
- [ ] 最终列出已通过客观项、仍未完成项、所需人工输入。先做完facts导入与portal启动等自主工作，再报告人工/账号阻塞；不以“没有录屏”阻止代码验证，也不代造录屏。

**收工条件**：这里的可修复问题全部有失败反例和通过回归，真实查询已完成，证据不再被普通测试覆盖、回放归因可核查。模型可有据不放行；只有全部适用的客观验收完成才称该部分完成，人工材料独立列状态。
