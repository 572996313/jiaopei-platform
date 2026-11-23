# syntax=docker/dockerfile:1.7
# ============================================
# 后端运行镜像：依赖来自 backend/Dockerfile.deps 产出的基础镜像
# ============================================
# 依赖(整个 ~/.m2/repository)已固化在 jiaopei-platform-backend-deps:prod 里，
# 本文件只做「拷源码 -> 编译打包 -> 装 jar」，源码变更不会触发依赖重新下载。
# 构建前必须先有依赖镜像：deploy.sh 会自动按 pom.xml 哈希判断是否需要重建；
# 手动构建时请先执行（docker-compose.prod.yml 为本地运行实例，首次需从 .example 复制）：
#   docker compose -f docker/docker-compose.prod.yml --profile deps build backend-deps

ARG DEPS_IMAGE=jiaopei-platform-backend-deps:prod
FROM ${DEPS_IMAGE} AS build

WORKDIR /workspace/backend

COPY backend/cornerstone-core/src cornerstone-core/src
COPY backend/module-organization/src module-organization/src
COPY backend/module-education/src module-education/src
COPY backend/app/src app/src

# -Dmaven.test.skip=true 同时跳过测试编译；javadoc 也不生成，缩短构建时间
RUN mvn -B -ntp -q -s /root/.m2/settings.xml \
        -Dmaven.test.skip=true \
        -Dmaven.wagon.http.retryHandler.count=5 \
        -Dmaven.artifact.threads=4 \
        -Dmaven.compiler.threads=4 \
        -Dmaven.javadoc.skip=true \
        -Dmaven.plugin.validation=BRIEF \
        package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 生产容器固定 8080：通过环境变量覆盖 application.yml 的 server.port（本地开发默认 9080）。
# 这行是容器内端口的唯一权威——删掉它，应用会按 yml 起在 9080，而 nginx 反代（backend:8080）、
# compose 端口映射与健康检查仍打 8080，表现为 unhealthy + 502 且日志不提示端口问题。
# docker-compose.prod.yml 的 environment.SERVER_PORT 是同值的第二道保险。
ENV SERVER_PORT=8080
ENV JAVA_OPTS=""

COPY --from=build /workspace/backend/app/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
