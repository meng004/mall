# 优惠券门槛候选的复现材料

2026-10-03，针对 mall 提交 `f35258868860fc78eb7cb803ae5e61bc9adc11c0`。

- [CouponTargetProbe.java](CouponTargetProbe.java)：实际执行的独立 Java 主程序，使用现有 E3 测试依赖中的 Mockito 和 Spring Test。调用真实 `UmsMemberCouponServiceImpl.listCart`，只替换会员服务和 DAO。
- [coupon-amount.log](coupon-amount.log)：本次原始标准输出及标准错误。日志开头的 commons-logging 提示不是测试失败。

门槛为 100 元，券未过期，数量 1，减价 0。98.90 元不可用、99.90 元可用、100.00 元可用；中间一项违反满 100 元的金额规格。

程序内断言用于确认本次观察到的现状，因此退出码 0 表示复现观察一致，不表示业务正确。它不是修复后的回归测试；正式出题应另写期望 99.90 元不可用的失败测试。

## 教师重新运行

先在 mall 根目录用 JDK 17 运行现有 E3 测试以编译业务类和准备测试依赖：

```sh
rtk proxy mvn -pl SDC/E3 -am test
```

打开 `SDC/E3/target/surefire-reports/TEST-com.macro.mall.sdc.e3.OrderIntegrationTest.xml`，取其中 `java.class.path` 属性的值作为下列 `<E3测试运行classpath>`。它是当前机器生成的路径，不复制本次机器的绝对路径。创建一个临时输出目录后编译、运行：

```sh
rtk proxy mkdir -p /tmp/sdc-coupon-probe
rtk proxy javac -cp '<E3测试运行classpath>' -d /tmp/sdc-coupon-probe SDC/evidence/target-candidates/CouponTargetProbe.java
rtk proxy java -cp '/tmp/sdc-coupon-probe:<E3测试运行classpath>' CouponTargetProbe
```

命令中的占位内容必须替换；Windows 使用对应临时目录及分号 classpath 分隔符。程序不需要数据库或模型服务，也不修改业务文件。源码修复后，程序可能因旧行为断言失败，这时应使用修复后的规格测试，而不是改回旧行为。

仅证明服务层金额判定，不证明真实数据库、完整下单、并发或权限路径。最初的金额复现材料不能作为其他候选通过的证明；追加核对材料及其范围如下。

## 本次追加的六项核对

上述两份文件保留最初金额复现的原样。本次另提供：

- [CandidateAuditProbe.java](CandidateAuditProbe.java)：六项实际类／控制器检查；数据库依赖使用 Mockito。
- [audit-observations.log](audit-observations.log)：六项规格均观察到不符合预期，程序退出码 1。这是复现结果，不是业务通过结果。
- [audit-compile.log](audit-compile.log)：相关八模块 `test-compile` 成功，未运行全部测试。
- [CandidateAttributeValidationTest.java](CandidateAttributeValidationTest.java) 与 [admin-junit-red.log](admin-junit-red.log)：在后台模块通过 Maven 实际执行 1 项 JUnit，断言失败 1 项、执行错误 0 项，确认 Long 上的 `@NotEmpty` 类型不匹配，也验证后台题的运行入口。

六项程序使用当前源码编译结果。先运行前文 E3 命令取得当前机器的测试 classpath，再编译后台源码：

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests test-compile
rtk proxy mkdir -p /tmp/sdc-candidate-audit
rtk proxy javac -cp 'mall-admin/target/classes:<E3测试运行classpath>' -d /tmp/sdc-candidate-audit SDC/evidence/target-candidates/CandidateAuditProbe.java
rtk proxy java -cp '/tmp/sdc-candidate-audit:mall-admin/target/classes:<E3测试运行classpath>' CandidateAuditProbe
```

同样需要替换占位内容；Windows 改用对应目录与分号。当前记录源码的预期摘要为 `checks=6 specificationFailures=6`，随后抛出 AssertionError，退出码 1；出现编译失败、初始化错误或摘要前中断不能算复现成功。控制器检查采用 MockMvc standalone，未加载完整认证链或事务代理。E3-05 只检查明确的关系清理规格，不证明真实抵扣错误。

验证后台 JUnit 入口时，在个人分支确认没有同名测试后，将本目录的 `CandidateAttributeValidationTest.java` 临时复制到 `mall-admin/src/test/java`，在 mall 根目录运行：

```sh
rtk proxy mvn -pl mall-admin -am -DskipTests=false -Dtest=CandidateAttributeValidationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

当前源码应报告 1 项测试、1 项断言失败，原因为 `UnexpectedTypeException`／HV000030。`failIfNoSpecifiedTests=false` 只是允许上游模块没有该测试，必须核对后台实际执行数量。本次运行完成后已移除临时测试源文件及其编译类；读者也应只移除自己复制的文件及对应生成类，避免后续把本复现失败混入常规测试结果。

## E2 原则问题的现状观察

[DesignPrincipleProbe.java](DesignPrincipleProbe.java) 和 [design-principle-observations.log](design-principle-observations.log) 记录 E2-02 的动态类型约定、E2-05 的异常原因丢失、E2-06 的订单详情会员范围不一致。复用上文后台 classes 加 E3 测试 classpath，将编译及运行主类替换为 `DesignPrincipleProbe` 即可。当前观察断言通过，退出码 0；这表示现状复现一致，**不表示改造完成或业务正确**。所有对象为人工数据，未连接真实数据库或验证完整 HTTP 认证链。

## 本轮替补与旧编号

原E2-05异常原因题、原E3-05旧优惠券关联题和原E4-05四字段映射题已从当前候选中退役；旧日志中的编号不改写，也不把它们当新题成绩。新题使用E2-07、E3-07、E4-07。

[AddressDefaultProbe.java](AddressDefaultProbe.java) 与[address-default-observation.log](address-default-observation.log) 是新E3-07的服务级观察：当前会员101，目标999不存在，更新返回0前已调用清除原默认地址的写入。沿用本页E3测试classpath，将源文件／主类换为AddressDefaultProbe即可复现；无需后台classes。输出成功表示观察一致，不是业务修复通过。Mapper为替身，未验证实际SQL提交、事务回滚、完整HTTP身份链或并发；接口现有@Transactional不因返回0自动回滚。
