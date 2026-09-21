# 项目开发约定（唯一事实源）

本文件是仓库开发约定的**唯一事实源**。`CLAUDE.md` 只做指引，不再单独维护内容；新增约定一律写在本文件，改完不需要两边同步。

## 项目概述

JiaoPei 是线下班课为主的教培管理平台，多模块单体仓库：

- `backend/` — Spring Boot 3.5 / Java 21 / MyBatis-Plus / Flyway / MySQL 8 / Redis，Maven 聚合工程（见「后端模块划分」）
- `frontend/` — Vue 3 + Vite + TypeScript + Element Plus 管理端
- `miniapp/` — uni-app（Vue 3）微信小程序 + H5
- `docker/`、`docs/` — 容器编排、交付/架构文档（原顶层 `db/` 只放过一个占位迁移，已随阶段二B 删除，迁移统一在 `backend/app/src/main/resources/db/migration`）

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

### 后端模块划分（阶段二B 起为 Maven 多模块）

`backend/` 是聚合工程（`jiaopei-platform-backend-parent`，`packaging=pom`，groupId `com.cornerstone`），四个子模块：

| 模块 | artifactId | Java 包根（阶段四起） | 内容 | 依赖方向 |
| --- | --- | --- | --- | --- |
| `cornerstone-core/` | `cornerstone-core` | `com.cornerstone.core.*` | 底座：`auth` / `system` / `common` / `config` / `tenant` / `audit` + 全部第三方 starter | 不依赖任何业务模块 |
| `module-organization/` | `module-organization` | `com.cornerstone.module.organization.*` | 业务：校区/教室/学期/时间段 | → core |
| `module-education/` | `module-education` | `com.cornerstone.module.education.*` | 业务：教师/学员/班级/排课/考勤/小程序 | → core、→ organization |
| `app/` | `jiaopei-platform-backend` | `com.cornerstone`（仅主类 `CornerstoneApplication`） | 组装启动：主类、`application.yml`、Flyway 迁移、全量测试、`spring-boot-maven-plugin` | → 上面三块 |

- **包根即模块身份**：底座类只能在 `com.cornerstone.core.*`，业务模块在 `com.cornerstone.module.<name>.*`；`ArchitectureRulesTest` 的包白名单按此约束，新增顶层包要同步。artifactId、jar 名、数据库库名、容器与镜像名**保持 jiaopei 不变**（那是本项目的部署标识，不属于底座身份）。表前缀属另一码事：底座表 `sys_`、业务表 `jp_`，阶段五-0b 已把底座四张表从 `jp_` 改到 `sys_`。
- **只有 `app/` 产出可执行 jar**，artifactId 保持 `jiaopei-platform-backend`，故 jar 文件名不变，落在 `backend/app/target/`。
- `spring-boot:run` 不能带 `-am`，也不能在根目录直接跑：会对外壳模块执行同一 goal，core 无主类直接 `Unable to find a suitable main class` 失败。改代码后先 `mvn -q -DskipTests install` 再 `-pl app spring-boot:run`，或直接用 `scripts/restart-backend.sh`。脚本自身的 `JAVA_HOME:-` 兜底不覆盖已存在的旧值，跑前先 `export JAVA_HOME=` 指向 JDK 21，否则 mvn 用系统 JDK 8 报 `class file version 61.0`。
- 业务专属的第三方依赖放各自模块的 pom：`wx-java-miniapp-spring-boot-starter` 已随微信登录下沉到 `module-education`（版本仍由 parent 的 `dependencyManagement` 管），core 不再声明。core 的 pom 里**不允许**出现 `module-*` 依赖，也不允许出现只有业务模块用得上的第三方 SDK。
- 新增类放在自己所属模块，不要在 `app/src/main/java` 下写业务代码（该模块只放主类与资源）。

### 模块装配与开关（阶段二C 起为真开关）

装配分两条路，**这是有意为之，别改成一致**：

- **底座**：`app` 启动类 `@SpringBootApplication(scanBasePackages = {auth, system, tenant, audit, common, config})` 显式扫底座六个顶层包。底座是必选项、且它提供的 `DataSource`/`SqlSessionFactory` 必须早于 Spring Boot 与 MyBatis-Plus 的自动配置出现（否则后者的 `@ConditionalOnMissingBean` 判定失效，多出重复数据源），所以走"用户配置"阶段而不是自动配置。
- **业务模块**：各模块 jar 自带 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，指向自己的 `XxxModuleAutoConfiguration`（`@AutoConfiguration` + `@ConditionalOnProperty` + `@ComponentScan` + `@MapperScan`）。**加/减模块只改 `app/pom.xml` 依赖**，不用动启动类。

