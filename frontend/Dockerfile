# syntax=docker/dockerfile:1.7
# 需要 BuildKit 才能使用 --mount=type=cache
# 部署脚本(deploy.sh)里已通过 DOCKER_BUILDKIT=1 启用
# 缓存策略:
#   - pnpm-store:  持久化 pnpm 全局存储,锁文件不变就秒过 install
#   - node-modules-cache:  持久化 node_modules,源码变了也不用重装依赖

FROM node:22-alpine AS build

WORKDIR /workspace/frontend

RUN corepack enable && corepack prepare pnpm@11.5.0 --activate

# 关键:在缓存挂载目录里设 pnpm 的 store 目录,持久化依赖
# pnpm 用 hardlink 从 store 引用,缓存命中时不消耗双倍磁盘
ENV PNPM_HOME="/root/.local/share/pnpm"
ENV PATH="${PNPM_HOME}:${PATH}"

COPY frontend/package.json frontend/pnpm-lock.yaml ./
# 先做一次 prefetch,把所有依赖下载到 store
# 锁文件不变时这一步秒命中缓存
RUN --mount=type=cache,target=/root/.local/share/pnpm/store,id=jiaopei-pnpm-store \
    --mount=type=cache,target=/workspace/frontend/node_modules,id=jiaopei-node-modules-cache \
    pnpm fetch --frozen-lockfile

# 然后做 install(同样会命中 node_modules 缓存)
RUN --mount=type=cache,target=/root/.local/share/pnpm/store,id=jiaopei-pnpm-store \
    --mount=type=cache,target=/workspace/frontend/node_modules,id=jiaopei-node-modules-cache \
    pnpm install --frozen-lockfile --offline --prefer-offline

COPY frontend/index.html ./index.html
COPY frontend/vite.config.ts ./vite.config.ts
COPY frontend/tsconfig.json ./tsconfig.json
COPY frontend/tsconfig.app.json ./tsconfig.app.json
COPY frontend/tsconfig.node.json ./tsconfig.node.json
COPY frontend/public ./public
COPY frontend/src ./src

ARG VITE_API_AUTHORIZATION="Bearer stub-access-token"
ENV VITE_API_AUTHORIZATION=$VITE_API_AUTHORIZATION

# build 阶段也缓存 node_modules,vite 内部用到的依赖不会反复下载
RUN --mount=type=cache,target=/root/.local/share/pnpm/store,id=jiaopei-pnpm-store \
    --mount=type=cache,target=/workspace/frontend/node_modules,id=jiaopei-node-modules-cache \
    pnpm build

FROM nginx:1.27-alpine

RUN rm -f /etc/nginx/conf.d/default.conf
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -qO- http://127.0.0.1/healthz >/dev/null || exit 1
