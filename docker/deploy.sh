#!/bin/bash
# ============================================
# 教培平台 - 生产环境部署脚本
# 用法: ./deploy.sh [版本说明]
# 示例: ./deploy.sh --from-ci latest     # 用 CI 最新产物部署(推荐，零编译)
#       ./deploy.sh --from-ci 20261123-b71fae8
#       ./deploy.sh --rollback 20261120-5cd7fb9
#       ./deploy.sh --local-build        # 本机 mvn+pnpm 构建后部署(仅开发机/非突发型机器)
#       ./deploy.sh --force --no-backup  # 强制重建容器、跳备份
#       ./deploy.sh --skip-pull          # 跳过 git pull(本地调试)
# ============================================
set -euo pipefail

# ---------- 配置区 ----------
# 默认服务器目录；可用 DEPLOY_PROJECT_DIR 覆盖（便于本地演练或在非标准路径部署）。
PROJECT_DIR="${DEPLOY_PROJECT_DIR:-/mnt/software/jiaopei}"
# 本地运行实例：由 docker/docker-compose.prod.yml.example 复制生成，已 gitignore，
# git pull 不会覆盖；可按服务器环境自由修改（如删除内置 mysql/redis 服务）。
COMPOSE_FILE="docker/docker-compose.prod.yml"
COMPOSE_TEMPLATE="docker/docker-compose.prod.yml.example"
BRANCH="master"
# 远程仓库默认 GitHub（CI 构建与部署同源）。需要时 export DEPLOY_REMOTE_URL=... 覆盖。
# Gitee 只作为本地推送备份，服务器不再从它拉代码——否则模板/脚本更新会滞后。
REMOTE_URL="${DEPLOY_REMOTE_URL:-git@github.com:572996313/jiaopei-platform.git}"
# 私有仓库需要凭据时使用 HTTPS 内嵌账号(可选)
# 留空表示仓库公开,git 直接匿名拉取;填写后脚本会自动注入,无需交互输入
# 推荐用 Gitee 私人令牌而不是账号密码(账号密码在 Gitee 已不推荐)
# GIT_CREDENTIALS="https://<your-token>:x-oauth-basic@gitee.com/liu-xiaofei001/educational-training-gateway.git"
GIT_CREDENTIALS="${GIT_CREDENTIALS:-}"
# 备份/数据目录默认落在项目目录下（服务器上等同于原来的 /mnt/software/jiaopei/...），
# 可用环境变量覆盖，便于本地演练。
BACKUP_DIR="${DEPLOY_BACKUP_DIR:-${PROJECT_DIR}/backups}"
DB_BACKUP_DIR="${BACKUP_DIR}/db"
LOG_FILE="${DEPLOY_LOG_FILE:-${PROJECT_DIR}/deploy.log}"
DATA_DIR="${DEPLOY_DATA_DIR:-${PROJECT_DIR}/data}"
KEEP_DB_BACKUPS=14
# CI 产物目录：GitHub Actions 把 releases/<日期>-<sha>/{app.jar,dist} 推到这里。
# 与仓库目录分开，git pull 不会碰它。
RELEASES_DIR="${RELEASES_DIR:-/mnt/software/jiaopei/releases}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"
# 数据库逻辑备份的容器与账号：
#   - 容器默认自动识别：先按编排里的 mysql 服务找（container_name 改了也能找到），
#     再按镜像名探测（复用面板/外部 MySQL 时编排里没有 mysql 服务）。
#   - 识别不出来或多解时，用 DB_BACKUP_CONTAINER=<容器名> 显式指定。
#   - 账号默认优先容器内 root（读 MYSQL_ROOT_PASSWORD），其次 prod.env 的
#     MYSQL_ROOT_PASSWORD，最后退回应用账号 DB_USERNAME/DB_PASSWORD。
#     需要指定时用 DB_BACKUP_USER / DB_BACKUP_PASSWORD 覆盖。
DB_BACKUP_CONTAINER="${DB_BACKUP_CONTAINER:-}"
DB_BACKUP_USER="${DB_BACKUP_USER:-}"
DB_BACKUP_PASSWORD="${DB_BACKUP_PASSWORD:-}"
# ---------- 配置区结束 ----------