开关（`application.yml`，可用同名环境变量覆盖）：

```yaml
cornerstone:
  modules:
    organization-enabled: ${MODULE_ORGANIZATION_ENABLED:true}
    education-enabled: ${MODULE_EDUCATION_ENABLED:true}
```

- `false` 时该模块整包不注册：接口 404、`/v3/api-docs` 与「功能权限总览」都不再列出，`/api/system/bootstrap` 的模块清单里也没有它。
- 依赖方向 `education → organization`，**关 organization 必须同时关 education**（只关前者会让后端的模块清单出现"装了但接口没挂上"的半残状态）。
- 迁移目录由 `cornerstone.flyway.locations`（默认只有 `classpath:db/migration`）拼接；模块要自带脚本时在此追加该 jar 内目录，不改代码。

必须知道的三个连带约束：

1. **不要把 `scanBasePackages` 放宽回 `com.cornerstone`** 或加进业务包——那等于无条件扫进所有业务 `@Component`，开关形同虚设。`ModuleSwitchGuardTest` 会拦住这类改动。
2. **`@WebMvcTest` 切片会关闭自动配置**，业务模块的控制器在切片里不会被扫描到。写业务控制器的切片测试时，要把控制器同时列进 `@Import({...})`（现有三处参照：`SecondThirdMainlineApiCompatibilityWebTest`、`EducationFourthMainlineApiCompatibilityWebTest`、`TimeSlotTemplateControllerWebTest`）；否则症状就是断言 `200` 实际 `404`。
3. **底座新增顶层包**时要同步三处：启动类 `scanBasePackages`、core `@MapperScan`（若带 mapper）、`ArchitectureRulesTest` 的包白名单。漏扫描的类不会报错，只是静默不进容器。

模块自述：底座定义 `ModuleDescriptor`（`system.module` 包），各模块注册自己的实现，`PlatformBootstrapController` 与 `OpenApiConfig` 只消费注册表，新增模块不用再改这两处。

### 后端常用命令（工作目录 `backend/`）

```bash
mvn -q test                                                # 全量测试（改动必跑，不只是 mvn compile）
mvn -q -Dtest=AuthControllerWebTest test                   # 单个测试类
mvn -q -Dtest=AuthControllerWebTest#方法名 test             # 单个测试方法
mvn -q -DskipTests package                                 # 打包全部模块，产物 app/target/jiaopei-platform-backend-0.0.1-SNAPSHOT.jar
mvn -q -pl module-education -am -DskipTests package         # 只构建某业务模块及其依赖（-am 自动带上 core）
mvn -q -pl app spring-boot:run                             # 本地运行（需先起 MySQL/Redis，且 core/业务模块已 install 到本地仓库）
mvn -q dependency:go-offline                               # 预拉依赖（Dockerfile 构建也走这一步）
```

本地依赖：`docker compose -f docker/docker-compose.yml up -d`（MySQL 8.4 + Redis 7.4，库 `jiaopei_platform`）。后端默认连 `127.0.0.1:3306` 与 `127.0.0.1:6379`。Flyway 已启用，迁移位于 `classpath:db/migration`，`baseline-on-migrate=true`。

> 已知不一致：`application.yml` 默认连 `root/1234`，而 `docker/docker-compose.yml` 初始化为 `root/root`。以实际能连上的为准，可用 `DB_*` / `REDIS_*` 环境变量覆盖，不要为迁就文档去改本机密码。

### 改代码后必须重启后端

旧进程不会加载新代码，不重启就是“改了没效果”的假象。**直接用仓库脚本**（Git Bash）：

```bash
scripts/restart-backend.sh              # 停旧进程 -> mvn package -> 后台启动 -> 健康检查轮询
scripts/restart-backend.sh --skip-build # 已有 jar 时只重启
```

手工流程（脚本不可用时）：

```bash
netstat -ano | grep ":8080" | grep LISTENING                                   # 记下监听 8080 的旧 PID
powershell -NoProfile -Command "Stop-Process -Id <PID> -Force"                 # 停旧进程（可先用 Get-CimInstance 查 CommandLine 确认是自己的 java 进程再杀）
cd backend && "C:/Users/57299/.posh_sdk/java/21.0.9-oracle/bin/java" -jar app/target/jiaopei-platform-backend-0.0.1-SNAPSHOT.jar &
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/health        # 200 才算起来，别猜日志
```

### CI 与本地门禁

