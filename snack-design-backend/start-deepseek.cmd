@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
echo 食有数Bite：DeepSeek官方云端搜索、网页抓取和AI总结。
echo 本模式会消耗你配置的DeepSeek账户额度，不使用本地大模型、Docker或其他平台fallback。
echo 密钥请在本目录.env或DEEPSEEK_API_KEY环境变量配置，勿粘贴到聊天或前端。
powershell -NoProfile -Command "if(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue){exit 1}"
if errorlevel 1 goto :port_busy
set AI_PROVIDER=deepseek
set AI_ZERO_COST_ONLY=false
call mvn spring-boot:run
pause
exit /b 0
:port_busy
echo 8080仍被旧后端占用。请先在旧服务窗口按Ctrl+C停止，再重新运行本脚本。
pause
exit /b 1
