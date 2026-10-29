@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo 免费云端AI + 公开文献检索：无需Docker，不启动本地模型，不使用付费搜索。
echo 请先在本机.env填写OPENROUTER_API_KEY，或在当前终端设置环境变量。
set AI_PROVIDER=openrouter
set AI_ZERO_COST_ONLY=true
if not defined OPENROUTER_MODEL set OPENROUTER_MODEL=openrouter/free
call mvn spring-boot:run
pause
