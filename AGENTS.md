# 项目开发约定（唯一事实源）

本文件是仓库开发约定的**唯一事实源**。`CLAUDE.md` 只做指引，不再单独维护内容；新增约定一律写在本文件，改完不需要两边同步。

## 项目概述

JiaoPei 是线下班课为主的教培管理平台，多模块单体仓库：

- `backend/` — Spring Boot 3.5 / Java 21 / MyBatis-Plus / Flyway / MySQL 8 / Redis
- `frontend/` — Vue 3 + Vite + TypeScript + Element Plus 管理端
- `miniapp/` — uni-app（Vue 3）微信小程序 + H5
- `db/`、`docker/`、`docs/` — 初始化脚本、容器编排、交付/架构文档

UI 文案、接口文档、数据库注释、代码注释**统一使用中文**（见「注释与文案语言规范」）。

## 注释与文案语言规范

1. 新增的**代码注释、日志文案、异常消息、`@Operation`/`@Schema` 说明、SQL COMMENT** 一律使用中文。
2. 存量英文注释/异常消息不强制回改，改到该文件时顺手转中文。
3. 变量/类/方法名仍用英文，不要中英混合命名。

## 本地环境与常用命令

### 编译环境：必须 JDK 21

本机系统默认 `java`/`mvn` 很可能是 JDK 1.8（`mvn -v` 可见），编译前先导出环境：

```bash
java -version                     # 不是 21 就导出环境变量
export JAVA_HOME="C:/Users/57299/.posh_sdk/java/21.0.9-oracle"
export PATH="$JAVA_HOME/bin:$PATH"
```

### 后端（工作目录 `backend/`）

```bash
mvn -q test                                                # 全量测试（改动必跑，不只是 mvn compile）
mvn -q -Dtest=AuthControllerWebTest test                   # 单个测试类
mvn -q -Dtest=AuthControllerWebTest#方法名 test             # 单个测试方法
mvn -q -DskipTests package                                 # 打包，产物 target/jiaopei-platform-backend-0.0.1-SNAPSHOT.jar
mvn spring-boot:run                                        # 本地运行（需先起 MySQL/Redis）
mvn -q dependency:go-offline                               # 预拉依赖（Dockerfile 构建也走这一步）
```

本地依赖：`docker compose -f docker/docker-compose.yml up -d`（MySQL 8.4 + Redis 7.4，库 `jiaopei_platform`）。后端默认连 `127.0.0.1:3306` 与 `127.0.0.1:6379`。Flyway 已启用，迁移位于 `classpath:db/migration`，`baseline-on-migrate=true`。

> 已知不一致：`application.yml` 默认连 `root/1234`，而 `docker/docker-compose.yml` 初始化为 `root/root`。以实际能连上的为准，可用 `DB_*` / `REDIS_*` 环境变量覆盖，不要为迁就文档去改本机密码。

### 改代码后必须重启后端

旧进程不会加载新代码，不重启就是“改了没效果”的假象：

```bash
netstat -ano | grep ":8080" | grep LISTENING                                   # 记下监听 8080 的旧 PID
powershell -NoProfile -Command "Stop-Process -Id <PID> -Force"                 # 停旧进程（可先用 Get-CimInstance 查 CommandLine 确认是自己的 java 进程再杀）
cd backend && "C:/Users/57299/.posh_sdk/java/21.0.9-oracle/bin/java" -jar target/jiaopei-platform-backend-0.0.1-SNAPSHOT.jar &
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/health        # 200 才算起来，别猜日志
```

### 前端（工作目录 `frontend/`）

```bash
pnpm install
pnpm dev        # vite dev server，127.0.0.1:5173，/api 代理到 127.0.0.1:8080
pnpm build      # vue-tsc 类型检查 + vite build —— 无 lint/单测脚本，build 即类型门禁
pnpm preview
```

### 小程序（工作目录 `miniapp/`）

```bash
pnpm dev:mp-weixin     # 微信开发者工具导入 dist/dev/mp-weixin
pnpm build:mp-weixin
pnpm dev:h5            # H5 调试
```

后端地址配置在 `miniapp/src/config.ts`（默认 `http://127.0.0.1:8080/api`，可被环境变量覆盖）。

## 鉴权：JWT + Redis 会话（最容易踩坑处）

