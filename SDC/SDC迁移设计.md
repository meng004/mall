# SDC Java 教学项目迁移设计

日期：2026-10-03。状态：已实施；验证结果与模型限制见[迁移验收](迁移验收.md)。

## 目标和已确认范围

在原始 `mall` 仓库中增加 `SDC`（software design and conventions），按 E1–E7 建立 Maven 子模块。产品修改和新增测试均使用 Java，教师可以在 IDEA 打开根 `pom.xml`，按实验浏览、运行、断点调试；学生使用 Maven 也能复现。

## 结构选择

推荐：`mall/SDC` 为 Maven 聚合模块，继承 mall 的 Java 17 和依赖版本，并由 mall 根 POM 聚合。E1–E7 各有 POM、说明、操作说明和标准 `src/main/java` / `src/test/java` 目录（按实际需要创建）。测试依赖真实 mall 模块，生产模块不依赖 SDC。

另外两种方式不采用：独立于 mall 的教学工程需要额外安装 mall 依赖，增加首次运行步骤；每个实验复制一份 mall 会造成七套产品代码分叉，难以判断测试覆盖的是哪份修复。

```text
mall/
  pom.xml
  mall-portal/src/main/java/...   产品修复、重构和功能代码的原位置
  ...                           其他原有产品模块
  SDC/
    pom.xml                     聚合 E1–E7，复用根项目版本
    README.md                   IDEA / Maven 入口与产出索引
    评审记录.md
    E1/ ... E7/
      pom.xml
      说明.md
      操作说明.md
      src/main/java/...         有实际用途的 Java 实验工具或入口
      src/test/java/...         JUnit 测试，直接调用产品代码
      evidence/                 历史证据（如原实验已有）
```

## 两类代码的归属

1. mall 产品代码：保留在原模块，不搬进 SDC，不为教学复制业务实现。E3 积分修复、E4 促销重构、E6 库存告警仍由真实 `mall-portal` 实现。
2. 实验及测试代码：集中在 SDC/E1–E7。已有 `ExperimentOrderTest` 按 E3、E6 拆分，`ExperimentPromotionTest` 迁入 E4；复用 JUnit、Mockito 和已有依赖。原项目与实验无关的测试不搬走。

E7 的查询接口、两种查询实现和 LLM 访问封装放在 `mall-portal` 的对应业务包中；SDC/E7 放演示入口、测试、题集和说明。产品模块不反向依赖教学模块。

## 各实验迁移

| 实验 | Java 产出与验证对象 |
|---|---|
| E1 | POM 依赖读取及源码锚点检查改为 Java；保留启动和真实查询演示说明 |
| E2 | 分层问题定位改为 Java；保留两种解耦方案、架构图和权衡，不把分析建议冒充已实施修改 |
| E3 | 删除 Python 积分模型作为验收入口；JUnit 在真实订单插入边界检查 null、0、30 等用例 |
| E4 | 删除 Python 促销副本作为验收入口；JUnit 直接验证真实促销实现的金额、文案、库存和赠送积分 |
| E5 | 库存修改影响定位改为 Java；保留支付、锁定、释放、退货和后台编辑的分析说明 |
| E6 | 删除 Python 告警模型作为验收入口；JUnit 验证真实告警阈值、日志及读取失败不破坏支付结果 |
| E7 | 统一查询接口、现有查询与 LLM 查询两种实现、独立 LLM 访问层；演示、评测器及测试全部改为 Java，仅查询 |

Java 源码检查仍明确属于静态辅助检查，不能替代业务执行测试。只建立每个实验实际需要的源码，不为目录整齐新增空类或抽象。

## E7 查询与 LLM 访问设计（按用户最新要求修订）

E7 收敛为只读查询，移除库存草稿、确认和撤销功能。默认采用可配置的模型 API；按用户后续“使用本机 Cursor 的 token”要求，另提供 Cursor 官方 CLI 适配器，复用本机登录态，查询业务与 LangChain4j API 路径均保持不变。

### 业务层：一个接口、两种实现

- `ProductQueryService`：统一接收 `ProductQueryRequest`，返回 `ProductQueryResult`。请求区分明确的结构化条件与自然语言文本，返回真实商品摘要、来源及查询状态。
- `ExistingProductQueryService`：适配现有 `PmsPortalProductService.search`，保留原商品查询链路及可见性规则。结构化条件直接执行，普通搜索文本按关键词处理。
- `LlmProductQueryService`：只负责查询业务。将自然语言解析成有限的 `ProductQueryCriteria`，校验后委托现有查询实现执行；结构化请求直接委托。通过构造注入 `LlmClient`，不接触 SDK、URL、认证头或登录逻辑。

现有 `/product/search` 的调用语义保持不变。E7 演示显式选择普通查询或 LLM 查询，避免替换旧接口后将关键词请求隐式解释成自然语言。LLM 失败时返回明确的不可用/不支持状态及普通查询入口，不把整句自然语言偷偷降级成关键词后声称查询成功。

