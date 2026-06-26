@echo off
echo === Prometheus starting on port :9090 ===
cd /d "D:\prometheus-3.13.0-rc.1.windows-amd64"
prometheus.exe --config.file=prometheus.yml
pause
