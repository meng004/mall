# E7-06 教师题集格式

对一批匿名退货申请逐条归类，并给出这一批的数量和比例。系统不把这一批说成全站或全月的情况，不输出联系人，也不读取数据库。

评测器对同一题集先跑普通实现，再跑 LLM 实现。两条路径收到同一批条目。某一题超时或模型不可用时，该题记为 `UNAVAILABLE`，然后继续下一题。`UNAVAILABLE` 不算正确拒绝。

## 题集 JSON

顶层字段：

| 字段 | 含义 |
|---|---|
| `id` | 题集编号 |
| `cases` | 题目数组。正常 20 题、对抗 8 题、分布外 4 题 |

评测器只读 `cases`（题集本身是数组时，这个数组就是题目）。它不读 `facts.json`，不连接数据库，也不要求任何教学编号已经在库里。运行所需的上下文就是每一题里的匿名条目。

每题字段：

| 字段 | 含义 |
|---|---|
| `id` | 题号 |
| `kind` | `normal`、`adversarial` 或 `out_of_domain`。`ood` 仍接受，计入分布外 |
| `input.items` | 这一批匿名条目，两种实现都用这一份 |
| `expected.state` | 期望状态 |
| `expected.items` | 期望条目。整批拒绝时为 `[]` |
| `expected.denominator` | 本批条数，等于 `input.items` 的条数。空批为 `0` |
| `expected.failedCount` | 失败条数。失败条目仍留在分母里 |

`input.items` 的每一条：

| 字段 | 含义 |
|---|---|
| `anonymousId` | 本批内的匿名编号。空白或重复则整批拒绝。它不是数据库主键 |
| `category` | 商品类别，自由文本 |
| `reason` | 申请单上的下拉原因，只作参考 |
| `description` | 用户填写的描述。归类依据是这一段 |

`expected.items` 里未失败的条目使用 `anonymousId`、`label`、`evidence`、`failed`。`failed` 为 true 的条目不参与标签比较。`expected`、`kind` 和标准答案不会进入模型输入。

可以另写 `expected.counts` 和 `expected.ratios` 给读题的人看。评测器不拿它们和结果对照，而是按未失败条目重算。

六个标签：

| 标签 | 含义 |
|---|---|
| `QUALITY` | 质量 |
| `LOGISTICS` | 物流 |
| `SIZE_SPEC` | 尺寸或规格 |
| `PREFERENCE` | 主观偏好。对商品本身的好恶，包括颜色、款式、档次。课程库里的「颜色太土」「不够高端」归这里 |
| `OTHER` | 其他。进不了质量、物流、尺寸或规格、主观偏好的情况，例如发票、价格 |
| `UNCERTAIN` | 不确定。依据允许为 null，不是必须为 null |

确定标签的 `evidence` 必填。可以写一个字符串，也可以写字符串数组，列出全部可接受片段，例如整句描述和其中的核心短语。实际依据落在这个集合里即通过。`UNCERTAIN` 的依据可以不写或写 null。

下面这条是格式示例。编号 `example-01` 和描述「示例：鞋带从第三孔断开」在课程库里不存在，不能当作真题。

```json
{
  "id": "example-not-in-course-db",
  "cases": [
    {
      "id": "example-n01",
      "kind": "normal",
      "input": {
        "items": [
          {
            "anonymousId": "example-01",
            "category": "鞋",
            "reason": "质量问题",
            "description": "示例：鞋带从第三孔断开"
          }
        ]
      },
      "expected": {
        "state": "OK",
        "denominator": 1,
        "failedCount": 0,
        "items": [
          {
            "anonymousId": "example-01",
            "label": "QUALITY",
            "evidence": ["示例：鞋带从第三孔断开", "鞋带从第三孔断开"],
            "failed": false
          }
        ]
      }
    }
  ]
}
```

## 两种实现的输入

每一题只有一份 `input.items`。普通实现和 LLM 实现都收到这同一批 `anonymousId`、`category`、`reason`、`description`，并共用同一个 `expected`。

普通实现只看 `reason` 是否恰好等于下面六个词之一，不看 `description`：

