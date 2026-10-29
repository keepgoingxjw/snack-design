# 云端零调用费方案（可选）

**本轮更新：云端模式已改为免费 AI 动态生成检索词 + Europe PMC 公开 API + 中文 AI 总结，无需 SearXNG/Docker。现行配置见 [CLOUD_FREE_AI.md](CLOUD_FREE_AI.md)。以下旧启动步骤仅作历史参考。**

当前最小闭环默认使用 `AI_PROVIDER=public-search`，直接查询 Europe PMC 公开 API，无需密钥、Docker 或大模型，返回英文文献摘要及来源，不执行 AI 总结。直接运行 `start-local.cmd`；本文件后续内容仅适用于主动选择 `AI_PROVIDER=openrouter` 的云端 AI 模式。

## 零费用的具体含义

- 不调用豆包/火山方舟的付费助手或搜索插件。
- 不在本机运行或下载大模型；不需要GPU。
- 云端推理由OpenRouter免费入口提供，默认 `openrouter/free`。
- AI/搜索API调用费必须为0。免费额度耗尽、模型下架、隐私路由不满足、搜索不可用时停止，不切付费模型、不提示充值来恢复本功能。
- Java与数据库继续在已有开发电脑运行。轻量搜索可以自托管，不需要购买服务器；现有电脑、电力、网络不等于没有物理运行成本。若未来自行租服务器、买域名则另计，不属于本方案承诺的零API费。
- 不承诺免费服务永久在线、不限量、免注册或生产级SLA。需要免费账号API Key。

本轮已读取官方免费文档与无需认证的模型目录，确认存在零价格条目。没有配置真实用户API Key，也没有替用户注册、充值或真实执行一次模型推理，不能宣称食品结论已生成。

## 为什么不选豆包

豆包助手和联网搜索有独立收费。免费推理token不抵扣搜索插件费用，因此不满足用户明确要求的全程0元。核实依据见 `docs/CLOUD_AI_FEASIBILITY.md`。

## 真实云端模型入口与保护

接口：`https://openrouter.ai/api/v1/chat/completions`。

1. 默认只使用 `openrouter/free`；也可指定目录中真实存在的 `:free` 变体。
2. 禁止普通付费模型、`:online` 后缀、自动模型选择入口。
3. 每次推理前读取官方 `/api/v1/models`，确认该条目所有公布价格为0，目录不可用或价格不明就停止。
4. 请求 `provider.max_price` 的prompt/completion/request/image均为0，`data_collection=deny`，`allow_fallbacks=false`。
5. 不发送plugins、tools、web_search_options、models fallback列表，不启用计费联网插件。
6. 默认 `AI_ZERO_COST_ONLY=true`，即使机器遗留了OpenAI密钥和provider配置，也禁止调用收费OpenAI路径。
7. 402/403/429等响应直接提示免费服务受限，不自动充值、不调用收费替代。

这些保护基于官方文档与服务条款，不能替代服务商对账单的控制。建议使用不充值、不启用自动充值的独立免费账号，并在控制台检查使用记录。不要为了增加配额多开账号或绕过限流。

`openrouter/free`只承诺免费路由，不承诺每个底层模型都开源。若还必须使用某个开放权重/开源模型，需要另行核对其许可证和当前可用的免费变体，不能把“免费托管”与“开源许可证”混为一谈。

## 免费检索不是模型自带的联网能力

免费推理不代表免费联网插件。为保留权威来源核对，Java另调用本机的轻量开源SearXNG，然后筛选允许的官方域名、读取网页、把本次资料交给云端免费模型总结。

SearXNG只是搜索聚合服务，不是大模型，无GPU，不下载模型权重。公开搜索引擎可能受限；没有出处就停止，不把模型记忆当作实时权威答案。用户只要离线Demo时，不需要模型、Key或搜索服务。

当前检索只允许127.0.0.1/localhost的自托管HTTP服务，不使用来历不明的公共代理。需要Docker Desktop或其他自行部署SearXNG的方式。Docker Desktop须遵守其许可条件；个人开发与相关免费使用条件应由使用者确认。

## 操作步骤

### 1. 获取免费账号密钥

在 https://openrouter.ai 注册账号，进入 https://openrouter.ai/settings/keys 创建API Key。无需购买积分，不要设置自动充值。不要把Key发给助手、提交Git或写到小程序里。

### 2. 启动轻量搜索（不启动模型）

已安装并启动Docker Desktop时：

```powershell
cd D:\wtt\小程序\snack-design-backend
docker compose -f compose.zero-cost-cloud.yml up -d
```

该文件只有SearXNG，没有Ollama。旧 `compose.free-ai.yml` 属于历史本地模型方案，不要再使用。新 `start-free-ai.cmd` 已改为转到云端脚本，不会下载本地模型。

### 3. 设置密钥并启动Java

先在旧Java服务窗口按Ctrl+C停止8080旧服务。再在同一PowerShell窗口：

```powershell
cd D:\wtt\小程序\snack-design-backend
$env:OPENROUTER_API_KEY="在自己的终端填写密钥"
$env:AI_PROVIDER="openrouter"
$env:AI_ZERO_COST_ONLY="true"
$env:OPENROUTER_MODEL="openrouter/free"
mvn spring-boot:run
```

或设置Key后运行 `./start-zero-cost-cloud.cmd`：检查Key、启动轻量搜索、强制零费用云端模式并启动Java。没有安装Docker、下载失败、搜索引擎不可用时脚本会提示，不会偷偷转收费API。

### 4. 重新编译小程序

使用 `snack-design-frontend`，确认原文后自动进入分组解析页。免费模型可能排队、限流或不支持所需JSON格式；失败时显示真实错误，不返回虚构成功。

## 免费额度和数据隐私

免费请求存在账号级别/模型级别限流，额度以官方Key状态及控制台为准。不要在本地写死“每天无限次数”或保证固定免费额度。可通过官方 `/api/v1/key` 查看免费请求剩余额度。

本方案只发送配料名、食品类别及检索资料，不发送照片或用户ID；仍属于向云端服务发送内容。默认拒绝可能存储数据的提供商，若因此没有可用免费端点就停止，不静默放松隐私设置。不要上传私密医疗信息或商业机密配方。OpenRouter自身和上游的具体隐私条款仍应阅读并纳入小程序隐私声明。

## 验证状态

- 已成功读取官方免费变体、路由参数、限流文档。
- 已成功读取官方公开模型目录，确认本轮存在 `openrouter/free` 和若干 `:free` 零价格条目；不代表未来一直可用。
- 已提供零价格校验、禁止付费fallback/插件、零费用模式拦截收费provider的测试。
- 当前未配置真实Key，当前机器也没有运行SearXNG，因此尚未真实执行云端推理和食品资料检索；需要用户完成上述配置后验收。

## 官方依据

- 免费变体与收费fallback说明：https://openrouter.ai/docs/guides/routing/model-variants/free
- 价格过滤、数据收集与fallback控制：https://openrouter.ai/docs/guides/routing/provider-selection
- 免费请求限流与Key状态接口：https://openrouter.ai/docs/api/reference/limits
- 无认证公开模型目录：https://openrouter.ai/api/v1/models

不使用聊天产品订阅、非官方豆包Cookie接口或免费代理来绕过计费/权限。
