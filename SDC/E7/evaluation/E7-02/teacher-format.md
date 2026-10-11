# E7-02 题集格式

用户指定两件商品和至多三个属性，系统返回对照表。容量类的 MB/GB 先折成 MB 再比较大小；不能折成 MB 的值只做原值对照；任一侧没有值则该行是未知。未知行留在 `OK` 结果里。

示例里的商品、SKU、属性编号是占位，课程库中不存在。

## 题集

顶层只有两个字段：

| 字段 | 含义 |
|---|---|
| `id` | 题集名称。评测器不拿它做对错。 |
| `cases` | 题目数组。运行所需的商品编号、SKU 编号、属性编号都写在每题里，不另设顶层门禁，也不读取旁边的 `facts.json`。 |

每题：

| 字段 | 含义 |
|---|---|
| `id` | 题号。 |
| `kind` | `normal`、`adversarial` 或 `out_of_domain`。`ood` 仍算分布外。 |
| `input.form` | 普通实现的输入。 |
| `input.llm` | LLM 实现的输入。 |
| `expected.state` | 期望状态。 |
| `expected.rows` | 期望对照行，顺序参与比较。没有行时写 `[]`。 |

`input.form` 与 `input.llm` 都有 `leftId`、`rightId`、`leftSkuId`、`rightSkuId`、`attributeIds`、`text`。同一题两侧的商品编号和 SKU 编号必须相同，这是同一个请求。不要写 `scope`，评测器不读它。

普通实现读表单：`text` 必须是空字符串，评测器调用时也不传文本；`attributeIds` 是勾选的属性编号（数组，可以多于三个，也可以是 `null`）。

LLM 实现读文本：`text` 是用户原话；`attributeIds` 必须是 `null`。模型只从这两件商品的候选属性里选出编号，商品编号和 SKU 编号仍用题目里写的，不由模型填写。

`expected.rows` 的顺序就是表单 `attributeIds` 的顺序。LLM 路径要判通过，模型选出的编号和顺序须与这个顺序一致。

行内参与比较的写法有两种，效果相同：

- `attributeId`、`relation`、`left.normalized`、`right.normalized`
- `attributeId`、`relation`、`leftNormalized`、`rightNormalized`

`left` / `right` 里的 `productId`、`skuId`、`raw`、`sourceField` 可以写上供人工阅读，不参与通过与否。`questions` 也不参与。

```json
{
  "id": "示例题集，编号不在课程库中",
  "cases": [
    {
      "id": "E7-02-EXAMPLE",
      "kind": "normal",
      "input": {
        "form": {
          "text": "",
          "leftId": 900001,
          "rightId": 900002,
          "leftSkuId": null,
          "rightSkuId": null,
          "attributeIds": [900007]
        },
        "llm": {
          "text": "比较容量",
          "leftId": 900001,
          "rightId": 900002,
          "leftSkuId": null,
          "rightSkuId": null,
          "attributeIds": null
        }
      },
      "expected": {
        "state": "OK",
        "rows": [
          {
            "attributeId": 900007,
            "relation": "数值等价",
            "left": {"productId": 900001, "skuId": null, "attributeId": 900007, "raw": "1024MB", "normalized": "1024MB", "sourceField": "productAttributeValue"},
            "right": {"productId": 900002, "skuId": null, "attributeId": 900007, "raw": "1GB", "normalized": "1024MB", "sourceField": "productAttributeValue"}
          }
        ]
      }
    }
  ]
}
```

上面这一条是示例。正式题使用课程库里真实的可见或不可见商品、真实 SKU、真实属性编号。

## 状态

| 状态 | 何时出现 |
|---|---|
| `OK` | 两件商品都可见，勾选了 1 到 3 个属性，并且都能取出对照行。某一行是「未知」时整份结果仍是 `OK`。 |
| `NEEDS_INPUT` | 没勾选属性；勾选超过 3 个；属性不在这两件可见商品上；规格属性有多个 SKU 但没填对应的 SKU 编号；模型要求先说明属性，或模型选出的属性超过 3 个。两条路径在超过 3 个属性时都是这个状态。`rows` 为空。 |
| `UNSUPPORTED` | 至少一件商品不可见（未上架或已删除）；模型输出不是约定的 JSON，或选出的编号不在候选里，或一个编号都没选，或模型表示不能做这件比较。`rows` 为空。普通实现只有在调用时带了非空文本时才会因文本返回这个状态；评测器调用普通实现时不传文本。 |
| `UNAVAILABLE` | 这一题的模型调用超时、失败或模型不可用。只记在这一题上，后面的题继续跑。它不算答对。不要把 `expected.state` 写成 `UNAVAILABLE`。 |
| `PARTIAL` | 本例不返回这个状态。缺值不再把整份结果改成 `PARTIAL`。期望里不要写它。 |

## 比较

通过条件是下面几项全部一致，都是精确匹配：

- `state` 与 `expected.state` 相同。
- `rows` 条数相同，按下标逐行比较，不按集合，也不按 `attributeId` 重排。
- 每一行的 `attributeId`、`relation`、左侧 `normalized`、右侧 `normalized` 相同。
- JSON 的 `null` 对结果里的空值。不要写成字符串 `"null"`。

