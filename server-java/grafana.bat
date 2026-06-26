@echo off
echo === Grafana starting on port :3001 ===
echo Login: http://localhost:3001  (admin / admin)
echo.
set GF_SERVER_HTTP_PORT=3001
"D:\GrafanaLabs\grafana\bin\grafana.exe" server --homepath="D:\GrafanaLabs\grafana"
pause
