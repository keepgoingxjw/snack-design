# DeepSeek 官方联网解析接入

## 当前链路

```text
小程序确认原文
→ Java创建异步任务
→ DeepSeek Search API检索官方站点（返回正文）
→ 正文缺失时，在上限内调用DeepSeek Fetch API
→ DeepSeek Chat生成中文JSON解析
→ 服务端校验配料名、来源、摘录、用量和条件性建议
→ 保存用户独立快照
→ 前端分组展示、轮询进度、复制来源和历史回看
```

所有云端搜索、抓取和模型请求均使用你的DeepSeek官方Key。**不需要Docker、SearXNG或Ollama，不调用OpenRouter/OpenAI，也不受固定配料词表限制。** 旧免费/公开文献模式仅作为可显式选择的兼容代码保留，DeepSeek失败时不会回退调用。

## 好处与风险的总结方式

DeepSeek输出现在按短要点组织：`benefit`优先2—3条、`risk`优先1—4条，每条“• 关键词：简短解释”，先讲重点并保留适用条件；证据少时少写，不凑条数。不重复堆叠“本次资料未提供”，也不把法规使用限制当成人体危害。

检索词同时关注用途、健康风险与安全评估，优先NHC、EFSA、FDA、WHO等已允许的官方来源；每项仍只发起1次Search，返回5条、最多采用2份正文，不增加搜索轮次或调用上限。没有依据不补写成本、蓄积、肝肾负担、儿童多动等结论，原有来源/用量/建议保护不变。

报告模型标记增加“要点总结v2”，只用于区分输出版本和缓存，不代表换了底层模型。旧报告不会自动改写或自动付费重查；重启后端、重新编译前端后，用户可通过现有“使用DeepSeek重新解析”按钮生成新版。页面仍是原来的分组两列，只把两段文本显示为分条、加粗关键词；原文摘录和来源保留。

## 1. 配置密钥（不要发到聊天）

当前目录结构已整理为：

```text
D:\wtt\小程序\snack-design\
  snack-design-backend\
  snack-design-frontend\
```

### 方式A：后端.env

后端已支持读取根目录 `.env`（properties语法）。参考 `.env.deepseek.example`，在你自己的 `snack-design-backend/.env` 添加或修改：

```properties
AI_PROVIDER=deepseek
AI_ZERO_COST_ONLY=false
DEEPSEEK_API_KEY=在自己电脑中填写真实密钥
DEEPSEEK_MODEL=deepseek-flash
DEEPSEEK_MAX_SEARCHES=30
DEEPSEEK_MAX_FETCHES=4
```

**已有.env时不要整文件覆盖，不要删除原微信AppSecret、数据库等设置。** 不加export，不给值包引号。`.env`已被Git忽略；示例文件不含真实密钥。Key不能放在前端、聊天、截图或提交中。

`AI_ZERO_COST_ONLY=false` 是这次用户已选择付费DeepSeek后的显式模式；如果遗留为true，服务会拦截付费调用并明确提示，不能悄悄绕过零费用保护。

### 方式B：PowerShell环境变量

```powershell
cd D:\wtt\小程序\snack-design\snack-design-backend
$env:DEEPSEEK_API_KEY="仅在本机终端填写密钥"
$env:AI_PROVIDER="deepseek"
$env:AI_ZERO_COST_ONLY="false"
$env:DEEPSEEK_MODEL="deepseek-flash"
.\start-deepseek.cmd
```

脚本会强制本次Java进程使用DeepSeek和允许付费模式，避免旧的OpenRouter/零费用环境变量继续生效，不会输出密钥。如果使用.env，也可以直接双击 `start-deepseek.cmd`。

当前模型别名 `deepseek-flash` 来自本轮官方文档；实际可用模型以你的控制台为准，可通过DEEPSEEK_MODEL修改。报告记录请求别名；服务商可能更新该别名对应的底层模型版本。

## 2. 启动与前端

1. 在旧Java窗口按Ctrl+C停止8080旧实例。
2. 使用上面的启动脚本，保持窗口打开。
3. 打开 `http://127.0.0.1:8080/api/health`，应看到 `apiVersion:3`、`aiModel`以`DeepSeek/deepseek-flash`开头并含`要点总结v2`、`aiConfigured:true`、`zeroCostOnly:false`。
4. `aiConfigured`仅代表读到了Key，**不代表已验证Key、余额、模型或Search/Fetch权限**。readiness也不会通过实际计费请求测试它们，真实查询时才核实。
5. 微信开发者工具导入/编译 `snack-design-frontend`，在真实输入入口填写配料并确认，直接进入分组解析并自动查询。
6. 之前的免费AI/公开文献报告保留为历史。当前配置不同会标注“旧模式结果”；点击“使用DeepSeek重新解析”或明确提交原文才重新调用。仅浏览历史不自动扣费重查。
7. 首页虚构Demo仍完全离线，不调用DeepSeek、不写入真实用户记录。

## 3. 实际使用的官方接口

