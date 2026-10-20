# 交给 AI 的提示（原文）

在 mall-admin 中修复管理员登录与改密的失败契约。只改产品代码，不改测试。

## 规格（需求验收准则）
1. 用户名不存在与密码错误：返回同一响应码（404）和同一文案“用户名或密码错误”。调用方不能据此区分账号是否存在。
2. 账号禁用：仍返回 500 与“帐号已被禁用”。
3. 改密：参数不合法、找不到该用户、旧密码错误三种失败，对外响应（响应码 500 与原文案）保持不变；失败文案由服务层给出，控制器不再把 -1/-2/-3 翻译成文案。
4. 改密成功：响应码 200、data 为 1，密码加密写库并清除缓存。

## 不可变约束
- 不改登录成功路径、令牌生成与登录日志；不改公开接口路径、请求体与 `UmsAdminService` 方法签名。
- 不改 mall-common、mall-security；不引入新依赖；不顺手修其他问题（如 SpringUtil 循环依赖，那是 E4-08）。

## 验证
`mvn -B -o -pl mall-admin -am -DskipTests=false -Dtest=SDCE300Test -Dsurefire.failIfNoSpecifiedTests=false test`
改前 `unknownUserAndWrongPasswordLookTheSame` 失败，改后 6 项全部通过。

相关代码：`mall-admin/src/main/java/com/macro/mall/service/impl/UmsAdminServiceImpl.java`（login 99–119、updatePassword 240–262、loadUserByUsername 266–274），`mall-admin/src/main/java/com/macro/mall/controller/UmsAdminController.java`（login 58–69、updatePassword 145–160），测试 `mall-admin/src/test/java/com/macro/mall/sdc/e3/SDCE300Test.java`。