- 登录 `POST /api/auth/login` 由 `JwtTokenService` 签发 HS256 JWT（自包含 userId/角色/权限声明，`exp` 与系统配置 `auth.token.expire_hours` 一致，默认 12 小时），同时写 Redis 会话；登出/踢人靠删会话实现。
- **Redis key 布局**（`TokenCacheService`，非数据库表）：`jiaopei:token:{token}` → 序列化的 `AuthenticatedUser`（TTL 由 `auth.token.expire_hours` 决定）；`jiaopei:user:tokens:{userId}` → 该用户 token 集合，供强制下线。业务侧统一调 `AuthApplicationService.invalidateUserSessions(userId)`（内部委托 `TokenCacheService.invalidateUserTokens`），改密/禁用账号/微信解绑时触发。
- `StubAuthenticationFilter` 读 `Authorization: Bearer <token>`，先验签名与过期，再查 Redis 会话。
- **过滤器认证失败时“吞异常继续放行”是有意设计**：该过滤器对所有请求生效（含公开路径），直接 `sendError` 会误拦“带过期 token 访问公开接口”。真正鉴权交给下游 `SecurityConfig.authorizeHttpRequests` + `jiaopei.security.public-paths`，不要在 catch 里加 `sendError`/`return`。
- JWT 密钥 `jiaopei.auth.jwt.secret`，生产必须用 `AUTH_JWT_SECRET` 环境变量覆盖（≥32 字节）。
- 权限判定：`@RequiresPermission("xxx:read|write")` + `PermissionGuardAspect`；取当前用户用 `AuthContext.requireUser()`。新增受保护接口路由必须加 `@RequiresPermission`，权限串与 `docs/后端交付说明.md` 命名一致。
- **登录失败排障**：`GlobalExceptionHandler` 把所有 `AuthenticationException` 统一渲染成同一句 `UNAUTHORIZED "Authentication is required"`，密码错和 token 失效响应体相同。排查 401 先用已知正确密码单独打 `/api/auth/login` 排除密码问题，再看过滤器/Redis。本地 admin 口令见 `V7__create_auth_user_role_tables.sql`（`{noop}` 前缀 = 明文比对）。
- **Redis 序列化**：`RedisConfig` 的 ObjectMapper **必须** `activateDefaultTyping(..., EVERYTHING)`（`NON_FINAL` 不给 record 里的 `Set<String>` 写 `@class`，反序列化会报 missing type id）；该 mapper 必须在方法内 new，**不要**做成全局 `@Bean ObjectMapper`，否则顶掉 Spring MVC 默认 mapper 导致 `OffsetDateTime` 序列化报错。序列化格式变更后旧数据无法反序列化，需 `redis-cli FLUSHDB`（所有人重新登录）。
- **系统参数优先入库**：可运行时调整的配置加到 `sys_system_config`（V14 引入），通过 `SystemConfigService.getConfigValue/getBooleanConfig/getIntConfig` 读取，别再写死进 `application.yml`；`is_system=1` 的配置禁止删除只能改值。当前在用的 key：
  - `auth.password.force_change_enabled` — 强制改密总开关（`StubAuthenticationFilter` 读取，关闭后忽略用户级 `forceChangePassword`）
  - `auth.password.strength_level` / `min_length` / `max_length` — 密码强度策略（`PasswordValidator` 读取）
  - `auth.token.expire_hours` — JWT/Redis 会话 TTL

## 统一响应与全局异常

- `ApiResponseAdvice`（`@ControllerAdvice`）自动把 controller 返回值包成 `ApiResponse{code/success/message/data/timestamp/traceId/path}`，controller 直接返回数据对象即可，不要手动包 `ApiResponse`。`null` 与 `Resource` 不包装。
- **新增 `byte[]` 返回接口（导出 Excel/图片、`/v3/api-docs` 等）必须绕过统一包装**：历史上 springdoc 的 `byte[]` 被强包进 `ApiResponse` 导致 `/v3/api-docs` 500（`ClassCastException: ApiResponse cannot be cast to [B`）。已在 `supports()` 开头放行 `ByteArrayHttpMessageConverter`，新增二进制接口保持该分支，症状复现先查 `common/web/ApiResponseAdvice.java`。验证：`/v3/api-docs` 返回 200 JSON。
- 业务错误抛 `BusinessException(ResultCode, message)`，由 `GlobalExceptionHandler` 统一渲染（校验异常、`AuthenticationException`、`AccessDeniedException`、`NoResourceFoundException` 也在此统一转失败响应），不要在 controller 拼响应。`ResultCode` 为固定枚举：`SUCCESS/BAD_REQUEST/UNAUTHORIZED/FORBIDDEN/NOT_FOUND/VALIDATION_ERROR/INTERNAL_ERROR`。
- `TraceIdFilter` 给每个请求打 `traceId`，`ApiResponse` 会带上，排障时可用。