- `.github/workflows/ci.yml`：后端 `mvn test` + 前端 `pnpm build`（远程当前是 Gitee，Actions 不执行）。
- Gitee 环境下用等价本地脚本：`scripts/ci-check.sh`（后端测试 -> eslint -> 类型检查+构建），提交前应跑通。
- 后端有 **ArchUnit 架构守护测试**（`backend/app/src/test/.../architecture/ArchitectureRulesTest.java`）：接口必须有 `@Operation`、写接口必须有 `@RequiresPermission`（**免检口径是"类级 `@RequestMapping` 以 `/api/auth` 开头"**，按路径而不是按包判定，所以业务模块挂 `/api/auth/**` 的公开登录接口同样豁免；改这个前缀要同步安全配置的 `public-paths`）、Service 不得依赖 Controller、`@TableName` 实体必须在 `..domain..` 包、底座包（auth/system/common/config/tenant/audit）不得依赖 organization/education 业务包、生产类必须落在已声明的顶层包（防"新增包忘了加进扫描清单"）。同目录的 `ModuleSwitchGuardTest` 守护模块开关前提。测试只在 `app/` 模块运行，能看到全部模块的类。违反即 `mvn test` 失败，取代人工 grep 自查。

### 前端（工作目录 `frontend/`）

```bash
pnpm install
pnpm dev          # vite dev server，127.0.0.1:5173，/api 代理到 127.0.0.1:8080
pnpm build        # vue-tsc 类型检查 + vite build
pnpm lint         # ESLint（flat config，eslint.config.js）
pnpm lint:fix     # ESLint 自动修复
pnpm format       # Prettier 格式化 src/ 与 packages/（.prettierrc.json：无分号/单引号/100 列）
pnpm preview
```

无单测脚本；`pnpm build`（类型）+ `pnpm lint`（规范）共同构成前端门禁。ESLint 存量宽松起步（`no-explicit-any` 关闭），清理完成后逐步收紧。`pnpm` 命令一律在 `frontend/` 根执行，workspace 会自动带到各包。

### 小程序（工作目录 `miniapp/`）

```bash
pnpm dev:mp-weixin     # 微信开发者工具导入 dist/dev/mp-weixin
pnpm build:mp-weixin
pnpm dev:h5            # H5 调试
```

后端地址配置在 `miniapp/src/config.ts`（默认 `http://127.0.0.1:8080/api`，可被环境变量覆盖）。

## 鉴权：JWT + Redis 会话（最容易踩坑处）

- 登录 `POST /api/auth/login` 由 `JwtTokenService` 签发 HS256 JWT（自包含 userId/角色/权限声明，`exp` 与系统配置 `auth.token.expire_hours` 一致，默认 12 小时），同时写 Redis 会话；登出/踢人靠删会话实现。
- **Redis key 布局**（`TokenCacheService`，非数据库表）：`cornerstone:token:{token}` → 序列化的 `AuthenticatedUser`（TTL 由 `auth.token.expire_hours` 决定）；`cornerstone:user:tokens:{userId}` → 该用户 token 集合，供强制下线。业务侧统一调 `AuthApplicationService.invalidateUserSessions(userId)`（内部委托 `TokenCacheService.invalidateUserTokens`），改密/禁用账号/微信解绑时触发。
- `StubAuthenticationFilter` 读 `Authorization: Bearer <token>`，先验签名与过期，再查 Redis 会话。
- **过滤器认证失败时“吞异常继续放行”是有意设计**：该过滤器对所有请求生效（含公开路径），直接 `sendError` 会误拦“带过期 token 访问公开接口”。真正鉴权交给下游 `SecurityConfig.authorizeHttpRequests` + `cornerstone.security.public-paths`，不要在 catch 里加 `sendError`/`return`。
- JWT 密钥 `cornerstone.auth.jwt.secret`，生产必须用 `AUTH_JWT_SECRET` 环境变量覆盖（≥32 字节）。
- 权限判定：`@RequiresPermission("xxx:read|write")` + `PermissionGuardAspect`；取当前用户用 `AuthContext.requireUser()`。新增受保护接口路由必须加 `@RequiresPermission`，权限串与 `docs/后端交付说明.md` 命名一致。**权限码前缀按归属分**：底座机构（=租户）接口用 `tenant:*`，业务模块用自己域名的码（`organization:*`/`education:*`）；底座接口挂业务前缀的码等于逼第二个项目授予一个它没有的模块（阶段五-0c 已把机构接口从 `organization:*` 改到 `tenant:*`，角色种子随之裁成底座码 + 业务码各自一段，见 V41/V42）。
- **登录失败排障**：`GlobalExceptionHandler` 把所有 `AuthenticationException` 统一渲染成同一句 `UNAUTHORIZED "Authentication is required"`，密码错和 token 失效响应体相同。排查 401 先用已知正确密码单独打 `/api/auth/login` 排除密码问题，再看过滤器/Redis。本地 admin 口令见 `V7__create_auth_user_role_tables.sql`（`{noop}` 前缀 = 明文比对）。
- **Redis 序列化**：`RedisConfig` 的 ObjectMapper **必须** `activateDefaultTyping(..., EVERYTHING)`（`NON_FINAL` 不给 record 里的 `Set<String>` 写 `@class`，反序列化会报 missing type id）；该 mapper 必须在方法内 new，**不要**做成全局 `@Bean ObjectMapper`，否则顶掉 Spring MVC 默认 mapper 导致 `OffsetDateTime` 序列化报错。序列化格式变更后旧数据无法反序列化，需 `redis-cli FLUSHDB`（所有人重新登录）。
- **系统参数优先入库**：可运行时调整的配置加到 `sys_system_config`（V14 引入），通过 `SystemConfigService.getConfigValue/getBooleanConfig/getIntConfig` 读取，别再写死进 `application.yml`；`is_system=1` 的配置禁止删除只能改值。当前在用的 key：
  - `auth.password.force_change_enabled` — 强制改密总开关（`StubAuthenticationFilter` 读取，关闭后忽略用户级 `forceChangePassword`）
  - `auth.password.strength_level` / `min_length` / `max_length` — 密码强度策略（`PasswordValidator` 读取）
  - `auth.token.expire_hours` — JWT/Redis 会话 TTL
  - `auth.login.page_tip` — 登录页表单下方提示语（`AuthController` 的 `GET /api/auth/login-page` 读取，core 无值即空串、前端不渲染；**种子放各项目的 `app/` 迁移**，教培侧见 V43，别再写死进 `LoginView`）