# ---------- 参数解析 ----------
FORCE_BUILD=false
SKIP_PULL=false
SKIP_BACKUP=false
FORCE_DEPS_BUILD=false
REUSE_DEPS=false
FROM_CI=""
ROLLBACK_SHA=""
LOCAL_BUILD=false
NOTE_ARGS=()
while [ "$#" -gt 0 ]; do
    case "$1" in
        --force) FORCE_BUILD=true; shift ;;
        --skip-pull) SKIP_PULL=true; shift ;;
        --no-backup) SKIP_BACKUP=true; shift ;;
        --update-deps) FORCE_DEPS_BUILD=true; shift ;;
        --reuse-deps) REUSE_DEPS=true; shift ;;
        --local-build) LOCAL_BUILD=true; shift ;;
        --from-ci)
            shift
            FROM_CI="${1:-latest}"
            # 后面紧跟其它 -- 参数或已无参数时，按 latest 处理
            case "${FROM_CI}" in
                --*) FROM_CI="latest" ;;
                *) if [ "$#" -gt 0 ]; then shift; fi ;;
            esac
            ;;
        --rollback)
            shift
            ROLLBACK_SHA="${1:-}"
            if [ -z "${ROLLBACK_SHA}" ] || [ "${ROLLBACK_SHA#--}" != "${ROLLBACK_SHA}" ]; then
                echo "错误: --rollback 需要版本目录名，例如 --rollback 20261123-b71fae8" >&2
                exit 2
            fi
            shift
            ;;
        --help|-h)
            echo "用法: $0 [版本说明] [--force] [--skip-pull] [--no-backup]"
            echo "       $0 --from-ci [版本目录名|latest]   用 CI 产物部署(零编译，推荐)"
            echo "       $0 --rollback <版本目录名>         回滚到历史 CI 产物"
            echo "       $0 --local-build                   本机直接 mvn+pnpm 构建后部署(仅开发机/非突发型机器)"
            echo "  --from-ci     用 ${RELEASES_DIR}/<版本> 下的 app.jar+dist 部署，不做任何 Docker 构建"
            echo "  --rollback    同 --from-ci，用于回滚到旧版本"
            echo "  --local-build 直接调用本机 mvn/pnpm 生成产物再部署；不要在突发性能型服务器上使用"
            echo "  --update-deps/--reuse-deps 已废弃（依赖镜像仅历史镜像构建使用），当前忽略"
            exit 0
            ;;
        *) NOTE_ARGS+=("$1"); shift ;;
    esac
done
if [ -n "${ROLLBACK_SHA}" ]; then
    FROM_CI="${ROLLBACK_SHA}"
fi
VERSION_NOTE="${NOTE_ARGS[*]:-手动部署 $(date '+%Y-%m-%d %H:%M')}"
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')

# ---------- 颜色 ----------
if [ -t 1 ]; then
    C_RED=$'\033[0;31m'
    C_GREEN=$'\033[0;32m'
    C_YELLOW=$'\033[0;33m'
    C_BLUE=$'\033[0;34m'
    C_RESET=$'\033[0m'
else
    C_RED=""; C_GREEN=""; C_YELLOW=""; C_BLUE=""; C_RESET=""
fi

log_info()  { echo "${C_BLUE}[INFO]${C_RESET} $*"; }
log_ok()    { echo "${C_GREEN}[ OK ]${C_RESET} $*"; }
log_warn()  { echo "${C_YELLOW}[WARN]${C_RESET} $*"; }
log_err()   { echo "${C_RED}[FAIL]${C_RESET} $*" >&2; }

# ---------- 退出收尾 ----------
# 脚本会在多个地方提前 exit(例如"代码已是最新"、健康检查失败、git 出错)，
# 这些路径同样要把暂存的本地部署改动还回去、清掉临时凭据脚本,
# 所以统一放进 EXIT trap,而不是只在脚本末尾处理。
DEPLOY_LOCAL_STASHED=0
cleanup_on_exit() {
    set +e
    if [ "${DEPLOY_LOCAL_STASHED:-0}" -eq 1 ]; then
        log_info "[清理] 恢复本地暂存的部署改动..."
        if git stash pop >/dev/null 2>&1; then
            log_ok "本地改动已恢复"
        else
            log_warn "stash pop 有冲突,你的本地改动暂存在 git stash list 里。可手动 git stash pop 解决"
            git stash list
        fi
    fi
    # 清理临时 askpass 脚本(避免 token 留在 /tmp)
    if [ -n "${GIT_ASKPASS:-}" ] && [ -f "${GIT_ASKPASS}" ]; then
        rm -f "${GIT_ASKPASS}"
    fi
}
trap cleanup_on_exit EXIT

# ---------- CI 产物辅助 ----------
# 解析 releases/<目录名>：支持 "latest"、完整目录名、以及只给 sha 前缀(自动匹配)。
resolve_release_dir() {
    local want="$1" dir=""
    [ -d "${RELEASES_DIR}" ] || return 1
    if [ "${want}" = "latest" ]; then
        dir=$(find "${RELEASES_DIR}" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null \
            | sort -r | head -n 1 || true)
    elif [ -d "${RELEASES_DIR}/${want}" ]; then
        dir="${want}"
    else
        # 只给了 sha 前缀时，匹配 *-<sha>*
        dir=$(find "${RELEASES_DIR}" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null \
            | grep -F -- "${want}" | sort -r | head -n 1 || true)
    fi
    [ -n "${dir}" ] || return 1
    printf '%s' "${RELEASES_DIR}/${dir}"
}

# 把产物复制到 docker/artifacts/（compose 相对路径的落点）。
apply_release_artifacts() {
    local src="$1"
    [ -f "${src}/app.jar" ] || { log_err "产物缺失: ${src}/app.jar"; return 1; }
    [ -d "${src}/dist" ]    || { log_err "产物缺失: ${src}/dist"; return 1; }
    mkdir -p "docker/artifacts"
    # 先删后拷：若之前某次挂载失败，docker 可能在这里留下同名空目录，cp 会报错。
    rm -rf "docker/artifacts/app.jar"
    cp -f "${src}/app.jar" "docker/artifacts/app.jar"
    rm -rf "docker/artifacts/dist"
    cp -R "${src}/dist" "docker/artifacts/dist"
    log_ok "产物已就位: $(du -h "docker/artifacts/app.jar" | cut -f1) jar + $(du -sh "docker/artifacts/dist" | cut -f1) dist"
}

