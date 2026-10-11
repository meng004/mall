# E7-02 题集格式

用户指定两件商品和至多三个属性，系统返回对照表。容量类的 MB/GB 先折成 MB 再比较大小；不能折成 MB 的值只做原值对照；任一侧没有值则该行是未知。未知行留在 `OK` 结果里。

示例里的商品、SKU、属性编号是占位，课程库中不存在。正式题用课程库里的真实编号。期望行可以用下面的只读 SQL 算出来。

## 题集

顶层只有两个字段：

| 字段 | 含义 |
|---|---|
| `id` | 题集名称。评测器不拿它做对错。 |
| `cases` | 题目数组。商品编号、SKU 编号、属性编号都写在每题里，不另设顶层门禁，也不读取旁边的 `facts.json`。 |

每题：

| 字段 | 含义 |
|---|---|
| `id` | 题号。全卷不能重复，也不能为空。 |
| `kind` | `normal`、`adversarial` 或 `out_of_domain`。`ood` 仍算分布外。 |
| `input.form` | 普通实现的输入。 |
| `input.llm` | LLM 实现的输入。 |
| `expected.state` | 期望状态。 |
| `expected.rows` | 期望对照行。没有行时写 `[]`。 |

`input.form` 与 `input.llm` 都有 `leftId`、`rightId`、`leftSkuId`、`rightSkuId`、`attributeIds`、`text`。同一题两边的 `leftId`、`rightId`、`leftSkuId`、`rightSkuId` 必须相同（`null` 与 `null` 相同）。不一致或题号重复时，评测器拒绝整份题集，不跑题。不要写 `scope`，评测器不读它。

普通实现读表单，评测器会把 `form.text` 原样传进去：

- 正常题的 `form.text` 必须是空串 `""`。空串和只含空白的文本都不会单凭文本变成 `UNSUPPORTED`。
- `attributeIds` 是勾选的属性编号，可以是 `null`、空数组，或多于三个。

LLM 实现读 `llm.text`。`llm.attributeIds` 必须是 `null`。模型只从这两件商品的候选属性里选出编号；商品编号和 SKU 编号仍用题目里写的，不由模型填写。`llm.text` 为空白时不会调用模型，而是把这份请求交给普通实现。

`expected.rows` 不必事先按编号排好。产品按表单（或模型选出）的属性顺序返回行；比较前评测器把期望行和实际行都按 `attributeId` 升序排列，不改产品本身的顺序。报告里逐题的 `structured.rows` 也是这个升序，便于和期望对照。

每一行写成：

```json
{"attributeId": 900007, "relation": "数值等价", "left": {"productId": 900001, "skuId": null, "attributeId": 900007, "raw": "1024MB", "normalized": "1024MB", "sourceField": "productAttributeValue"}, "right": {"productId": 900002, "skuId": null, "attributeId": 900007, "raw": "1GB", "normalized": "1024MB", "sourceField": "productAttributeValue"}}
```

`questions` 不参与比较。

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

上面这一条是示例。

## 取值

可见商品：

```sql
SELECT id, product_attribute_category_id
FROM pms_product
WHERE id = :id AND delete_status = 0 AND publish_status = 1;
```

查不到则该侧不可见。两侧都要可见，才会读属性和取值。

属性来自该商品的属性分类：

```sql
SELECT id, name, type
FROM pms_product_attribute
WHERE product_attribute_category_id = :product_attribute_category_id;
```

候选属性是两侧分类里这些行的并集。题目里的属性编号必须落在其中，否则整题 `NEEDS_INPUT`，`rows` 为空，追问「属性不在当前可见商品上」。编号先在左商品的分类里找，找不到再用右商品的。

SKU：

```sql
SELECT id, sp_data
FROM pms_sku_stock
WHERE product_id = :productId;
```

参数值：

```sql
SELECT product_attribute_id, value
FROM pms_product_attribute_value
WHERE product_id = :productId;
```

