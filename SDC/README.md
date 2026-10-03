# SDC — software design and conventions

SDC 是 mall 内的 Java 教学项目，按 E1–E7 组织。产品修复保留在原业务模块，实验代码和 JUnit 测试在这里；不需要 Python。

第一次使用请从[学生操作手册](学生操作手册.md)开始：包含教师仓库克隆、安装、独立样例环境、前后端启动、模型配置与E1–E7逐项验收。教学仓库为 [meng004/mall](https://github.com/meng004/mall/tree/sdc-java-migration)，配套[课程实验方案](课程实验方案.md)和[发布验收](发布验收.md)。

## 快速开始

1. 安装 JDK 17、Maven；`java -version` 与 `mvn -version` 均应显示 Java 17。macOS 上默认 Java 可能仍为 8，需将 `JAVA_HOME` 和 `PATH` 指向安装的 JDK 17。终端示例沿用本项目 RTK 约定；未安装 RTK 的学生可以去掉 `rtk proxy`，直接运行后面的 Maven/Java 命令。
2. 在 IDEA 中打开 **mall 根目录的 `pom.xml`**，选择作为 Maven 工程导入。Project SDK、Maven Importer JDK、Maven Runner JRE 均选择 JDK 17，然后 Reload All Maven Projects。
3. 首次运行需要网络下载 Maven 依赖。以下所有命令的工作目录为 **mall**：

```bash
rtk proxy mvn -pl SDC/E1,SDC/E2,SDC/E3,SDC/E4,SDC/E5,SDC/E6,SDC/E7 -am test
```

该命令编译所需产品模块并运行实验测试。mall 原项目默认跳过自己的集成测试，SDC 子模块单独启用测试；普通 SDC 测试无需数据库、中间件或在线模型。真实 mall 启动和在线模型演示的前置条件见 E1、E7 操作说明。

完整编译与打包（跳过镜像构建，不跳过 SDC 测试）：

```bash
rtk proxy mvn -Ddocker.skip=true clean package
```

portal 同时生成普通依赖 JAR 和 `mall-portal-1.0-SNAPSHOT-exec.jar`；后者用于 `java -jar` 启动。Docker 配置仍将可执行产物放到容器原文件名，原镜像启动入口不变。

单项运行示例：

```bash
rtk proxy mvn -pl SDC/E3 -am test
```

也可在 IDEA 直接运行各模块 `src/test/java` 的测试类。在 `mall-portal/src/main/java` 对应业务方法设断点，即可从测试进入真实业务代码。不要把工程设成 Java 8，也不需要安装额外 Python 测试工具。

## 实验导航

| 实验 | 课堂内容 | 入口 |
|---|---|---|
| E1 | 系统理解、依赖图、源码与运行证据 | [说明](E1/说明.md) · [操作](E1/操作说明.md) |
| E2 | 分层问题、两种解耦方案与架构视图 | [说明](E2/说明.md) · [操作](E2/操作说明.md) |
| E3 | 规格、积分缺陷、插入边界测试 | [说明](E3/说明.md) · [操作](E3/操作说明.md) |
| E4 | 策略重构、金额与行为特征测试 | [说明](E4/说明.md) · [操作](E4/操作说明.md) |
| E5 | 库存需求变更的影响分析 | [说明](E5/说明.md) · [操作](E5/操作说明.md) |
| E6 | 库存预警、日志与交易返回值 | [说明](E6/说明.md) · [操作](E6/操作说明.md) |
| E7 | 双查询实现、LLM 访问封装、只读验收 | [说明](E7/说明.md) · [操作](E7/操作说明.md) |

E1/E2/E5 的静态分析工具帮助定位和讲解，不是运行时业务验证。E3/E4/E6 直接调用实际业务服务，只替换外部系统依赖。E7 的普通测试和真实模型准确率验收分开记录。

## 两类产出

- **产品代码**：`mall-portal/src/main/java` 中的积分修复、促销策略、库存预警，以及 E7 查询接口、两种实现和 LLM 访问层。其他原有产品模块保持原归属。
- **实验与测试代码**：`SDC/E1` 至 `E7` 中的 Java 工具、演示、评测及 JUnit。业务模块不依赖 SDC。E2/E5 复用 E1 的源码与 POM 读取工具。

产品代码只有一份；不会在每个实验中复制算法。E7 当前只读查询，不再提供库存草稿、确认或撤销。

## 原版对照与覆盖率

首次准备 JaCoCo 依赖后，可用 Java 源文件运行器重建 E3/E4/E6 对照：

```bash
rtk proxy mvn -pl SDC/E3,SDC/E4,SDC/E6 -am org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test
rtk proxy java SDC/tools/JavaEvidence.java --original
rtk proxy java SDC/tools/JavaEvidence.java
```

`--original` 需要 mall 的 Git 历史包含原提交 `dcaa93b3150352e5044708d7211b5bed0af4509f`。它在临时目录重建原始订单/促销服务，不改当前源码；原版特定断言失败是预期结果。Java 工具内部调用 `rtk proxy mvn/git`，使用前需安装 RTK。普通 JUnit 运行不受此附加工具限制。

新日志和 JaCoCo XML 在 [evidence/java-migration](evidence/java-migration)。历史日志、图和题集保留原文；历史 Python 测试数、旧模型的 34/34 不能作为新版 Java 或新模型通过的证明。本次16个 Maven 模块构建成功、32项 JUnit 通过；Cursor 真实模型最新独立评测31/32，失败原因与边界见[迁移验收](迁移验收.md)。

## 设计与记录

- [迁移设计](SDC迁移设计.md)
- [实施计划](实施计划.md)
- [迁移验收](迁移验收.md)
- [历史评审](评审记录.md)

课堂个人讲解、互评、录屏及模型服务凭据由师生准备；历史记录不代替这些活动。
