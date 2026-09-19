#!/usr/bin/env bash
# 一键重启后端：停旧进程 -> （可选）打包 -> 后台启动 -> 健康检查轮询
# 用法：
#   scripts/restart-backend.sh              # 打包并重启
#   scripts/restart-backend.sh --skip-build # 跳过打包，直接重启现有 jar
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-C:/Users/57299/.posh_sdk/java/21.0.9-oracle}"
export PATH="$JAVA_HOME/bin:$PATH"
JAR="$ROOT/backend/target/jiaopei-platform-backend-0.0.1-SNAPSHOT.jar"
LOG_DIR="$ROOT/logs"
mkdir -p "$LOG_DIR"

# 1. 停掉监听 8080 的旧进程
PIDS=$(netstat -ano | grep ':8080' | grep LISTENING | awk '{print $5}' | sort -u || true)
if [[ -n "$PIDS" ]]; then
  for pid in $PIDS; do
    echo "停止旧进程 PID=$pid"
    powershell -NoProfile -Command "Stop-Process -Id $pid -Force" || true
  done
else
  echo "8080 无监听进程，跳过停止"
fi

# 2. 打包
if [[ "${1:-}" != "--skip-build" ]]; then
  echo "开始打包后端（mvn -DskipTests package）..."
  (cd "$ROOT/backend" && mvn -q -DskipTests package)
fi

# 3. 后台启动
nohup "$JAVA_HOME/bin/java" -jar "$JAR" > "$LOG_DIR/backend-restart.log" 2>&1 &
echo "后端已后台启动 PID=$! 日志：logs/backend-restart.log"

# 4. 健康检查（最多 60 秒）
for _ in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/health || true)
  if [[ "$code" == "200" ]]; then
    echo "健康检查通过（/api/health 200）"
    exit 0
  fi
  sleep 1
done
echo "错误：60 秒内健康检查未通过，请查看 logs/backend-restart.log" >&2
exit 1
