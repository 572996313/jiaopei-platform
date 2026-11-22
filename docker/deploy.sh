#!/bin/bash
# ============================================
# 教培平台 - 生产环境部署脚本
# 用法: ./deploy.sh [版本说明]
# 示例: ./deploy.sh "新增排课功能"
#       ./deploy.sh --force       # 强制重新构建(代码无变化也构建)
#       ./deploy.sh --skip-pull   # 跳过 git pull(用于本地调试)
#       ./deploy.sh --no-backup   # 跳过数据库逻辑备份
#       ./deploy.sh --update-deps # 强制重建依赖基础镜像(重新下载依赖)
#       ./deploy.sh --reuse-deps  # 强制沿用现有依赖镜像(不检查依赖变化)
# ============================================
set -euo pipefail

# ---------- 配置区 ----------
PROJECT_DIR="/mnt/software/jiaopei"
# 本地运行实例：由 docker/docker-compose.prod.yml.example 复制生成，已 gitignore，
# git pull 不会覆盖；可按服务器环境自由修改（如删除内置 mysql/redis 服务）。
COMPOSE_FILE="docker/docker-compose.prod.yml"
COMPOSE_TEMPLATE="docker/docker-compose.prod.yml.example"
BRANCH="master"
REMOTE_URL="https://gitee.com/liu-xiaofei001/educational-training-gateway.git"
# 私有仓库需要凭据时使用 HTTPS 内嵌账号(可选)
# 留空表示仓库公开,git 直接匿名拉取;填写后脚本会自动注入,无需交互输入
# 推荐用 Gitee 私人令牌而不是账号密码(账号密码在 Gitee 已不推荐)
# GIT_CREDENTIALS="https://<your-token>:x-oauth-basic@gitee.com/liu-xiaofei001/educational-training-gateway.git"
GIT_CREDENTIALS="${GIT_CREDENTIALS:-}"
BACKUP_DIR="/mnt/software/jiaopei/backups"
DB_BACKUP_DIR="/mnt/software/jiaopei/backups/db"
LOG_FILE="/mnt/software/jiaopei/deploy.log"
DATA_DIR="/mnt/software/jiaopei/data"
KEEP_DB_BACKUPS=14
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
# 依赖基础镜像的哈希戳：pom/lock 变化时自动重建依赖镜像，否则直接复用
DEPS_STAMP_FILE="/mnt/software/jiaopei/.deps-stamp"
BACKEND_DEPS_IMAGE="jiaopei-platform-backend-deps:prod"
FRONTEND_DEPS_IMAGE="jiaopei-platform-frontend-deps:prod"
# ---------- 配置区结束 ----------

# ---------- 参数解析 ----------
FORCE_BUILD=false
SKIP_PULL=false
SKIP_BACKUP=false
FORCE_DEPS_BUILD=false
REUSE_DEPS=false
NOTE_ARGS=()
for arg in "$@"; do
    case "${arg}" in
        --force) FORCE_BUILD=true ;;
        --skip-pull) SKIP_PULL=true ;;
        --no-backup) SKIP_BACKUP=true ;;
        --update-deps) FORCE_DEPS_BUILD=true ;;
        --reuse-deps) REUSE_DEPS=true ;;
        --help|-h)
            echo "用法: $0 [版本说明] [--force] [--skip-pull] [--no-backup] [--update-deps] [--reuse-deps]"
            echo "  --update-deps  强制重建依赖基础镜像(重新下载 Maven/pnpm 依赖)"
            echo "  --reuse-deps   强制沿用现有依赖镜像(依赖声明变化也不重建)"
            exit 0
            ;;
        *) NOTE_ARGS+=("${arg}") ;;
    esac
done
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
    if ! git remote get-url origin >/dev/null 2>&1; then
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
        if [ "${FORCE_BUILD}" != "true" ]; then
            log_warn "无需重建,如需强制重新构建请加 --force"
            log_warn "部署取消。"
            exit 0
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

# ---------- 3. 依赖基础镜像 ----------
# 把 Maven / pnpm 依赖固化在带 tag 的镜像层里：
#   - 只有 pom.xml、pnpm-lock.yaml 等依赖声明变化(或显式 --update-deps)时才重建
#   - 日常部署直接复用，源码变化只重新编译，不再联网重下依赖
#   - 带 tag 的镜像不会被 builder GC / docker image prune 回收，
#     这正是原先 BuildKit cache mount 方案在低配服务器上失效的原因
log_info "[3/9] 检查依赖基础镜像..."
BACKEND_DEPS_HASH=$(cat backend/pom.xml \
        backend/cornerstone-core/pom.xml \
        backend/module-organization/pom.xml \
        backend/module-education/pom.xml \
        backend/app/pom.xml \
        backend/settings-docker.xml | sha256sum | cut -c1-16)
