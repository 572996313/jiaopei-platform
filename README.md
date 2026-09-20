# JiaoPei Platform

线下班课为主的教培管理平台，多端单体仓库：管理后台（Web）、微信小程序、Spring Boot 后端。

> 开发约定的**唯一事实源是 [AGENTS.md](AGENTS.md)**（环境、命令、鉴权、迁移规范、安全约定），本文件只作项目门面，不重复细节。

## 目录结构

```text
jiaopei-platform/
  backend/            # Spring Boot 3.5 / Java 21，Maven 聚合工程（阶段二B 起多模块）
    cornerstone-core/   # 底座：auth / system / common / config / tenant / audit + 第三方 starter
    module-organization/# 业务：校区/教室/学期/时间段
    module-education/   # 业务：教师/学员/班级/排课/考勤/小程序接口
    app/                # 组装启动：主类、application.yml、Flyway 迁移、全部测试（唯一可执行 jar）
  frontend/           # Vue 3 + Vite + TypeScript + Element Plus 管理端
  miniapp/            # uni-app 微信小程序「教培学堂」+ H5
  docker/             # 本地依赖编排与生产部署
  docs/               # 交付/设计文档（索引见 docs/README.md，历史方案在 docs/archive/）
  scripts/            # 本地脚本：restart-backend.sh、ci-check.sh 等
```

数据库迁移统一在 `backend/app/src/main/resources/db/migration/`（Flyway，原顶层 `db/` 已删除）。

## 功能模块

- **底座（Cornerstone）**：登录/JWT+Redis 会话、用户/角色/菜单权限、机构（租户）、审计留痕、列表配置、系统参数
- **organization**：校区、教室、学期、时间段模板
- **education**：教师、学员与家长、课程/班型、班级与开班规则、报名/入班、排课/课次、考勤、班级变动通知
- **miniapp**：家长/学生端微信登录、课表、考勤

后端模块可按开关装配（`cornerstone.modules.*-enabled`，阶段二C 起为真开关，阶段四起配置前缀为 `cornerstone.*`），详见 `docs/底座分离与Cornerstone重构计划.md`。

## 快速开始

依赖：JDK 21（系统默认可能是 JDK 8，先导出 `JAVA_HOME`）、Maven 3.9+、Node 20+、pnpm、Docker Desktop。

```bash
docker compose -f docker/docker-compose.yml up -d   # MySQL 8.4 + Redis 7.4
scripts/restart-backend.sh                          # 编译并后台启动后端（含健康检查）
cd frontend && pnpm install && pnpm dev             # 管理端 http://127.0.0.1:5173
cd miniapp && pnpm dev:mp-weixin                    # 小程序，微信开发者工具导入 dist/dev/mp-weixin
```

完整命令与本地环境注意事项（含 MySQL/Redis 账号已知不一致的说明）见 AGENTS.md「本地环境与常用命令」。

## 文档索引

- 全部现行文档分类索引：`docs/README.md`
- 接口与权限矩阵：`docs/后端交付说明.md`；页面与路由映射：`docs/前端交付说明.md`
