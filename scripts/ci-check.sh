#!/usr/bin/env bash
# 本地 CI 门禁：与 .github/workflows/ci.yml 的 job 等价（当前远程是 Gitee，Actions 不执行时用本脚本）
# 依次执行：后端单测（含 ArchUnit 架构规则）-> 前端 ESLint -> 前端类型检查+构建
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-C:/Users/57299/.posh_sdk/java/21.0.9-oracle}"
export PATH="$JAVA_HOME/bin:$PATH"

echo "==> [1/3] 后端 mvn test"
(cd "$ROOT/backend" && mvn -q test)

echo "==> [2/3] 前端 eslint"
(cd "$ROOT/frontend" && pnpm -s lint)

echo "==> [3/3] 前端 vue-tsc + vite build"
(cd "$ROOT/frontend" && pnpm -s build)

echo "全部通过 ✔"
