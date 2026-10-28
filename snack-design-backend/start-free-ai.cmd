@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo 已按新要求切换为云端免费AI，不再下载或启动本机大模型。
call "%~dp0start-zero-cost-cloud.cmd"