## 统一响应与全局异常

- `ApiResponseAdvice`（`@ControllerAdvice`）自动把 controller 返回值包成 `ApiResponse{code/success/message/data/timestamp/traceId/path}`，controller 直接返回数据对象即可，不要手动包 `ApiResponse`。`null` 与 `Resource` 不包装。
- **新增 `byte[]` 返回接口（导出 Excel/图片、`/v3/api-docs` 等）必须绕过统一包装**：历史上 springdoc 的 `byte[]` 被强包进 `ApiResponse` 导致 `/v3/api-docs` 500（`ClassCastException: ApiResponse cannot be cast to [B`）。已在 `supports()` 开头放行 `ByteArrayHttpMessageConverter`，新增二进制接口保持该分支，症状复现先查 `common/web/ApiResponseAdvice.java`。验证：`/v3/api-docs` 返回 200 JSON。
- 业务错误抛 `BusinessException(ResultCode, message)`，由 `GlobalExceptionHandler` 统一渲染（校验异常、`AuthenticationException`、`AccessDeniedException`、`NoResourceFoundException` 也在此统一转失败响应），不要在 controller 拼响应。`ResultCode` 为固定枚举：`SUCCESS/BAD_REQUEST/UNAUTHORIZED/FORBIDDEN/NOT_FOUND/VALIDATION_ERROR/INTERNAL_ERROR`。
- `TraceIdFilter` 给每个请求打 `traceId`，`ApiResponse` 会带上，排障时可用。

## 模块划分与持久化