## 模块划分与持久化

- 包结构 `com.jiaopei.platform`：`auth` / `organization`（机构/校区/教室/学期/时间段）/ `education`（教师/学员/班级/课程/报名/排课/考勤/变动通知）/ `system`（用户角色菜单字典、bootstrap、health）/ `common` / `config`。
- 模块内子包：`controller / service(+impl) / repository(仅 org/edu 有) / mapper / domain(entity) / dto`。简单 CRUD 走 mapper。
- `JiaopeiProperties.modules.*-enabled` 仅是 bootstrap 展示状态，代码里没有 `@ConditionalOnProperty`，别误当模块开关。
- 实体继承 `BaseEntity`：雪花 `Long` id（`IdType.ASSIGN_ID`）、审计字段、`deleted`（`@TableLogic`）。建表 `deleted` 默认 0。
- Mapper 由 `MybatisPlusConfig` 的 `@MapperScan` 显式扫描四个业务模块的 `mapper` 包；XML 位置 `classpath*:/mapper/**/*.xml`（当前 `resources/mapper/` 为空，全部走 `BaseMapper`）；分页用 `PaginationInnerInterceptor`（MySQL 方言）。
- schema 变更一律走 Flyway：`backend/src/main/resources/db/migration/V<n>__desc.sql`，表名 `jp_` 前缀（系统表沿用 `sys_`），**不要手改既有迁移**。

## 安全与数据一致性约定（必填）

以下为审查发现的高频问题固化的硬约定：

