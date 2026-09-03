#!/bin/bash
# ============================================
# 教培平台 - 生产环境部署脚本
# 用法: ./deploy.sh [版本说明]
# 示例: ./deploy.sh "新增排课功能"
#       ./deploy.sh --force       # 强制重新构建(代码无变化也构建)
#       ./deploy.sh --skip-pull   # 跳过 git pull(用于本地调试)
#       ./deploy.sh --no-backup   # 跳过数据库逻辑备份
# ============================================
set -euo pipefail

# ---------- 配置区 ----------
PROJECT_DIR="/mnt/software/jiaopei"
COMPOSE_FILE="docker/docker-compose.prod.yml"
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
# ---------- 配置区结束 ----------

# ---------- 参数解析 ----------
FORCE_BUILD=false
SKIP_PULL=false
SKIP_BACKUP=false
NOTE_ARGS=()
for arg in "$@"; do
    case "${arg}" in
        --force) FORCE_BUILD=true ;;
        --skip-pull) SKIP_PULL=true ;;
        --no-backup) SKIP_BACKUP=true ;;
        --help|-h)
            echo "用法: $0 [版本说明] [--force] [--skip-pull] [--no-backup]"
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

echo "========================================"
echo "  教培平台 - 生产部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  说明: ${VERSION_NOTE}"
echo "========================================"

cd "${PROJECT_DIR}"

# ---------- 0. 预检 ----------
log_info "[0/8] 预检..."

# 0.0 自我保护:检测本地有未提交的 deploy.sh 改动
# 服务器上 chmod +x 后会触发 "modified" 标记,导致 git pull 失败。
# 这里把 deploy.sh / prod.env 之类的"部署机器本地"改动 stash 起来,
# pull 之后再 pop 回来。如果没冲突(通常不会有)就静默恢复。
if [ -d ".git" ] && [ "${SKIP_PULL}" != "true" ]; then
    DEPLOY_LOCAL_STASHED=0
    for LOCAL_FILE in docker/deploy.sh docker/prod.env docker/prod.env.example; do
        if [ -f "${LOCAL_FILE}" ] && ! git diff --quiet -- "${LOCAL_FILE}" 2>/dev/null; then
            if [ "${DEPLOY_LOCAL_STASHED}" -eq 0 ]; then
                log_warn "检测到本地有未提交的部署相关改动,先 stash 起来以免 git pull 失败"
                git stash push -m "deploy-local-$(date '+%Y%m%d_%H%M%S')" -- docker/deploy.sh docker/prod.env docker/prod.env.example
                DEPLOY_LOCAL_STASHED=1
            fi
        fi
    done
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
mkdir -p "${BACKUP_DIR}" "${DB_BACKUP_DIR}" "${DATA_DIR}/mysql" "${DATA_DIR}/redis"
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

# ---------- 2. 拉取最新代码 ----------
if [ "${SKIP_PULL}" = "true" ]; then
    log_warn "[2/8] 跳过 git pull (--skip-pull)"
else
    log_info "[2/8] 拉取最新代码..."
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
        git pull origin "${BRANCH}" || { log_err "git pull 失败"; exit 1; }
        log_ok "代码已更新。"
    fi
fi
NEW_COMMIT=$(git rev-parse --short HEAD)
log_ok "新版本: ${NEW_COMMIT}"

# ---------- 3. 备份 ----------
log_info "[3/8] 备份配置与数据库..."
mkdir -p "${BACKUP_DIR}" "${DB_BACKUP_DIR}"
if [ -f "docker/prod.env" ]; then
    cp "docker/prod.env" "${BACKUP_DIR}/prod.env.${TIMESTAMP}"
    log_ok "配置已备份: ${BACKUP_DIR}/prod.env.${TIMESTAMP}"
fi

if [ "${SKIP_BACKUP}" = "true" ]; then
    log_warn "跳过数据库备份 (--no-backup)"
