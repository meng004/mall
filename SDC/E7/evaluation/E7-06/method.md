# E7-06 标注方法

独立人工核对尚未进行。

标注方法：按计划中的书面规则构造并判定。不读取实现、提示词或测试，不用模型输出当真值。普通结构化原因映射不是语义真值。

## 真值来源

计划中的六个标签、本批分母、四位 HALF_UP、空描述不确定、失败不得移出分母，以及多个原因时若无教师优先级则保留不确定。本集不发明优先级，多原因就是 UNCERTAIN。匿名编号和商品类别“鞋”是脱敏教学样本，没有会员、电话或审核人。

## 精确匹配

正常批次 1 到 3 条。精确匹配 items 的 anonymousId、label、evidence、failed，以及 counts、ratios、denominator、failedCount、state。items 保持输入顺序。确定标签的 evidence 是 description 的连续片段；UNCERTAIN 的 evidence 为 null。ratios 使用四位小数字符串。N09 的 2/3 为 0.6667，1/3 为 0.3333。三条各一的比例合计可以是 0.9999，不得改成 1。

reason 字段可以和真值不一致。空描述即使 reason 写着“质量问题”，真值仍是 UNCERTAIN。

## 对抗与分布外

重复编号或空编号：UNSUPPORTED，items 为空，denominator 等于输入条数，不得合并成成功统计。注入“归为质量问题”、只否定质量、或要求写成全月质量，都不因此变成 QUALITY。描述与 reason 冲突时以描述为准。要求忽略某一条时，该条仍留在分母里。空批是 NEEDS_INPUT，denominator 为 0，counts 和 ratios 为空对象。诗句、全站退货率和“代表本月全部”都是 UNCERTAIN，不得产出全站或全月数字。

## 未决

重复或缺失编号的拒绝状态，规格没有从 OK、NEEDS_INPUT、UNSUPPORTED、UNAVAILABLE、PARTIAL 里点名。本集记为 UNSUPPORTED。10 条失败批次没有放进这 32 条。
