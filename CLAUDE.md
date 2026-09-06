# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

JiaoPei 是线下班课为主的教培管理平台。仓库为前后端分离的多模块项目（`frontend/` + `backend/` + `db/`）。语言/UI 全部为中文；后端日志、异常消息、注释为英文。文档（需求、交付、架构）集中在 `docs/`。

## 常用命令

### 后端（Spring Boot 3.5 / Java 21 / Maven，工作目录 `backend/`）

```bash
mvn spring-boot:run                      # 本地运行（需先起 MySQL/Redis，见下）
mvn -q test                              # 全量测试
mvn -q -Dtest=AuthControllerWebTest test # 跑单个测试类
mvn -q -Dtest=AuthControllerWebTest#loginReturnsStubToken test  # 跑单个方法
mvn -q package -DskipTests               # 打包
mvn -q dependency:go-offline             # 预拉依赖（Dockerfile 也用这一步）
```

本地依赖：`docker compose -f docker/docker-compose.yml up -d`（起 MySQL 8.4 + Redis 7.4，库名 `jiaopei_platform`，root/root）。

后端默认连 `127.0.0.1:3306` 的 MySQL 与 `127.0.0.1:6379` 的 Redis，账号 `root/1234`（见 `application.yml`，可用 `DB_*` / `REDIS_*` 环境变量覆盖）。Flyway 已启用，迁移位于 `classpath:db/migration`，`baseline-on-migrate=true`。

`JAVA_HOME` 必须指向 JDK 21（不是旧版 JRE/JDK），否则 Maven 起不来。

### 前端（Vue 3 / Vite / pnpm，工作目录 `frontend/`）

```bash
pnpm install
pnpm dev        # vite dev server，本机 127.0.0.1:5173，/api 代理到 127.0.0.1:8080
pnpm build      # vue-tsc -b 类型检查 + vite build
pnpm preview
```

无 lint / 单测脚本；`pnpm build` 同时作为类型检查门禁。

## 架构要点

### 鉴权：JWT + Redis 会话

登录签发 JWT,Redis 会话负责吊销,这是最容易踩坑的地方：

- 登录 `POST /api/auth/login` 校验密码后,由 `JwtTokenService` 签发 HS256 JWT(自包含 userId/角色/权限声明,`exp` 与系统配置 `auth.token.expire_hours` 一致,默认 12 小时),同时在 Redis 写入会话(`jiaopei:token:<jwt>`);每次登录 token 唯一,用户之间互不影响。
- `StubAuthenticationFilter` 读取 `Authorization: Bearer <token>`,调用 `AuthApplicationService.authenticateToken(token)` 校验:先验 JWT 签名与过期时间,再查 Redis 会话(登出/踢人后立即失效)。
- JWT 签名密钥在 `jiaopei.auth.jwt.secret`(`application.yml`,生产必须用环境变量 `AUTH_JWT_SECRET` 覆盖,至少 32 字节)。
- `jiaopei.security.public-paths`（`application.yml`）声明放行路径；其余需认证。`SecurityConfig` 是 stateless、CSRF/form/basic 全关。
- 业务侧权限通过 `@RequiresPermission("xxx:read|write")` 注解 + `PermissionGuardAspect`（AOP `@Before`）拦截，命中检查由 `DefaultPermissionService` 直接判断 `AuthenticatedUser.permissions()` 是否包含该权限串。`AuthenticatedUser` 以 record 形式挂在 `SecurityContextHolder` 的 principal 上。
- 取当前用户：`AuthContext.requireUser()` / `AuthContext.currentUser()`（`AuthContext` 是静态工具）。

新增受保护接口时：路由加 `@RequiresPermission`，权限串与 `docs/后端交付说明.md` 表中 `organization:read` / `education:write` 这类命名保持一致。