# 清理过期的 releases，只保留最近 KEEP_RELEASES 个。
prune_releases() {
    [ -d "${RELEASES_DIR}" ] || return 0
    local count=0
    while IFS= read -r d; do
        count=$((count + 1))
        if [ "${count}" -gt "${KEEP_RELEASES}" ]; then
            log_warn "清理过期产物: $(basename "${d}")"
            rm -rf "${d}"
        fi
    done < <(find "${RELEASES_DIR}" -mindepth 1 -maxdepth 1 -type d -printf '%p\n' 2>/dev/null | sort -r)
}

echo "========================================"
echo "  教培平台 - 生产部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  说明: ${VERSION_NOTE}"
echo "========================================"

cd "${PROJECT_DIR}"

# ---------- 0. 预检 ----------
log_info "[0/9] 预检..."

# 0.0 自我保护:检测本地有未提交的部署相关改动
# 服务器上 chmod +x 后会触发 "modified" 标记,导致 git pull 失败。
# 这里把 deploy.sh / *.example 之类的"部署机器本地"改动 stash 起来,
# pull 之后再 pop 回来(见脚本顶部的 EXIT trap)。
#
# 注意: 只 stash 受版本控制的文件。docker/prod.env、docker/docker-compose.prod.yml
# 都被 .gitignore 忽略、git 不跟踪,裸 pathspec 传给 git stash push 会报
# "pathspec ... did not match" 并让整个 stash 失败;而且它们本来就不会被 git pull
# 覆盖(compose 运行实例见下方 1.1 检查),不需要保护。
if [ -d ".git" ] && [ "${SKIP_PULL}" != "true" ]; then
    STASH_PATHS=()
    for LOCAL_FILE in docker/deploy.sh docker/docker-compose.prod.yml.example docker/prod.env.example; do
        # 先确认文件被 git 跟踪,再确认确有改动,两者都满足才加入 stash 清单
        if git ls-files --error-unmatch "${LOCAL_FILE}" >/dev/null 2>&1 \
            && ! git diff --quiet -- "${LOCAL_FILE}" 2>/dev/null; then
            STASH_PATHS+=("${LOCAL_FILE}")
        fi
    done
    if [ "${#STASH_PATHS[@]}" -gt 0 ]; then
        log_warn "检测到本地有未提交的部署相关改动,先 stash 起来以免 git pull 失败"
        if git stash push -m "deploy-local-$(date '+%Y%m%d_%H%M%S')" -- "${STASH_PATHS[@]}"; then
            DEPLOY_LOCAL_STASHED=1
        else
            log_warn "stash 失败,git pull 可能因本地改动而失败,请手动处理后重试"
        fi
    fi
fi

# 0.0 项目目录存在性 + 是否为 git 仓库
if [ ! -d "${PROJECT_DIR}" ]; then
    log_err "项目目录 ${PROJECT_DIR} 不存在,请先创建(例如: sudo mkdir -p ${PROJECT_DIR} && sudo chown \$USER:\$USER ${PROJECT_DIR})"
    exit 1
fi
cd "${PROJECT_DIR}"

# 若目录不是 git 仓库,自动 clone 远程仓库
if [ ! -d ".git" ]; then
    if [ "${SKIP_PULL}" = "true" ]; then
        log_err "当前目录不是 git 仓库,但指定了 --skip-pull。请先手动 git clone ${REMOTE_URL} ${PROJECT_DIR}"
        exit 1
    fi
    log_warn "当前目录不是 git 仓库,自动执行 git clone ${REMOTE_URL} ${PROJECT_DIR}"
    # 仅当目录为空时才直接 clone 到当前目录,避免误覆盖已有文件
    if [ -z "$(ls -A "${PROJECT_DIR}" 2>/dev/null)" ]; then
        CLONE_URL="${REMOTE_URL}"
        # 内嵌凭据:把 https://x-access-token:<token>@gitee.com/... 转成 git 可识别的形式
        if [ -n "${GIT_CREDENTIALS}" ]; then
            CLONE_URL="${GIT_CREDENTIALS}"
            log_warn "使用 GIT_CREDENTIALS 克隆(token 不会写入日志)"
        fi
        git clone "${CLONE_URL}" "${PROJECT_DIR}"
    else
        log_err "${PROJECT_DIR} 已存在但不是 git 仓库,且目录非空。请手动处理:"
        echo "  - 若想保留现有文件: cd ${PROJECT_DIR} && git init && git remote add origin ${REMOTE_URL} && git pull origin ${BRANCH}"
        echo "  - 若目录是历史残留: rm -rf ${PROJECT_DIR}/* ${PROJECT_DIR}/.[!.]* 2>/dev/null; 然后重新执行本脚本"
        exit 1
    fi
fi

# 0.1 目录与持久化数据目录
mkdir -p "${BACKUP_DIR}" "${DB_BACKUP_DIR}" "${DATA_DIR}/mysql" "${DATA_DIR}/redis" "${DATA_DIR}/minio"
if [ ! -w "${DATA_DIR}" ]; then
    log_err "数据目录 ${DATA_DIR} 不可写,容器无法挂载。"
    exit 1
fi

# 0.2 prod.env 存在性
if [ ! -f "docker/prod.env" ]; then
    log_err "docker/prod.env 不存在,请先从 ${BACKUP_DIR} 恢复或基于 prod.env.example 创建。"
    exit 1
fi

# 0.3 docker / compose 可用性
if ! command -v docker >/dev/null 2>&1; then
    log_err "未找到 docker 命令。"
    exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
    log_err "未找到 docker compose (v2) 子命令。"
    exit 1