同一商品、同一 `product_attribute_id` 有多行时，后读到的 `value` 覆盖先读到的。这两条查询都没有 `ORDER BY`。出题时每个商品的每个属性只保留一行，每个商品的 SKU 以 `id` 区分即可。

`type = 0` 是规格属性，并且该侧至少有一条 SKU 时，从 SKU 的 `sp_data` 取值：

- `sp_data` 是 JSON 数组，元素含 `key` 和 `value`。`key` 必须与属性 `name` 完全相同。用数组里第一次命中的 `value`。
- 该侧 SKU 多于一条时，必须在请求里给出这一侧的 SKU 编号，否则整题 `NEEDS_INPUT`，`rows` 为空，追问「请先选择规格」。给出的编号必须是该商品的某条 SKU；对不上时这一侧没有值。
- 该侧只有一条 SKU 时，不必在请求里填 SKU 编号，请求里写了别的编号也不改选择，结果始终用这一条。期望里的 `skuId` 填这条 SKU 的 `id`，不是 `null`。
- 取到非空原文时，`sourceField` 为 `skuSpData`，`skuId` 为实际用到的那条 SKU 的 `id`，`raw` 为下面「空白」处理之后的原文。

`type` 不是 0，或者 `type` 为 0 但该侧没有任何 SKU 时，改从 `pms_product_attribute_value.value` 取值。这时结果里的 `skuId` 为 `null`。取到非空原文时 `sourceField` 为 `productAttributeValue`。

例外：属性编号只在另一侧商品的分类里时，这一侧不追问 SKU。是否追问只看「这一侧的分类里有没有这个编号」。取值仍按上面的 `type` 和这一侧的 SKU 走，属性名称用的是另一侧那条属性的 `name`：

- `type = 0` 且这一侧至少有一条 SKU 时，仍到这一侧的 `sp_data` 里按名称找 `key`。只有一条 SKU 时就用那一条；名称对上则取出原文，`sourceField` 为 `skuSpData`，这一侧不是未知。多于一条，且请求里的这一侧 SKU 编号留空或对不上任何一条时，选不中 SKU，这一侧 `raw`、`normalized`、`skuId`、`sourceField` 都是 null。另一侧有值时，该行关系是「未知」，整题仍是 `OK`，不是 `NEEDS_INPUT`。
- `type` 不是 0，或这一侧没有 SKU 时，改查这一侧的属性值表。没有对应行时这一侧没有值，另一侧有值则关系是「未知」。

两侧 `Value.attributeId` 都是题目里的那个属性编号，即使这一侧的分类里没有它。

占位例子（这些编号不在课程库中）：属性 900008 的 `type` 为 0、名称是「容量」，只在右商品 900002 的分类里。右商品只有一条 SKU，`sp_data` 里 `key` 为「容量」、`value` 为 `32GB`。左商品 900001 的分类里没有 900008，但有两条 SKU，请求的 `leftSkuId` 为 null。比较 900008 的结果是 `OK`，不追问规格。左侧 `raw`、`normalized`、`skuId`、`sourceField` 都是 null，`attributeId` 为 900008。右侧 `raw` 为 `32GB`，`normalized` 为 `32768MB`，`skuId` 为那条 SKU 的编号，`sourceField` 为 `skuSpData`，`attributeId` 为 900008。关系是「未知」。若左商品改成只有一条 SKU，且它的 `sp_data` 里也有 `key`「容量」，左侧就会取出该原文，关系按 MB 数值比较，不再是「未知」。

空白：`null`、空串、只含空白的原文都当成没有值。`raw` 为 `null`，`normalized` 为 `null`，`sourceField` 为 `null`。非全空白的原文不去掉首尾空格，`raw` 就是库里的那段文字（规格值是 `sp_data` 里命中的 `value`，参数值是表里的 `value`）。

任一侧 `raw` 为 `null` 时，该行 `relation` 为「未知」，整份 `state` 仍是 `OK`。

## 状态