- 包结构（阶段四起）：底座在 `com.cornerstone.core.*`，六个顶层包 `auth` / `system` / `common` / `config` / `tenant`（机构=租户）/ `audit`；业务在 `com.cornerstone.module.*`，`organization`（校区/教室/学期/时间段）→ `module-organization`、`education`（教师/学员/班级/课程/报名/排课/考勤/变动通知/小程序）→ `module-education`；`app` 只放主类 `com.cornerstone.CornerstoneApplication`。
- 模块内子包：`controller / service(+impl) / repository(仅 org/edu 有) / mapper / domain(entity) / dto`。简单 CRUD 走 mapper。
- `CornerstoneProperties.modules.*-enabled` 自阶段二C 起是**真开关**（`@ConditionalOnProperty` 落在各模块 `XxxModuleAutoConfiguration` 上），见「模块装配与开关」。
- 实体继承 `BaseEntity`：雪花 `Long` id（`IdType.ASSIGN_ID`）、审计字段、`deleted`（`@TableLogic`）。建表 `deleted` 默认 0。
- Mapper 扫描按模块各自声明：core 的 `MybatisPlusConfig` 扫 `auth`/`system`/`tenant` 三个 mapper 包，`AuditDataSourceConfig` 单独扫 `audit.mapper`（日志库），各业务模块在自己的 `XxxModuleAutoConfiguration` 上 `@MapperScan` 本模块 mapper 包（`sqlSessionFactoryRef` 都指到 `primarySqlSessionFactory`）。新模块不需要改 core。XML 位置 `classpath*:/mapper/**/*.xml`（当前 `app/src/main/resources/mapper/` 为空，全部走 `BaseMapper`）；分页用 `PaginationInnerInterceptor`（MySQL 方言）。
- schema 变更一律走 Flyway：`backend/app/src/main/resources/db/migration/V<n>__desc.sql`（迁移脚本目前全部集中在 `app/` 模块，阶段二C 才按模块拆分目录），表名前缀按归属分：**底座表 `sys_`**（认证授权/系统配置/租户，阶段五-0b 起统一，V40 把原 `jp_user_account`/`jp_role`/`jp_user_role_rel`/`jp_institution` 改了名，索引与外键键名保持历史 `jp_` 不动）、**业务模块表 `jp_`**，**不要手改既有迁移**（已上生产的迁移出问题时同样走新增 `V<n+1>` 修复；仅本地验证阶段、生产未跑过的迁移允许改写并本地删 `flyway_schema_history` 对应行重放）。

## 迁移脚本生产兼容检查（必填，V38 生产翻车事故固化）

**迁移脚本按 Flyway 的 MySQL-5 语法解析**（2026-09-20 生产日志实证：服务端实为 MySQL 8.4，但 Flyway 11.7.2 支持上限 8.1，对 8.4 退化走 `MySQL-5 SqlParser`，日志 `MySQL-5 SqlParser failed` 即由此而来——限制在解析器，不在服务器版本）。**每个新迁移提交前必须逐条自查以下各项**，任何一条不满足就不许合并：

1. **禁用 MySQL 8.0-only 语法**：CTE（`WITH ... AS`）、窗口函数（`ROW_NUMBER() OVER (...)`）、`JSON_TABLE`、8.0 专属字符集/函数（如 `utf8mb4_0900_ai_ci`）。去重等操作改用 `DELETE t1 FROM t JOIN t t2 ON t1.id > t2.id AND ...` 自连接。
2. **禁用 MariaDB 方言**：`ALTER TABLE ... ADD COLUMN|DROP INDEX|MODIFY COLUMN IF [NOT] EXISTS` 在 MySQL 任何版本都不存在，只有 `CREATE TABLE IF NOT EXISTS` 合法。
3. **禁用 `IF(` 函数**：Flyway MySQL-5 解析器会把表达式里的 `IF (` 按存储程序 `IF...THEN...END IF` 块处理而直接报语法错（`IFNULL`/`CONCAT`/`GROUP_CONCAT` 不受影响）。
4. **迁移保持普通 DDL、一条路径**：不写 `@sql + PREPARE` 动态守卫（审查口径：脚本要一眼读懂）。唯一键重建按"**先建新键、后删旧键**"顺序：新键同样以 institution_id 开头，外键最左前缀索引全程不断（避免 1553）；若新旧键**同名**，先建新会报 1061 Duplicate key name，必须改为先 `DROP INDEX` 旧键再 `ADD` 新键（删建也可合进同一条 ALTER）；软删数据清理用普通 `DELETE`。脚本停在半实施状态时（重放报 1060/1061/1091，说明上次已执行过部分语句）注意：**Flyway 每次重启都从头重放整个脚本，注释掉已执行语句是无效的**——按脚本尾部恢复注释处理：手工补完缺失 DDL，删失败行并补插一条 checksum 与 jar 一致的 `success=1` 历史记录，再重启。
5. **外键与前缀索引**：删唯一键/索引前确认它是子查询外键列的最左前缀索引，否则先 `DROP FOREIGN KEY`、键调整完再回挂（否则 1553）。
6. **验证方式**：本地用 scratch 库把 V1..V<n> 全量 source 一遍（`mysql --default-character-set=utf8mb4`，Windows 客户端默认码字符集会报 Data too long），至少覆盖“干净链路一次跑通 + 上次失败现场的中间态按恢复指引续跑”两种状态（本地服务端与生产同为 8.4，回放即等价验证，无需另找 5.7 环境；语法以 MySQL-5 解析器口径为准）。
7. **生产修复流程**：迁移失败（`Detected failed migration to version X`）→ 修复脚本后在生产执行 `DELETE FROM flyway_schema_history WHERE version='X' AND success=0`（等价 flyway repair），再重启后端让 Flyway 重放；改过的脚本若已在生产成功过，则不允许原地改，必须新增修复迁移。