fi

# 0.4 远程仓库
if [ "${SKIP_PULL}" != "true" ]; then
    # 服务器必须从 GitHub 拉代码（CI 与部署同源）。仍指向 Gitee 时模板/脚本会滞后，
    # --from-ci 可能用旧编排跑新产物，所以这里硬拦一次。
    CURRENT_ORIGIN_CHECK=$(git remote get-url origin 2>/dev/null || true)
    if printf '%s' "${CURRENT_ORIGIN_CHECK}" | grep -q 'gitee\.com'; then
        log_warn "当前 origin 仍指向 Gitee：${CURRENT_ORIGIN_CHECK}"
        log_warn "  请改为 GitHub（与 CI 同源），否则模板与脚本更新会滞后："
        log_warn "    git remote set-url origin git@github.com:<你的账号>/jiaopei-platform.git"
        log_warn "  确认本次就是要从 Gitee 拉时，可 export DEPLOY_ALLOW_GITEE=1 跳过本检查。"
        if [ "${DEPLOY_ALLOW_GITEE:-0}" != "1" ]; then
            exit 1
        fi
    fi
    if ! git remote get-url origin >/dev/null 2>&1; then
        # 只有"没有 origin、需要新建"时才会用到 REMOTE_URL。origin 已存在时
        # 一律沿用现有地址，不受这里的占位符影响。
        if printf '%s' "${REMOTE_URL}" | grep -q 'CHANGE_ME'; then
            log_err "未配置 git remote origin，且 REMOTE_URL 还是占位符：${REMOTE_URL}"
            echo "  请 export DEPLOY_REMOTE_URL=git@github.com:<你的账号>/jiaopei-platform.git，"
            echo "  或修改脚本顶部 REMOTE_URL 为真实地址。"
            exit 1
        fi
        log_warn "未配置 git remote origin,自动添加 ${REMOTE_URL}"
        ADD_URL="${REMOTE_URL}"
        if [ -n "${GIT_CREDENTIALS}" ]; then
            ADD_URL="${GIT_CREDENTIALS}"
        fi
        git remote add origin "${ADD_URL}"
    fi
    # 凭据切换:如果 origin 已经是公开地址,但配置了凭据,则临时切到含凭据地址进行 fetch/pull
    NEEDS_AUTH_URL=""
    if [ -n "${GIT_CREDENTIALS}" ]; then
        CURRENT_ORIGIN=$(git remote get-url origin)
        # 如果当前 origin 不含凭据但配置了 GIT_CREDENTIALS,临时切到含凭据地址
        if [[ "${CURRENT_ORIGIN}" != *"@${REMOTE_URL#*://}"* ]] && [[ "${CURRENT_ORIGIN}" != *"@"* || "${CURRENT_ORIGIN}" != *"gitee"* ]]; then
            NEEDS_AUTH_URL="${GIT_CREDENTIALS}"
            log_warn "已为本次操作临时注入 git 凭据(不会持久化到 .git/config)"
        fi
    fi
    CURRENT_REMOTE=$(git remote get-url origin)
    log_ok "git remote origin = ${CURRENT_REMOTE}"

    # 把临时的 credential helper 装上,只对本次脚本生效
    # 用 GIT_ASKPASS 把 token 通过环境传给 git,比写 URL 更安全
    if [ -n "${GIT_CREDENTIALS}" ]; then
        GIT_ASKPASS_SCRIPT=$(mktemp)
        cat > "${GIT_ASKPASS_SCRIPT}" <<'ASKPASS_EOF'
#!/bin/sh
case "$1" in
    Username*) echo "x-access-token" ;;
    Password*) echo "$GIT_CREDENTIALS_TOKEN" ;;
    *) echo "" ;;
esac
ASKPASS_EOF
        chmod +x "${GIT_ASKPASS_SCRIPT}"
        # 提取 token 部分(去掉 https://x-access-token: 前缀)
        export GIT_ASKPASS="${GIT_ASKPASS_SCRIPT}"
        # 从 GIT_CREDENTIALS 里提取 token(GIT_CREDENTIALS 形如 https://x-access-token:<token>@gitee.com/...)
        if [[ "${GIT_CREDENTIALS}" == *"x-access-token:"* ]]; then
            export GIT_CREDENTIALS_TOKEN="${GIT_CREDENTIALS#*x-access-token:}"
            export GIT_CREDENTIALS_TOKEN="${GIT_CREDENTIALS_TOKEN%%@*}"
        else
            export GIT_CREDENTIALS_TOKEN=""
        fi
        export GIT_TERMINAL_PROMPT=0
    fi
fi

# ---------- 1. 拉取最新代码 ----------
CURRENT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
if [ "${SKIP_PULL}" = "true" ]; then
    log_warn "[1/9] 跳过 git pull (--skip-pull)"
