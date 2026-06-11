# syntax=docker/dockerfile:1

FROM node:22-alpine AS build

WORKDIR /workspace/frontend

RUN corepack enable && corepack prepare pnpm@11.5.0 --activate

COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
RUN chmod +x node_modules/.bin/* || true

COPY frontend/index.html ./index.html
COPY frontend/vite.config.ts ./vite.config.ts
COPY frontend/tsconfig.json ./tsconfig.json
COPY frontend/tsconfig.app.json ./tsconfig.app.json
COPY frontend/tsconfig.node.json ./tsconfig.node.json
COPY frontend/public ./public
COPY frontend/src ./src

ARG VITE_API_AUTHORIZATION="Bearer stub-access-token"
ENV VITE_API_AUTHORIZATION=$VITE_API_AUTHORIZATION

RUN pnpm build

FROM nginx:1.27-alpine

RUN rm -f /etc/nginx/conf.d/default.conf
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -qO- http://127.0.0.1/healthz >/dev/null || exit 1