else
    # 仅在 MySQL 容器已经在跑的情况下做逻辑备份
    if docker ps --format '{{.Names}}' | grep -q '^jiaopei-prod-mysql$'; then
        log_info "导出 MySQL 逻辑备份 -> ${DB_BACKUP_DIR}/${TIMESTAMP}.sql"
        set +e
        docker exec jiaopei-prod-mysql sh -c \
            "exec mysqldump -uroot -p\"\${MYSQL_ROOT_PASSWORD}\" --single-transaction --routines --triggers --events --databases ${DB_NAME:-jiaopei_platform}" \
            > "${DB_BACKUP_DIR}/${TIMESTAMP}.sql" 2> "${DB_BACKUP_DIR}/${TIMESTAMP}.log"
        DUMP_RC=$?
        set -e
        if [ "${DUMP_RC}" -eq 0 ] && [ -s "${DB_BACKUP_DIR}/${TIMESTAMP}.sql" ]; then
            gzip -f "${DB_BACKUP_DIR}/${TIMESTAMP}.sql"
            log_ok "数据库备份完成: ${DB_BACKUP_DIR}/${TIMESTAMP}.sql.gz"
            rm -f "${DB_BACKUP_DIR}/${TIMESTAMP}.log"
        else
            log_warn "mysqldump 失败 (rc=${DUMP_RC}),日志见 ${DB_BACKUP_DIR}/${TIMESTAMP}.log"
        fi
    else
        log_warn "MySQL 容器未运行,跳过本次逻辑备份(数据目录本身已持久化)。"
    fi

    # 清理过期的逻辑备份
    if [ -d "${DB_BACKUP_DIR}" ]; then
        DELETED=$(find "${DB_BACKUP_DIR}" -maxdepth 1 -type f -name '*.sql.gz' -mtime +${KEEP_DB_BACKUPS} -print -delete | wc -l)
        log_ok "已清理 ${DELETED} 份 ${KEEP_DB_BACKUPS} 天前的备份"
    fi
fi

# ---------- 4. 构建并启动 ----------
# 启用 BuildKit 后,backend/Dockerfile 与 frontend/Dockerfile 里的
# --mount=type=cache 才会生效,这是把构建从 553s 降到几十秒的关键
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1
# BUILDKIT_INLINE_CACHE=0 表示不把缓存元数据写入镜像 tag,纯靠 BuildKit 本地缓存
# 如果你的 docker 版本较旧(< 23),需要改成 export DOCKER_BUILDKIT=1 写到 /etc/docker/daemon.json
log_info "[4/8] 构建并启动服务..."
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
log_info "[5/8] 等待服务健康..."
ALL_HEALTHY=true
HEALTH_TIMEOUT=180
HEALTH_ELAPSED=0
HEALTH_INTERVAL=5
while [ "${HEALTH_ELAPSED}" -lt "${HEALTH_TIMEOUT}" ]; do
    PENDING=0
    for SERVICE in mysql redis backend frontend; do
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

for SERVICE in mysql redis backend frontend; do
    STATE=$(docker compose -f "${COMPOSE_FILE}" ps "${SERVICE}" --format "{{.Health}}" 2>/dev/null | head -1 || echo "unknown")
    if [ "${STATE}" = "healthy" ]; then
        log_ok "${SERVICE}: healthy"
    else
        log_warn "${SERVICE}: ${STATE:-unknown}"
        ALL_HEALTHY=false
    fi
done

# ---------- 6. 清理悬空镜像 ----------
log_info "[6/8] 清理悬空镜像..."
set +e
PRUNED=$(docker image prune -f 2>/dev/null | tail -1)
set -e
log_ok "${PRUNED:-已清理}"

# ---------- 7. 记录日志 ----------
log_info "[7/8] 记录部署日志..."
mkdir -p "$(dirname "${LOG_FILE}")"
echo "${TIMESTAMP} | ${CURRENT_COMMIT} -> ${NEW_COMMIT} | ${VERSION_NOTE}" >> "${LOG_FILE}"

# ---------- 8. 完成 ----------
log_info "[8/8] 部署完成！"
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

# 健康失败时返回非零,便于 CI/外部调度感知
if [ "${ALL_HEALTHY}" != "true" ]; then
    exit 2
fi

# ---------- 9. 恢复本地 stash + 清理临时凭据 ----------
# 部署完成后再 pop 回来,这样 deploy.sh 的 chmod / 临时改动不会丢。
# 冲突几乎不会发生(因为我们是在 pull 之后才 pop,deploy.sh 内容只会更新一次)。
if [ "${DEPLOY_LOCAL_STASHED:-0}" -eq 1 ]; then
    log_info "[9/9] 恢复本地暂存的部署改动..."
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
unset GIT_ASKPASS GIT_ASKPASS_SCRIPT GIT_CREDENTIALS_TOKEN GIT_TERMINAL_PROMPT 2>/dev/null || true