| 功能 | 地址 | 关键参数 |
|---|---|---|
| 搜索 | `POST https://api.deepseek.com/v1/tools/search` | query、count=5、need_content=true |
| 正文抓取 | `POST https://api.deepseek.com/v1/tools/fetch` | url，仅本次检索的官方域名来源 |
| 中文总结 | `POST https://api.deepseek.com/chat/completions` | model、messages、response_format=json_object、thinking=disabled |

均使用 `Authorization: Bearer <DEEPSEEK_API_KEY>`，只由Java发送。没有将OpenRouter免费价格限制、插件参数、模型fallback列表透传至DeepSeek。

这是真正的“Search/Fetch独立API + 模型总结”，不是只调用聊天API却声称已联网，也不是抓取DeepSeek网页聊天/Cookie。

## 4. 来源与安全边界

- 优先查询NHC、EFSA、FDA、WHO等用途/健康风险评估站点；返回网址仍需通过既有官方域名白名单，模型不能伪造URL并使服务去抓取。
- 每项最多交给模型2个有正文的出处，整份上下文最多约48000字符并按配料数分配，避免大量不必要输入费用。
- 模型每项配料必须与输入原名和顺序匹配。
- 摘录必须出现在该配料本次DeepSeek搜索/抓取返回的正文片段中。网址未检索或引用不匹配则丢弃该来源，无有效来源时降级“资料不足”，不推断安全或危害。
- 摘录匹配表示“与DeepSeek返回网页内容相符”，不是独立访问原网站的验证，更不是人工科学/法规审核。页面保留该限制说明。
- 用量必须有食品类别、中国大陆来源、标准/版本、数值和单位及匹配原文；缺少条件不提供数字。不能把ADI/国外标准当作该食品添加限量。
- “最后建议”仍为条件性提示，不保证“吃一点没事”，不在缺乏实际用量和健康信息时作整款食品能吃/不能吃判定。
- 没有正文时暂停实质总结，不使用模型记忆或硬编码配料解释回填成功结果。

## 拍照文字识别（OCR）

拍照识字已按最新要求改用传统Tesseract OCR，**不调用DeepSeek、不需要AI Key、不收AI API调用费**。前端专用接口为 `/api/ocr/tesseract`，结果只回填现有核对框；旧识字接口也已在新版后端替换为同一传统引擎，没有云端识字回退。图片只上传应用后端，不发送给DeepSeek。

运行配置、语言包、实测及识别误差说明见 [OCR.md](OCR.md)。本文件其余内容只描述用户确认原文后的配料联网解析，该流程本次不改。

## 5. 费用、上限与错误

这是付费模式，不再宣称“全程0元”。本轮官方价格页显示：Search和Fetch各按成功请求计费，刊例为每1000次5元；模型推理按token另算，具体单价和适用规则以DeepSeek最新官方页面及账户账单为准。不要把模型Key可用理解为所有接口免费。

- 单份报告：每项1次Search，最多30次；最多4次额外Fetch；最多1次模型总结。
- 可通过 `DEEPSEEK_MAX_SEARCHES`（1—30）和 `DEEPSEEK_MAX_FETCHES`（0—30）调整；超过搜索上限会在任何付费请求前停止，提示分批。
- 模型输出最多12000 tokens，关闭思考模式；输出截断/空JSON不自动再次付费重试。
- 原有每用户24小时任务数限制仍在（AI_DAILY_LIMIT默认30）。生产环境还需账号级/全局预算、网关限流和滥用防护；所有C端用户共享应用后端配置的Key，费用归该Key所属账号。
- 401密钥无效、402余额不足、429限流、Search权限/服务错误均明确停止，不自动重复请求或切换其他平台。Fetch对单个网页403/404会跳过，不抓取模型自报的任意URL。
- **最终报告失败不等于整次零费用**：此前已成功的Search/Fetch可能已计费。额度和账单请查看平台，不输出未经核实的单次费用估算。

日志只记录请求阶段、官方接口路径、HTTP状态和异常类别，不记录Key、请求正文或上游原始错误body。

## 6. 已验证与待验收

已提供模拟官方Search/Fetch/Chat响应的适配器测试：实际请求路径、唯一平台、无Key不调用、费用上限、原文与伪造引用、余额不足、JSON截断；另验证路由付费开关、报告缓存不重复调用、前端旧模式识别。

本轮没有收到或使用你的真实Key，**尚未完成真实计费调用与微信真机验收**。配置后先只输入一种配料，核对平台调用记录与前端来源，再扩大数量。不要把测试数据当成真实食品建议。

## 官方资料核对

本轮成功读取以下官方文档正文：

- 首次调用API、当前模型别名：https://api-docs.deepseek.com/zh-cn/
- Search/Fetch接口、字段、来源与计费：https://api-docs.deepseek.com/zh-cn/guides/search
- 模型及Search/Fetch计费：https://api-docs.deepseek.com/zh-cn/quick_start/pricing
- JSON输出及空content/截断注意：https://api-docs.deepseek.com/zh-cn/guides/json_mode
- thinking开关：https://api-docs.deepseek.com/zh-cn/guides/thinking_mode

官方模型与价格会调整；账号权限和实际网络可用性需真实配置后验收，不应拿过期的“DeepSeek API不能联网”印象替代当前文档。
