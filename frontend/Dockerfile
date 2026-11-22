# syntax=docker/dockerfile:1.7
# ============================================
# 前端运行镜像：依赖来自 frontend/Dockerfile.deps 产出的基础镜像
# ============================================
# node_modules 与 pnpm store 已固化在 jiaopei-platform-frontend-deps:prod 里，
# 本文件只做「拷源码 -> pnpm build -> 交给 nginx」，源码变更不会触发依赖重新下载。
# 构建前必须先有依赖镜像：deploy.sh 会自动按锁文件哈希判断是否需要重建；
# 手动构建时请先执行（docker-compose.prod.yml 为本地运行实例，首次需从 .example 复制）：
#   docker compose -f docker/docker-compose.prod.yml --profile deps build frontend-deps

ARG DEPS_IMAGE=jiaopei-platform-frontend-deps:prod
FROM ${DEPS_IMAGE} AS build

WORKDIR /workspace/frontend

COPY frontend/index.html ./index.html
COPY frontend/vite.config.ts ./vite.config.ts
COPY frontend/tsconfig.json ./tsconfig.json
COPY frontend/tsconfig.app.json ./tsconfig.app.json
COPY frontend/tsconfig.node.json ./tsconfig.node.json
COPY frontend/public ./public
# 底座包与业务模块包(源码级引用，无各自构建)
# COPY 是合并覆盖，不会删除依赖镜像里已装好的 packages/*/node_modules 软链
COPY frontend/packages ./packages
COPY frontend/src ./src

RUN pnpm build

FROM nginx:1.27-alpine

RUN rm -f /etc/nginx/conf.d/default.conf
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -qO- http://127.0.0.1/healthz >/dev/null || exit 1
