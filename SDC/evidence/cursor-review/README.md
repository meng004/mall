# 2026-10-04 产出评审证据

被审查集成版本：`af284e3`，基准`f352588`。本目录为审查观察，不是产品修复后的通过证明。

## 本次重跑

工作目录`/Users/limeng/软件设计与规范AI/.worktrees/sdc-integrated`，Java17。以下Maven命令实际通过`rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@17 PATH=/opt/homebrew/opt/openjdk@17/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin`指定运行环境。

```sh
rtk proxy mvn -o -pl mall-admin,mall-portal,SDC/E3,SDC/E4,SDC/E6,SDC/E7 -am -DskipTests=false '-Dtest=SDCE*Test,!SDCE*MongoTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

[rerun-207.log](rerun-207.log)：进程退出0，207项，失败/错误/跳过均0。模块计数依次70、43、15、5、23、51。

```sh
rtk proxy mvn -o -pl mall-admin,mall-portal,SDC/E1,SDC/E2,SDC/E3,SDC/E4,SDC/E5,SDC/E6,SDC/E7 -am -DskipTests=false '-Dtest=*Test,!SDCE*MongoTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

[full-offline-host.log](full-offline-host.log)：进程退出0，239项，失败/错误/跳过均0；admin70、portal43、E1 3、E2 3、E3 19、E4 6、E5 3、E6 25、E7 67。本轮未运行Mongo集成测试、真实MySQL测试或在线模型。

首次同命令在受限沙箱中出现本机HTTP监听及进程查询权限错误，1失败2错误，见[full-offline-sandbox.log](full-offline-sandbox.log)。随后取得正常本机执行权限，在未修改源码/测试的情况下通过。因此该次失败归因执行环境，不列为产品缺陷，也未删掉失败记录。

## 最小探针

[SpecProbe.java](SpecProbe.java)使用实际MyBatis Mapper XML渲染空IN，并让Spring读取真实服务事务声明。结果[SpecProbe.log](SpecProbe.log)：非法`WHERE ( id in )`及`UPDATE_TRANSACTION_ATTRIBUTE=null`。未连接数据库，不证明已经发生部分提交。

[CouponProbe.java](CouponProbe.java)复用现有SDCE301Test数据装配，调用真实`listCart`。结果[CouponProbe.log](CouponProbe.log)：0.90匹配金额/0.50门槛，分类及商品券可用数量均0、预期1。没有另写业务算法。

[SdcAttributeProbe.java](SdcAttributeProbe.java)直接调用真实`ExistingAttributeExtractionService.check`与`CandidateE705Evaluation.matches`，Mapper被设为调用即失败。结果[SdcAttributeProbe.log](SdcAttributeProbe.log)：蓝色原文被认可为红色值，同ID双值被认可，比较器抛Duplicate key。

源文件是只读诊断程序，进程退出0表示观察完成，**不表示这些错误行为通过验收**。修复计划要求将反例转为正式失败断言，再取得修复后通过证据。

在上面的测试已经生成Surefire XML后，从任意目录重跑：

```sh
rtk proxy python3 /Users/limeng/软件设计与规范AI/mall/SDC/evidence/cursor-review/run-probes.py /Users/limeng/软件设计与规范AI/.worktrees/sdc-integrated /opt/homebrew/opt/openjdk@17
```

脚本只读取XML中的classpath，编译到临时目录，不输出环境变量、凭据或整个XML。原日志记录修改前观察；以后重跑请另存，不覆盖本目录首次结果。

## 只读环境核对

实际执行`rtk proxy docker compose -f .worktrees/sdc-integrated/SDC/environment/compose.yml ps -a`：mysql/redis/mongo/rabbitmq四个教学容器均Exited(0)。正常本机权限TCP探测18085、13306、27018均`ConnectionRefusedError 61`。本轮未启动容器、未写或删除库记录。

独立核对Git：三个互斥分支对`af284e3`的`merge-base --is-ancestor`退出1；`git diff --check f352588 af284e3`退出2，只涉及22个evidence文件的尾随空白。完整测试日志按原样保存，没有为通过空白检查改写日志。