## 底座边界（Cornerstone 阶段二B 起为物理 Maven 隔离，详见 `docs/底座分离与Cornerstone重构计划.md`）

- 底座包 = `com.cornerstone.core.` 下的 `auth` / `system` / `common` / `config` / `tenant`（机构=多租户概念，阶段二A已从 organization 迁回）/ `audit`，业务包 = `com.cornerstone.module.organization`（校区/教室/学期/时间段）/ `com.cornerstone.module.education`（教师/学员/班级/排课等）。判定标准：**换一个行业后这段代码还有没有用**，没用就不许进底座。
- 边界现在有两层保护：Maven 依赖方向（`cornerstone-core` 的 pom 不声明任何 `module-*` 依赖，写错直接编译不过）+ ArchUnit 规则（底座包不得依赖 `organization..` / `education..`，Institution 豁免已随机构迁回移除）。
- 底座确需感知业务数据时（如注销账号清理学员关联、判定登录令牌属于哪个端），用**事件**（底座发 `UserAccountDeregisteredEvent`，业务模块 `@EventListener` 监听）或 **SPI 接口**（接口定义在底座如 `PortalResolver`，实现放业务模块；单实现经 `ObjectProvider` 可选注入，多实现（如各端判定）注入 `List<T>` 按装配顺序取第一个非空结果，全都不适用时底座落回默认值），不要在底座里直接 import 业务 mapper。**业务属性往底座表加列不算解耦**（如"用户默认校区"，阶段五-0a 已迁成 `module-organization` 自己的 `jp_user_campus_pref`）。
- **底座不得按接口路径/字段值硬编码业务语义**：如审计的操作类型，改成由业务控制器自己标 `@OperateLog(actionType = ActionType.LOGIN)`，`OperateLogAspect` 只认注解，不再写死 `/api/auth/wechat/login` 这类业务路径。行业专属枚举（`Portal` 之类只服务某个业务端的枚举）不进底座，直接删；底座只留通用默认。
- 新的小程序/行业专属服务放业务包（参照 `MiniappServiceImpl` 已迁至 `education.service`），`auth` 只留通用登录态。
- **命名边界（阶段四定稿，改名字时照这张表判断，别顺手连数据库名一起改）**：
  - 属于底座身份 → 用 `cornerstone`：Java 包根 `com.cornerstone.*`、Maven groupId、Spring 配置前缀 `cornerstone.*`、Redis key 前缀 `cornerstone:`、前端 npm scope `@cornerstone/*`、浏览器 storage key `cornerstone.auth.session` / `cornerstone-nav-mode` / `cornerstone-menu-preferences`、小程序 `cornerstone.mini.auth.session`、Hikari 池名 `cornerstone-*-pool`。
  - 属于本项目部署/业务标识 → **保持 `jiaopei` / `jp_`**：库名 `jiaopei_platform` 与日志库 `jiaopei_log`、MySQL 账号、业务表前缀 `jp_`（**底座表已在阶段五-0b 统一改 `sys_`**，见「模块划分与持久化」的 Flyway 条目；索引/外键/检查约束名 `uk_jp_*`/`fk_jp_*`/`chk_jp_*` 保持原样，不随表改名）、artifactId 与 jar 名 `jiaopei-platform-backend`、`spring.application.name`、容器名 `jiaopei-prod-*`、镜像名、宿主机目录 `/mnt/software/jiaopei`。改这些要生产数据迁移，不属于改名范围。
  - 环境变量名一律中性、不带前缀：`AUTH_JWT_SECRET`、`DATA_ENCRYPT_KEY`、`DB_*`、`REDIS_*`、`MODULE_*_ENABLED`（yml 里显式占位符，改配置前缀不影响它们，生产 prod.env 不用跟着改）。
  - **切 `cornerstone:` Redis 前缀需要 FLUSHDB**：旧 `jiaopei:*` 会话键不再被读取，留着只会让人误判会话仍有效。该次发布等同全员重新登录，安排停机窗口（前端 storage key 同批切换，表现也是"掉线一次"）。

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

## 前端模块划分与装配（阶段三 起为 pnpm workspace）

与后端同构：底座包 + 业务模块包 + 壳工程组装。`frontend/` 是 pnpm workspace，**根目录本身就是壳工程**（`index.html`/`vite.config.ts`/`tsconfig.*` 都在原位不动），换业务方向 = 换挂载的包，底座包不动。