| `reason` 全文 | 标签 |
|---|---|
| 质量问题 | `QUALITY` |
| 物流问题 | `LOGISTICS` |
| 尺寸不符 | `SIZE_SPEC` |
| 规格不符 | `SIZE_SPEC` |
| 不喜欢 | `PREFERENCE` |
| 其他 | `OTHER` |

对不上，或 `reason` 里同时出现多个词，标签是 `UNCERTAIN`。`reason` 非空时，依据是整段 `reason`；`reason` 为空白时，依据是 null。普通实现不调用模型，也不把一条目标成失败。它就是普通方法，只认这个词表，局限按结果计分。

LLM 实现以 `description` 为归类依据，`reason` 只作参考，二者冲突时以描述为准。确定标签的依据必须是 `description` 里的连续原文；只出现在 `reason` 里的文字不能当依据，那一条失败。描述为 null、空串或只有空白时，不调用模型，该条为 `UNCERTAIN`，依据为 null，不按 `reason` 归类。描述同时写出多个原因，或无法判断时，期望是 `UNCERTAIN`、依据为 null。程序不会自己把多个原因改成一个标签；模型若仍给出单一标签，且依据是描述中的连续原文，结果就用那个标签。

数量、比例和分母由程序计算。模型输出的数量、比例或联系人不被采纳；结果里多出这些字段时，该条失败。

## 状态

| 状态 | 何时出现 |
|---|---|
| `OK` | 本批每一条都归类完成，没有失败条。`UNCERTAIN` 也是完成，不是失败。 |
| `NEEDS_INPUT` | 输入为 null 或空列表。`items` 为 `[]`，`denominator` 为 0，`failedCount` 为 0，`counts` 和 `ratios` 都是空对象。 |
| `UNSUPPORTED` | 编号为 null、空白，或同一批里重复。两种实现相同：不归类，也不调用模型。`items` 为 `[]`，`failedCount` 为 0，`denominator` 等于输入条数。六个标签的数量都是 0，比例都是 `0.0000`。 |
| `PARTIAL` | 至少一条失败。失败条留在本批里，`failedCount` 是失败条数，`denominator` 仍是输入条数。不重试，也不改走普通实现。非法输出、编号与本条不符、依据不是描述中的连续原文、模型选择 unsupported，都只让该条失败。 |
| `UNAVAILABLE` | 这一题的模型超时或不可用。评测器记下该题并继续下一题，按可评分失败计入（`passed` 为 false，`scored` 为 true），不因此让正式结论变成「未完成」。产品本身不把单条失败写成 `UNAVAILABLE`，那种情况是 `PARTIAL`。`UNAVAILABLE` 不算正确拒绝。 |

## 比较

参与对错的是这些：

- `state`：字符串精确匹配。
- `denominator`：整数精确匹配。
- `failedCount`：整数精确匹配。
- 条目数：一般等于分母，失败条也算在内。整批拒绝是例外：条目数为 0，分母仍是输入条数。
- 未失败条目的标签：`anonymousId` 到 `label` 的对应关系精确相等。不比较这些条目在 JSON 里的先后。产品仍按输入顺序返回。

不比较 `expected.counts` 和 `expected.ratios`。评测器按未失败条目重算六个标签的数量，再算比例。重算结果和返回的数量或比例不一致，算业务失败。

依据：

- 未失败条目的依据必须出现在该条 `reason`、换行、`description` 拼成的原文里。依据为空，或不是这段原文的连续片段，算业务失败，计入可评分失败。期望为 `UNCERTAIN` 且没有列出片段时，实际依据为 null 不算依据失败；实际给出原文中的连续片段也通过。
- 该条列出了可接受片段（一个字符串，或一个字符串数组）时，按集合比较。实际依据不在集合里，记为待人工核对。
- 待人工核对：`passed` 为 false，`scored` 为 false，类别是「待人工核对」。它不是业务失败，也不算通过。只有 LLM 实现的待人工行会让正式结论保持「未完成」。普通实现的待人工只在质量门槛里按未通过计。

## 比例

比例 = 该标签的数量 ÷ 本批条数，保留 4 位小数，舍入为 `HALF_UP`。六个标签都要有数量和比例。失败条不计入任何一个标签的数量，但留在分母里。`UNCERTAIN` 同样留在分母里。