else
    log_info "[1/9] 拉取最新代码..."
    git fetch origin "${BRANCH}" || { log_err "git fetch 失败"; exit 1; }
    LOCAL=$(git rev-parse "${BRANCH}" 2>/dev/null || echo "")
    REMOTE=$(git rev-parse "origin/${BRANCH}" 2>/dev/null || echo "")
    if [ -n "${LOCAL}" ] && [ "${LOCAL}" = "${REMOTE}" ]; then
        log_ok "代码已是最新。"
        if [ "${FORCE_BUILD}" != "true" ] && [ -z "${FROM_CI}" ]; then
            log_warn "无需重建,如需强制重新构建请加 --force"
            log_warn "部署取消。"
            exit 0
        elif [ -n "${FROM_CI}" ]; then
            log_info "--from-ci 指定,即使代码无变化也继续用 CI 产物部署。"
        else
            log_warn "--force 已指定,继续重新构建。"
        fi
    else
        # fetch 已把 origin/分支 更新到本地,这里只做快进合并,
        # 不再 git pull(否则会产生第二次网络请求、二次凭据输入)
        git merge --ff-only "origin/${BRANCH}" || { log_err "git merge --ff-only 失败,本地分支可能与远程分叉,请手动处理"; exit 1; }
        log_ok "代码已更新。"
    fi
fi
NEW_COMMIT=$(git rev-parse --short HEAD)
log_ok "新版本: ${NEW_COMMIT}"

# 1.1 本地 compose 运行实例存在性
# 该文件是各服务器私有配置（见 .gitignore），git pull 不会创建/恢复它，
# 所以必须放在 pull 之后检查，并给出可直接执行的初始化命令。
if [ ! -f "${COMPOSE_FILE}" ]; then
    log_err "${COMPOSE_FILE} 不存在。它是各服务器私有的运行配置，不进 git，pull 不会生成。"
    echo "  首次部署请复制模板后按本机环境修改："
    echo "    cp ${COMPOSE_TEMPLATE} ${COMPOSE_FILE}"
    echo "  若服务器复用已有 MySQL/Redis，请按《生产部署排障手册》场景 D 删除内置 mysql/redis 服务。"
    exit 1
fi

# ---------- 2. 备份 ----------
# 从 docker/prod.env 读取单个键的值。
# 刻意不 source：prod.env 是运维手写文件，source 会把里面任意内容当命令执行。
# 只做「取最后一处 KEY=VALUE、去掉成对引号与行尾 CR」的解析，备份用途足够。
prod_env_get() {
    local key="$1" line="" first="" last=""
    [ -f "docker/prod.env" ] || return 0
    line=$(grep -E "^[[:space:]]*${key}=" "docker/prod.env" | tail -n 1 || true)
    [ -n "${line}" ] || return 0
    line="${line#*=}"
    line="${line%$'\r'}"
    if [ "${#line}" -ge 2 ]; then
        first="${line:0:1}"
        last="${line: -1}"
        if { [ "${first}" = '"' ] && [ "${last}" = '"' ]; } \
            || { [ "${first}" = "'" ] && [ "${last}" = "'" ]; }; then
            line="${line:1:${#line}-2}"
        fi
    fi
    printf '%s' "${line}"
}

# 定位本次备份要连的 MySQL 容器：
#   1) DB_BACKUP_CONTAINER 显式指定优先（最稳，任何拓扑都适用）；
#   2) 按编排服务名 mysql 定位——即使 container_name 被改，也能拿到真实容器名；
#   3) 编排里没有 mysql 服务（复用 1Panel/外部 MySQL）时，按镜像名探测唯一候选。
# 找不到或多解时返回非零，由调用方打印可操作提示，而不是静默跳过备份。
resolve_mysql_container() {
    if [ -n "${DB_BACKUP_CONTAINER}" ]; then
        if [ "$(docker inspect -f '{{.State.Running}}' "${DB_BACKUP_CONTAINER}" 2>/dev/null || true)" = "true" ]; then
            printf '%s' "${DB_BACKUP_CONTAINER}"
            return 0
        fi
        log_warn "DB_BACKUP_CONTAINER=${DB_BACKUP_CONTAINER} 不存在或未运行" >&2
        return 1
    fi
    local cid="" name="" candidates="" count=0
    cid=$(docker compose -f "${COMPOSE_FILE}" --env-file "docker/prod.env" ps -q mysql 2>/dev/null | head -n 1 || true)
    if [ -n "${cid}" ]; then
        name=$(docker inspect -f '{{.Name}}' "${cid}" 2>/dev/null | sed 's#^/##' || true)
        if [ -n "${name}" ]; then
            printf '%s' "${name}"
            return 0
        fi
    fi
    candidates=$(docker ps --format '{{.Names}} {{.Image}}' 2>/dev/null \
        | awk 'tolower($2) ~ /(^|\/)(mysql|mariadb)(:|@|$)/ {print $1}' || true)
    count=$(printf '%s' "${candidates}" | grep -c . || true)
    if [ "${count}" -eq 1 ]; then
        printf '%s' "${candidates}"
        return 0
    fi
    if [ "${count}" -gt 1 ]; then
        log_warn "检测到多个 MySQL 容器，无法确定备份对象：" >&2
        printf '%s\n' "${candidates}" | sed 's/^/    /' >&2
        log_warn "请用 DB_BACKUP_CONTAINER=<容器名> 指定后重跑" >&2
        return 2
    fi
    return 1
}

log_info "[2/9] 备份配置与数据库..."
mkdir -p "${BACKUP_DIR}" "${DB_BACKUP_DIR}"
if [ -f "docker/prod.env" ]; then
    cp "docker/prod.env" "${BACKUP_DIR}/prod.env.${TIMESTAMP}"
    log_ok "配置已备份: ${BACKUP_DIR}/prod.env.${TIMESTAMP}"