FRONTEND_DEPS_HASH=$(cat frontend/package.json \
        frontend/pnpm-workspace.yaml \
        frontend/pnpm-lock.yaml \
        frontend/packages/core/package.json \
        frontend/packages/module-organization/package.json \
        frontend/packages/module-education/package.json | sha256sum | cut -c1-16)
DEPS_HASH="backend=${BACKEND_DEPS_HASH} frontend=${FRONTEND_DEPS_HASH}"

NEED_DEPS_BUILD=false
if [ "${FORCE_DEPS_BUILD}" = "true" ]; then
    NEED_DEPS_BUILD=true
    log_warn "已指定 --update-deps，强制重建依赖镜像"
elif [ "${REUSE_DEPS}" = "true" ]; then
    PREV_DEPS_HASH=$(cat "${DEPS_STAMP_FILE}" 2>/dev/null || true)
    log_warn "已指定 --reuse-deps，沿用现有依赖镜像"
    if [ "${PREV_DEPS_HASH}" != "${DEPS_HASH}" ]; then
        log_warn "注意: 依赖声明与依赖镜像不一致(旧=${PREV_DEPS_HASH:-<无>} 新=${DEPS_HASH})"
        log_warn "      编译将使用旧依赖版本;若报找不到依赖，请改用 --update-deps"
    fi
else
    PREV_DEPS_HASH=$(cat "${DEPS_STAMP_FILE}" 2>/dev/null || true)
    if [ "${PREV_DEPS_HASH}" != "${DEPS_HASH}" ]; then
        NEED_DEPS_BUILD=true
        log_warn "依赖声明有变化，需要重建依赖镜像"
        log_warn "  旧: ${PREV_DEPS_HASH:-<无>}"
        log_warn "  新: ${DEPS_HASH}"
    fi
fi

# 镜像不存在时无论如何都要构建，否则 backend/frontend 的 FROM 会失败
if ! docker image inspect "${BACKEND_DEPS_IMAGE}" >/dev/null 2>&1; then
    NEED_DEPS_BUILD=true
    log_warn "依赖镜像 ${BACKEND_DEPS_IMAGE} 不存在，先构建"
fi
if ! docker image inspect "${FRONTEND_DEPS_IMAGE}" >/dev/null 2>&1; then
    NEED_DEPS_BUILD=true
    log_warn "依赖镜像 ${FRONTEND_DEPS_IMAGE} 不存在，先构建"
fi

if [ "${NEED_DEPS_BUILD}" = "true" ]; then
    log_info "重建依赖基础镜像(仅在依赖变化时发生)..."
    docker compose -f "${COMPOSE_FILE}" --env-file "docker/prod.env" \
        --profile deps build backend-deps frontend-deps
    echo "${DEPS_HASH}" > "${DEPS_STAMP_FILE}"
    log_ok "依赖镜像已更新: ${DEPS_HASH}"
else
    log_ok "依赖未变化，直接复用现有依赖镜像"
fi

# ---------- 4. 构建并启动 ----------
# 依赖已在第 3 步固化进基础镜像,这里只重新编译源码;
# 仍启用 BuildKit,编译层的文件指纹缓存与依赖镜像配合,进一步缩短耗时。
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1
log_info "[4/9] 构建并启动服务..."
# --parallel:                 后端和前端同时构建(总耗时取较慢的那一个)
# 不传 --pull:                Compose 默认行为 —— 基础镜像本地有就用本地 tag,没有才拉
#                            注意:--pull=missing 在某些 compose 插件版本上会报
#                              "invalid argument \"missing\" for \"--pull\" flag: strconv.ParseBool..."
#                            不传该参数后所有版本都按预期走默认行为,行为一致。
# backend frontend:           只构建我们自己写的两个镜像,不动 mysql/redis(它们用 image: 直接拉)
docker compose -f "${COMPOSE_FILE}" --env-file "docker/prod.env" \
    build --parallel backend frontend

docker compose -f "${COMPOSE_FILE}" --env-file "docker/prod.env" up -d

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

# ---------- 6. 清理悬空镜像 ----------
log_info "[6/9] 清理悬空镜像..."
set +e
PRUNED=$(docker image prune -f 2>/dev/null | tail -1)
set -e
log_ok "${PRUNED:-已清理}"

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
echo "  回滚代码: git checkout ${CURRENT_COMMIT} && $0 --force --no-backup"
echo "========================================"

# ---------- 9. 恢复本地 stash + 清理临时凭据 ----------
# 由脚本顶部的 EXIT trap(cleanup_on_exit)统一处理,无需在这里再写一遍:
# 这样"代码已是最新"/健康失败/构建报错等提前退出的路径也不会丢本地改动。

# 健康失败时返回非零,便于 CI/外部调度感知
if [ "${ALL_HEALTHY}" != "true" ]; then
    exit 2
fi
