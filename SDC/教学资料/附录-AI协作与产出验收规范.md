# 附录　AI 协作与产出验收规范

《软件设计与规范》课程讲义附录｜版本 1.0｜2026 年秋季学期适用
本规范是课程大纲第六章的必读材料（大纲参考资料 [47]），第 1 次课公布，适用于线上预习、实验、作品讲解、现场演示和期末。条款后方括号里的编号，对应本附录末尾的依据来源。

---

## 1　目的与适用范围

1.1　本课程允许并鼓励使用生成式 AI 编码助手（以下简称"AI"）和工程化 skill。本规范说清四件事：什么时候可以用 AI，用了以后要做什么，怎样判断 AI 的产出能不能用，以及怎样如实记录。

1.2　本规范的核心只有一句话：**AI 可以替你写，不能替你负责。** 提交物中的每一处内容，无论是谁写的，都由提交者本人解释并承担责任。实证研究表明，即使使用编码智能体，定位问题、测试与审查仍主要由人完成[2]；AI 时代开发者的核心能力也包括对 AI 产出的验证与负责[1]。

1.3　本规范适用于课程中使用的一切 AI 工具，包括 IDE 插件、对话式 AI、命令行智能体以及 skill。

## 2　术语

| 术语 | 含义 |
|---|---|
| AI 产出 | AI 生成的代码、测试、设计、结论、解释或文字 |
| 验收准则 | 在让 AI 生成之前写下的、判断产出是否合格的条件 |
| 不可变约束 | 本次修改不得改变的内容，例如生成代码目录、共用表结构、对外接口契约 |
| 验证 | 用证据（打开代码、运行测试、对照原则或规范条款）核对 AI 产出是否成立 |
| 采纳 / 部分采纳 / 驳回 | 对 AI 产出的三种处理结论 |
| AI 协作记录 | 记录本次使用 AI 的范围、过程、修改与验证结果的说明（格式见第 8 节） |

## 3　基本原则

| 原则 | 要求 |
|---|---|
| 责任在人 | 提交物的正确性与质量由本人负全部责任，"AI 是这样写的"不能作为理由[1][2] |
| 准则先行 | 先写验收准则，再让 AI 生成；没有准则的产出无法验收[6][7][14] |
| 证据优先 | 任何结论都要有证据：`文件:行号`、测试结果或规范条款[3][11] |
| 可以解释 | 能用自己的话讲清每一处产出做了什么、为什么这样做[4][5][10] |
| 如实披露 | 如实标注 AI 的使用范围，如实记录失败[17] |

## 4　使用边界

各环节按学习目标分别规定 AI 的使用方式[17]：

| 场景 | 规定 |
|---|---|
| 线上预习 | 可以使用 AI，但必须按第 6 节"AI 验证四步"作答；第 1 题"本人初判"必须在问 AI 之前独立写出 |
| 课堂实操、每周作业 | 可以使用 AI 与 skill；须写 AI 协作记录 |
| 作品讲解录屏 | 必须是本人声音讲解，必须演示测试运行；讲稿可以借助 AI 整理，但"五问"的内容须由本人回答 |
| 线上互评 | 评分与理由须由本人给出，不得让 AI 代评 |
| 现场演示、期末展示 | 本人现场讲解与回答追问，不得使用 AI 作答 |
| 期末独立任务 | 开卷，可以使用电脑和 AI；提交的"五问"说明须能当场解释 |

## 5　协作流程

每一次用 AI 完成一项修改，都按以下四步走。把需求拆成带约束的规格，再给 AI 一个能判定通过与否的目标，协作的可靠性会高很多[6][7][14][15]。

**第 1 步　写验收准则（生成之前）**
至少写出以下四类准则，每类一到两条：