| 包 | 包名 | 内容 | 依赖方向 |
| --- | --- | --- | --- |
| `packages/core/` | `@cornerstone/core` | 底座：登录/改密、系统管理（用户/角色/菜单/字典/列表配置/系统参数/审计）、`ConfigurableCrudPage` 等脚手架、`AppLayout`、全局样式、扩展点注册表 | 不依赖任何业务包 |
| `packages/module-organization/` | `@cornerstone/module-organization` | 业务：校区/教室/学期/时间段页面 + `educationScope` store（范围筛选归此包） | → core |
| `packages/module-education/` | `@cornerstone/module-education` | 业务：教师/学员/班级/排课/考勤页面 | → core、→ organization |
| `frontend/src/`（壳） | — | `main.ts`、`router/index.ts`、**装配清单 `modules.ts`**、跨模块工作台 | → 上面三块 |

- **源码级引用，不单独构建各包**：包没有 `dist`，靠 vite `resolve.alias` + tsconfig `paths` 把 `@cornerstone/*` 直接指到 `packages/*/src`。新增包要同时改这两处 + `pnpm-workspace.yaml` 覆盖到的 `packages/*` 位置。**`vite.config.ts` 的 `@` 别名必须排在最后**，否则 `@cornerstone/...` 被 `@` 抢先匹配。
- **import 写法**：包内/跨包一律写全包名（`@cornerstone/core/api/http`）；`@/` 只允许出现在壳工程 `frontend/src/**`。这条由 `eslint.config.js` 的 `no-restricted-imports` 分块强制（`boundary/core`、`boundary/module-organization`、`boundary/module-education`），写错 `pnpm lint` 直接报错，等价于后端的 ArchUnit 边界规则。依赖方向同后端：core ↛ `module-*`，organization ↛ education。
- **装配清单 `frontend/src/modules.ts` 两件事必须同时成立**：① `import` 模块入口（模块对底座的扩展点注册是 **import 副作用**，不 import 就是"装了但没挂上"）；② 模块导出的路由清单进 `projectChildRoutes`。只留底座时把这两处的模块条目与 import 一并删掉即可，`pnpm build` 产物不含任何业务 chunk（实测：全挂 54 assets / 13 业务 chunk，只挂 core 33 assets / 0 业务 chunk，且登录与系统管理页全可用）。
- **扩展点**（`packages/core/src/extension/module-extensions.ts`，对应后端的事件/SPI）：`registerListRenderer`/`getListRenderer`/`listRendererChoices`（列表单元格渲染器）、`registerPermissionLabels`（业务权限码中文名与分组）、`registerNavItems`（业务模块登记自己的侧边栏入口）、`registerBranding`（壳工程登记本项目品牌与登录页文案）、`onInstitutionReset`/`notifyInstitutionReset`（机构切换/登出广播，业务 store 自行清理）、`registerPageSlot`/`getPageSlots` + `onPageRefresh`/`notifyPageRefresh`（页面插槽注入 + 宿主刷新广播）。阶段五-0d 删掉了"数据目录 provider"（`registerDirectory`/`loadDirectory` 与 `RenderContext.directories`）：0a 之后没有任何宿主再注入目录，业务下拉的正确通道是「数据源配置」（`sys_data_source`），列表编辑器里直接输入编码即可。
  - **侧边栏导航（阶段五-0d-2）**：`AppLayout` 只内置「系统设置」这一段（底座概念），业务入口一律由模块 `registerNavItems` 登记；分组不单独注册，由导航项自带的 `group`/`groupLabel`/`groupOrder` 推导（组间按 `groupOrder`，组内按登记顺序，同键首个登记方的组名生效——所以跨模块共用的组要在各自条目里写同一个组名）。工作台这类跨模块页面由壳 `modules.ts` 自己登记。新增业务页面要出现在导航里，改的是模块 `index.ts` 而不是 core。
  - **品牌文案（阶段五-0d-2）**：侧边栏标题/窄栏角标与登录页标题、介绍、能力卡全部走壳 `registerBranding` 的部分覆盖，core 只留中性默认值（`管理平台` / `通用管理平台` / 空能力卡）。登录页表单下方那句提示语**不走品牌扩展点**，它是运行期可改的文案，走系统参数 `auth.login.page_tip`（见「系统参数优先入库」）；账号密码预填也只在 `import.meta.env.DEV` 下生效，生产构建不把默认口令打进登录页。
  - **列表渲染器**：`sys_list_config` 的 `renderer` 名字先查 core 内置表（只剩机构/权限/密码态这类底座概念），查不到再回退模块注册表。业务渲染器**自己取名称数据**（读模块自己的 store/接口，或改用 `type: 'map'`、字典列），core 不再透传业务目录；模块未装配时该列退回普通渲染（显示原始 id），列表配置编辑器也不会给出这个候选（`rendererChoices()` 只合并已登记项）。新增业务渲染器要同步两处：模块入口 `registerListRenderer` 登记、`sys_list_config` 种子里的 `renderer` 名保持一致。
  - **权限码标签**：core 的 `constants/permissionLabels.ts` 只列底座码（`system:*` / `tenant:*`，与 core 各 Controller 的 `@RequiresPermission` 对齐），业务模块在自己入口 `registerPermissionLabels` 贡献本域码（如 organization 的 `organization:read|write` → 组织档案）。权威中文名是 `sys_menu` 按钮节点（`/system/permission-options` 动态返回），静态表只作接口不可用时的兜底与角色弹窗的分组名，因此**读取处一律现算**，不要在模块加载时取快照——注册晚于快照就会漏。
  - **数据源候选**：`OPTIONS_LOADER_OPTIONS` 只列 `institutions`/`roles`/`status`；业务编码（如 `campuses`）在数据源配置页维护，编辑器下拉用 `allow-create` 直接输入。新增编码不改 core 代码，运行期命中不了宿主注入就自动走 `sys_data_source` 定义。
  - 页面插槽用于"业务页签注入到别的模块页面"（参照校区管理页里教务侧的课程/课程分类/班型三张卡片：宿主是 organization 的 `CampusManagementView`，内容来自 education 包的 `views/campus/EducationMasterDataCards.vue`）。宿主 `refreshAll` 末尾 `notifyPageRefresh(pageKey)`，插槽子组件订阅并自己拉数；**`onPageRefresh` 返回注销函数，必须在 `onUnmounted` 里调用**，否则订阅者随打开次数累积。
  - `registerPageSlot` 幂等、按 `order` 排序；插槽子组件依赖全局 `core/src/styles/index.css` 里的 Element Plus 布局类（不在 scoped 样式内），组件不要再自带 reset。