| 状态 | 何时出现 |
|---|---|
| `OK` | 两件商品都可见，勾选了 1 到 3 个属性，并且文本为空，能取出对照行。某一行是「未知」时整份结果仍是 `OK`。 |
| `NEEDS_INPUT` | 没勾选属性；勾选超过 3 个；属性不在这两件可见商品上；规格属性有多个 SKU 但没填对应的 SKU 编号；模型要求先说明属性，或模型选出的属性超过 3 个。两条路径在超过 3 个属性时都是这个状态。`rows` 为空。这些题的 `form.text` 保持空串，否则普通路径会先变成 `UNSUPPORTED`。 |
| `UNSUPPORTED` | 普通路径收到非空文本；至少一件商品不可见（未上架或已删除）；模型输出不是约定的 JSON，或选出的编号不在候选里，或一个编号都没选，或模型表示不能做这件比较。`rows` 为空。 |
| `UNAVAILABLE` | 这一题的模型调用超时、失败或模型不可用。只记在这一题上，后面的题继续跑。它不算答对。不要把 `expected.state` 写成 `UNAVAILABLE`。 |
| `PARTIAL` | 本例不返回这个状态。缺值不再把整份结果改成 `PARTIAL`。期望里不要写它。 |

## 比较

通过条件是下面几项全部一致，都是精确匹配：

- `state` 与 `expected.state` 相同。
- `rows` 条数相同。
- 两边都按 `attributeId` 升序后，按下标逐行比较。相同编号的相对顺序保持原样。不按集合比较。
- 每一行的 `attributeId`、`relation`，以及左右两侧的 `productId`、`skuId`、`attributeId`、`raw`、`normalized`、`sourceField` 相同。行上的 `attributeId` 与左右两侧 `Value.attributeId` 都要写，都参与比较。
- JSON 的 `null` 对结果里的空值。不要写成字符串 `"null"`。

`relation` 只有这五个词：数值等价、左大于右、右大于左、原值对照、未知。

## 规范化与关系

先看 `raw` 是不是存储容量。去掉普通空格（U+0020）之后，整段必须是「非负数字或小数，再紧跟 MB 或 GB」，大小写不限，才折成 MB。这个判断用的是去掉空格后的 `raw`，`raw` 字段本身仍保留原文。

- `GB` 乘 1024。`1GB`、`1 GB`、`1gb` 的 `normalized` 都是 `1024MB`。`1.5GB` 是 `1536MB`。`8GB` 是 `8192MB`。`32GB` 是 `32768MB`。
- `MB` 不乘。`1024MB`、`1024.0MB` 的 `normalized` 都是 `1024MB`。尾随的 0 去掉。
- 两侧都能折成 MB 时比数值：相等是「数值等价」；左边大是「左大于右」；右边大是「右大于左」。

不能这样折的原文，`normalized` 是 `raw` 去掉首尾空白后的文字，不做单位换算。

- 任一侧没有值时，该行是「未知」，这一侧 `normalized` 为 `null`。另一侧有值也不比较大小。整份 `state` 仍是 `OK`。
- 两侧都有值，但并非两侧都能折成 MB：关系是「原值对照」。两边原文相同也一样，例如颜色都是「红」，关系仍是「原值对照」，不是「数值等价」。
- `g` / `kg` 没有换算。`500g` 对 `0.5kg` 是「原值对照」，两侧 `normalized` 分别是 `500g` 和 `0.5kg`，`raw` 与之相同。

课程库里这些写法都不能折成 MB。两侧都是这种值时，关系是「原值对照」，`normalized` 等于去掉首尾空白后的原文：

| 原文 | 处理 |
|---|---|
| `32G` | 单位是 `G`，不是 `GB`。`normalized` 仍是 `32G`。它和 `32GB`（`normalized` 为 `32768MB`，`raw` 为 `32GB`）放在一起是「原值对照」。 |
| `6GB+64GB` | 中间有加号，整段不是单个 MB/GB。`normalized` 仍是 `6GB+64GB`。 |
| `5.8` | 没有单位。`normalized` 仍是 `5.8`。 |
| `6.1英寸` | 单位不是 MB/GB。`normalized` 仍是 `6.1英寸`。 |
| `3000ml` | `normalized` 仍是 `3000ml`。 |
| `5500mAh` | `normalized` 仍是 `5500mAh`。 |

