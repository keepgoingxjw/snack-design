# 历史方案：本机免费 AI（当前不采用）

> 用户已明确不在本机运行模型，默认现为OpenRouter云端零费用模式。请使用 [ZERO_COST_CLOUD.md](ZERO_COST_CLOUD.md)；下方仅保留历史说明，不要按它下载Ollama。`start-free-ai.cmd` 已转向云端脚本。

## 这是什么

默认后端改为 `AI_PROVIDER=ollama`：本机运行 Qwen2.5 模型推理，本机 SearXNG 负责实时检索，Java 只采纳允许的官方域名结果、尝试读取原文后再让模型总结。没有按次 API 费用，不需要 OpenAI 密钥，也不会失败后自动切换收费服务。

不是免费的托管云API：电脑承担内存/算力/电费，需要联网下载容器、数GB模型和搜索网页。个人开发可使用开源运行组件；Docker Desktop的使用还需遵守其当前许可条款。普通本地模型未必比云模型快或准确，CPU运行可能需要几分钟。

本轮机器未检测到 Docker / Ollama，因此适配代码已测试，但**真实模型下载、真实公开搜索引擎、真实苯甲酸钠结果尚未验收**。不能将模拟HTTP测试当作真实食品结论。

## 1. 安装前提

- 安装并启动 Docker Desktop（Windows按官方要求启用WSL2/虚拟化）。下载：https://www.docker.com/products/docker-desktop/
- 建议16GB以上内存、至少10GB额外磁盘空间；模型使用 `qwen2.5:7b`。低配电脑可按下文改为 `qwen2.5:3b`，但输出质量可能下降。
- 保持网络能下载官方镜像、模型并访问公开搜索引擎。网络限制/搜索引擎封锁可能使查询失败，不会伪造结果。
- 本机已有Java21和Maven。

## 2. 停止旧的Java服务

在旧Java后端窗口按 Ctrl+C，直到8080端口释放。此前检测到8080健康检查只有 `{"status":"ok"}`、查询路由404，是未提供新接口的旧运行实例，不是配料名称错误。

新前端会检查 `/api/health` 的 `apiVersion=3`、`researchAvailable=true`。旧实例会明确提示重启，不再让用户反复点击不存在的接口。

## 3. 运行免费启动脚本

双击本目录 `start-free-ai.cmd`。

脚本会：

1. 检查Docker是否已安装并运行，不会自动安装系统软件。
2. 征求确认后下载/启动Ollama、SearXNG容器。
3. 下载Qwen模型（数GB，已有模型不重复完整下载）。
4. 检查8080是否仍被旧服务占用；不会擅自结束你的进程。
5. 以免费模式启动最新Java服务。

保持Java窗口打开。浏览器访问 `http://127.0.0.1:8080/api/health`，应有 `apiVersion:3` 和 `aiModel:"ollama/qwen2.5:7b"`。其中aiConfigured代表配置已选定，不代表模型、网络或搜索引擎已验收可用。

Compose文件为 `compose.free-ai.yml`，两个服务端口只发布到127.0.0.1；SearXNG的开发secret不适合公网。模型卷保留下载内容，`docker compose -f compose.free-ai.yml down` 只停止容器，不要加 `-v`，否则会删除模型卷。

也可手动运行：

```powershell
cd D:\wtt\小程序\snack-design-backend
docker compose -f compose.free-ai.yml up -d
docker compose -f compose.free-ai.yml exec -T ollama ollama pull qwen2.5:7b
$env:AI_PROVIDER="ollama"
mvn spring-boot:run
```

如果你原生安装了Ollama，也可只用Compose启动searxng：`docker compose -f compose.free-ai.yml up -d searxng`，原生Ollama要运行在11434并下载相同模型。不要让两个Ollama实例同时抢占11434。

## 4. 小程序操作

重新编译 `snack-design-frontend`，输入配料后点击确认上传，**直接进入原设计的分组两列解析页，自动查询并填入结果**，不再先展示一个查询设置页。

食品类别移到原文编辑页下方的选填项，例如“碳酸饮料、酱油”，不是“苯甲酸钠”这种配料名。可以不填，但用量无法匹配适用条件时不会生成具体数值。

已保存结果可直接读取，不自动重查；从编辑页提交新原文会触发自动解析。离开页面暂停前端轮询，不取消后台任务。界面保留来源、摘录和未审核提醒。

免费模式只把配料名/食品类别作为搜索关键词发给公开搜索引擎，本地模型接收检索上下文；照片不上传。AI_PROVIDER改为openai时则改走云端，可能收费，不会自动切换。

## 5. 配置

| 环境变量 | 默认值 |
|---|---|
| AI_PROVIDER | ollama；可手动改openai，但不是免费路径 |
| OLLAMA_URL | http://127.0.0.1:11434 |
| SEARXNG_URL | http://127.0.0.1:8888 |
| OLLAMA_MODEL | qwen2.5:7b |

为防止给不明远程代理传数据，免费服务地址只允许本机HTTP。

低配模型：先执行 `docker compose -f compose.free-ai.yml exec -T ollama ollama pull qwen2.5:3b`，再设 `$env:OLLAMA_MODEL="qwen2.5:3b"` 手动启动Java（不要用会指定7b的一键脚本）。

## 6. 资料和安全边界

- SearXNG是搜索聚合器，不是权威机构；搜索结果必须经过官方域名过滤，网页摘要不当作核实原文。
- 摘录核对、用量条件校验沿用 `docs/AI_RESEARCH.md` 的保护。PDF/重定向/非UTF-8等无法读取时，不能冒充正文已核实。
- 每个配料最多提供两个检索来源给本地模型；一次任务最多读取12个网页正文，模型接收正文片段。超出部分/无正文可能导致资料不足。
- AI语义、标准现行性和食品类别映射仍需人工复核。用途不等于健康收益，无资料不等于安全，不提供个人食用建议。
- 首次启动Docker镜像采用latest标签，尚未实际下载验证；正式部署应固定验收过的镜像摘要，并单独配置安全、备份和限流。
- 第三方搜索引擎可能验证码/限流/失效，不能保证免费公共网页检索永久可用。可在SearXNG中配置自己可访问的引擎，但不要绕过引擎的访问限制。

## 常见提示

- **当前连接的是旧版后端**：停止8080上的旧Java程序，从本目录重启。
- **未连接本机免费AI**：检查Ollama容器/原生服务是否启动。
- **本机尚未下载模型**：下载与OLLAMA_MODEL完全一致的名称。
- **未连接本机免费搜索服务**：检查8888端口、SearXNG JSON格式配置和可用引擎。
- **未找到官方来源**：可能确实没有检索结果，或搜索网络不可用，不会用模型记忆代替证据。
- **模型输出不完整/超时**：减少单次配料数量、使用更快硬件或更小模型后重试。
