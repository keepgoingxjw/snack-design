# snack-design-backend · 食有数Bite Java 后端

Java21 + Spring Boot + JDBC + H2。提供登录、用户原文记录、异步调研、来源与用量校验、条件性食用建议和历史快照。小程序通过HTTP API访问，不直接连接数据库。

## 当前默认：DeepSeek 官方云端联网解析

用户已选择使用自己的DeepSeek付费Key。当前默认 `AI_PROVIDER=deepseek`、`AI_ZERO_COST_ONLY=false`，由官方Search/Fetch接口检索网页，再调用模型生成中文解析，不需要Docker、SearXNG或本地大模型，不回退其他平台。

请按 **[DEEPSEEK.md](DEEPSEEK.md)** 在后端 `.env` 或环境变量配置 `DEEPSEEK_API_KEY`，停止旧Java服务后运行 `start-deepseek.cmd`。示例见 `.env.deepseek.example`，已有.env不要整文件覆盖。Search/Fetch及模型总结分别计费，密钥不能发到聊天或放入前端。本轮未使用真实Key发起计费调用。

## 历史可选：免费中文配料 AI 解释

需要“输入任意配料 → 联网查资料 → 中文解释与出处”时，请按 [CLOUD_FREE_AI.md](CLOUD_FREE_AI.md) 配置本机 `.env`，选择 `AI_PROVIDER=openrouter`。
此模式由免费 AI 动态生成查询词，不受下方旧词表演示限制；使用公开文献 API，无需 Docker 或付费搜索。没有可靠资料时如实说明，不承诺所有输入都能获得确定结论。
下方 `public-search` 仅为历史无 AI 检索模式，不代表通用 AI 能力。

## 历史可选：无密钥公开检索闭环

显式设置 `AI_PROVIDER=public-search` 后可运行 `mvn spring-boot:run`，此历史模式无需AI密钥、Docker或本地模型。
该模式为配料输入 → Europe PMC 官方公开文献 API → 后端保存快照 → 小程序自动轮询并显示标题、英文摘要节选和可复制来源链接。
可先输入 `苯甲酸钠、柠檬酸、食盐` 验证；常见中文名称使用明确的英文词表，未覆盖中文名称会提示，不猜测翻译。也支持英文配料名称。
只检索食品/饮食相关、带摘要的开放文献；这是文献检索，未调用 AI，不提供食品安全判断或食用建议。空结果如实显示，429/网络失败立即停止，不自动重试、切收费 API 或使用虚构资料。
API 依据：https://europepmc.org/RestfulWebService 。公开 API 不等于整套服务代码开源，文献各自的许可证仍适用。
前端重新编译同级 `snack-design-frontend`。健康检查 `aiModel` 应为 `public-search/EuropePMC`；`aiConfigured` 在此表示查询模式可用，不代表配置了 AI。

## 可选：云端AI，零调用费

**OpenRouter免费模型 + 独立免费网页检索**。显式选择 `AI_PROVIDER=openrouter`、`AI_ZERO_COST_ONLY=true`，不需要本机大模型/GPU，不使用豆包收费搜索，不启用付费模型fallback。免费额度不可用就停止。

完整启动说明及官方依据见 **[ZERO_COST_CLOUD.md](ZERO_COST_CLOUD.md)**。需要注册免费的OpenRouter API Key，只有轻量搜索服务需要单独运行，云端模型无需下载。API密钥不要发送到聊天、前端或Git。

在同一PowerShell窗口：

```powershell
cd D:\wtt\小程序\snack-design-backend
$env:OPENROUTER_API_KEY="在本机终端填写你的免费Key"
$env:AI_PROVIDER="openrouter"
$env:AI_ZERO_COST_ONLY="true"
./start-zero-cost-cloud.cmd
```

脚本只启动SearXNG轻量搜索容器与Java，不下载/运行Ollama。需要先安装并启动Docker Desktop，或自行在127.0.0.1:8888运行SearXNG后单独 `mvn spring-boot:run`。此搜索服务不是大模型，没有GPU或模型权重。没有来源时不会把模型记忆当作实时核实。

旧 `start-free-ai.cmd` 已转向云端脚本。历史 `FREE_AI.md` / `compose.free-ai.yml` 描述本机模型，不是当前方案，不要继续用于本需求。