**登录失败排障陷阱**：`GlobalExceptionHandler` 把所有 `AuthenticationException`（含 `BadCredentialsException` 密码错、`DisabledException` 账号禁用等子类）统一渲染成同一句 `UNAUTHORIZED "Authentication is required"`。所以 `/api/auth/login` 密码错和 `/api/auth/me` token 失效返回的**是同一个错误文案**，仅凭响应体无法区分"密码输错了"还是"token/过滤器逻辑坏了"——排查 401 时先用已知正确密码单独打一次 `/api/auth/login` 排除"密码错"这个最常见的可能，再去看过滤器/Redis。本地测试账号：`admin` / `Admin@1234567`（种子数据见 `V7__create_auth_user_role_tables.sql`，`{noop}` 前缀表示 Spring Security 委托编码器按明文比对，不是真正 BCrypt）。

**Token 存储：Redis，非数据库表**。`TokenCacheService`（`auth/service/TokenCacheService.java` + impl）用 `RedisTemplate<String, Object>` 存 `AuthenticatedUser`，key 布局：
- `jiaopei:token:{token}` → 序列化后的 `AuthenticatedUser`（TTL 由 `auth.token.expire_hours` 系统配置项决定）
- `jiaopei:user:tokens:{userId}` → 该用户名下的 token 集合，用于 `invalidateUserTokens(userId)` 强制下线（改密/禁用账号后触发）

`RedisConfig` 里的 `ObjectMapper` **必须**用 `activateDefaultTyping(..., ObjectMapper.DefaultTyping.EVERYTHING, ...)`（不能用 `NON_FINAL`）——`AuthenticatedUser` record 里的 `Set<String> roles/permissions` 字段是接口类型，`NON_FINAL` 不会给它们写入 `@class` 类型信息，反序列化时报 `InvalidTypeIdException: missing type id property '@class'`。这个 ObjectMapper 要在 `redisTemplate()` 方法内部单独 new，**不要**做成全局 `@Bean ObjectMapper`，否则会顶掉 Spring MVC 默认的 ObjectMapper 导致接口响应里的 `OffsetDateTime` 等类型序列化报错（缺 JSR310 模块）。

`RedisConfig` 序列化格式变更后，Redis 里旧格式的数据无法被新 ObjectMapper 反序列化（同样报 `missing type id property '@class'`），必须清库：`docker exec <redis容器名> redis-cli FLUSHDB`（容器名以 `docker ps` 实际结果为准，本仓库当前是 `redis` 不带 compose 前缀）。清库后所有已登录用户的 token 失效，前端要重新登录。

**`StubAuthenticationFilter` 认证失败时"吞异常继续放行"是有意为之，不是 bug**：`authenticateToken` 抛出的 `AuthenticationException` 被 catch 后仅 `clearContext()`，然后照常 `filterChain.doFilter()` 往下走，不直接 `sendError`。这是因为该 filter 对**所有**请求生效（包括 `permitAll()` 的公开路径），如果在这里直接返回 401，会导致"带了过期 token 访问公开接口"（例如刷新页面时前端自动带旧 token 打 `/api/health`）被误拦截。真正的鉴权决定交给下游 `SecurityConfig.authorizeHttpRequests`：公开路径即使 `SecurityContext` 是空的也会放行，非公开路径没有认证信息才会 401。**不要**在 catch 块里加 `sendError`/`return` 去"提前拦截"，这只是把本该由 Spring Security 统一做的判断挪到了 filter 里，容易和 `public-paths` 配置产生逻辑重复或冲突。

**系统参数配置表 `sys_system_config`**（`V14__drop_token_table_and_add_system_config.sql` 引入）：把过去写死在代码里的登录/密码策略做成可运行时调整的配置项，通过 `SystemConfigService.getConfigValue/getBooleanConfig/getIntConfig` 读取，`@Cacheable("systemConfig")` 缓存查询结果，写操作 `@CacheEvict` 清缓存。当前用到的 key：
- `auth.password.force_change_enabled` — 强制改密总开关（`StubAuthenticationFilter` 读取，关闭后忽略用户级 `forceChangePassword` 标记）
- `auth.password.strength_level` / `min_length` / `max_length` — 密码强度策略（`PasswordValidator` 读取）
- `auth.token.expire_hours` — Redis token TTL（`AuthApplicationServiceImpl.login()` 读取）