- **接口归属**：机构（=租户）的列表接口在底座 `core/src/api/tenant.ts`（阶段二A 后端把它从 organization 迁回 tenant，前端同步；请求路径 `/organizations/institutions` 未变）。`module-organization/src/api/organization.ts` 只留校区/教室/学期/时间段。
- **新增页面落包判断**：换一个行业后还常用 → `packages/core`；否则按域放对应 `module-*`；跨模块聚合页（工作台等）放壳 `frontend/src/views/`。业务模块专属依赖放各自 `package.json`，不要塞进 core。

## 前端 / 小程序约定

- API 层 `frontend/src/api/`：`http.ts`（axios 实例 + 大 ID 兜底）、`request.ts`（`unwrap` 解包 `ApiResponse.data`，非 success 抛 `ApiError`），按模块拆 `auth/organization/education/system.ts`。
- 状态：Pinia，登录态持久化在 `localStorage` key `cornerstone.auth.session`（`utils/auth-session.ts`），`http.ts` 从该处取 `Authorization`；未登录时回退到 `import.meta.env.VITE_API_AUTHORIZATION`（仅本地联调用的固定头，生产构建不要注入）。
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
grep -rlE "ENGINE=InnoDB" backend/app/src/main/resources/db/migration | xargs grep -L "COMMENT"
# 列注释非空验证
# SHOW CREATE TABLE / INFORMATION_SCHEMA.COLUMNS.COLUMN_COMMENT
```

完整示例见 `V31__add_miniapp_rel_table_notes.sql`。

## 文档索引

动接口或菜单前先查 `docs/`（分类索引见 `docs/README.md`）：

- `docs/后端交付说明.md` — 接口/权限矩阵（权限串照此命名）
- `docs/前端交付说明.md` — 页面/路由与接口映射
- `docs/核心实体清单.md`（按模块分组的现行表清单）、`docs/后端主数据与成班规则落点说明.md`
- `docs/miniapp.md`、`docs/list-config-guide.md`、`docs/config-contract.md`
- `docs/操作留痕与敏感数据说明.md` — 审计与脱敏链路
- `docs/生产部署说明.md`、`docker/生产环境MySQL重建与安全加固方案.md`、`docker/prod.env.example` — 生产部署（`docker-compose.prod.yml` + `deploy.sh`）
- `docs/archive/` — 历史过程方案归档（首批建模、菜单重组、班级变动提示范围等），勿作为现行口径引用
