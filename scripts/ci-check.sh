#!/usr/bin/env bash
# 本地 CI 门禁：与 .github/workflows/ci.yml 的 job 一一对应（当前远程是 Gitee，Actions 不执行时用本脚本）
# 依次执行：后端单测（含 ArchUnit 架构规则）-> 前端 ESLint -> 前端类型检查+构建 -> 小程序类型检查
# 用法：bash scripts/ci-check.sh            # 全跑
#       bash scripts/ci-check.sh backend    # 只跑某一步，可选 backend|frontend-lint|frontend-build|miniapp
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/jdk21.sh
. "$ROOT/scripts/lib/jdk21.sh"

run_backend() {
  echo "==> [1/4] 后端 mvn test"
  ensure_jdk21
  (cd "$ROOT/backend" && mvn -q test)
}

run_frontend_lint() {
  echo "==> [2/4] 前端 eslint"
  (cd "$ROOT/frontend" && pnpm -s lint)
}

run_frontend_build() {
  echo "==> [3/4] 前端 vue-tsc + vite build"
  (cd "$ROOT/frontend" && pnpm -s build)
}

run_miniapp() {
  echo "==> [4/4] 小程序 vue-tsc --noEmit"
  (cd "$ROOT/miniapp" && pnpm -s type-check)
}

# 不带参数跑全部；带参数只跑指定步骤（调试时省时间）
case "${1:-all}" in
  all)
    run_backend
    run_frontend_lint
    run_frontend_build
    run_miniapp
    echo "全部通过 ✔"
    ;;
  backend) run_backend ;;
  frontend-lint) run_frontend_lint ;;
  frontend-build) run_frontend_build ;;
  miniapp) run_miniapp ;;
  *)
    echo "未知步骤：$1（可选 all|backend|frontend-lint|frontend-build|miniapp）" >&2
    exit 2
    ;;
esac