新增系统级可配置项时优先加到这张表，别再写死在 `application.yml`；`SystemConfigServiceImpl` 里系统配置（`is_system=1`）禁止删除，只能改值。

### 统一响应与全局异常

- 所有 controller 返回值被 `ApiResponseAdvice`（`@ControllerAdvice`）自动包成 `ApiResponse{T}`：`code/success/message/data/timestamp/traceId/path`。controller 里直接返回 data 对象即可，不要手动包 `ApiResponse`（已有分支会跳过重复包装）。`null` 与 `Resource` 不包装。
- `GlobalExceptionHandler`（`@RestControllerAdvice`）把校验异常、`BusinessException`、`AuthenticationException`、`AccessDeniedException`、`NoResourceFoundException` 等统一转成 `ApiResponse.failure(...)`。业务错误请抛 `BusinessException(ResultCode, message)`，不要在 controller 里拼响应。
- `ResultCode` 是固定枚举（`SUCCESS/BAD_REQUEST/UNAUTHORIZED/FORBIDDEN/NOT_FOUND/VALIDATION_ERROR/INTERNAL_ERROR`）。
- `TraceIdFilter` 给每个请求打 `traceId`，`ApiResponse` 会带上。

### 模块划分（`com.jiaopei.platform`）

- `auth` — 用户/角色/令牌、权限注解+AOP、`SecurityConfig` 相关 filter/handler。
- `organization` — 机构/校区/教室/学期/时间段主数据。
- `education` — 教师/学员/班级/班型/课程/报名/班级师生关系/排课课次/考勤/班级变动通知。
- `system` — 系统管理（用户、角色、菜单、字典、机构自取）、`PlatformBootstrapController`（`GET /api/system/bootstrap` 返回模块就绪状态）、`HealthController`。
- `common` — `ApiResponse`/`ResultCode`/`BusinessException`/`BaseEntity`/全局异常与 response advice/`TraceIdFilter`/ID 解析工具。
- `config` — `MybatisPlusConfig`、`SecurityConfig`、`PasswordConfig`、`OpenApiConfig`、`JiaopeiProperties`。

每个业务模块内部约定子包：`controller / service(+impl) / repository(部分模块) / mapper / domain(+entity) / dto`。`repository` 包仅 organization/education 有，做复杂查询聚合；简单 CRUD 走 mapper。

`JiaopeiProperties.modules`（`organization-enabled` / `education-enabled`）目前**仅作为 bootstrap 展示的状态标记**，代码里没有 `@ConditionalOnProperty` 做模块开关，不要误以为能关掉模块。

### 持久化：MyBatis-Plus + Flyway

- 实体继承 `BaseEntity`：`id`（`IdType.ASSIGN_ID` 雪花 ID，`Long`）、`createdAt/createdBy/updatedAt/updatedBy`、`deleted`（`@TableLogic` 逻辑删除）。建表 SQL 里 `deleted` 默认 0。
- Mapper 由 `MybatisPlusConfig` 的 `@MapperScan` 显式扫描四个模块的 `mapper` 包；XML 位置 `classpath*:/mapper/**/*.xml`（当前 `resources/mapper/` 为空，全用 BaseMapper）。
- 分页用 `PaginationInnerInterceptor`（MySQL 方言）。
- 所有 schema 变更走 Flyway 迁移：在 `backend/src/main/resources/db/migration/` 加 `V<next>__desc.sql`，表名统一 `jp_` 前缀（如 `jp_class`、`jp_user_account`）。不要手改既有迁移文件。

### Service 接口签名变更规范

**修改 Service 接口方法签名后必须同步所有调用方**，否则测试编译失败：

- Controller 注入调用处
- 测试类中的 `given(...willReturn(...))` mock 与 `verify(...)` 调用
- 其他 Service 内部的跨模块调用

验证`mvn -q test`（不是只 `mvn compile`），因为 mock/verify 的参数列表必须与新签名匹配。`SecondThirdMainlineApiCompatibilityWebTest` 这类兼容性测试会显式 `verify` 各 Service 方法签名，是签名变更的"安全网"，改了签名必须同步改这些测试。

