# syntax=docker/dockerfile:1.7
# ============================================
# 后端运行镜像：依赖来自 backend/Dockerfile.deps 产出的基础镜像
# ============================================
# 依赖(整个 ~/.m2/repository)已固化在 jiaopei-platform-backend-deps:prod 里，
# 本文件只做「拷源码 -> 编译打包 -> 装 jar」，源码变更不会触发依赖重新下载。
# 构建前必须先有依赖镜像：deploy.sh 会自动按 pom.xml 哈希判断是否需要重建；
# 手动构建时请先执行：
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

ENV SERVER_PORT=8080
ENV JAVA_OPTS=""

COPY --from=build /workspace/backend/app/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