fi
if [ -f "${COMPOSE_FILE}" ]; then
    cp "${COMPOSE_FILE}" "${BACKUP_DIR}/docker-compose.prod.yml.${TIMESTAMP}"
    log_ok "编排配置已备份: ${BACKUP_DIR}/docker-compose.prod.yml.${TIMESTAMP}"
fi

if [ "${SKIP_BACKUP}" = "true" ]; then
    log_warn "跳过数据库备份 (--no-backup)"
else
    # 备份参数既可经环境变量传入，也可写在 docker/prod.env 里（环境变量优先）
    DB_BACKUP_CONTAINER="${DB_BACKUP_CONTAINER:-$(prod_env_get DB_BACKUP_CONTAINER)}"
    DB_BACKUP_USER="${DB_BACKUP_USER:-$(prod_env_get DB_BACKUP_USER)}"
    DB_BACKUP_PASSWORD="${DB_BACKUP_PASSWORD:-$(prod_env_get DB_BACKUP_PASSWORD)}"
    # 定位 MySQL 容器（不再写死 jiaopei-prod-mysql：container_name 被改或复用外部
    # MySQL 时，旧写法会静默跳过备份，让人误以为「有备份」）
    MYSQL_CONTAINER=""
    RESOLVE_RC=0
    MYSQL_CONTAINER=$(resolve_mysql_container) || RESOLVE_RC=$?
    if [ "${RESOLVE_RC}" -eq 0 ]; then
        log_info "备份源容器: ${MYSQL_CONTAINER}"
    else
        MYSQL_CONTAINER=""
    fi

    if [ -z "${MYSQL_CONTAINER}" ]; then
        if [ "${RESOLVE_RC}" -eq 2 ]; then
            log_warn "存在多个 MySQL 容器,已跳过本次逻辑备份;请按上面的提示指定 DB_BACKUP_CONTAINER。"
        else
            log_warn "未找到可备份的 MySQL 容器,跳过本次逻辑备份(数据目录本身已持久化)。"
            log_warn "  若 MySQL 由面板/外部维护: 在 prod.env 里加 DB_BACKUP_CONTAINER=<容器名> 再部署,"
            log_warn "  或确认该库的定时备份已由面板负责,避免两套备份互相以为对方在管。"
        fi
    else
        # 账号优先级：显式 DB_BACKUP_USER/PASSWORD > 容器内 root(MYSQL_ROOT_PASSWORD)
        # > prod.env 的 MYSQL_ROOT_PASSWORD > 应用账号 DB_USERNAME/DB_PASSWORD。
        if [ -n "${DB_BACKUP_USER}" ]; then
            BACKUP_USER="${DB_BACKUP_USER}"
            BACKUP_PASS="${DB_BACKUP_PASSWORD:-}"
        else
            CONTAINER_ROOT_PW=$(docker exec "${MYSQL_CONTAINER}" printenv MYSQL_ROOT_PASSWORD 2>/dev/null || true)
            if [ -n "${CONTAINER_ROOT_PW}" ]; then
                BACKUP_USER="root"
                BACKUP_PASS="${CONTAINER_ROOT_PW}"
            elif [ -n "$(prod_env_get MYSQL_ROOT_PASSWORD)" ]; then
                BACKUP_USER="root"
                BACKUP_PASS="$(prod_env_get MYSQL_ROOT_PASSWORD)"
            else
                BACKUP_USER="$(prod_env_get DB_USERNAME)"
                BACKUP_PASS="$(prod_env_get DB_PASSWORD)"
            fi
        fi
        BACKUP_DB="$(prod_env_get DB_NAME)"
        BACKUP_DB="${BACKUP_DB:-jiaopei_platform}"

        if [ -z "${BACKUP_USER}" ]; then
            log_warn "无法确定 MySQL 备份账号(容器内无 MYSQL_ROOT_PASSWORD,prod.env 也没有 DB_USERNAME),跳过备份。"
            log_warn "  请设置 DB_BACKUP_USER / DB_BACKUP_PASSWORD 后重跑。"
        else
            log_info "导出 MySQL 逻辑备份 -> ${DB_BACKUP_DIR}/${TIMESTAMP}.sql (账号: ${BACKUP_USER}, 库: ${BACKUP_DB})"
            # 密码经 stdin 送进容器，再由容器内的 sh 写入临时 defaults 文件（umask 077），
            # 用完立即删除：密码不在宿主机进程参数里，也不受引号/空格等特殊字符影响。
            set +e
            printf '%s\n' "${BACKUP_PASS}" | docker exec -i \
                -e DEPLOY_BACKUP_USER="${BACKUP_USER}" -e DEPLOY_BACKUP_DB="${BACKUP_DB}" \
                "${MYSQL_CONTAINER}" sh -c '
                    IFS= read -r DEPLOY_BACKUP_PASSWORD || true
                    umask 077
                    CNF=/tmp/.deploy-backup-my.cnf
                    printf "[client]\nuser=%s\npassword=%s\n" "$DEPLOY_BACKUP_USER" "$DEPLOY_BACKUP_PASSWORD" > "$CNF"
                    mysqldump --defaults-extra-file="$CNF" --single-transaction --routines --triggers --events \
                        --databases "$DEPLOY_BACKUP_DB"
                    rc=$?
                    rm -f "$CNF"
                    exit $rc
                ' > "${DB_BACKUP_DIR}/${TIMESTAMP}.sql" 2> "${DB_BACKUP_DIR}/${TIMESTAMP}.log"
            DUMP_RC=$?
            set -e
            if [ "${DUMP_RC}" -eq 0 ] && [ -s "${DB_BACKUP_DIR}/${TIMESTAMP}.sql" ]; then
                gzip -f "${DB_BACKUP_DIR}/${TIMESTAMP}.sql"
                log_ok "数据库备份完成: ${DB_BACKUP_DIR}/${TIMESTAMP}.sql.gz"
                rm -f "${DB_BACKUP_DIR}/${TIMESTAMP}.log"
            else
                rm -f "${DB_BACKUP_DIR}/${TIMESTAMP}.sql"
                log_warn "mysqldump 失败 (rc=${DUMP_RC}),日志见 ${DB_BACKUP_DIR}/${TIMESTAMP}.log"
                log_warn "  若账号权限不足: 在 prod.env 里配置 DB_BACKUP_USER / DB_BACKUP_PASSWORD 后重跑。"
            fi
        fi
    fi

    # 清理过期的逻辑备份
    if [ -d "${DB_BACKUP_DIR}" ]; then
        DELETED=$(find "${DB_BACKUP_DIR}" -maxdepth 1 -type f -name '*.sql.gz' -mtime +${KEEP_DB_BACKUPS} -print -delete | wc -l)
        log_ok "已清理 ${DELETED} 份 ${KEEP_DB_BACKUPS} 天前的备份"
    fi
