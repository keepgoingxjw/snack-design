# 免费云端 AI + 公开资料检索

当前修复不再依赖配料名称词表：

1. 免费云端模型理解中文、英文或复合配料，生成检索词。
2. 使用 Europe PMC 官方公开 API 检索食品相关开放文献摘要，无搜索密钥、无 Docker。
3. 免费模型基于本次检索内容输出中文作用与注意事项，附原始来源及逐字引用。
4. 后端核对引用必须来自本次返回的摘要；找不到来源则明确资料不足，不编造标准或食用剂量。

这是食品文献检索及中文解释，不是全网搜索引擎。任何名称都可提交给 AI 理解，但不保证每种输入都有可靠出处或实质结论。

## 本机配置

在 https://openrouter.ai/settings/keys 创建免费账号密钥，不充值、不开自动充值。
只在本机 `.env` 填写（文件已忽略 Git，不发送到聊天）：

```properties
OPENROUTER_API_KEY=你的密钥
AI_PROVIDER=openrouter
AI_ZERO_COST_ONLY=true
OPENROUTER_MODEL=openrouter/free
```

从项目目录运行 `mvn spring-boot:run` 或 `start-local.cmd`，配置修改需要重启。
每次完整查询最多进行两次免费模型调用；两次调用前都核对当前模型目录所有价格为零。
禁止付费模型、`:online`、计费搜索插件及收费 fallback；额度不足、目录不明、响应不完整或检索失败就停止。
免费模型可能排队、被隐私路由策略拒绝或不支持所需 JSON，因此不能承诺每次请求成功。

## 验证

`mvn test` 包含“丙酸钙不在旧词表仍通过 AI 生成检索词”的模拟链路测试，以及额度耗尽立即停止、伪造引用被拒绝的测试。
这些模拟测试不能代替真实云端推理验收；后者必须配置用户自己的有效免费密钥。

当前实际验收：用户密钥已通过官方状态接口验证，免费账号状态有效；真实模型调用先后出现结构化输出不完整、免费服务额度/权限/路由受限，尚未成功拿到最终中文解释。程序已停止后续推理测试，没有自动切换收费服务。不能将模拟链路测试通过称为真实 AI 闭环验收通过。

后续单次诊断已直接捕获：`google/gemma-4-31b-it:free` 返回 HTTP 429、上游代码 429；确认当前免费请求受额度或速率限制，而不是凭界面泛化提示推断。响应未提供可用的等待秒数，无法承诺恢复时间；已停止继续推理调用。仅凭 429 不能断言用户每日免费额度已耗尽，也可能是上游共享限流。精确诊断写入本机 `logs/backend.log`，不记录密钥、用户输入或原始上游响应。

官方资料：
- https://openrouter.ai/docs/guides/routing/model-variants/free
- https://openrouter.ai/docs/guides/routing/provider-selection
- https://europepmc.org/RestfulWebService