1. **租户隔离**：客户端传入的 `institutionId`/`campusId` 只是筛选条件，服务端必须与当前登录用户所属机构比对（`AuthContext.requireUser()`），不允许拿任意入参直查他机构数据。参照 `SystemInstitutionController` 的正确写法。
2. **权限回收即时生效**：修改角色权限、用户角色、菜单授权后必须调用 `invalidateUserSessions(userId)` 强制下线受影响用户；权限快照写进了 JWT，不清会话则 12 小时内旧权限继续可用。
3. **逻辑删除与唯一索引**：配合 `@TableLogic` 的唯一索引必须包含 `deleted` 列（如 `uk(code, deleted)`），否则“删除后重建同名记录”必撞键（V18 已为 `sys_menu` 擦过一次）。
4. **先查后插必须有兜底**：冲突校验（排课撞教师/教室/班级、绑定唯一性）selectCount 后 insert 存在并发竞态，必须配数据库唯一约束或显式锁；多表写操作必须 `@Transactional`，注意同类内自调用事务不生效。
5. **MyBatis-Plus 置空字段**：`updateById` 默认忽略 null，想把字段设为 NULL 必须用 `UpdateWrapper.set(col, null)` 或字段加 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`。
6. **缓存现状**：项目**未启用** `@EnableCaching`，`@Cacheable`/`@CacheEvict` 目前是空注解。启用缓存前先补配置并验证序列化；不要依赖注解暗示“已有缓存”。
7. **敏感字段**：手机号等敏感数据出入参遵循 `docs/操作留痕与敏感数据说明.md` 的脱敏/加密链路（`SensitiveCipher`），新增明文外泄即视为缺陷。

## 大 ID 跨端精度规范

雪花 ID 为 19 位 `Long`，超出 JS `Number.MAX_SAFE_INTEGER`，跨端处理机制：

- **后端入参**：`FlexibleLongIdDeserializer` 全局注册，`Long` 字段同时接受数字与字符串，业务 id 字符串（非纯数字、以数字结尾）由 `ExternalIdParser` 解析尾部数字。新增接收 `Long` 的 DTO 自动生效，不要自行写解析。
- **响应出参**：后端仍按 JSON 数字输出 `Long`；`frontend/src/api/http.ts` 与 `miniapp/src/utils/request.ts` 在 `transformResponse` 里用**字段名模式**兜底——匹配 `id`、`*Id`、`*Ids`、`*By` 且值为 16 位以上数字的，转成字符串再 `JSON.parse`。
- **结论：不需要维护字段白名单**（旧 `LARGE_ID_FIELDS` 白名单机制已废弃，文档旧描述作废）。新增雪花 ID 字段按 `xxxId`/`xxxIds`/`xxBy` 命名即被自动覆盖。
- **剩余边界**：若出现**不按 Id/By 命名**的 16+ 位 Long 响应字段（或嵌套在自由 JSON 字符串里的 id），会被静默截断精度。更彻底的替代方案是后端 Jackson 全局 `Long → ToStringSerializer`（响应统一输出字符串），但那会改变所有接口响应形态、前端两端要一起回归，仅在边界字段真的出现时再评估。
- 已排查现状：全仓库非 id 语义的 `Long` 字段均为计数/分页/时长（`count`/`total`/`page`/`durationMs` 等小数值），当前机制全覆盖，无未适配字段。
- 自查方式：改相关字段后看真实响应——字段值应为字符串形式，且前端显示 id 与数据库一致。

## Service 接口签名变更规范

修改 Service 接口方法签名后必须同步所有调用方，否则测试编译失败：Controller 注入调用处、测试里的 `given(...willReturn)` mock 与 `verify(...)`、跨模块的 Service 内部调用。验证用 `mvn -q test`（不是只 `mvn compile`）；`*ApiCompatibilityWebTest` 会显式 verify 各 Service 方法签名，是签名变更的安全网，改了签名必须同步改。

## 学期（term）必填关系

后端设计上学期是**可选维度**，前端不要误强制：

- 班级 `jp_class.term_id`：可为 NULL，创建班级学期可选。
- 报名 `jp_enrollment` / 入班 `jp_class_student`：完全不涉及学期。
- 排课 `jp_schedule_lesson.term_id`：`NOT NULL`，唯一强制学期的实体。

前端范围栏的学期选择器是**筛选条件**，不应作为报名/入班前置校验；只有 `ScheduleAttendanceView.vue` 的排课弹窗校验学期是对的。

## 前端 / 小程序约定

- API 层 `frontend/src/api/`：`http.ts`（axios 实例 + 大 ID 兜底）、`request.ts`（`unwrap` 解包 `ApiResponse.data`，非 success 抛 `ApiError`），按模块拆 `auth/organization/education/system.ts`。
- 状态：Pinia，登录态持久化在 `localStorage` key `jiaopei.auth.session`（`utils/auth-session.ts`），`http.ts` 从该处取 `Authorization`；未登录时回退到 `import.meta.env.VITE_API_AUTHORIZATION`（仅本地联调用的固定头，生产构建不要注入）。
- 路由 `router/index.ts`：`/login`、`/change-password` 公开；其余挂在 `AppLayout` 下；系统管理多 tab 复用 `SystemManagementView.vue`，靠 `meta.systemTab` 区分。
- 路径别名 `@` → `frontend/src`（vite + tsconfig 均配置）。
- 页面写操作按钮要按权限码显隐（与后端 `@RequiresPermission` 同一套码）；列表页优先使用 `ConfigurableCrudPage`/`ConfigurableList`（自带请求序列号守卫，切机构不竞态）。

## 实体 / DTO 字段说明规范（软规范）

### 背景

项目已集成 `springdoc-openapi-starter-webmvc-ui 2.8.13`，Controller 已使用 `@Tag`，但实体与 DTO 的字段缺少中文说明，导致 IDEA 悬停看不到语义、Swagger UI 字段名是英文、枚举字段调用方不知道取值。

### 约定内容

1. 所有 `domain/entity/*` 与 `dto/*`（Request/Response/View）的公共字段必须添加 `@Schema(description="中文说明")`。
2. **枚举型字段**额外用 `allowableValues` 列码值，`description` 给“码=含义”对照：

   ```java
   @Schema(description = "出勤状态(PRESENT=出勤,ABSENT=缺勤,LATE=迟到,LEAVE=请假)",
           allowableValues = {"PRESENT", "ABSENT", "LATE", "LEAVE"})
   private String attendanceStatus;
   ```

3. 继承 `BaseEntity` 的公共字段（`id`/`createdAt`/`createdBy`/`updatedAt`/`updatedBy`/`deleted`）由基类统一定义，子类不重复声明。
4. 必填字段标注 `requiredMode = Schema.RequiredMode.REQUIRED`。
5. Java record DTO 的 `@Schema` 直接写在 record 组件上，与其他注解并列。
6. `@Schema` 的中文与对应表/字段 COMMENT 保持一致。

适用注解 `io.swagger.v3.oas.annotations.media.Schema`（springdoc 已带依赖，无需新增）。落地：新建实体/DTO 同步加、不留存量；存量按域补齐；暂不强制 Checkstyle/ArchUnit。验证：`swagger-ui.html` 里字段说明应展示中文，`/v3/api-docs` 的字段 `description` 应已写入。

## 接口中文说明规范（@Operation，软规范）

所有 Controller 的每个接口方法必须写：

```java
@Operation(summary = "获取教师列表", description = "按机构与校区筛选,返回教师列表")
@GetMapping
@RequiresPermission("education:read")
public ApiResponse<List<TeacherEntity>> list(...) { ... }
```

- `summary` 简洁中文短语，展示在 Swagger 列表与「功能权限总览」页（`PermissionOverviewController` 靠 `@RequiresPermission` + `@Operation.summary` 生成“受控接口”说明；缺 `@Operation` 总览页“说明”列为空）。`description` 可选，补充入参/筛选细节；两者都用中文。
- 公开接口（如 `HealthController`）同样建议补，保持文档完整。
- 覆盖度自查（注意类级 `@RequestMapping` 会被计入 mapping 数，应相差 1）：

  ```bash
  grep -c "@Operation" **/*Controller.java
  grep -cE "@(Get|Post|Put|Delete|Request)Mapping" **/*Controller.java
  ```

- 验证：`http://localhost:8080/swagger-ui.html` 接口显示中文 summary；总览页“说明”列非空即为补齐。

## 数据库表 / 字段中文注释规范（必填）

建新表/加新列时表注释与字段注释**必须有**，缺注释视为未完成；存量按域补齐。

1. 新表建表语句末行 `COMMENT='中文说明'`（说明业务用途）；存量表用 `ALTER TABLE \`表名\` COMMENT = '...';`。
2. 每个字段列定义后跟 `COMMENT '中文说明'`；存量补注释用 `MODIFY COLUMN` 且**必须与原列定义完全一致**（类型 / NULL / DEFAULT），否则 MySQL 隐式改列。
3. 枚举/布尔字段在 COMMENT 给码值对照：

   ```sql
   status  VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT '状态(active=启用,disabled=停用)',
   deleted TINYINT(1)  NOT NULL DEFAULT 0        COMMENT '逻辑删除标识(0=未删除,1=已删除)'
   ```

4. 通用审计字段（`id`/`created_at`/`created_by`/`updated_at`/`updated_by`/`deleted`）每表都要有 COMMENT，文案与 `BaseEntity` 的 `@Schema` 一致。
5. 表/字段注释中文与实体 `@Schema(description=...)` 一致，避免“表里一个说法、代码里另一个说法”。

自查：

```bash
# 找没有末行 COMMENT 的建表
grep -rlE "ENGINE=InnoDB" backend/src/main/resources/db/migration | xargs grep -L "COMMENT"
# 列注释非空验证
# SHOW CREATE TABLE / INFORMATION_SCHEMA.COLUMNS.COLUMN_COMMENT
```

完整示例见 `V31__add_miniapp_rel_table_notes.sql`。

## 文档索引

动接口或菜单前先查 `docs/`：

- `docs/后端交付说明.md` — 接口/权限矩阵（权限串照此命名）
- `docs/前端交付说明.md` — 页面/路由与接口映射
- `docs/核心实体清单.md`、`docs/后端主数据与成班规则落点说明.md`
- `docs/产品架构与菜单重组方案.md` — 菜单重组主线
- `docs/miniapp.md`、`docs/list-config-guide.md`、`docs/config-contract.md`
- `docs/操作留痕与敏感数据说明.md` — 审计与脱敏链路
- `docs/生产部署说明.md`、`docker/生产环境MySQL重建与安全加固方案.md`、`docker/prod.env.example` — 生产部署（`docker-compose.prod.yml` + `deploy.sh`）
