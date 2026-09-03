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
BRANCH="main"
REMOTE_URL="https://gitee.com/liu-xiaofei001/educational-training-gateway.git"
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
        git remote add origin "${REMOTE_URL}"
    fi
    CURRENT_REMOTE=$(git remote get-url origin)
    log_ok "git remote origin = ${CURRENT_REMOTE}"
fi

# ---------- 1. 记录当前版本 ----------
CURRENT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
log_info "[1/8] 当前版本: ${CURRENT_COMMIT}"

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
log_info "[4/8] 构建并启动服务..."
docker compose -f "${COMPOSE_FILE}" --env-file "docker/prod.env" up -d --build

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
