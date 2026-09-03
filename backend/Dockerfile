# syntax=docker/dockerfile:1.7
# 需要 BuildKit 才能使用 --mount=type=cache
# 部署脚本(deploy.sh)里已通过 DOCKER_BUILDKIT=1 启用
# 缓存策略:
#   - m2-cache:        持久化 Maven 仓库,避免每次重拉依赖
#   - target-cache:    持久化 target 目录,增量编译更快
# 只要 backend/pom.xml 不变,本层缓存就命中,几乎秒过

FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace/backend

COPY backend/settings-docker.xml /root/.m2/settings.xml
COPY backend/pom.xml ./

# 第一层:只拉依赖(只依赖 pom.xml,源码改了不会重跑这步)
# -q 安静输出,省 IO
# -fae 失败时继续构建其他模块(Fail At End),不会因一个插件问题卡死
# -T 1C 每核一个项目线程,后端单模块收益不大但能省一点 plugin 解析时间
RUN --mount=type=cache,target=/root/.m2/repository,id=jiaopei-m2-cache \
    mvn -B -ntp -q -s /root/.m2/settings.xml -DskipTests \
        -Dmaven.wagon.http.retryHandler.count=5 \
        -Dmaven.wagon.httpconnectionManager.ttlSeconds=60 \
        -Dmaven.artifact.threads=4 \
        -Dmaven.plugin.validation=BRIEF \
        dependency:go-offline

COPY backend/src ./src

# 第二层:编译(同时缓存 m2 和 target,源码变更只触发这一步)
# -Dmaven.test.skip=true 同时跳过测试编译(比 -DskipTests 更彻底)
RUN --mount=type=cache,target=/root/.m2/repository,id=jiaopei-m2-cache \
    --mount=type=cache,target=/workspace/backend/target,id=jiaopei-target-cache \
    mvn -B -ntp -q -s /root/.m2/settings.xml \
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

COPY --from=build /workspace/backend/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