### 学期（term）与各业务实体的必填关系

后端设计上学期是**可选维度**，不要在前端误强制为必填：

- **班级 `jp_class.term_id`**：数据库 `NULL`，Entity 无 `@NotNull`——创建班级时学期可选
- **报名 `jp_enrollment` / 入班 `jp_class_student`**：表和 DTO 均无 `term_id`，完全不涉及学期
- **排课 `jp_schedule_lesson.term_id`**：数据库 `NOT NULL`，DTO `@NotNull`——排课是唯一强制要求学期的实体

前端范围栏的学期选择器是**筛选条件**，不应作为报名/入班的前置校验。只有排课页面（`ScheduleAttendanceView.vue`）的 `validateDialogContext` 校验学期是正确的。

### 大 ID 跨进精度边界（前后端必须协同）

后端主键是雪花 `Long`（16+ 位），超过 JS 安全整数。前后端都有专门处理，改相关字段时两边都要顾到：

- 后端 `FlexibleLongIdDeserializer` + `ExternalIdParser`：`Long` 字段既接受数字也接受字符串，业务 id（非纯数字、以数字结尾）会被解析出尾部数字。新增接收 `Long` 的 DTO 字段时这套机制自动生效（全局 Jackson），不要自行写解析。
- 前端 `frontend/src/api/http.ts` 用自定义 `transformResponse` 对 `id/userId/orgId/institutionId/campusId/termId/classId/classroomId/timeSlotId/studentId/teacherId/createdBy/updatedBy` 及数组字段 `teacherIds` 做正则兜底，把 16+ 位数字包成字符串再 `JSON.parse`。**新增大 ID 字段时必须把字段名加进 `LARGE_ID_FIELDS` / `LARGE_ID_ARRAY_FIELDS`**，否则前端会丢精度。

### 前端约定

- API 层 `frontend/src/api/`：`http.ts`（axios 实例 + 大 ID 兜底）、`request.ts`（`unwrap` 解包 `ApiResponse.data`，非 `success` 抛 `ApiError`）、按模块拆 `auth/organization/education/system.ts`。
- 状态：Pinia 单 store `stores/auth.ts`，token + 用户 + 菜单。鉴权信息持久化在 `localStorage` key `jiaopei.auth.session`（见 `utils/auth-session.ts`），`http.ts` 请求拦截器从该处取 `Authorization`，未登录时回退到 `import.meta.env.VITE_API_AUTHORIZATION`。
- 路由 `router/index.ts`：`/login`、`/change-password` 为 public/`allowForceChange`；其余在 `AppLayout` 下。系统管理多 tab 复用同一个 `SystemManagementView.vue`，靠 `meta.systemTab` 区分。
- 路径别名 `@` → `frontend/src`（vite + tsconfig 都配了）。

## 文档

`docs/` 下有交付清单、API 约定与架构方案，动接口或菜单前应先看：

- `docs/后端交付说明.md` — 接口/权限矩阵（新增接口照此表的权限串命名）。
- `docs/前端交付说明.md` — 页面/路由与接口映射。
- `docs/核心实体清单.md`、`docs/后端主数据与成班规则落点说明.md` — 实体与缺口。
- `docs/产品架构与菜单重组方案.md` — 菜单重组主线（教务工作台）。
- `docker/生产环境MySQL重建与安全加固方案.md`、`docs/生产部署说明.md`、`docker/prod.env.example` — 生产部署（`docker-compose.prod.yml` + `deploy.sh`）。

## 实体 / DTO 字段说明规范（软规范）

`domain/entity/*` 与 `dto/*` 公共字段必须加 `@Schema(description="中文")`（已集成 springdoc-openapi 2.8.13）。枚举字段用 `allowableValues` 列码值并在 `description` 给"码=含义"对照；必填字段加 `requiredMode = REQUIRED`；`BaseEntity` 公共字段由基类统一定义，子类不重复声明。Java record DTO 把 `@Schema` 写在组件上。完整约定见 `AGENTS.md`，新字段新增时同步，存量按域逐步补齐（教育域已首批补齐）。
