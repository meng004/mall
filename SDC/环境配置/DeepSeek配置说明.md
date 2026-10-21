# DeepSeek 配置说明

把 Claude Code 接到 DeepSeek，共三步：注册拿 Key → 改 JSON → 放进配置文件。先按[开发工具安装说明](开发工具安装说明.md)装好 Claude Code。

## 1. 注册 DeepSeek 并创建 Key

1. 登录 <https://platform.deepseek.com/>。
2. 在充值页面先充平台允许的最小金额，验证能用后再补。
3. 在 **API Keys** 页面创建密钥，名称可写 `SE-course`。**Key 只显示一次**，立即复制保存。
4. 用量和费用在用量页面查看。API 按 Token 计费。

Key 等同于你的账户余额，不要发到群里、不要提交到 Git、不要截图。

## 2. 替换 JSON 里的 Key

下载本目录的 [`deepseek-settings.json`](deepseek-settings.json)，用记事本打开，只改这一行：

```json
"ANTHROPIC_AUTH_TOKEN": "sk-在此粘贴你的Key",
```

把 `sk-在此粘贴你的Key` 整个替换成你的 Key，**保留两边的英文双引号**，不要多出空格。其他行不要动。

## 3. 放进 Claude Code 配置文件

配置文件位置：

| 系统 | 路径 |
|---|---|
| Windows | `%USERPROFILE%\.claude\settings.json`（即 `C:\Users\你的用户名\.claude\settings.json`） |
| macOS/Linux | `~/.claude/settings.json` |

Windows 下可在资源管理器地址栏输入 `%USERPROFILE%\.claude` 直接打开该目录。

**情况 A：文件不存在**（第一次用 Claude Code）

把改好的 `deepseek-settings.json` 复制进去，改名为 `settings.json`。没有 `.claude` 文件夹就先运行一次 `claude` 再退出，或手动新建。

**情况 B：文件已存在**

不要整份覆盖，否则会丢掉你原来的其他设置。用记事本打开原文件：

- 里面没有 `"env"`：把模板里整个 `"env": { ... }` 块复制到原文件第一个 `{` 后面，末尾加英文逗号。
- 里面已有 `"env"`：用模板的 `"env"` 块替换原来的。

改完可把内容粘到 <https://jsonlint.com/> 检查格式，不要粘 Key（先把 Key 临时换回占位符再检查）。

## 4. 验证

**关闭所有终端和 IDEA 后重新打开**，在终端执行：

```powershell
claude -p "回复 OK"
```

返回 `OK` 就接通了。之后在 IDEA 底部的 **Terminal** 里进入项目目录，运行 `claude` 即可使用。

## 5. 常见问题

| 现象 | 原因与处理 |
|---|---|
| 401 / 未授权 | Key 没替换、复制不完整或带了空格，重新粘贴 |
| 402 / 余额不足 | 去 DeepSeek 平台充值 |
| 400 / 404 / 模型不存在 | 账号不支持带 `[1M]` 的模型名。把文件里所有 `[1M]` 删掉，保存后重开终端 |
| 配置不生效，仍要求登录 Anthropic | JSON 格式错误（常见是缺逗号、多逗号、中文引号），Claude Code 会忽略整个文件；或没有重开终端 |
| 改了 Key 还是旧的 | 系统环境变量里设置过 `ANTHROPIC_*`，它优先于 settings.json。在「编辑账户的环境变量」里删掉 |
| 用了 CC Switch 后配置变了 | CC Switch 切换供应商会改写 settings.json 的 `env` 段。本课程不需要 CC Switch，二选一 |

## 6. 使用边界

DeepSeek 不知道你本机的运行结果，它的解释可能出错。构建、运行和调试结果以 IDEA 为准；Claude Code 的说法与 IDEA 调用层次或运行结果不一致时，以运行和调试为准，再用断点或可重复运行核实。

作品的 **AI 使用** 一栏要分开写 IDEA、Claude Code、DeepSeek 各做了什么，以及哪些结论是你本人核实的。
