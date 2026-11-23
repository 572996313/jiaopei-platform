# syntax=docker/dockerfile:1.7
# ============================================
# 前端运行镜像：依赖来自 frontend/Dockerfile.deps 产出的基础镜像
# ============================================
# 【注意】deploy.sh 自 2026-11 起不再调用本文件：生产部署改为 GitHub Actions
# 构建 dist、服务器挂载 docker/artifacts/dist 运行（零编译）。本文件仅保留给
# 「需要自包含镜像」的手工场景。
# node_modules 与 pnpm store 已固化在 jiaopei-platform-frontend-deps:prod 里，
# 本文件只做「拷源码 -> pnpm build -> 交给 nginx」，源码变更不会触发依赖重新下载。
# 构建前必须先有依赖镜像：
#   docker compose --profile deps build frontend-deps

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
