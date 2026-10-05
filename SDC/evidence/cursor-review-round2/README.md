# 第二轮复审证据

2026-10-04，审查集成`1dfe421`，对照`af284e3`。全部文件为审查证据，不是后续修复通过记录。

## 本次真实复跑

工作目录`/Users/limeng/软件设计与规范AI/.worktrees/sdc-integrated`。Java17通过`rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17 PATH=/opt/homebrew/opt/openjdk@17/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin`指定；为访问本机教学库和HTTP测试替身，使用获准的本机执行权限。

```sh
rtk proxy mvn -o -pl mall-admin,mall-portal,SDC/E1,SDC/E2,SDC/E3,SDC/E4,SDC/E5,SDC/E6,SDC/E7 -am -DskipTests=false '-Dtest=*Test,!SDCE*MongoTest' -Dsurefire.failIfNoSpecifiedTests=false test
rtk proxy mvn -o -pl SDC/E6 -am -DskipTests=false '-Dtest=SDCE604MongoTest,SDCE605MongoTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

- [full-regression.log](full-regression.log)：退出0。257项，失败0、错误0、跳过2；255项执行通过。admin78、portal43、E1 3、E2 3、E3 21、E4 6、E5 3、E6 25、E7 75。跳过的是E701/E702的`unreachableTeachingDatabaseStaysBlocked`，数据库可连接时它们主动跳过。
- [mongo-regression.log](mongo-regression.log)：退出0。两项执行通过，失败/错误/跳过均0。运行前已核对测试用专用数据库，只清理本次插入ID。
- 回归实际触发MySQL事务/空删除/品牌数据测试，非纯离线单测；未调用在线模型、未启动18085业务服务。

## 回放测试的写入副作用

普通`CandidateEvaluationTest`将已提交的三个replay.json写成新一轮时间和耗时。运行前集成工作区干净，运行后只有这三个tracked文件变动。

本次生成内容独立保存：[E7-04](E7-04-replay-from-review-tests.json)、[E7-05](E7-05-replay-from-review-tests.json)、[E7-06](E7-06-replay-from-review-tests.json)。随后仅恢复这三个文件为本次运行前的HEAD字节，集成工作区重新干净，没有覆盖其他用户改动。后续计划要求改成JUnit临时输出，避免再次发生。

## 最小反例

- [ReviewScoreProbe.java](ReviewScoreProbe.java)直接调用当前真实评分代码，结果见[日志](ReviewScoreProbe.log)：分类QUALITY但LOGISTICS计数1仍通过；两个语义正确的标签因关键词规则判失败；同参数codeVerdict=REJECT而decision=不放行。
- [OrderFactsProbe.java](OrderFactsProbe.java)使用合成facts与可控外部查询返回，调用真实`teachingOrdersReady`，未连接数据库或模型。结果见[日志](OrderFactsProbe.log)：要求2条、仅返回1条且状态错误仍ready=true。它证明预检逻辑过弱，不证明实际教学库存在此数据。

这些探针退出0代表观察正常结束，**不代表反例通过业务验收**。后续必须将观察转换为正式失败断言。

已有E7 Surefire报告后复跑：

```sh
rtk proxy python3 /Users/limeng/软件设计与规范AI/mall/SDC/evidence/cursor-review-round2/run-probes.py /Users/limeng/软件设计与规范AI/.worktrees/sdc-integrated /opt/homebrew/opt/openjdk@17
```

脚本只读classpath，编译到临时目录，不修改产品或原报告，不读取未公开验收答案。

## 公开历史案例的归因

[published-replay-cases.json](published-replay-cases.json)逐字摘录集成版本公开model-eval/replay的相关行：E704 A02零调用空输入被误标不可重算，E706 N12原为传输超时而回放标业务错误。仅摘录已公开结果，未从新独立题集提取答案。

## 环境与来源限制

只读`docker compose ... ps`确认四个教学容器运行；正常本机权限TCP探测13306/27018连接成功、18085拒绝连接。没有为本次审查开启在线模型。

在持久化交付目录没有找到本轮14秒pong原始记录；发现的`/tmp/sdc-cursor-smoke.log`是10月3日旧运行，不作为本轮证据。未找到不等于证明未执行，应由执行者补原记录或明确缺失。

上轮探针中事务缺失、小额金额与空删除问题已修复，不重复将历史失败当当前缺陷。已有JaCoCo XML与本轮索引的覆盖率数字相符，本次没有另采集覆盖率。
