#!/usr/bin/env bash
# 一键重启后端：停旧进程 -> （可选）打包 -> 后台启动 -> 健康检查轮询
# 用法：
#   scripts/restart-backend.sh              # 打包并重启
#   scripts/restart-backend.sh --skip-build # 跳过打包，直接重启现有 jar
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-C:/Users/57299/.posh_sdk/java/21.0.9-oracle}"
export PATH="$JAVA_HOME/bin:$PATH"
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
  echo "开始打包后端（mvn -DskipTests clean package）..."
  # 必须带 clean：maven-jar-plugin 默认 forceCreation=false，app 模块自身没改动时直接跳过重打，
  # fat jar 里的 BOOT-INF/lib/module-*.jar 还是上一次的，症状是"改了代码重启却不生效、也不报错"
  (cd "$ROOT/backend" && mvn -q -DskipTests clean package)
fi

# 3. 取 jar：jar 名带版本号，写死每次转版本都要回来改（0f 就改过一回），按前缀取最新修改的那个。
#    必须在打包之后取：转版后的首次运行，target 里只有旧版 jar，按名字取会挑错。
#    spring-boot repackage 会留下同名 .jar.original，必须排除。
JAR=$( { ls -t "$ROOT"/backend/app/target/jiaopei-platform-backend-*.jar 2>/dev/null || true; } | grep -v '\.original$' | head -1)
if [[ -z "$JAR" ]]; then
  echo "错误：backend/app/target 下没有 jiaopei-platform-backend-*.jar，先跑不带 --skip-build 的完整流程" >&2
  exit 1
fi
echo "使用 jar：$JAR"

# 4. 后台启动
nohup "$JAVA_HOME/bin/java" -jar "$JAR" > "$LOG_DIR/backend-restart.log" 2>&1 &
echo "后端已后台启动 PID=$! 日志：logs/backend-restart.log"

# 5. 健康检查（最多 60 秒）
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