| 类别 | 写什么 | 示例（mall） |
|---|---|---|
| 功能准则 | 修改后系统应当表现出的行为 | 下单时使用的积分与赠送的积分分别记录，后写不得覆盖先写 |
| 设计准则 | 必须遵守的设计原则与分层约束 | 业务判断不得放在 Controller；服务层不得新增对生成查询对象的依赖 |
| 规范准则 | 必须遵守的编码与接口规范条款 | 错误返回沿用统一返回结构，不新增魔法返回码 |
| 验证准则 | 用什么证明合格 | 先写一个能复现问题的失败测试，修改后该测试与原有测试全部通过 |

同时列出本次修改的**不可变约束**。

**第 2 步　生成**
向 AI 提要求时，把验收准则和不可变约束一起给它，并要求它回答三件事：改了哪些文件和行号，依据哪条原则或规范，需要跑哪些测试。

**第 3 步　验证**
按第 6 节"AI 验证四步"逐项核对，并对照第 1 步的验收准则逐条判定。

**第 4 步　记录与提交**
按第 8 节填写 AI 协作记录，按第 9 节标注，再提交。

## 6　AI 验证四步

1. **先判后问**：先写下自己的一句话判断，再去问 AI。这样才看得出你和 AI 分歧在哪。研究发现，元认知能力较弱的学习者更容易全盘接受 AI 的建议，并因此产生能力错觉[4]；用 AI 助手的人也更倾向于高估自己代码的安全性[11]。
2. **核对证据**：AI 提到的类、方法、行号、配置项、依赖，都要在 IDE 里逐个打开，看它是不是真的存在，内容是不是 AI 说的那样。代码生成模型会引用根本不存在的依赖包[13]。
3. **核对事实**：AI 的结论是否符合运行时的真实情况（框架行为、调用关系、事务边界、并发），是否符合课程讲的原则和规范。能运行测试的，以测试结果为准[14]。
4. **定结论**：采纳、部分采纳或驳回，并写出理由。

## 7　评判规则与验收结论

### 7.1　评判规则

| AI 的表现 | 处理 |
|---|---|
| 引用的行号、类、方法或配置在仓库中不存在 | 该结论不可采信[13] |
| 只看注解或代码字面，没考虑运行时（如接口上的 `@Transactional`、同类自调用、代理） | 降为"待验证"，须自己核实 |
| 说"测试会通过"，但没有实际运行 | 视为未验证[14] |
| 两次提问得到的回答不一致 | 必须自己运行或阅读代码后再判定 |
| 只说"违反最佳实践""更易维护"，不指出具体原则或条款 | 依据不足，不能作为结论 |
| 修改触及不可变约束（生成代码、共用表结构、对外接口） | 驳回该部分修改[16] |
| 修改超出任务范围（顺手重命名、格式化大量无关代码） | 拆出无关部分，或驳回 |
| 新增依赖、新增配置但未说明理由 | 待验证，说明理由后再决定[13] |

### 7.2　验收结论

| 结论 | 条件 |
|---|---|
| 放行 | 全部验收准则满足；无不可变约束被触及；测试已实际运行并通过；能解释每一处修改 |
| 修改后放行 | 主体可用，但有个别准则未满足或依据不足；写明需要修改的条目，修改并重新验证后放行 |
| 驳回 | 触及不可变约束；或功能准则不满足；或关键结论无法核实；或无法解释产出 |

要提醒的是：**测试全部通过，不等于可以放行。** 测试只证明"测到的部分"成立。像积分字段被后写覆盖这类问题，现有测试很可能压根没覆盖，只能对照功能准则和设计准则逐条判定。开源项目的准实验显示，采用 AI 编码工具后短期产出增加，静态分析告警和代码复杂度也跟着上升[3]；AI 生成的代码里有相当比例存在安全缺陷[12]。

## 8　AI 协作记录格式

每次作业附一份 AI 协作记录，放在"五问"说明之后，不超过半页：

