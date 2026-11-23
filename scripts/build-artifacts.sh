#!/usr/bin/env bash
# 本地构建产物：在开发机/非突发型机器上生成 app.jar + dist，放到 docker/artifacts/。
#
# 用途：GitHub Actions 之外的手工兜底。不要在生产服务器上跑（会烧 CPU 积分）。
#
# 用法：
#   bash scripts/build-artifacts.sh              # 生成 docker/artifacts/{app.jar,dist}
#   bash scripts/build-artifacts.sh --tar        # 另外打包成 docker/artifacts/release.tar.gz
#
# 生成后传输到服务器（任选其一）：
#   # A. 直接投到服务器 releases/（与 CI 产物同构，服务器用 deploy.sh --from-ci <目录名>）
#   ssh <user>@<host> 'mkdir -p /mnt/software/jiaopei/releases/local-$(date +%Y%m%d-%H%M%S)'
#   # 然后 scp docker/artifacts/app.jar 与 docker/artifacts/dist 到该目录
#   # B. 打包后 scp release.tar.gz，服务器解包到 releases/<目录名>/
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAKE_TAR=false
for arg in "$@"; do
  case "$arg" in
    --tar) MAKE_TAR=true ;;
    -h|--help)
      sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "未知参数：$arg（可用 --tar）" >&2; exit 2 ;;
  esac
done

if [ -f "$ROOT/scripts/lib/jdk21.sh" ]; then
  # shellcheck source=lib/jdk21.sh
  . "$ROOT/scripts/lib/jdk21.sh"
  ensure_jdk21
fi

command -v mvn  >/dev/null 2>&1 || { echo "未找到 mvn" >&2; exit 1; }
command -v pnpm >/dev/null 2>&1 || { echo "未找到 pnpm" >&2; exit 1; }

echo "==> [1/3] 后端 mvn package（跳过测试，线程 2）"
(cd "$ROOT/backend" && env MAVEN_OPTS="${MAVEN_OPTS:--Xmx512m -XX:MaxMetaspaceSize=256m}" \
  mvn -B -ntp -q -DskipTests -Dmaven.artifact.threads=2 -Dmaven.compiler.threads=2 clean package)

JAR=$(ls -t "$ROOT"/backend/app/target/jiaopei-platform-backend-*.jar 2>/dev/null | head -1 || true)
[ -n "$JAR" ] || { echo "未找到后端 jar" >&2; exit 1; }

echo "==> [2/3] 前端 pnpm build"
(cd "$ROOT/frontend" && pnpm build)
[ -f "$ROOT/frontend/dist/index.html" ] || { echo "未找到前端 dist" >&2; exit 1; }

echo "==> [3/3] 归集到 docker/artifacts/"
mkdir -p "$ROOT/docker/artifacts"
rm -rf "$ROOT/docker/artifacts/app.jar"
cp -f "$JAR" "$ROOT/docker/artifacts/app.jar"
rm -rf "$ROOT/docker/artifacts/dist"
cp -R "$ROOT/frontend/dist" "$ROOT/docker/artifacts/dist"

if [ "$MAKE_TAR" = true ]; then
  tar -czf "$ROOT/docker/artifacts/release.tar.gz" \
    -C "$ROOT/docker/artifacts" app.jar dist
  echo "已打包：docker/artifacts/release.tar.gz"
fi

echo "完成："
du -h "$ROOT/docker/artifacts/app.jar"
du -sh "$ROOT/docker/artifacts/dist"
echo
echo "提示：本机若就是目标服务器（不要在突发型机器上这样做），可直接 ./docker/deploy.sh --from-ci latest"
echo "      否则把 app.jar 与 dist 传到服务器 /mnt/software/jiaopei/releases/<目录名>/ 后执行 --from-ci"
