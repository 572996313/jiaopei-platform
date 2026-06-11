# JiaoPei Platform

线下班课为主的教培管理平台。

## 目录结构

```text
jiaopei-platform/
  frontend/        # Vue 3 + Vite
  backend/         # Spring Boot
  db/migration/    # Flyway SQL
  docs/            # 需求与设计文档
  docker/          # 本地环境与部署脚本
```

## 首期范围

- 权限底座
- 机构、校区、教室、学期
- 教师、学员
- 课程、班级
- 排课、课表、考勤

## 本地环境

- Node.js 20+
- pnpm
- Java 21
- Maven 3.9+
- Docker Desktop

注意：

- 本机运行后端前请确认 `JAVA_HOME` 指向 JDK 21，而不是旧版 JRE/JDK

## 启动规划

前端：

```bash
cd frontend
pnpm install
pnpm dev
```

后端：

```bash
cd backend
mvn spring-boot:run
```

基础环境：

```bash
docker compose -f docker/docker-compose.yml up -d
```