fi

# ---------- 3. 准备部署产物 ----------
# 两种来源，最终都落到 docker/artifacts/，由 compose 只读挂载进容器——全程不触发 docker build：
#   --from-ci / --rollback : 用 GitHub Actions 投递到 releases/<ver> 的预构建产物（推荐）
#   --local-build          : 在本机直接跑 mvn + pnpm 生成产物（仅开发机/非突发型机器）
log_info "[3/9] 准备部署产物..."
if [ "${LOCAL_BUILD}" = "true" ]; then
    log_warn "本地构建模式：直接调用本机 mvn/pnpm，不要在突发性能型服务器上使用"
    if [ -f scripts/lib/jdk21.sh ]; then
        # shellcheck source=lib/jdk21.sh
        . scripts/lib/jdk21.sh
        ensure_jdk21
    fi
    command -v mvn  >/dev/null 2>&1 || { log_err "本机没有 mvn"; exit 1; }
    command -v pnpm >/dev/null 2>&1 || { log_err "本机没有 pnpm"; exit 1; }
    log_info "编译后端(Maven，线程 2)..."
    ( cd backend && env MAVEN_OPTS="${MAVEN_OPTS:--Xmx512m -XX:MaxMetaspaceSize=256m}" \
        mvn -B -ntp -q -DskipTests -Dmaven.artifact.threads=2 -Dmaven.compiler.threads=2 clean package ) \
        || { log_err "后端构建失败"; exit 1; }
    JAR=$(ls -t backend/app/target/jiaopei-platform-backend-*.jar 2>/dev/null | head -1 || true)
    [ -n "${JAR}" ] || { log_err "未找到后端 jar"; exit 1; }
    log_info "编译前端(pnpm build)..."
    ( cd frontend && pnpm build ) || { log_err "前端构建失败"; exit 1; }
    [ -f frontend/dist/index.html ] || { log_err "未找到前端 dist"; exit 1; }
    mkdir -p docker/artifacts
    cp -f "${JAR}" docker/artifacts/app.jar
    rm -rf docker/artifacts/dist
    cp -R frontend/dist docker/artifacts/dist
    NEW_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "local")
    log_ok "本地构建完成: $(basename "${JAR}")"
else
    [ -n "${FROM_CI}" ] || FROM_CI="latest"
    RELEASE_SRC=$(resolve_release_dir "${FROM_CI}") || {
        log_err "找不到 CI 产物: ${FROM_CI}（查找目录 ${RELEASES_DIR}）"
        echo "  可用版本:"
        find "${RELEASES_DIR}" -mindepth 1 -maxdepth 1 -type d -printf '    %f\n' 2>/dev/null | sort -r || true
        echo "  若还没有产物：先让 GitHub Actions 跑完，或在本机用 --local-build。"
        exit 1
    }
    log_info "产物目录: ${RELEASE_SRC}"
    apply_release_artifacts "${RELEASE_SRC}" || exit 1
    NEW_COMMIT=$(basename "${RELEASE_SRC}")
fi

# ---------- 4. 重建容器 ----------
log_info "[4/9] 重建 backend/frontend 容器（零编译）..."
# 防呆：产物挂载模式要求 compose 里没有 build 段，否则 up -d 仍可能重新编译、把积分烧掉。
# 先取完整 config 再判断：如果 config 本身失败（prod.env 缺必填变量、compose 版本不兼容），
# 直接管道给 grep 会因"没有匹配"而静默放行，这里必须把解析失败显式拦下来。
if ! COMPOSE_CFG=$(docker compose -f "${COMPOSE_FILE}" --env-file "docker/prod.env" config); then
    log_err "docker compose config 解析失败，无法确认编排是否含 build 段，已中止。"
    echo "  常见原因：docker/prod.env 缺少必填变量，或 compose 版本过旧。"
    exit 1
fi
if printf '%s\n' "${COMPOSE_CFG}" | grep -qE '^[[:space:]]+build:'; then
    log_err "当前 ${COMPOSE_FILE} 仍含 build 段，拒绝继续（会触发重新编译）。"
    echo "  请把服务器运行实例更新为「产物挂载」模式（去掉 build 段），见 docs/生产部署说明.md。"
    echo "  确需在本机编译请用 --local-build。"
    exit 1