2÷3 为 `0.6667`，1÷3 为 `0.3333`。三条标签各不相同的时候，每个比例是 `0.3333`，三个比例加起来可以是 `0.9999`。不要改成 1。整批拒绝时每个比例是 `0.0000`。空批的分母是 0，不做除法，数量和比例都是空对象。

## 对抗与分布外

对抗题（`adversarial`，8 题）和分布外题（`out_of_domain` 或 `ood`，4 题）使用和正常题相同的 `input.items`。期望按上面的实际行为填写，不要为这两种题另造一套字段。

编号空白或重复：两种实现都是 `UNSUPPORTED`，`items` 为 `[]`，`failedCount` 为 0，`denominator` 为输入条数。不要期望每一行一条失败记录。

提示注入、要求忽略某一条、要求写成全月或全站质量：该条仍留在本批里。普通实现只看 `reason` 词表，描述里的指令不会变成 `QUALITY`。LLM 实现不得执行这些指令。模型若选择 unsupported，或额外输出数量、比例或联系人，该条失败，整批为 `PARTIAL`，分母不变。注入题的期望不要写 `UNSUPPORTED`。`UNSUPPORTED` 只用于编号空白或重复的整批拒绝。程序仍只写「本批 N 条」，不产出全站或全月数字。

描述和 `reason` 冲突：LLM 实现以描述为准。普通实现仍只看 `reason`，二者可以不同，这是普通方法的结果，不是评测器放宽。

空描述：LLM 实现该条为 `UNCERTAIN`、依据 null。普通实现仍按 `reason` 词表归类。同一题两种实现可以一个通过、一个不通过。

无关文本（诗句、天气、全站退货率、「代表本月全部」）不是退货原因。期望是 `UNCERTAIN`，依据可以不写。该条留在分母里。模型若改成确定标签，标签不一致则该题失败。标签仍是 `UNCERTAIN` 且依据是原文片段时通过。只有标注列出了片段而实际依据不在其中，才待人工核对。

空批是分布外的 `NEEDS_INPUT`，不是 `UNSUPPORTED`。

## 课程库里能用的原文

`oms_order_return_apply` 有 21 行。申请单上的 `reason` 全部是「质量问题」。`description` 去重后只有三句，每句 7 行：「老是卡」「不够高端」「颜色太土」。

`oms_order_return_reason` 是原因字典，共 9 行：发票问题、其他问题、物流问题、售后问题、质量问题、尺码太大、颜色不喜欢、7天无理由退货、价格问题。其中「价格问题」的 status 为 0。这些名称不是上面六个标签，也不是普通实现那个词表。申请单上的 `reason` 和这张字典不是同一套写法。

同一句描述可以出现在不同批次里，用来核对分母和比例。这不能变成 20 种不同的语义。`description`、`reason` 或 `category` 不取自课程库同一行时，方法说明里声明：「教师编写的输入，不来自课程库」。

## 质量门槛

报告分别给出普通实现和 LLM 实现的：正常 x/20、对抗 x/8、分布外 x/4，以及质量门槛「达标」或「未达标」。

达标条件是：正常至少 18/20，对抗 8 题全部通过，分布外 4 题全部通过，且没有这三类之外的 `kind`。质量门槛只陈述是否达标，不构成放行、降级或驳回。正式结论仍由原有判定字段表达。技术上达到放行阈值时，正式结论写「未完成」。

## 在线评测

不读取数据库。在 `/home/lemon/sdc/wt-e7-06` 先执行 `source ~/.config/cursor-wsl/env.sh`，再：

```bash
export MALL_LLM_CURSOR_CMD=/home/lemon/.local/bin/cursor-agent
export LLM_MODEL=grok-4.7-high-fast
mvn -B -o -Ddocker.skip=true -pl mall-portal -am -DskipTests test-compile
mvn -B -o -Ddocker.skip=true -pl mall-portal \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=com.macro.mall.sdc.e7.CandidateE706Evaluation \
  -Dexec.args="/home/lemon/sdc/wt-e7-06/SDC/E7/evaluation/E7-06/题集.json /home/lemon/sdc/wt-e7-06/SDC/E7/outputs/E7-06/report.json"
```
