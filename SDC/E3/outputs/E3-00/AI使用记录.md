# E3-00 AI 使用记录

工具：Claude Code 子代理（模型 Sonnet）；JDK 17；JaCoCo 0.8.13。对照用 `SDC/compare.sh E3-00`。

## 过程

1. 先写规格与失败测试（提交 `d412136431afcd8179a6e3b53c7e4531fad5bc5d`），在基线上确认红灯来自规格不符：密码错返回 500，规格要求与用户名不存在一样返回 404。
2. 把 [ai-run/prompt.md](ai-run/prompt.md)（规格、不可变约束、验证命令）原文交给 AI；AI 只改产品代码，不改测试、不提交。
3. AI 的原始差异存为 [ai-run/raw.diff](ai-run/raw.diff)，逐处人工审查后提交修复 `e0ea94ef65c9831bf241f4bd6d5c48c189a89e17`。

## 逐处判定

| 位置 | AI 的改动 | 判定 | 理由 |
|---|---|---|---|
| `UmsAdminServiceImpl.login` 密码错 | `Asserts.fail("密码不正确")` → `throw new BadCredentialsException("用户名或密码错误")` | 采纳 | 与用户名不存在走同一条路径：被既有 `catch (AuthenticationException)` 吞掉返回 null，控制器回 404 同文案；不改控制器的登录分支、令牌与日志 |
| `UmsAdminServiceImpl.updatePassword` | `-1/-2/-3` → `Asserts.fail(原文案)` | 采纳 | 失败文案移到服务层；`ApiException` 经全局处理器回 500 与原文案，对外响应不变 |
| `UmsAdminController.updatePassword` | 删去 -1/-2/-3 到文案的翻译，只包装成功 | 采纳 | 这正是 T3 所说的逻辑泄漏；原 `else failed()` 分支已不可达 |
| 新增 import | `BadCredentialsException` | 采纳 | 必需 |
| `UmsAdminService.updatePassword` 注释 | AI 未改 | 人工补充 | 返回值含义变了（失败抛异常、成功返回 1），接口注释须写明；全仓唯一调用方是该控制器 |

AI 没有越出约束：未动 mall-common、mall-security、`SpringUtil` 与测试。

## 人工核对

- `evidence/compare.txt`：改前 fail（预期 fail），失败点是 `unknownUserAndWrongPasswordLookTheSame` 的响应码断言；改后 6 项通过，Skipped=0，结论“符合”。
- `evidence/coverage.txt`：`login` 行 92.9%、分支 100%；`updatePassword` 行 81.3%、分支 80.0%，未覆盖部分已解释。

## 限制

- 只经 MockMvc 独立装配验证控制器、服务与全局异常处理器；未起完整 Spring 容器，未验证安全过滤链与真实数据库。
- 测试为替换缓存服务，直接改了 `SpringUtil` 的静态字段（它的上下文只允许设一次），这本身是 E4-08 的题目。