| 项目 | 填写内容 |
|---|---|
| 使用范围 | 哪些部分用了 AI（规格、代码、测试、审查、讲稿整理等），哪些部分没有用 |
| 工具与模型 | 例如：IntelliJ IDEA + Qoder CN 插件，DeepSeek 模型；使用的 skill 名称 |
| 关键提示 | 1–3 条最关键的提示，原文摘录或概括 |
| 采纳与驳回 | AI 给出的主要结论或修改，各自的处理（采纳 / 部分采纳 / 驳回）及理由 |
| 人工修改 | 对 AI 产出做了哪些修改，`文件:行号` 与原因 |
| 一次失败 | 至少一次 AI 给错、给偏或自己判断失误的情况，以及如何发现、如何纠正 |
| 验证结果 | 运行了哪些测试，结果如何；核对了哪些证据 |

使用 skill（如 `improve-codebase-architecture`）时，在"采纳与驳回"中说明工具给出了什么、漏掉了什么、自己为什么这样改。

## 9　标注规定

1. 每次作业的 AI 协作记录即为主要标注，不要求在代码中逐行注释。
2. 提交说明（commit message）在正文末尾加一行：`AI-Assisted: <工具与模型>; <使用范围>`。完全未使用 AI 的写 `AI-Assisted: none`。这一行用 Git 提交说明尾注（trailer）的"键: 值"格式[18][19]。
3. 作品讲解录屏中，讲到 AI 产出的部分时说明"这一处是 AI 给出的，我核对了……"。

## 10　安全与隐私

按"只读还是写入、能否撤销、涉及哪些权限"评估 AI 工具操作的风险[16]：

1. API Key 不得写入代码、配置文件或提交到仓库；不得在录屏中露出。
2. 不得把个人身份信息、他人作业、教师下发的未公开题目或评分材料提交给 AI。
3. 本人的 API 账号与额度只供本人使用，不得转借。

## 11　学术诚信

1. AI 检测器的结论不作为认定违规的依据：现有检测器在代码上不稳定，换语言、换模型或稍作改写后即失效[8][9]；核验依靠作品讲解、现场演示和期末展示中的本人解释[10]。
2. 以下行为按学术不诚信处理：未如实标注 AI 使用；录屏不是本人讲解；伪造测试结果或协作记录；在规定独立完成的环节使用 AI 或他人帮助。
3. 在现场演示或期末展示中无法解释本人提交内容的，该次作品讲解记零；累计 2 次，按学校学术不端处理程序办理，总评不及格。

## 12　提交前自查清单

- [ ] 生成之前写了验收准则（功能、设计、规范、验证四类）和不可变约束
- [ ] AI 提到的每个类、方法、行号都在 IDE 中打开核对过
- [ ] 测试是实际运行过的，不是"应该能过"
- [ ] 没有触及不可变约束，没有夹带无关修改
- [ ] "五问"每一问都能用自己的话回答，每条结论都有 `文件:行号` 或测试证据
- [ ] AI 协作记录七项齐全，含一次失败
- [ ] 提交说明末尾有 `AI-Assisted` 行
- [ ] API Key 没有出现在代码、配置和录屏中

---

## 参考文献

[1] Kam M, et al. What do professional software developers need to know to succeed in an age of artificial intelligence?[C]//Companion Proceedings of the 33rd ACM International Conference on the Foundations of Software Engineering (FSE Companion). 2025. DOI: 10.1145/3696630.3727251.

[2] Kumar A, Bajpai Y, Gulwani S, Soares G, Murphy-Hill E. Why AI agents still need you: findings from developer-agent collaborations in the wild[C]//Proceedings of the 40th IEEE/ACM International Conference on Automated Software Engineering (ASE). 2025: 432-444. DOI: 10.1109/ASE63991.2025.00043.

[3] He H, Miller C, Agarwal S, Kästner C, Vasilescu B. Speed at the cost of quality: how Cursor AI increases short-term velocity and long-term complexity in open-source projects[C]//Proceedings of the 23rd International Conference on Mining Software Repositories (MSR). DOI: 10.1145/3793302.3793349.