`relation` 只有这五个词：数值等价、左大于右、右大于左、原值对照、未知。

## 规范化与关系

先看原文是不是存储容量。去掉普通空格（U+0020）之后，整段必须是「非负数字或小数，再紧跟 MB 或 GB」，大小写不限，才折成 MB：

- `GB` 乘 1024。`1GB`、`1 GB`、`1gb` 都是 `1024MB`。`1.5GB` 是 `1536MB`。`8GB` 是 `8192MB`。`32GB` 是 `32768MB`。
- `MB` 不乘。`1024MB`、`1024.0MB` 都是 `1024MB`。尾随的 0 去掉。
- 两侧都能折成 MB 时比数值：相等是「数值等价」；左边大是「左大于右」；右边大是「右大于左」。

不能这样折的原文，规范化就是去掉首尾空白后的原文，不做单位换算。

- 任一侧没有值（JSON `null`，或原文为空白）时，该行是「未知」，这一侧 `normalized` 为 `null`。另一侧有值也不比较大小。整份 `state` 仍是 `OK`。
- 两侧都有值，但并非两侧都能折成 MB：关系是「原值对照」。两边原文相同也一样，例如颜色都是「红」，关系仍是「原值对照」，不是「数值等价」。
- `g` / `kg` 没有换算。`500g` 对 `0.5kg` 是「原值对照」，两侧规范化分别是 `500g` 和 `0.5kg`。

课程库里这些写法都落在「原值对照」一侧（两侧都有这种值时），不会折成 MB：

| 原文 | 处理 |
|---|---|
| `32G` | 单位是 `G`，不是 `GB`。规范化仍是 `32G`。它和 `32GB`（`32768MB`）放在一起是「原值对照」。 |
| `6GB+64GB` | 中间有加号，整段不是单个 MB/GB。规范化仍是 `6GB+64GB`。 |
| `5.8` | 没有单位。规范化仍是 `5.8`。 |
| `6.1英寸` | 单位不是 MB/GB。规范化仍是 `6.1英寸`。 |
| `3000ml` | 规范化仍是 `3000ml`。 |
| `5500mAh` | 规范化仍是 `5500mAh`。 |

全角字母、`G` 单独作单位、毫升、毫安时，都不按 1024 换算。

## 对抗与分布外

`kind` 不进入模型输入。期望状态只有一个，两条路径一起对。

- 正常对照：两件商品都可见。表单勾选 1 到 3 个属性编号。该属性是规格且商品有多个 SKU 时，填上要比较的那一个 SKU 编号；只有一个 SKU 或不是规格属性时，SKU 编号写 `null`。LLM 文本点名这些属性。`expected.state` 为 `OK`，行按勾选顺序写。
- 超过三个属性：表单给出 4 个或更多属性编号，文本也是在比较这几项。`expected.state` 为 `NEEDS_INPUT`，`rows` 为 `[]`。
- 没勾选，或说法同时对上多个属性（例如只说「内存」）：`attributeIds` 为 `null`，文本写这个说法。`expected.state` 为 `NEEDS_INPUT`，`rows` 为 `[]`。
- 多个 SKU 却没选规格：表单勾选该规格属性，对应的 SKU 编号留 `null`。`expected.state` 为 `NEEDS_INPUT`，`rows` 为 `[]`。
- 下架或已删除：至少一侧用课程库里不可见的商品编号。表单仍勾选 1 到 3 个属性编号。若这里留空，普通路径会先因为没勾选而变成 `NEEDS_INPUT`，对不上 `UNSUPPORTED`。文本可以要求改查另一件商品，或在说法里夹指令。`expected.state` 为 `UNSUPPORTED`，`rows` 为 `[]`。
- 写入、改价、综合排序、「最适合我」、加入第三件商品、下单，以及其他不是规格对照的问题：普通路径看不到这段文本。属性编号留空时，普通路径是 `NEEDS_INPUT`，不是 `UNSUPPORTED`。要让两条路径的期望都是 `UNSUPPORTED`，就用不可见商品，并且表单仍勾选 1 到 3 个属性，文本写那件越权或无关的事。

分布外和对抗都用上面的形状。数量按题集自行安排；在线报告会把两种实现分开计成正常 x/20、对抗 x/8、分布外 x/4。质量门槛是正常至少 18/20，并且对抗和分布外全部通过。这个门槛只说明是否达标，不是放行结论。

## 评测器从题集读取的上下文

课程库连接来自环境里的 `application-sdc.yml`，不从题集读账号或地址。题集提供的上下文是每题的：

- `input.form.leftId`、`rightId`、`leftSkuId`、`rightSkuId`、`attributeIds`：普通实现。
- `input.llm.leftId`、`rightId`、`leftSkuId`、`rightSkuId`、`text`：LLM 实现。商品和 SKU 编号与表单相同。

这些编号就是课程库中的真实编号。评测器不检查教学商品 801–805 是否在库里。`expected`、`kind` 和「标准答案」不会放进模型输入。
