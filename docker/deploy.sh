#!/bin/bash
# ============================================
# 教培平台 - 生产环境部署脚本
# 用法: ./deploy.sh [版本说明]
# 示例: ./deploy.sh "新增排课功能"
# ============================================
set -euo pipefail

# ---------- 配置区 ----------
PROJECT_DIR="/mnt/software/jiaopei"
COMPOSE_FILE="docker/docker-compose.prod.yml"
BRANCH="main"
BACKUP_DIR="/mnt/software/jiaopei/backups"
LOG_FILE="/mnt/software/jiaopei/deploy.log"
# ---------- 配置区结束 ----------

VERSION_NOTE="${1:-手动部署 $(date '+%Y-%m-%d %H:%M')}"
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')

echo "========================================"
echo "  教培平台 - 生产部署"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  说明: ${VERSION_NOTE}"
echo "========================================"

cd "${PROJECT_DIR}"

# 1. 记录当前版本
CURRENT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
echo "[1/7] 当前版本: ${CURRENT_COMMIT}"

# 2. 拉取最新代码
echo "[2/7] 拉取最新代码..."
git fetch origin ${BRANCH}
LOCAL=$(git rev-parse ${BRANCH})
REMOTE=$(git rev-parse origin/${BRANCH})

if [ "${LOCAL}" = "${REMOTE}" ]; then
    echo "  代码已是最新，无需更新。"
    echo "  如需强制重新构建，请用 --force 参数。"
    if [ "${1:-}" != "--force" ]; then
        echo "  部署取消。"
        exit 0
    fi
else
    git pull origin ${BRANCH}
    echo "  代码已更新。"
fi

NEW_COMMIT=$(git rev-parse --short HEAD)
echo "  新版本: ${NEW_COMMIT}"

# 3. 备份当前 prod.env
echo "[3/7] 备份配置..."
mkdir -p "${BACKUP_DIR}"
if [ -f "docker/prod.env" ]; then
    cp "docker/prod.env" "${BACKUP_DIR}/prod.env.${TIMESTAMP}"
    echo "  配置已备份到 ${BACKUP_DIR}/prod.env.${TIMESTAMP}"
fi

# 4. 检查 prod.env 是否存在
if [ ! -f "docker/prod.env" ]; then
    echo "  ⚠️ 警告: docker/prod.env 不存在！"
    echo "  请从 ${BACKUP_DIR} 恢复或重新创建。"
    exit 1
fi

# 5. 重建并启动服务
echo "[4/7] 构建并启动服务..."
docker compose -f ${COMPOSE_FILE} up -d --build

# 6. 等待健康检查
echo "[5/7] 等待服务健康..."
sleep 10

ALL_HEALTHY=true
for SERVICE in mysql redis backend frontend; do
    STATUS=$(docker compose -f ${COMPOSE_FILE} ps ${SERVICE} --format json 2>/dev/null | grep -o '"Health":"[^"]*"' | head -1 || echo "unknown")
    if echo "${STATUS}" | grep -q "healthy"; then
        echo "  ✅ ${SERVICE}: healthy"
    else
        echo "  ⏳ ${SERVICE}: ${STATUS} (可能还在启动中)"
        ALL_HEALTHY=false
    fi
done

# 7. 记录部署日志
echo "[6/7] 记录部署日志..."
mkdir -p $(dirname ${LOG_FILE})
echo "${TIMESTAMP} | ${CURRENT_COMMIT} -> ${NEW_COMMIT} | ${VERSION_NOTE}" >> ${LOG_FILE}

# 完成
echo "[7/7] 部署完成！"
echo "========================================"
echo "  版本: ${CURRENT_COMMIT} -> ${NEW_COMMIT}"
echo "  说明: ${VERSION_NOTE}"
if [ "${ALL_HEALTHY}" = "false" ]; then
    echo "  ⚠️ 部分服务仍在启动中，请稍后检查："
    echo "  docker compose -f ${COMPOSE_FILE} ps"
fi
echo "  查看日志: docker compose -f ${COMPOSE_FILE} logs -f"
echo "  回滚方法: git checkout ${CURRENT_COMMIT} && docker compose -f ${COMPOSE_FILE} up -d --build"
echo "========================================"
