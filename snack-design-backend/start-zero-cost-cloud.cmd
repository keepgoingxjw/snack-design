@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo 云端零调用费模式：不下载模型，不启动Ollama，不使用付费AI或搜索插件。
if not defined OPENROUTER_API_KEY goto :no_key
where docker >nul 2>&1
if errorlevel 1 goto :no_search
docker info >nul 2>&1
if errorlevel 1 goto :no_search
powershell -NoProfile -Command "if(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue){exit 1}"
if errorlevel 1 goto :port_busy
echo 仅启动轻量搜索容器SearXNG，不包含任何大模型或GPU服务。
docker compose -f compose.zero-cost-cloud.yml up -d
if errorlevel 1 goto :failed
set AI_PROVIDER=openrouter
set AI_ZERO_COST_ONLY=true
if not defined OPENROUTER_MODEL set OPENROUTER_MODEL=openrouter/free
call mvn spring-boot:run
pause
exit /b 0
:no_key
echo 请先注册OpenRouter免费账号，在同一PowerShell窗口设置OPENROUTER_API_KEY，再运行本脚本。
echo 无需充值，不要开启自动充值，也不要把密钥发到聊天。
echo 详细步骤见 ZERO_COST_CLOUD.md。
pause
exit /b 1
:no_search
echo AI算力在云端，不需要本机模型。联网核实另需运行轻量搜索服务。
echo 当前未检测到运行中的Docker。可先安装并启动Docker Desktop，或使用已经运行在8888的本机SearXNG，再单独启动Java。
echo 未配置检索时不会使用付费搜索或假装已核实。详细步骤见 ZERO_COST_CLOUD.md。
pause
exit /b 1
:port_busy
echo 8080仍被旧Java服务占用。请在旧窗口按Ctrl+C停止，再重新运行。
pause
exit /b 1
:failed
echo 搜索容器未启动，请检查上方错误。不会切换到任何收费服务。
pause
exit /b 1