本机没有配置真实Key与搜索运行组件，因此尚未真实调用模型。代码/模拟测试完成不等于真实食品分析已核实。免费不代表永久在线、无限请求或免注册；服务商规则变化时会停止，不擅自收费。

## 零费用保护

- 只接受 `openrouter/free` 或目录真实存在的 `:free`；禁止 `:online`、普通付费模型。
- 调用前检查公开目录所有价格为0，无法确认则停止。
- 请求provider的prompt/completion/request/image价格上限均为0，拒绝数据收集提供商，不启用fallback。
- 不发送付费plugins、tools或models候选列表。
- 402/403/429不充值、不切付费模型。
- 显式启用零费用模式时会拦截DeepSeek/OpenAI等付费provider；当前DeepSeek接入已获用户付费授权，使用false。

免费托管不等于开源许可；若指定开放权重模型，还需核对许可证及免费变体可用性。豆包官方方案为什么不满足严格0元，见 [docs/CLOUD_AI_FEASIBILITY.md](docs/CLOUD_AI_FEASIBILITY.md)。

## 健康检查与前端

健康检查 `http://127.0.0.1:8080/api/health` 应有 `apiVersion:3`、`researchAvailable:true`。若只有 `status:ok`，仍运行旧版本，请在旧Java窗口Ctrl+C停止再重启。默认local只监听127.0.0.1，测试登录不能暴露公网。

导入同级 `snack-design-frontend`，本地开发工具关闭合法域名校验后编译。真实原文确认后自动进入分组解析；已有结果直接读取，不反复自动调用。首页Demo完全离线，不依赖本后端。

## 数据库和报告

从本目录启动后，文件为 `data/snack.mv.db`，重启保留，不要删除/提交Git。

- `app_users` 用户；`sessions` 会话token摘要。
- `ingredient_records` 原文与录入来源，列表最近50条。
- `research_reports` 用户隔离的查询、原文快照、来源、摘录、模型、建议和时间。
- 旧 `ingredient_knowledge` 示例表不参与真实流程，示例接口返回410，自动导入在正常模式关闭。

更新原文不把旧报告当新结果，删除记录级联删除报告。AI报告不是人工审核知识。当前单实例H2，迁移MySQL需驱动、DDL及数据迁移，不能只改URL。

来源/用量边界：[docs/AI_RESEARCH.md](docs/AI_RESEARCH.md)。最后建议规则：[docs/ADVICE.md](docs/ADVICE.md)。缺少实际用量和个人健康信息，不保证“吃一点没事”，也不按名称宣判整款食品能吃/不能吃。

## API

除健康检查/登录，均需Bearer会话；错误JSON含message。

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/health` | 状态与版本 |
| GET | `/api/research/readiness` | 当前查询模式配置检查，不调用 AI |
| POST | `/api/auth/dev` | 仅local测试登录 |
| POST | `/api/auth/wechat` | 微信code校验 |
| GET/POST/DELETE | `/api/records` | 列表/创建/清空 |
| GET/PUT/DELETE | `/api/records/{id}` | 查询/修改/删除 |
| POST | `/api/records/{id}/research` | 异步任务：foodCategory、refresh |
| GET | `/api/records/{id}/research/latest` | 当前原文最新结果 |
| GET | `/api/records/{id}/research/history` | 最近10次快照 |
| GET | `/api/research/{id}` | 进度及报告 |

记录输入raw最多5000字、30项，保留复合括号；kind为手动输入/虚构示例/照片手动录入。全局2个工作线程、10个等候任务，每用户1个活动任务及默认24小时30次限制。服务重启标记未完成任务失败，不自动再次调用。

## 正式部署

设置prod、WECHAT_APP_ID、WECHAT_APP_SECRET、DEEPSEEK_API_KEY及数据库密码，明确选择deepseek与允许付费模式，前端改wechat登录与HTTPS合法域名。需补隐私指引、备份、限流、监控；不自动购买服务器或开通后付费。手机127.0.0.1不是电脑。

## 验证

```powershell
mvn test
mvn package
node scripts/smoke-test.js
node scripts/public-search-smoke.js
```

测试使用独立内存库或临时文件库，验证权限、持久化、来源、用量、建议与零费用保护；不调用付费服务，也不冒充真实云端验收。OCR拍照文字识别使用Tesseract传统引擎（见OCR.md），不调用DeepSeek，结果仅回填现有原文框；商品库、人工知识审核后台、完整过敏原检测和营养安全评级未实现。
