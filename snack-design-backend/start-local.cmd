@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo 启动食有数 Java 后端，关闭窗口或按 Ctrl+C 停止。
echo 健康检查：http://127.0.0.1:8080/api/health
call mvn spring-boot:run
pause