[4] Prather J, et al. The widening gap: the benefits and harms of generative AI for novice programmers[C]//Proceedings of the 2024 ACM Conference on International Computing Education Research (ICER), Volume 1. 2024. DOI: 10.1145/3632620.3671116.

[5] Shihab M, Hundhausen C, Tariq A, Haque S, Qiao Y, Mulanda B. The effects of GitHub Copilot on computing students' programming effectiveness, efficiency, and processes in brownfield coding tasks[C]//Proceedings of the 2025 ACM Conference on International Computing Education Research (ICER). 2025. DOI: 10.1145/3702652.3744219.

[6] Ullrich J, Koch M, Vogelsang A. From requirements to code: understanding developer practices in LLM-assisted software engineering[C]//2025 IEEE 33rd International Requirements Engineering Conference (RE). 2025: 257-266. DOI: 10.1109/RE63999.2025.00032.

[7] Wang Y, Rizi M B, Khashei F, Rubin J. From specifications to implementation in the Gen-AI era: lessons from a project-based software engineering course[J]. Proceedings of the ACM on Software Engineering, 2026, 3: 1424-1447. DOI: 10.1145/3797092.

[8] Pan W, et al. Assessing AI detectors in identifying AI-generated code: implications for education[C]//2024 IEEE/ACM 46th International Conference on Software Engineering: Software Engineering Education and Training (ICSE-SEET). 2024: 1-11. DOI: 10.1145/3639474.3640068.

[9] Suh H, Tafreshipour M, Li J, Bhattiprolu A, Ahmed I. An empirical study on automatically detecting AI-generated source code: how far are we?[C]//Proceedings of the 47th International Conference on Software Engineering (ICSE). 2025. arXiv: 2411.04299.

[10] Kannam S, Yang Y, Dharm A, Lin K. Code interviews: design and evaluation of a more authentic assessment for introductory programming assignments[C]//Proceedings of the 56th ACM Technical Symposium on Computer Science Education (SIGCSE TS), V. 1. 2025. DOI: 10.1145/3641554.3701806.

[11] Perry N, Srivastava M, Kumar D, Boneh D. Do users write more insecure code with AI assistants?[C]//Proceedings of the 2023 ACM SIGSAC Conference on Computer and Communications Security (CCS). 2023: 2785-2799. DOI: 10.1145/3576915.3623157.

[12] Pearce H, Ahmad B, Tan B, Dolan-Gavitt B, Karri R. Asleep at the keyboard? Assessing the security of GitHub Copilot's code contributions[C]//2022 IEEE Symposium on Security and Privacy (SP). 2022: 754-768. DOI: 10.1109/SP46214.2022.9833571.

[13] Spracklen J, Wijewickrama R, Sakib A H M N, Maiti A, Viswanath B, Jadliwala M. We have a package for you! A comprehensive analysis of package hallucinations by code generating LLMs[C]//34th USENIX Security Symposium (USENIX Security 25). 2025: 3687-3706. arXiv: 2406.10279.

[14] Anthropic. Claude Code: best practices for agentic coding[EB/OL]. 2025[2026-10-01]. https://www.anthropic.com/engineering/claude-code-best-practices.

[15] Schluntz E, Zhang B. Building effective agents[EB/OL]. Anthropic, 2024-12-19[2026-10-01]. https://www.anthropic.com/research/building-effective-agents.

[16] OpenAI. A practical guide to building agents[EB/OL]. 2025[2026-10-01]. https://cdn.openai.com/business-guides-and-resources/a-practical-guide-to-building-agents.pdf.

[17] Northeastern University CS4530. Policy on use of AI (Spring 2026)[EB/OL]. 2026[2026-09-30]. https://neu-se.github.io/CS4530-Spring-2026/policies/ai.

[18] Conventional Commits. Conventional Commits 1.0.0[EB/OL]. [2026-09-30]. https://www.conventionalcommits.org/en/v1.0.0/.

[19] Git Project. git-interpret-trailers documentation[EB/OL]. [2026-10-01]. https://git-scm.com/docs/git-interpret-trailers.
