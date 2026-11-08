# E7-06 现行题集与业务规则

接口ReturnReasonBatchService.analyze(List<Item>)。每条只含匿名ID、脱敏类别、结构化原因和描述。模型只分类及摘录依据；Java校验编号、标签与依据，计算本批数量/比例，不读取或写入生产申请。

空批NEEDS_INPUT；编号空白或重复时模型调用前整批UNSUPPORTED，items空、failedCount=0，denominator仍是输入条数，各类数量与比例均为0。description空时UNCERTAIN且evidence=null，不按reason强行猜描述。单条模型失败保留在分母、该条failed=true，整体PARTIAL；不重试或悄悄转普通路径。

模型动作协议：无法判断时必须仅输出 `{"action":"needs_input"}`；不支持时必须仅输出 `{"action":"unsupported"}`。两者不得含 anonymousId、reason 或其他字段。`classify` 按既有规则提供 action、anonymousId、label、evidence；重复键、额外字段、代码围栏和尾随说明仍视为非法输出，不自动修剪或改写。

已确认：发票补开或重开属于售后 OTHER；OTHER 是有明确内容的其他售后诉求，未知、无关或无法判断为 UNCERTAIN。以 description 为准；多个原因无法确定主因时为 UNCERTAIN；明确否定 A 并肯定 B 时按 B，矛盾或不能确定则 UNCERTAIN。确定标签的依据应为 description 中的非空连续片段；UNCERTAIN允许null。本地修订版评测器检查 description；远端固定版仍检查 reason 加 description，不应混用。expected比较state、每条匿名ID和标签、denominator及failedCount，再独立重算六类数量和scale=4/HALF_UP比例。合法异片段待人工、不计通过。普通路径现有扩展只识别有限表达，不能冒充自由文本人工真值或未经验证的可靠降级；“本批”统计不能推断全站或产品缺陷因果。

已确认：description 只有“其他”（允许首尾空白）时为 UNCERTAIN；普通路径、模型路径和人工录入均遵循该规则。发票以外的售后范围仍待逐项确认。

## 独立实施与证据边界

学生从exp/E7独立副本领取公开接口、Candidate评测器、EvaluationSupport/EvaluationFiles及本题公开题集；业务类由本人实现。评测器的studentExisting、studentLlm是仅有的参考构造点，连同import替换为本人类；compareCases（02为runCases）只接公开业务接口，不依赖参考实现的内部成员。若沿用参考类名/构造则无需改构造；两条实现都必须确实进入本人代码。主入口与已有的replay入口共用构造方法，不改期望、阈值、题量或判分。01/02仍需教师提供适用只读课程数据，连通不表示隔离快照已核对。

先做本人JUnit及LlmClient替身，再运行公开回归。公开题和历史响应已经暴露，只作回归；本轮离线报告明确modelCalls=0、模型不可用，不证明模型质量。自动技术达标仍保留“未完成”，待教师另供未见题、独立标注、人工核对和适用事实。正常20、对抗8、分布外4，预定质量/安全/非功能要求均须有证据。UNAVAILABLE不是正确拒绝，待人工/未执行不计通过。有证据的不放行、未完成均可作为诚实作业结果。

报告保存提交版本、题集来源、事实/时钟配置、逐题实际结果、错误、调用原文、耗时和人工判断；密钥不入报告。真正替代路径还须保持原业务含义；未做HTTP/UI时不声称端到端降级已验证。未见题答案不公开给开发者调参。教师最终结论记录在已有报告/Issue，不靠填reviewer名字自动放行。