查询范围保留商品名称包含、价格严格低于、商品库存严格少于及 AND 组合。当前 `/product/search` 只有关键词、品牌、分类、分页和排序参数，不能假定它已经支持价格/库存条件。实现时在现有商品数据访问处补充需要的参数化条件，并在分页之前过滤；两种实现共用这条执行路径。模型不输出 SQL，不指定 URL，不获得数据库或工具权限；结果来自真实查询而非模型生成。

### 访问层：薄封装复用成熟 SDK

`LlmClient` 只提供一次文本生成调用（系统指令、用户输入 → 模型输出）；`LangChain4jLlmClient` 封装 LangChain4j 的 `ChatModel`。SDK 对象与配置在访问层创建；商品字段、查询语法和提示词留在查询业务类，访问层不认识 mall 商品。

推荐 [LangChain4j](https://github.com/langchain4j/langchain4j)，采用普通 Java 的 `langchain4j-open-ai` 模块及其统一模型接口，避免为单次意图解析引入 Agent、RAG 或聊天记忆。官方文档提供 `baseUrl`、`apiKey`、`modelName`、自定义请求头及动态头 Supplier；依赖选用与 Java 17 兼容的正式版本，并在实施时编译验证。

备选 [Spring AI](https://github.com/spring-projects/spring-ai) 同样支持可移植 ChatModel 及配置化 OpenAI 访问；本实验优先 LangChain4j 的普通 Java 接入，便于独立 JUnit 和教学演示，不同时引入两个框架。

### 配置与认证边界

最小配置为 `base-url`、`model` 和一项凭据（`api-key` 或 `access-token`，二选一）。模型名不能普遍省略；它是服务端接受的模型 ID，不能假定与某个 IDE/CLI 的别名相同。凭据由环境变量提供，不写入仓库、示例输出或测试报告。

仅改上述配置即可切换模型的承诺，限定在支持相同 OpenAI-compatible 协议和对应认证方式的服务。其他厂商的原生协议使用 SDK 对应适配器，在访问层增加实际需要的接入，业务类保持不变，不预先实现全部厂商。

Cursor 模式配置 `LLM_PROVIDER=cursor` 和 `LLM_MODEL`，通过 `CursorCliLlmClient` 使用官方 CLI 已保存的认证；不提取、打印或转发登录 token 到兼容 API。CLI 仅在临时空目录执行意图解析，关闭文件读写、Shell、网页抓取及 MCP 工具权限。默认 API 模式仍由 LangChain4j 实现。

`access-token` 指目标模型 API 接受的访问令牌，例如其明确支持的 Bearer token；不能把任意网页 Cookie、IDE 登录态或其他服务的登录 token 当成通用 API 凭据。首版支持用户提供的有效访问令牌，过期时报认证失败；自动登录及刷新需提供商的具体 OAuth 协议，不凭空新增通用登录器。

依据：[LangChain4j OpenAI 集成](https://docs.langchain4j.dev/integrations/language-models/open-ai/)、[Spring AI OpenAI 配置](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)。

### 测试与证据

普通 `mvn test` 不需要在线模型或凭据。JUnit 覆盖两种查询实现的共同条件语义、LLM 非法输出/超时/认证失败、不支持操作拒绝，以及 SDK 适配层的请求与响应协议（使用本地 HTTP 测试服务）。业务测试通过 `LlmClient` 替身注入，不另写模拟业务算法。

真实模型和真实 mall 查询另行验收，不将替身结果称为模型准确率。原 34 条已公开题只能作为历史材料：查询题可用于回归；草稿题改为写操作拒绝测试，旧草稿成功期望不再适用。模型访问和业务范围均已变化，不沿用原 34/34 作为新版通过结论；新模型效果验收需要新的独立题集。

## 资料和证据迁移

将现有 `experiments/E1` 至 `E7` 的说明、图、题集和历史证据移入 `mall/SDC` 对应子模块；迁移共用评审资料并修正相关文档链接。废弃 Python 实验实现及运行脚本在 Java 替代通过后移除，不把 Python 留作复现依赖。

历史日志保留原文和生成语言，不改写成 Java 证据。原有通过数量、覆盖率、红绿对照记录均注明属于历史运行；新增 Java 运行结果单独记录，只有实际重跑才更新结论。正常生成的 Maven `target/` 不纳入教学源码。

## 验证和交付

- 根 Maven reactor 能识别 SDC/E1–E7，依赖顺序正确；测试不受 mall 默认 `skipTests=true` 影响。
- Java 测试调用当前产品类，覆盖原 Python 中仍有效的行为要求，消除重复业务算法。
- 从根项目执行实验测试可通过；单独执行某实验时提供准确的 `-pl` / `-am` 命令。
- 验证 E7 两种查询实现、模型失败、非法输出、数值边界、查询组合、写操作拒绝和评测判定；在线验证与离线测试分开报告。
- 文档提供 IDEA 导入、JDK 17 配置、JUnit 运行与断点位置，以及中间件、模型登录等在线前置条件。
- 不以本机某个绝对路径、IDEA 私有配置或 Python 环境作为学生运行前提。

本次不新增业务写接口；按用户最新要求移除 E7 库存草稿功能。不迁移整个 mall 到七份项目，不发布或推送代码。