fi
docker compose -f "${COMPOSE_FILE}" --env-file "docker/prod.env" \
    up -d --no-build --force-recreate backend frontend

# ---------- 5. 等待健康 ----------
log_info "[5/9] 等待服务健康..."
ALL_HEALTHY=true
HEALTH_TIMEOUT=180
HEALTH_ELAPSED=0
HEALTH_INTERVAL=5
# 只检查编排里实际存在的服务：复用宿主机/面板 MySQL、Redis 的服务器会删掉
# mysql/redis 服务定义，若仍按固定清单检查，会空等满 180 秒并误判部署失败。
DEPLOY_SERVICES=$(docker compose -f "${COMPOSE_FILE}" --env-file "docker/prod.env" config --services 2>/dev/null || true)
if [ -z "${DEPLOY_SERVICES}" ]; then
    log_warn "无法从 compose 解析服务清单(如 prod.env 缺少必填变量),健康检查回退为 backend/frontend"
fi
HEALTH_SERVICES=""
for SERVICE in mysql redis minio backend frontend; do
    if printf '%s\n' "${DEPLOY_SERVICES}" | grep -qx "${SERVICE}"; then
        HEALTH_SERVICES="${HEALTH_SERVICES} ${SERVICE}"
    fi
done
# 解析失败或编排里四个服务都不存在时兜底（正常至少会有 backend/frontend）
if [ -z "${HEALTH_SERVICES// /}" ]; then
    HEALTH_SERVICES="backend frontend"
fi
log_info "健康检查服务:${HEALTH_SERVICES}"
while [ "${HEALTH_ELAPSED}" -lt "${HEALTH_TIMEOUT}" ]; do
    PENDING=0
    for SERVICE in ${HEALTH_SERVICES}; do
        STATE=$(docker compose -f "${COMPOSE_FILE}" ps "${SERVICE}" --format "{{.Health}}" 2>/dev/null | head -1 || echo "")
        if [ "${STATE}" != "healthy" ]; then
            PENDING=$((PENDING + 1))
        fi
    done
    if [ "${PENDING}" -eq 0 ]; then
        break
    fi
    sleep "${HEALTH_INTERVAL}"
    HEALTH_ELAPSED=$((HEALTH_ELAPSED + HEALTH_INTERVAL))
    echo "  ⏳ 等待中... (${HEALTH_ELAPSED}s / ${HEALTH_TIMEOUT}s,剩余 ${PENDING} 个服务)"
done

for SERVICE in ${HEALTH_SERVICES}; do
    STATE=$(docker compose -f "${COMPOSE_FILE}" ps "${SERVICE}" --format "{{.Health}}" 2>/dev/null | head -1 || echo "unknown")
    if [ "${STATE}" = "healthy" ]; then
        log_ok "${SERVICE}: healthy"
    else
        log_warn "${SERVICE}: ${STATE:-unknown}"
        ALL_HEALTHY=false
    fi
done

# ---------- 6. 清理悬空镜像与构建缓存 ----------
log_info "[6/9] 清理悬空镜像与构建缓存..."
set +e
PRUNED=$(docker image prune -f 2>/dev/null | tail -1)
log_ok "${PRUNED:-已清理}"
# 只清悬空镜像清不掉 BuildKit 构建缓存；不加这一步缓存会累积到数 GB，
# 小盘机器上会挤占空间、也让后续误构建更难收场。
BUILDER_PRUNED=$(docker builder prune -f --keep-storage 2GB 2>/dev/null | tail -1)
log_ok "构建缓存: ${BUILDER_PRUNED:-已清理}"
# 清理过期的 CI 产物目录（只留最近 KEEP_RELEASES 个）
prune_releases
set -e

# ---------- 7. 记录日志 ----------
log_info "[7/9] 记录部署日志..."
mkdir -p "$(dirname "${LOG_FILE}")"
echo "${TIMESTAMP} | ${CURRENT_COMMIT} -> ${NEW_COMMIT} | ${VERSION_NOTE}" >> "${LOG_FILE}"

# ---------- 8. 完成 ----------
log_info "[8/9] 部署完成！"
echo "========================================"
echo "  版本: ${CURRENT_COMMIT} -> ${NEW_COMMIT}"
echo "  说明: ${VERSION_NOTE}"
if [ "${ALL_HEALTHY}" != "true" ]; then
    log_warn "部分服务未达 healthy,请检查:"
    echo "  docker compose -f ${COMPOSE_FILE} ps"
fi
echo "  查看日志: docker compose -f ${COMPOSE_FILE} logs -f"
echo "  查看后端: docker compose -f ${COMPOSE_FILE} logs -f backend"
echo "  回滚 CI 产物: $0 --rollback <版本目录名>"
echo "  回滚代码: git checkout ${CURRENT_COMMIT} && $0 --force --no-backup"
echo "========================================"

# ---------- 9. 恢复本地 stash + 清理临时凭据 ----------
# 由脚本顶部的 EXIT trap(cleanup_on_exit)统一处理,无需在这里再写一遍:
# 这样"代码已是最新"/健康失败/构建报错等提前退出的路径也不会丢本地改动。

# 健康失败时返回非零,便于 CI/外部调度感知
if [ "${ALL_HEALTHY}" != "true" ]; then
    exit 2
fi