全角字母、单独的 `G`、毫升、毫安时，都不按 1024 换算。

## 对抗与分布外

`kind` 不进入模型输入。期望状态只有一个，两条路径一起对。正常题的 `form.text` 是空串。

- 正常对照：两件商品都可见。`form.text` 为 `""`。表单勾选 1 到 3 个属性编号。规格属性且该侧 SKU 多于一条时，填上要比较的那一个 SKU 编号；只有一条 SKU，或不是规格属性时，请求里的 SKU 编号写 `null`（期望行里的 `skuId` 仍按「取值」一节填写）。`llm.text` 点名这些属性。`expected.state` 为 `OK`。
- 超过三个属性：`form.text` 为 `""`，表单给出 4 个或更多属性编号，`llm.text` 也是在比较这几项。`expected.state` 为 `NEEDS_INPUT`，`rows` 为 `[]`。
- 没勾选，或说法同时对上多个属性（例如只说「内存」）：`form.text` 为 `""`，`attributeIds` 为 `null`，`llm.text` 写这个说法。`expected.state` 为 `NEEDS_INPUT`，`rows` 为 `[]`。
- 多个 SKU 却没选规格：`form.text` 为 `""`，表单勾选该规格属性，对应的 SKU 编号留 `null`。`expected.state` 为 `NEEDS_INPUT`，`rows` 为 `[]`。
- 语义越界（写入、改价、综合排序、「最适合我」、加入第三件商品、下单，以及其他不是规格对照的问题）：两件商品都必须可见。`form.text` 与 `llm.text` 写同一句用户原话。表单可以勾选 1 到 3 个合法属性。普通路径因这句非空文本直接 `UNSUPPORTED`，不再读属性。LLM 路径会先确认两件商品可见，然后调用模型；期望模型拒绝。`expected.state` 为 `UNSUPPORTED`，`rows` 为 `[]`。
- 权限边界（下架或已删除）：至少一侧用课程库里不可见的商品编号。`form.text` 为 `""`，表单仍勾选 1 到 3 个属性编号，这样普通路径会读到可见性并返回 `UNSUPPORTED`。LLM 路径在调用模型之前就因不可见返回 `UNSUPPORTED`，这一题的模型调用次数为 0。`expected.state` 为 `UNSUPPORTED`，`rows` 为 `[]`。

分布外题用语义越界那一形：可见商品、同一句原话、可勾选 1 到 3 个合法属性、期望 `UNSUPPORTED`。数量按题集自行安排。在线报告把两种实现分开计成正常 x/20、对抗 x/8、分布外 x/4。质量门槛是正常至少 18/20，并且对抗和分布外全部通过。这个门槛只说明是否达标，不是放行结论。

## 评测器从题集读取的上下文

课程库连接来自环境里的 `application-sdc.yml`，不从题集读账号或地址。题集提供的上下文是每题的：

- `input.form` 的 `leftId`、`rightId`、`leftSkuId`、`rightSkuId`、`attributeIds`、`text`：普通实现。`text` 会传进去。
- `input.llm` 的 `leftId`、`rightId`、`leftSkuId`、`rightSkuId`、`text`：LLM 实现。商品和 SKU 编号与表单相同。`attributeIds` 不传给模型。

这些编号就是课程库中的真实编号。评测器不检查教学商品 801–805 是否在库里。`expected`、`kind` 和「标准答案」不会放进模型输入。

报告每一行的 `structured` 含 `state` 和按 `attributeId` 升序的 `rows`。每一行含 `attributeId`、`relation`，以及左右的 `productId`、`skuId`、`attributeId`、`raw`、`normalized`、`sourceField`。这些就是参与比较的字段。
