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
4. **异常消息面向操作员，不许拼雪花主键**：指认实体一律用业务编号（班级编码/学号/教师编码/校区编码…）或名称，操作员在列表里看得到编号、看不懂 19 位主键；实体拿不到时（「X 不存在」类，手里只有入参 ID）才允许留主键。「X 已存在」类冲突提示拼的是**冲突的那个值**（编码/名称/手机号/单号，取请求或已查出的 existing 实体），不是机构、校区这类范围 ID——范围 ID 对操作员没有信息量。实体没有编号字段的（如排课计划）用它挂的班级编码等业务标识指认。

## 本地环境与常用命令

### 编译环境：必须 JDK 21

本机系统默认 `java`/`mvn` 很可能是 JDK 1.8（`mvn -v` 可见），编译前先导出环境：

```bash
java -version                     # 不是 21 就导出环境变量
export JAVA_HOME="C:/Users/57299/.posh_sdk/java/21.0.9-oracle"
export PATH="$JAVA_HOME/bin:$PATH"
```

> 走 `scripts/ci-check.sh` 或 `scripts/restart-backend.sh` 时**不需要**手动导出：两个脚本共用 `scripts/lib/jdk21.sh` 自动定位 JDK 21。上面这组命令只在直接敲 `mvn` 时需要。

### 后端模块划分（阶段二B 起为 Maven 多模块）

`backend/` 是聚合工程（`jiaopei-platform-backend-parent`，`packaging=pom`，groupId `com.cornerstone`），四个子模块：

| 模块 | artifactId | Java 包根（阶段四起） | 内容 | 依赖方向 |
| --- | --- | --- | --- | --- |
| `cornerstone-core/` | `cornerstone-core` | `com.cornerstone.core.*` | 底座：`auth` / `system` / `common` / `config` / `tenant` / `audit` / `file`（通用文件上传/元数据/下载/孤儿清理）+ 全部第三方 starter | 不依赖任何业务模块 |
| `module-organization/` | `module-organization` | `com.cornerstone.module.organization.*` | 业务：校区/教室/学期/时间段 | → core |
| `module-education/` | `module-education` | `com.cornerstone.module.education.*` | 业务：教师/学员/班级/排课/考勤/小程序 | → core、→ organization |
| `app/` | `jiaopei-platform-backend` | `com.cornerstone`（仅主类 `CornerstoneApplication`） | 组装启动：主类、`application.yml`、Flyway 迁移、全量测试、`spring-boot-maven-plugin` | → 上面三块 |

- **包根即模块身份**：底座类只能在 `com.cornerstone.core.*`，业务模块在 `com.cornerstone.module.<name>.*`；`ArchitectureRulesTest` 的包白名单按此约束，新增顶层包要同步。artifactId、jar 名、数据库库名、容器与镜像名**保持 jiaopei 不变**（那是本项目的部署标识，不属于底座身份）。表前缀属另一码事：底座表 `sys_`、业务表 `jp_`，阶段五-0b 已把底座四张表从 `jp_` 改到 `sys_`。
- **只有 `app/` 产出可执行 jar**，artifactId 保持 `jiaopei-platform-backend`，故 jar 文件名不变，落在 `backend/app/target/`。
- `spring-boot:run` 不能带 `-am`，也不能在根目录直接跑：会对外壳模块执行同一 goal，core 无主类直接 `Unable to find a suitable main class` 失败。改代码后先 `mvn -q -DskipTests install` 再 `-pl app spring-boot:run`，或直接用 `scripts/restart-backend.sh`。两个脚本共用 `scripts/lib/jdk21.sh` 自动定位 JDK 21（依次尝试 `JAVA_HOME`、`JDK21_HOME`、常见安装路径），找不到直接报错退出；直接敲 `mvn` 时才需要手动 `export JAVA_HOME=` 指向 JDK 21。
- 业务专属的第三方依赖放各自模块的 pom：`wx-java-miniapp-spring-boot-starter` 已随微信登录下沉到 `module-education`（版本仍由 parent 的 `dependencyManagement` 管），core 不再声明。core 的 pom 里**不允许**出现 `module-*` 依赖，也不允许出现只有业务模块用得上的第三方 SDK。判据是"这段依赖服务的是谁的功能"，不是"有没有业务模块用得上"：`easyexcel`（连带它传递进来的 POI）自「用户账号批量导入」起服务的是底座自己的功能（模板生成与 xlsx 解析全在 `core/auth` 下，消费方装了底座就带这套），因此进 core 合规；只服务某个行业表意的 SDK（微信、支付、地图一类）仍留在业务模块。
- 新增类放在自己所属模块，不要在 `app/src/main/java` 下写业务代码（该模块只放主类与资源）。

### 模块装配与开关（阶段二C 起为真开关）

装配分两条路，**这是有意为之，别改成一致**：

- **底座**：`app` 启动类 `@SpringBootApplication(scanBasePackages = {auth, system, tenant, audit, common, config, file})` 显式扫底座七个顶层包。底座是必选项、且它提供的 `DataSource`/`SqlSessionFactory` 必须早于 Spring Boot 与 MyBatis-Plus 的自动配置出现（否则后者的 `@ConditionalOnMissingBean` 判定失效，多出重复数据源），所以走"用户配置"阶段而不是自动配置。
- **业务模块**：各模块 jar 自带 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，指向自己的 `XxxModuleAutoConfiguration`（`@AutoConfiguration` + `@ConditionalOnProperty` + `@ComponentScan` + `@MapperScan`）。**加/减模块只改 `app/pom.xml` 依赖**，不用动启动类。

开关（`application.yml`，可用同名环境变量覆盖；`cornerstone.modules.enabled` 是「模块码 → 开关」的映射，底座不写死任何模块名，新项目加自己那一条即可）：

```yaml
cornerstone:
  modules:
    enabled:
      organization: ${MODULE_ORGANIZATION_ENABLED:true}
      education: ${MODULE_EDUCATION_ENABLED:true}
```

- `false` 时该模块整包不注册：接口 404、`/v3/api-docs` 与「功能权限总览」都不再列出，`/api/system/bootstrap` 的模块清单里也没有它。
- 依赖方向 `education → organization`，**关 organization 必须同时关 education**（只关前者会让后端的模块清单出现"装了但接口没挂上"的半残状态）。
- 迁移目录是可配的两组列表：业务库 `cornerstone.flyway.locations`（教培只配 `classpath:db/migration`）、日志库 `cornerstone.flyway.audit-locations`（教培只配 `classpath:db/audit-migration`）。模块要自带脚本时在此追加该 jar 内目录，不改代码。**这两项必须经 `CornerstoneProperties` 绑定，不能用 `@Value`**：YAML 列表在 Environment 里是 `locations[0]`/`[1]`，`@Value` 取不到列表只会静默回落到默认值，症状是"配了没生效、Flyway 报 `No migrations found`、启动不报错"。教培自己的配置值恰好等于默认值，所以这个坑只有下游配第二条目录时才暴露（守护：`CornerstoneBaselineGuardTest#flywayLocationsMustBindTheYamlListForm`）。`CornerstoneProperties` 由底座自己的 `CornerstonePropertiesConfiguration`（`@EnableConfigurationProperties`）登记，**下游不需要、也不应该**把 `com.cornerstone.core.config.properties` 列进它的 `@ConfigurationPropertiesScan`——那条要求漏写的症状同样是"配置全取默认值且不报错"（守护：`ModuleSwitchGuardTest#coreMustSelfRegisterItsConfigurationProperties`）。
- **改底座种下的那几行数据要两边各写一份迁移**。core jar 自带的 `cornerstone/migration/V100~V199` 是"下游从零建库"才走的路径，**教培自己的 `locations` 里没有这条目录**，本项目的库是从 `app/` 的 V1..V55 长起来的，所以 V1xx 在教培一条都不执行（反过来，新下游只跑 V1xx，拿不到 `app/` 的脚本）。落到实操：给 `sys_list_config`/`sys_menu`/`sys_system_config` 这类**底座持有的行**加字段或改值时，core 侧新增一个 `V10<n>`、`app/` 侧同时新增一个 `V5<n>`，两份 UPDATE 内容一字不差，并在各自头部注释写明另一个文件（参照 `V104__add_user_import_toolbar_action.sql` 与 `V55__add_user_import_toolbar_action.sql`）。只写 core 那份的症状是"教培页面上按钮没出现、也不报错"；只写 `app/` 那份则新项目永远缺这个按钮。V1xx 基线脚本（`V100`/`V101`）已发过下游，**同样不许原地改**，checksum 一变下游直接拒绝启动。

必须知道的三个连带约束：

1. **不要把 `scanBasePackages` 放宽回 `com.cornerstone`** 或加进业务包——那等于无条件扫进所有业务 `@Component`，开关形同虚设。`ModuleSwitchGuardTest` 会拦住这类改动。
2. **`@WebMvcTest` 切片会关闭自动配置**，业务模块的控制器在切片里不会被扫描到。写业务控制器的切片测试时，要把控制器同时列进 `@Import({...})`（现有三处参照：`SecondThirdMainlineApiCompatibilityWebTest`、`EducationFourthMainlineApiCompatibilityWebTest`、`TimeSlotTemplateControllerWebTest`）；否则症状就是断言 `200` 实际 `404`。
3. **底座新增顶层包**时要同步三处：启动类 `scanBasePackages`、core `@MapperScan`（若带 mapper）、`ArchitectureRulesTest` 的包白名单。漏扫描的类不会报错，只是静默不进容器。
4. **跨模块同名组件必须显式命名 bean**：`module-education` 与 `module-organization` 各有一份 `DictValueValidator`（通用字典校验器，不下沉进底座），默认 bean 名同为 `dictValueValidator`，两模块一起装配时容器直接 `ConflictingBeanDefinitionException` 起不来。这类冲突**单测发现不了**（全仓库无 `@SpringBootTest`，Service 测试是 Mockito 手工构造），只有真启动才炸，故由 `ArchitectureRulesTest#springComponentBeanNamesMustBeUnique` 静态拦：同名就写 `@Component("educationXxx")`，或干脆换类名。

模块自述：底座定义 `ModuleDescriptor`（`system.module` 包），各模块注册自己的实现，`PlatformBootstrapController` 与 `OpenApiConfig` 只消费注册表，新增模块不用再改这两处。

### 后端常用命令（工作目录 `backend/`）

```bash
mvn -q test                                                # 全量测试（改动必跑，不只是 mvn compile）
mvn -q -Dtest=AuthControllerWebTest test                   # 单个测试类
mvn -q -Dtest=AuthControllerWebTest#方法名 test             # 单个测试方法
mvn -q -DskipTests clean package                           # 打包全部模块，产物 app/target/jiaopei-platform-backend-<版本>.jar（必须带 clean，见「改代码后必须重启后端」）
mvn -q -pl module-education -am -DskipTests package         # 只构建某业务模块及其依赖（-am 自动带上 core）
mvn -q -pl app spring-boot:run                             # 本地运行（需先起 MySQL/Redis，且 core/业务模块已 install 到本地仓库）
mvn -q dependency:go-offline                               # 预拉依赖（Dockerfile 构建也走这一步）
```

本地依赖：`docker compose -f docker/docker-compose.yml up -d`（compose 声明 MySQL 8.4 + Redis 7.4，库 `jiaopei_platform`）。后端默认连 `127.0.0.1:3306` 与 `127.0.0.1:6379`。Flyway 已启用，迁移位于 `classpath:db/migration`，`baseline-on-migrate=true`。

> **本机实况与 compose 声明不一致**：这台机器没走 Docker，MySQL/Redis 是原生服务，`SELECT VERSION()` 是 **8.0.46**（客户端 8.4.9）。上面那行描述的是 compose 文件，不是本机在跑的东西；判断版本一律以 `SELECT VERSION()` 为准（生产则是 8.4，见「迁移脚本生产兼容检查」）。

> 已知不一致：`application.yml` 默认连 `root/1234`，而 `docker/docker-compose.yml` 初始化为 `root/root`。以实际能连上的为准，可用 `DB_*` / `REDIS_*` 环境变量覆盖，不要为迁就文档去改本机密码。

### 端口口径：本地 9080 / 容器 8080

两套端口是**有意为之**，不要试图统一：

- **本地开发**：`application.yml` 的 `server.port: 9080`，`frontend/vite.config.ts` 代理与 `miniapp/src/config.ts` 默认地址都指向 9080。
- **生产容器**：`backend/Dockerfile` 的 `ENV SERVER_PORT=8080` 覆盖 yml（Spring Boot 环境变量优先级高于配置文件）。容器内固定 8080，nginx 反代 `backend:8080`、健康检查、compose 端口映射（`18090:8080`）全部以此为准。
- 改端口只改 `application.yml` 一处；`restart-backend.sh` 自动读取。**不要删 Dockerfile 的 `SERVER_PORT`**——它是容器内端口的唯一权威。

### 改代码后必须重启后端

旧进程不会加载新代码，不重启就是“改了没效果”的假象。**直接用仓库脚本**（Git Bash）：

> 打包必须带 `clean`（脚本已改成 `mvn -DskipTests clean package`）。`maven-jar-plugin` 默认 `forceCreation=false`：`app` 模块自身没有源码改动时**跳过重打 fat jar**，`app/target` 里的 jar 仍是旧内容，`BOOT-INF/lib/module-education-*.jar` 也还是上一次的——症状同样是"改了代码、重启也做了、行为却没变，而且一句错都不报"。多模块改完业务代码手工打包时同理。

```bash
scripts/restart-backend.sh              # 停旧进程 -> mvn clean package -> 后台启动 -> 健康检查轮询
scripts/restart-backend.sh --skip-build # 已有 jar 时只重启
```

端口默认读 `backend/app/src/main/resources/application.yml` 的 `server.port`（本机改成 9080 也无需改脚本），可用 `PORT=xxxx` 覆盖。脚本会自动定位 JDK 21，不需要先手动 export。

手工流程（脚本不可用时）：

```bash
netstat -ano | grep ":9080" | grep LISTENING                                   # 记下监听端口的旧 PID（端口见 application.yml 的 server.port）
powershell -NoProfile -Command "Stop-Process -Id <PID> -Force"                 # 停旧进程（可先用 Get-CimInstance 查 CommandLine 确认是自己的 java 进程再杀）
cd backend && "C:/Program Files/Java/jdk-21.0.12/bin/java" -jar app/target/jiaopei-platform-backend-*.jar &   # jar 名带版本，用通配或先 ls 确认
curl -s -o /dev/null -w "%{http_code}" http://localhost:9080/api/health        # 200 才算起来，别猜日志
```

### 底座发版（上传私服）

- **版本策略**：开发期用「下一版 + `-SNAPSHOT`」（当前 `0.1.1-SNAPSHOT`），阶段五验收通过才切正式号并打 tag。五个 pom 的 `<version>` 一起改；SNAPSHOT 可反复发同一号，Maven 按时间戳取最新。
- **发布范围**：只有 **parent pom 与 `cornerstone-core`** 上私服。下游靠 parent 的 `dependencyManagement` 解析传递依赖版本，parent 不发下游就构建不过。`module-organization`/`module-education`/`app` 各自 pom 里 `maven.deploy.skip=true` —— 教培业务模块和可执行 jar 不属底座身份。
- **目标库与凭据**：上传地址在 `backend/pom.xml` 的 `<distributionManagement>`（server id `cornerstone-releases`/`cornerstone-snapshots`），账号密码在 Maven 安装级 `conf/settings.xml` 的 `<servers>`，**不要写进 pom、也不要提交进仓库**。下载侧匿名只读，不需要凭据。
- **命令**：`mvn deploy`（**别加 `-DskipTests`**，私服上的底座产物必须过全量测试）。
- **前端包**：先改 `packages/core/package.json` 的 `version` 再 `cd frontend/packages/core && npm publish --tag snapshot`。**npm 没有 SNAPSHOT 语义**（同号不能覆盖），开发期按 `0.1.1-SNAPSHOT.<n>` 递增迭代号（当前 `.2`），消费方装到的必须写死精确版本。`publishConfig.registry` 已钉死私服地址，防止手滑发到公网 npm；npm 对预发布版本强制要求 `--tag`。core 的运行时依赖一律写 **`peerDependencies`、不写 `dependencies`**：写成 dependencies 时下游 pnpm 会在 `node_modules/.pnpm` 长出第二份 vue/element-plus，两个 vue 实例直接打散 pinia 与依赖注入（宿主自己声明这些包，见 achievement 前端）。当前仍是**源码形态**（包里只含 `src`，`exports` 把 `@cornerstone/core/*` 映射到 `./src/*`，无 `dist`/`.d.ts`）——阶段五-3 已按这个形态被下游接通，真实构建产物属优化项、不是接入前置条件。

### CI 与本地门禁

- `.github/workflows/ci.yml`：**只在 PR 触发**，跑后端 `mvn test` + 前端 `pnpm lint`/`pnpm build` + 小程序 `pnpm type-check`。仓库已迁到 GitHub，Actions 会真正执行（Gitee 上的旧仓不跑）。
- `.github/workflows/build-release.yml`：**只在 master 推送触发**，先跑后端测试与前端 lint，再构建 `app.jar` + `dist` 并投递到服务器 `releases/`。与 ci.yml 分开触发是为了不把免费私有仓 2000 分钟/月额度重复消耗；因为习惯直接 push master，测试内建在这里而不是只靠 PR。
- **生产构建不在服务器上做**：服务器是突发性能型（CPU 积分制），部署时 `mvn`/`pnpm`/`docker build` 会耗尽积分并触发限速/关机。服务器只用 `./docker/deploy.sh --from-ci <版本>` 挂载预构建产物重建容器；详见 `docs/生产部署说明.md`「构建与启动」。
- Gitee 环境下用等价本地脚本：`bash scripts/ci-check.sh`（后端测试 -> eslint -> 类型检查+构建 -> 小程序类型检查），提交前应跑通。只跑某一步可传参：`backend` / `frontend-lint` / `frontend-build` / `miniapp`。脚本会自动找 JDK 21（依次尝试 `JAVA_HOME`、`JDK21_HOME`、常见安装路径），找不到直接报错退出，不会拿 JDK 8 硬跑。
- 手工构建部署产物：`bash scripts/build-artifacts.sh`（生成 `docker/artifacts/{app.jar,dist}`，`--tar` 可打包），用于 GitHub Actions 之外的兜底；不要在突发性能型服务器上跑。
- 后端有 **ArchUnit 架构守护测试**（`backend/app/src/test/.../architecture/ArchitectureRulesTest.java`）：接口必须有 `@Operation`、写接口必须有 `@RequiresPermission`（**免检口径是"类级 `@RequestMapping` 以 `/api/auth` 开头"**，按路径而不是按包判定，所以业务模块挂 `/api/auth/**` 的公开登录接口同样豁免；改这个前缀要同步安全配置的 `public-paths`）、Service 不得依赖 Controller、`@TableName` 实体必须在 `..domain..` 包、底座包（auth/system/common/config/tenant/audit）不得依赖 organization/education 业务包、生产类必须落在已声明的顶层包（防"新增包忘了加进扫描清单"）。同目录的 `ModuleSwitchGuardTest` 守护模块开关与配置装配前提，`CornerstoneBaselineGuardTest` 守护 core 自带基线，`ExternalIdVocabularyGuardTest` 守护底座不带行业词汇。测试只在 `app/` 模块运行，能看到全部模块的类。违反即 `mvn test` 失败，取代人工 grep 自查。

### 前端（工作目录 `frontend/`）

```bash
pnpm install
pnpm dev          # vite dev server，127.0.0.1:5173，/api 代理到 127.0.0.1:9080
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

后端地址配置在 `miniapp/src/config.ts`（默认 `http://127.0.0.1:9080/api`，可被环境变量覆盖）。

- **展示文案走系统参数，与 PC 同一份**：`src/composables/use-branding.ts` 拉免登录的 `GET /api/system/branding`（模块级单例、不落本机缓存，口径同 core 那份），登录页与首页问候语都读它。取不到就显示 `未配置(brand.name)`，**不许回落成构建期写死的名字**。改 PC 登录页视觉（`core/styles/index.css` 的 `.login-shell` 夜晚档）时，同步改 `miniapp/src/pages/login/index.vue` 里那组写死的同值色——小程序不支持 `backdrop-filter`，卡片通透感靠加深实底换可读性，其余配色一一对应。
- **`auth.login.page_tip` 不给小程序读**：那条参数写的是管理端账号密码登录的提示（默认账号/初始口令一类），家长端看到会误导；小程序登录页的说明是固定的「首次使用将自动注册」。**不要为了"两处一致"把它接过来。**
- `pages.json` 的 `navigationBarTitleText` 与 `manifest.json` 的 `name` 是构建期清单，运行期改不了（见「品牌文案」），要跟着变得另开注入通道。

## 鉴权：JWT + Redis 会话（最容易踩坑处）

- 登录 `POST /api/auth/login` 由 `JwtTokenService` 签发 HS256 JWT（自包含 userId/角色/权限声明，`exp` 与系统配置 `auth.token.expire_hours` 一致，默认 12 小时），同时写 Redis 会话；登出/踢人靠删会话实现。
- **Redis key 布局**（`TokenCacheService`，非数据库表）：`cornerstone:token:{token}` → 序列化的 `AuthenticatedUser`（TTL 由 `auth.token.expire_hours` 决定）；`cornerstone:user:tokens:{userId}` → 该用户 token 集合，供强制下线。业务侧统一调 `AuthApplicationService.invalidateUserSessions(userId)`（内部委托 `TokenCacheService.invalidateUserTokens`），改密/禁用账号/微信解绑时触发。
- `StubAuthenticationFilter` 读 `Authorization: Bearer <token>`，先验签名与过期，再查 Redis 会话。
- **强制改密以库为准，不能只看 Redis 快照**：`authenticateToken` 本来就每次查 `sys_user_account`，返回时用库里的 `force_change_password` 覆盖会话快照里的值。自助改密只清库不清会话，若沿用登录时的快照，同一个 token 改完密后仍会被当成"待改密"拦成 403，表现就是"必须退出重登才能进系统"。过滤器拦下时**直接写 403 JSON**（`ApiResponse.failure(FORBIDDEN, "需要先修改密码")`），不要改回 `sendError`：`sendError` 会触发 `/error` 的 ERROR 派发，那次派发不走本过滤器且 `/error` 不在免登录清单里，最终会被 `SecurityConfig` 再拦成 401，把"需要改密"伪装成"未登录或登录已失效"。
- **业务模块改账号显示名只能走 `AuthApplicationService.updateDisplayName(userId, displayName)`**：小程序绑定档案后要回写 `sys_user_account.display_name`（学生取自己档案姓名，家长是「孩子A、孩子B家长」），不要直连 `UserAccountMapper` 写底座表的列。列宽上限是该接口上的常量 `MAX_DISPLAY_NAME_LENGTH`（128），拼接方在业务侧按它截断（放不下就补「等」、单孩姓名超长就截名字保住后缀），接口只拒绝不静默裁。显示名不在 JWT 也不在 Redis 会话里，`/api/auth/me` 每次现读库，所以改名**不需要**失效会话——但已打开的页面拿的是本机缓存，小程序「我的」页靠 `onShow` 重拉。
- **过滤器认证失败时“吞异常继续放行”是有意设计**：该过滤器对所有请求生效（含公开路径），直接 `sendError` 会误拦“带过期 token 访问公开接口”。真正鉴权交给下游 `SecurityConfig.authorizeHttpRequests` + `cornerstone.security.public-paths`，不要在 catch 里加 `sendError`/`return`。
- JWT 密钥 `cornerstone.auth.jwt.secret`，生产必须用 `AUTH_JWT_SECRET` 环境变量覆盖（≥32 字节）。
- 权限判定：写/敏感接口用 `@RequiresPermission("xxx:write|manage")` + `PermissionGuardAspect`；取当前用户用 `AuthContext.requireUser()`。新增受保护接口路由必须加 `@RequiresPermission`，权限串与 `docs/后端交付说明.md` 命名一致。**业务模块的读取接口不再挂码（V113 起）**：`education:*`/`organization:*`/`announcement:*` 的 GET 登录即可，机构/校区范围由服务层 `InstitutionScopeGuard`/`CampusScopeGuard` 兜住；页面可见性只由 `sys_role_menu` 决定，页面节点不挂读取码。**权限码前缀按归属分**：底座机构（=租户）接口用 `tenant:*`，业务模块用自己域名的写码；底座接口挂业务前缀的码等于逼第二个项目授予一个它没有的模块（阶段五-0c 已把机构接口从 `organization:*` 改到 `tenant:*`，角色种子随之裁成底座码 + 业务码各自一段，见 V41/V42）。`tenant:*`、`system:*` 仍按码硬拦，见「权限码与菜单节点的分配规则」。
- **登录失败排障**：`GlobalExceptionHandler` 把所有 `AuthenticationException` 统一渲染成同一句 `UNAUTHORIZED "Authentication is required"`，密码错和 token 失效响应体相同。排查 401 先用已知正确密码单独打 `/api/auth/login` 排除密码问题，再看过滤器/Redis。本地 admin 口令见 `V7__create_auth_user_role_tables.sql`（`{noop}` 前缀 = 明文比对）。
- **Redis 序列化**：`RedisConfig` 的 ObjectMapper **必须** `activateDefaultTyping(..., EVERYTHING)`（`NON_FINAL` 不给 record 里的 `Set<String>` 写 `@class`，反序列化会报 missing type id）；该 mapper 必须在方法内 new，**不要**做成全局 `@Bean ObjectMapper`，否则顶掉 Spring MVC 默认 mapper 导致 `OffsetDateTime` 序列化报错。序列化格式变更后旧数据无法反序列化，需 `redis-cli FLUSHDB`（所有人重新登录）。
- **系统参数优先入库**：可运行时调整的配置加到 `sys_system_config`（V14 引入），通过 `SystemConfigService.getConfigValue/getBooleanConfig/getIntConfig` 读取，别再写死进 `application.yml`；`is_system=1` 的配置禁止删除只能改值。当前在用的 key：
  - `auth.password.force_change_enabled` — 强制改密总开关（`StubAuthenticationFilter` 读取，关闭后忽略用户级 `forceChangePassword`）。前端判断是否提示改密只能看 `/auth/me` 的 `forceChangePassword`（生效值），不要用 `passwordResetRequired`（库里"初始密码未轮换"的原始事实，不受总开关控制）——否则关掉总开关后横幅/登录提示仍会挂着。
  - 已删除（勿加回）：`auth.password.force_change_on_first_login`（“首次登录强制改密”）。该键从 V14/V101 种下后 Java 侧零引用，系统参数页却能看见能改、改完毫无效果，属误导性开关；教培 `V115` / 底座 `V118` 已删除。是否强制改密只由上面的 `force_change_enabled` 决定，想支持“首次登录不强制改密”必须改 `SystemUserServiceImpl#createUser/resetPassword`，不能再种一条没人读的配置。
  - `auth.password.strength_level` / `min_length` / `max_length` — 密码强度策略（`PasswordValidator` 读取）
  - `auth.token.expire_hours` — JWT/Redis 会话 TTL
  - `auth.login.page_tip` — 登录页表单下方提示语（`AuthController` 的 `GET /api/auth/login-page` 读取，core 无值即空串、前端不渲染；**种子放各项目的 `app/` 迁移**，教培侧见 V43，别再写死进 `LoginView`）
  - `brand.name` / `brand.tagline` / `brand.login_copy` / `brand.login_form_title` — 系统名称与登录页文案（`BrandingController` 的 `GET /api/system/branding` 一次性下发，**免登录可读**，登录页要用；`mark` 角标不是参数，后端按 `brand.name` 首字推导。种子见 V68，底座不写任何项目名词、`CornerstoneBaselineGuardTest` 拦着）。**与 `page_tip` 口径不同：读不到即空串、前端显示 `未配置(参数键名)` 的维护提示，不回落任何构建期默认名**——回落等于"改了名字线上还挂着旧名"，比留提示更糟。
  - `auth.register.default_institution_id` — 小程序自助注册账号的默认归属机构（`MiniappServiceImpl`/`WechatAuthServiceImpl` 读取；种子见 V47）
  - `miniapp.register.page` — 邀请码小程序码扫码直达的页面路径（`MiniappQrcodeServiceImpl` 生成 wxacode 时读取，默认 `pages/register/index`；种子见 V47）
  - `file.upload.max_size_mb` — 单文件上限（MB，`FileValidator` 读取；`spring.servlet.multipart` 的 50MB 是硬顶，这里再收窄）
  - `file.upload.allowed_extensions` — 允许的扩展名，逗号分隔，空串表示不限制（`FileValidator` 读取）
  - `file.orphan.retention_hours` — 未绑定业务文件（`biz_id IS NULL`）的保留时长（小时，`FileServiceImpl` 读取；`<=0` 停止自动清理）
  - `user.avatar.max_size_mb` — 个人头像单文件上限（MB，`UserAvatarServiceImpl` 读取；头像只接受 JPG/PNG，且服务端会真解码校验）

  **新增 `SystemConfigService.getConfigValue/getIntConfig/getBooleanConfig` 读取的 key 时，必须同步在 `app/` 迁移里种入 `sys_system_config` 并登记进上面的清单**——只写代码默认值回落等于隐藏配置：系统参数页看不见也改不了（V47 就是补的这两条历史欠账）。

## 权限码与菜单节点的分配规则（必填）

一句话判据：**只勾这个页面，这一页到底能不能用？** 业务读取已改为登录即可（V113），所以勾页面 = 入口可见 + 能读；写操作随页面一起勾（授权弹窗 `togglePage` 会把本页按钮节点一并授出），单独取消某个按钮才是只读岗。

1. **业务读取码已清零（V113 起）**：`education:read`/`organization:read`/`announcement:read` 不再存在，业务模块 GET 接口登录即可，机构/校区范围由服务层 `InstitutionScopeGuard`/`CampusScopeGuard` 兜住；页面节点 `permission_code` 留空，可见性只由 `sys_role_menu` 决定。**不要再新增业务读取码**，否则又回到"为了给码必须勾基础页"的耦合。系统管理页仍按原规则把码挂在页面节点上（见 V62/底座 V105），读写共用单码。**机构列表例外已解除**：底座 `GET /api/organizations/institutions` 改为登录即可（不再挂 `tenant:read`），平台管理员返回全量、其他账号只返回本机构——`AppLayout` 启动即调它填机构芯片、各业务页的 `institutionId` 也取自该 store，所以业务角色不再需要勾「机构资料」页。`tenant:read` 仍保留在机构资料页节点 8 与 `GET /api/system/institution/current` 上，`tenant:write` 仍拦 `PUT /api/system/institution/current` 与建机构 POST。
2. **写/操作码挂 `button` 节点**（`visible=0`，不进导航）：新增/编辑/删除/导入/维护一类，勾了才给。按钮节点仍受后端 `@RequiresPermission` 硬拦，前端隐藏按钮只是体验。授权弹窗勾页面时会把本页全部按钮节点一起授出，客户不用逐项勾。
3. **`catalog`（目录）节点永不挂码**：它是分组不是能力。
4. **读写共用单码的页面（`system:user:manage` 一族，GET 也拦同一个码）把码挂在页面节点上，不再配同名"维护"按钮**。页面与按钮挂同一个码 = 勾哪个都等价，纯重复且会让人以为勾页面不够（历史上 103「菜单维护」与 109「功能权限查看」就是同一个 `system:menu:manage` 挂两处）。代价是这类页面没有"只看不动"的只读岗——要拆 `read`/`manage` 得连后端注解、角色 CSV、下游一起改，属于另一批。
5. **接口只需登录的页面不挂码**（如功能权限总览，它的两个 GET 无 `@RequiresPermission`），节点只做导航。给它挂码等于"勾了这个页面就白送一个管理能力"，那是越权不是授权。业务读取接口按 V113 统一走这条。
6. **同一个码在同一页面只挂一处**；写码允许跨页面共用（如 `education:profile:write` 同时服务教师/学员页），这是有意设计，不要把一页拆成多条写码。
7. 权限码的**中文名权威源是前端登记表**（见「前端模块划分与装配」的权限码标签条目），菜单节点名只作兜底与"挂在哪"的说明。V113 起业务读码不再登记。
8. **撤销授权有两条路**：角色 `permission_codes` CSV ∪ 已勾节点带的码（`UserAccessTokenService`），任一授予即生效，两边都要撤。V113 已把三个业务读码从 CSV 清掉；写码两条路仍都存在。
9. 改 `sys_menu` 里**底座种下的行**（id 1~14、18、101~111、115 这批）必须两边各写一份迁移且内容一字不差：底座 `cornerstone/migration/V10n`、教培 `app/.../db/migration/V6n`（见「模块装配与开关」的双轨规则）。业务页面/按钮（15~25、112~123）与预设角色（110/111）只在教培侧，改它只写 `app/` 那份。
10. 权限码变更后要**重登才生效**（快照写进 JWT）；管理接口侧的会话失效链路见「安全与数据一致性约定」第 2 条。
11. **写码按"页面域"拆，不按"动作"拆**（V74 把 `education:write` 拆成六码就是这条）：`education:schedule:write`（排课计划/规则/校历/课次增删改）、`education:attendance:write`（保存课次点名）、`education:attendance:manage`（代点任意课次）、`education:class:write`（班级/入班即报名/邀约码）、`education:profile:write`（教师与学员档案、导入、绑号）、`education:catalog:write`（课程/课程分类/班型）。判据是"机构里有没有这么一个人：只做这类事、不碰别的"——教师能点名但不能改排课，教务能排课但不能改教师档案。**不要**为"新增/编辑/删除"各配一码，那只会让操作员在一页上勾三个。
12. **数据范围不靠权限码数量解决，用双码 + 服务端兜底**：一个码回答"能不能做"，"能对哪些行做"必须由业务校验兜住。点名是唯一的样例——只有 `education:attendance:write` 的账号在 `AttendanceServiceImpl#assertLessonMarkableByCurrentUser` 里被限定为**本人授课的课次**（按 `jp_schedule_teacher_rel` + `jp_teacher.user_id` 反查），持 `education:attendance:manage` 才跳过这层。新增同类"本人范围"写操作照此办理：粗码进 `manage`、细码走数据范围校验，不要在权限码上带 `:mine`、`:own` 一类后缀。
13. **文件接口的码**：上传 `system:file:upload`（POST，必须授给全部在职角色，否则业务表单一上传就 403）；文件管理页/删除/清理孤儿 `system:file:manage`。`GET /api/system/files/{fileId}` 与 `/content` 只要求登录 + 同机构，**不挂码**——所有业务页面的图片都走这两条，挂码等于每个角色都要单独授一次、漏授就整页图片 403。
13. **预设角色只挂菜单节点，`permission_codes` 留空**（V74 的 `INSTITUTION_ADMIN`/`TEACHER`）：节点是唯一事实源，页面勾选即生效，避免"CSV 与节点两份账"。代价是角色弹窗不能再强制"至少一个权限码"（`RoleManagementView.beforeSubmit` 已放开），也意味着**撤销预设角色的授权只能撤节点**。种子必须同时写 `sys_role_menu`，只写角色行会导致登录后侧边栏为空（导航只认节点）。

自查：`功能权限总览` 页（`/system/permission-overview`）的「未登记码」和「已登记未挂菜单」两组必须都是空的；非空即说明有码只存在于 CSV/列表按钮配置而没有节点承载，或反之。

## 角色数据范围：按校区授权（必填，2026-11-20 落地）

权限码回答"能不能做"，数据范围回答"能对哪些校区做"。两者是叠加关系，**权限码不能替代服务端范围校验**。

- **存储**：`sys_role_data_scope(role_id, institution_id, scope_type, scope_value)`，`scope_value=0` 表示该维度全部；唯一键含 `deleted`（与 `sys_role_menu` 同口径）。保存按 `(role_id, institution_id, scope_type)` 物理删旧行再插入，与角色授权同一事务。
- **口径**：未配置 = 不限制；显式 0 = 不限制；多角色并集，任一角色未配置或含 0 则整体不限制；白名单为空 = 拒绝（`1=0` / 403），**绝不能退化成"看全部"**。小程序端（`portal != admin`）与 `PLATFORM_ADMIN` 不受限。
- **快照**：`AuthenticatedUser.dataScopeKey / dataScopeIds` 由 `UserAccessTokenService` 登录时解析并写入 Redis；`AuthApplicationServiceImpl.authenticateToken` 每次请求重建用户对象时**必须原样带回这两个字段**（漏传 = 登录后受限、刷新又变全部）。JWT 不加 claim，Redis 会话是唯一事实源。新增字段必须是引用类型，旧 Redis JSON 缺字段才能反序列化为 null（= 不限制）。
- **强制点**：受校区限制的读写一律走 `CampusScopeGuard`（module-organization，静态读 `AuthContext`）。列语义：非空归属列用 `apply`，NULL=机构通用用 `applyIncludingNull`，0=全校区用 `applyWithAllSentinel`，无校区列经班级推导用 `applyClassIds` / `EducationCampusScopeSupport`。写侧用 `assertCampus` / `assertNullableCampus` / `assertUnrestricted`。客户端传来的 `campusId` 只是额外筛选，为 null 绝不放宽服务端范围。
- **新增带校区维度（或可经班级推导）的查询/写入时，必须同时接上对应 Guard**：只做前端筛选等于没做。公告的 `campus_scope=all` 对受限角色不可见写入（`assertUnrestricted`），读取按 `campus_ids` / 班级所在校区与可访问校区求交集。
- 角色范围变更沿用 `invalidateUserSessionsByRole`，在线用户需重登生效；删除校区前会检查 `sys_role_data_scope` 引用并拒绝。

## 统一响应与全局异常

- `ApiResponseAdvice`（`@ControllerAdvice`）自动把 controller 返回值包成 `ApiResponse{code/success/message/data/timestamp/traceId/path}`，controller 直接返回数据对象即可，不要手动包 `ApiResponse`。`null` 与 `Resource` 不包装。
- **新增 `byte[]` 返回接口（导出 Excel/图片、`/v3/api-docs` 等）必须绕过统一包装**：历史上 springdoc 的 `byte[]` 被强包进 `ApiResponse` 导致 `/v3/api-docs` 500（`ClassCastException: ApiResponse cannot be cast to [B`）。已在 `supports()` 开头放行 `ByteArrayHttpMessageConverter`，新增二进制接口保持该分支，症状复现先查 `common/web/ApiResponseAdvice.java`。验证：`/v3/api-docs` 返回 200 JSON。
- 业务错误抛 `BusinessException(ResultCode, message)`，由 `GlobalExceptionHandler` 统一渲染（校验异常、`AuthenticationException`、`AccessDeniedException`、`NoResourceFoundException` 也在此统一转失败响应），不要在 controller 拼响应。`ResultCode` 为固定枚举：`SUCCESS/BAD_REQUEST/UNAUTHORIZED/FORBIDDEN/NOT_FOUND/VALIDATION_ERROR/INTERNAL_ERROR`。
- `TraceIdFilter` 给每个请求打 `traceId`，`ApiResponse` 会带上，排障时可用。

## 模块划分与持久化

- 包结构（阶段四起）：底座在 `com.cornerstone.core.*`，七个顶层包 `auth` / `system` / `common` / `config` / `tenant`（机构=租户）/ `audit` / `file`（通用文件能力）；业务在 `com.cornerstone.module.*`，`organization`（校区/教室/学期/时间段）→ `module-organization`、`education`（教师/学员/班级/课程/报名/排课/考勤/变动通知/小程序）→ `module-education`；`app` 只放主类 `com.cornerstone.CornerstoneApplication`。
- 模块内子包：`controller / service(+impl) / repository(仅 org/edu 有) / mapper / domain(entity) / dto`。简单 CRUD 走 mapper。
- `CornerstoneProperties.modules.*-enabled` 自阶段二C 起是**真开关**（`@ConditionalOnProperty` 落在各模块 `XxxModuleAutoConfiguration` 上），见「模块装配与开关」。
- 实体继承 `BaseEntity`：雪花 `Long` id（`IdType.ASSIGN_ID`）、审计字段、`deleted`（`@TableLogic`）。建表 `deleted` 默认 0。
- Mapper 扫描按模块各自声明：core 的 `MybatisPlusConfig` 扫 `auth`/`system`/`tenant`/`file` 四个 mapper 包，`AuditDataSourceConfig` 单独扫 `audit.mapper`（日志库），各业务模块在自己的 `XxxModuleAutoConfiguration` 上 `@MapperScan` 本模块 mapper 包（`sqlSessionFactoryRef` 都指到 `primarySqlSessionFactory`）。新模块不需要改 core。XML 位置 `classpath*:/mapper/**/*.xml`（当前 `app/src/main/resources/mapper/` 为空，全部走 `BaseMapper`）；分页用 `PaginationInnerInterceptor`（MySQL 方言）。
- **文件能力（`core/file`，SPI）**：业务表只存 `fileId`，**不存 objectKey/bucket/URL**；对象键由后端生成（`机构ID/yyyy/MM/UUID.扩展名`）。存储后端由 `cornerstone.file.provider` 切换（`minio` 生产 / `local` 本机兜底），`FileStorage` 是 SPI，加 S3/OSS 只加一个实现。`sys_file` 用物理删除（`FileMapper#physicalDeleteById`），`biz_id IS NULL` 且超过 `file.orphan.retention_hours` 的行由定时任务清理；业务保存文件引用时在自己的事务里调 `FileService#bind`，不要另开公开绑定接口。
  - **头像（`core/auth`，V114 / core V117 双轨）**：`sys_user_account.avatar_file_id` 存 fileId，文件按 `(biz_type='user_avatar', biz_id=用户ID)` 绑定，否则会被孤儿清理删掉。只提供**本人**上传/清除（`POST|DELETE /api/auth/me/avatar`，登录即可、不挂码），不做管理员代设。换头像时旧文件只解绑不立即删（对象存储删除不可回滚），交给孤儿清理回收；账号注销由 `UserAvatarCleanupListener` 调 `FileService#releaseBoundByBiz` 释放绑定——账号行物理删除后若还绑着，文件永远清不掉。PC 端入口是顶栏「个人信息」弹窗（`UserProfileDialog`）：除头像外展示 `phone`/`lastLoginAt`/`passwordUpdatedAt`/`roleNames`（均由 `/api/auth/me` 下发），并把改密表单（`ChangePasswordForm`，与强制改密页共用）收在同一弹窗里。
- schema 变更一律走 Flyway：`backend/app/src/main/resources/db/migration/V<n>__desc.sql`（迁移脚本目前全部集中在 `app/` 模块，阶段二C 才按模块拆分目录），表名前缀按归属分：**底座表 `sys_`**（认证授权/系统配置/租户，阶段五-0b 起统一，V40 把原 `jp_user_account`/`jp_role`/`jp_user_role_rel`/`jp_institution` 改了名，索引与外键键名保持历史 `jp_` 不动）、**业务模块表 `jp_`**，**不要手改既有迁移**（已上生产的迁移出问题时同样走新增 `V<n+1>` 修复；仅本地验证阶段、生产未跑过的迁移允许改写并本地删 `flyway_schema_history` 对应行重放）。

## 迁移脚本生产兼容检查（必填，V38 生产翻车事故固化）

**服务器口径：只支持 MySQL 8.x（8.0 起），不兼容 5.7/5.x**。不为旧版本做任何写法妥协，也不允许再引入"因为 5.7 没有所以绕一下"的理由；现场库若低于 8.x，要求升级到 8.x，不通过改迁移去适配。版本以 `SELECT VERSION()` 为准，别拿 compose 的镜像声明当实况（本机实为 8.0.46，见「后端常用命令」的本地依赖一条）。

**"解析器退化"这个旧依据已在 2026-09-26 撤回**，改成下面的实测结论，别再拿它当禁项的理由：

- 先前写的"Flyway 11.7.2 的 MySQL 支持上限是 8.1，连到 8.4 会退化走 `MySQL-5 SqlParser`"在本机**查不到任何佐证**：flyway-core / flyway-mysql 11.7.2（含仓库里 8.0.5、8.5.11、8.5.13、9.22.3、10.10.0 的 jar）内不存在 `MySQL-5` 与 `SqlParser` 这两个字符串，MySQL 侧只有 `MySQLParser` 与 `MariaDBParser` 两个实现，也没有任何版本上限判定。
- 同一套 Flyway 11.7.2 打到 MySQL **8.0.46** 做真回放，`WITH ... AS` 更新、`ROW_NUMBER() OVER ()` 更新、`DEFAULT (IF(1 = 1, 'yes', 'no'))` 三种写法**全部原样执行通过**（`-X` 打印的 `Executing SQL` 与源文件一字不差，说明没有被改写或错切）。
- 所以下面这批禁项当前的身份是**保守口径**，不是"已定位的解析器缺陷"。生产 8.4 那次 `MySQL-5 SqlParser failed` 的组件归属还没查实——要查实得翻生产启动日志里第一条 Flyway/SQL 报错，并核对部署 jar 内 `org/flywaydb/` 的实际版本。在查实之前照旧执行（改口径要重新回放验证，成本远低于再翻一次生产）；要放开某一条，先在与生产同版本的库上实测通过，再把本节的依据替换成那次实测。
- **已经发过的迁移脚本里那些"Flyway 的 MySQL-5 解析器"注释不要回头改**（V48/V49/V59/V70/V71 与 core 的 V106/V107）：改一个字符就变 checksum，生产会直接拒绝启动，按「已上生产不许原地改」的规矩只能新增迁移，而迁移内容一个字都不需要变。本节把口径纠正过来就够了。

**每个新迁移提交前必须逐条自查以下各项**，任何一条不满足就不许合并：

1. **暂不使用 MySQL 8.0-only 语法**（保守口径，见上面 2026-09-26 的实测：这三样在 8.0.46 + Flyway 11.7.2 已验证可跑，生产侧未复测前不放开）：CTE（`WITH ... AS`）、窗口函数（`ROW_NUMBER() OVER (...)`）、`JSON_TABLE`。去重等操作改用 `DELETE t1 FROM t JOIN t t2 ON t1.id > t2.id AND ...` 自连接。**`utf8mb4_0900_ai_ci` 一类的 8.0 专属排序规则不在此列、任何情况都不许写**——那是第 2 条的排序规则漂移问题，跟解析器无关。
2. **建表/加列必须显式写 `COLLATE`**：只写 `DEFAULT CHARSET=utf8mb4` 会让排序规则随服务器默认漂移（8.4 默认 `utf8mb4_0900_ai_ci`），同一脚本在不同环境长出不同表。本项目统一 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`；V14 漏写导致的漂移已由 V44 归一。
3. **禁用 MariaDB 方言**：`ALTER TABLE ... ADD COLUMN|DROP INDEX|MODIFY COLUMN IF [NOT] EXISTS` 在 MySQL 任何版本都不存在，只有 `CREATE TABLE IF NOT EXISTS` 合法。
4. **暂不使用 `IF(` 函数**（保守口径）：旧依据"Flyway 解析器会把表达式里的 `IF (` 按存储程序 `IF...THEN...END IF` 块处理"已被 2026-09-26 的实测否证（`DEFAULT (IF(1 = 1, 'yes', 'no'))` 被原样执行通过）。仍先不放开，是因为只验了一种上下文；要放开就在与生产同版本的库上把你实际要用的写法跑通再改。表达式里需要条件时用 `CASE WHEN ... END`（V72 的生成列就是这么写的，已验证）。`IFNULL`/`CONCAT`/`GROUP_CONCAT` 从来不受影响。
5. **迁移保持普通 DDL、一条路径**：不写 `@sql + PREPARE` 动态守卫（审查口径：脚本要一眼读懂）。唯一键重建按"**先建新键、后删旧键**"顺序：新键同样以 institution_id 开头，外键最左前缀索引全程不断（避免 1553）；若新旧键**同名**，先建新会报 1061 Duplicate key name，必须改为先 `DROP INDEX` 旧键再 `ADD` 新键（删建也可合进同一条 ALTER）；软删数据清理用普通 `DELETE`。脚本停在半实施状态时（重放报 1060/1061/1091，说明上次已执行过部分语句）注意：**Flyway 每次重启都从头重放整个脚本，注释掉已执行语句是无效的**——按脚本尾部恢复注释处理：手工补完缺失 DDL，删失败行并补插一条 checksum 与 jar 一致的 `success=1` 历史记录，再重启。**手工 `DROP COLUMN` 把本地库还原成"迁移未执行"状态时，它不会带走只含该列的索引，复合索引还会退化成剩余列上的同名索引**（V78 重放撞 1061 `Duplicate key name` 就是这么来的），必须显式 `DROP INDEX` 后再重放。
6. **外键与前缀索引**：删唯一键/索引前确认它是子查询外键列的最左前缀索引，否则先 `DROP FOREIGN KEY`、键调整完再回挂（否则 1553）。
7. **验证方式**：本地用 scratch 库把 V1..V<n> 全量走**真 Flyway** 跑一遍（`mvn org.flywaydb:flyway-maven-plugin:<版本>:migrate`，插件要额外挂 `flyway-mysql` 与 `mysql-connector-j`，否则报找不到 driver / database plugin；**别用 `mysql` 客户端 source 整个目录**，V13 这类脚本会 `ALTER TABLE flyway_schema_history`、历史表不存在直接失败），至少覆盖“干净链路一次跑通 + 上次失败现场的中间态按恢复指引续跑”两种状态。**本地是 8.0.46、生产是 8.4，回放不等于等价验证**：本地通过只证明 8.0 这一档没问题，涉及 8.4 特有默认值（排序规则、`information_schema` 行为）的脚本要在头部注释写明"本地已验、生产待验"。手工用 `mysql` 客户端复测单条语句时带 `--default-character-set=utf8mb4` 并**从文件喂入**（`mysql … < probe.sql`）：Windows 客户端默认码字符集会报 Data too long，而 Git Bash 里 `mysql -e "…中文 COMMENT…"` 会把中文字面量打坏成 1064——这个 1064 曾被我误判成"MySQL 不接受该写法"。
8. **生产修复流程**：迁移失败（`Detected failed migration to version X`）→ 修复脚本后在生产执行 `DELETE FROM flyway_schema_history WHERE version='X' AND success=0`（等价 flyway repair），再重启后端让 Flyway 重放；改过的脚本若已在生产成功过，则不允许原地改，必须新增修复迁移。
9. **生成列的写法**：`GENERATED ALWAYS AS` 之前**不能**出现可空声明（`VARCHAR(64) NULL GENERATED …` 直接 1064，省略即默认 NULL），`COLLATE` 写在数据类型后面；表达式里被拼进的列必须全 `NOT NULL`，否则 `CONCAT_WS` 会跳过 NULL 参数、把不同的行拼成同一个键（V72 的键取值范围就是按这两条定的）。

## 底座边界（Cornerstone 阶段二B 起为物理 Maven 隔离，详见 `docs/底座分离与Cornerstone重构计划.md`）

- 底座包 = `com.cornerstone.core.` 下的 `auth` / `system` / `common` / `config` / `tenant`（机构=多租户概念，阶段二A已从 organization 迁回）/ `audit`，业务包 = `com.cornerstone.module.organization`（校区/教室/学期/时间段）/ `com.cornerstone.module.education`（教师/学员/班级/排课等）。判定标准：**换一个行业后这段代码还有没有用**，没用就不许进底座。
- 边界现在有两层保护：Maven 依赖方向（`cornerstone-core` 的 pom 不声明任何 `module-*` 依赖，写错直接编译不过）+ ArchUnit 规则（底座包不得依赖 `organization..` / `education..`，Institution 豁免已随机构迁回移除）。
- **底座不得带行业词汇**：报错文案、`@Schema(example=...)`、随 jar 发布的基线种子都不许出现教培名词（校区/班级/学员/课次…）。字段中文名走 SPI：接口 `ExternalIdFieldLabelContributor` 定义在底座 `common/web`，各业务模块在自己的 `config` 包里注册（`OrganizationExternalIdLabels` / `EducationExternalIdLabels`），启动期由 `ExternalIdLabelRegistry` 合并；底座词表只剩机构/角色/菜单三条。模块没装配时该字段名的报错回落成原始英文名（守护：`ExternalIdVocabularyGuardTest`，它同时扫底座 class 常量池与 `classpath*:cornerstone/**/*.sql`）。
- 底座确需感知业务数据时（如注销账号清理学员关联、判定登录令牌属于哪个端），用**事件**（底座发 `UserAccountDeregisteredEvent`，业务模块 `@EventListener` 监听）或 **SPI 接口**（接口定义在底座如 `PortalResolver`，实现放业务模块；单实现经 `ObjectProvider` 可选注入，多实现（如各端判定）注入 `List<T>` 按装配顺序取第一个非空结果，全都不适用时底座落回默认值），不要在底座里直接 import 业务 mapper。**业务属性往底座表加列不算解耦**（如"用户默认校区"，阶段五-0a 已迁成 `module-organization` 自己的 `jp_user_campus_pref`）。
- **底座不得按接口路径/字段值硬编码业务语义**：如审计的操作类型，改成由业务控制器自己标 `@OperateLog(actionType = ActionType.LOGIN)`，`OperateLogAspect` 只认注解，不再写死 `/api/auth/wechat/login` 这类业务路径。行业专属枚举（`Portal` 之类只服务某个业务端的枚举）不进底座，直接删；底座只留通用默认。
- 新的小程序/行业专属服务放业务包（参照 `MiniappServiceImpl` 已迁至 `education.service`），`auth` 只留通用登录态。
- **底座自带的通用能力接口**（换行业照样要用的一律进 `core`，不再放业务模块）：`/api/system/user-import` 三条——`GET /template` 下载 xlsx 模板、`POST /preview` 逐行校验不落库、`POST /commit` 整批新建登录账号，实现 `com.cornerstone.core.auth.service.impl.UserAccountImportServiceImpl` + `SystemUserImportController`，权限串 `system:user:manage`（与 `SystemUserController` 全部方法同码）。三条约定别改回去：模板列是 `@ExcelProperty(index=n)` 按位置解析（客户改表头不影响），数据必须在第一个工作表，单次上限 500 行；**归属机构只取 `AuthContext.requireUser().orgId()`，模板没有机构列、接口也不收 `institutionId`**——批量入口一旦允许指定机构，等于允许往别人机构里整批写账号；**初始口令必填**，不许复用 `SystemUserServiceImpl#initialPassword` 那个公共兜底口令（单个建号是便利，批量等于给一整批人发同一个号且全程无提示）。登录账号是全系统唯一键，所以同号分两种结论要分开报：本机构已有→跳过新建（`notice`）、他机构占用→报错换号，合成一句"已存在"会让操作员以为账号在自己名下。
- **列表按钮走配置不走硬编码**：按钮定义在 `sys_list_config.actions_json`（`action`/`label`/`position`/`buttonType`/`permission`），前端 `ConfigurableList` 按 `auth.hasPermission(permission)` 显隐，动作实现注册在页面的 `CrudPageOptions.extraActions`。**`actions_json` 里的 `action` 串必须与 `extraActions` 的键一字不差**，对不上就是点了没反应（配置里 `action` 写错没有任何校验能发现）。改按钮要两边一起改：新增迁移整值覆盖 `actions_json`（`NULL`=未配置→回退 `#actions` 插槽模式，`[]`=明确无按钮）+ 前端补 handler，参照用户管理页的 `user-import`。
- **命名边界（阶段四定稿，改名字时照这张表判断，别顺手连数据库名一起改）**：
  - 属于底座身份 → 用 `cornerstone`：Java 包根 `com.cornerstone.*`、Maven groupId、Spring 配置前缀 `cornerstone.*`、Redis key 前缀 `cornerstone:`、前端 npm scope `@cornerstone/*`、浏览器 storage key `cornerstone.auth.session` / `cornerstone-nav-mode` / `cornerstone-menu-preferences` / `cornerstone-theme`（全站外观档：明亮 / 夜晚 / 跟随系统，状态源是 core 的 `composables/use-theme.ts` 模块级单例，登录页与外壳共用一份、切档内外联动；旧键 `cornerstone-login-theme` 只读一次做迁移，不再写入）、小程序 `cornerstone.mini.auth.session`、Hikari 池名 `cornerstone-*-pool`。
  - 属于本项目部署/业务标识 → **保持 `jiaopei` / `jp_`**：库名 `jiaopei_platform` 与日志库 `jiaopei_log`、MySQL 账号、业务表前缀 `jp_`（**底座表已在阶段五-0b 统一改 `sys_`**，见「模块划分与持久化」的 Flyway 条目；索引/外键/检查约束名 `uk_jp_*`/`fk_jp_*`/`chk_jp_*` 保持原样，不随表改名）、artifactId 与 jar 名 `jiaopei-platform-backend`、`spring.application.name`、容器名 `jiaopei-prod-*`、镜像名、宿主机目录 `/mnt/software/jiaopei`。改这些要生产数据迁移，不属于改名范围。
  - 环境变量名一律中性、不带前缀：`AUTH_JWT_SECRET`、`DATA_ENCRYPT_KEY`、`DB_*`、`REDIS_*`、`MODULE_*_ENABLED`（yml 里显式占位符，改配置前缀不影响它们，生产 prod.env 不用跟着改）。
  - **切 `cornerstone:` Redis 前缀需要 FLUSHDB**：旧 `jiaopei:*` 会话键不再被读取，留着只会让人误判会话仍有效。该次发布等同全员重新登录，安排停机窗口（前端 storage key 同批切换，表现也是"掉线一次"）。

## 安全与数据一致性约定（必填）

以下为审查发现的高频问题固化的硬约定：

1. **租户隔离**：客户端传入的 `institutionId`/`campusId` 只是筛选条件，服务端必须与当前登录用户所属机构比对（`AuthContext.requireUser()`），不允许拿任意入参直查他机构数据。参照 `SystemInstitutionController` 的正确写法。**整批写入口（批量导入一类）干脆不接受机构参数**，只认登录态所在机构——少一个参数就少一条绕过比对的通道，参照 `SystemUserImportController`（见「底座边界」）。
2. **权限回收即时生效**：修改角色权限、用户角色、菜单授权后必须让受影响用户下线——**底座已经兜住这三条路径**（阶段五-1e）：`SystemRoleServiceImpl.updateRole` 与 `SystemMenuServiceImpl.updateRoleMenus` 调 `AuthApplicationService.invalidateUserSessionsByRole(roleId)`（按 `sys_user_role_rel` 反查持有用户），`SystemUserServiceImpl.updateRoles` 调 `invalidateUserSessions(userId)`。业务模块新增"改授权"的接口时仍要自己调对应那个方法，权限快照写进 JWT，不清会话则 12 小时内旧权限继续可用。两条约束：**改菜单节点自身权限码（`updateMenu`/`deleteMenu`）底座没覆盖**（两跳反查，见 `docs/底座下游接入说明.md` 第 6 节 15 条）；`SystemMenuServiceImpl` 注 `AuthApplicationService` 必须走 `ObjectProvider`——`AuthApplicationServiceImpl → UserAccessTokenService → SystemMenuService` 已成环，构造注入会让容器启动失败。
3. **逻辑删除与唯一索引**：配合 `@TableLogic` 的唯一索引必须包含 `deleted` 列（如 `uk(code, deleted)`），否则“删除后重建同名记录”必撞键（V18 已为 `sys_menu` 擦过一次）。但 `(业务键, deleted)` 唯一键下 `deleted` 是 0/1 二值，**每个键只容得下一条软删墓碑**——“删除→重建同键→再删”第二次必撞唯一键。因此**管理端显式删除且重建同键属正常工作流的表**（系统参数、字典类型/条目、数据源、列表配置、菜单），删除一律走 mapper 上的 `physicalDeleteById`（`@Delete` 原生 SQL）物理删除，先例见 `SystemRoleMenuMapper.physicalDeleteByRoleId`；审计由操作日志承担，不留墓碑。新增带唯一键 + 删除入口的表照此办理，不要新写 `deleteById` 逻辑删除。**同一批软删行还会占住外键**：子表逻辑删除后行仍在，父表物理删除被 1451 拒（`jp_time_slot` ↔ `schedule_lesson` 就是这条），所以判"能不能删"的计数**不许带 `deleted = 0`**（`TimeSlotMapper.countLessonRefs`），被引用的父行只能停用或先清墓碑。节次侧的处置是：`TimeSlotServiceImpl#deleteTimeSlot` 在拒绝消息里给出引用总数与其中已删除的数量，`GET|DELETE /api/education/time-slot-lessons` 负责"列出引用（含墓碑）"与"只清 `deleted=1` 的课次连同其考勤与教师关系"（`TimeSlotLessonRefServiceImpl`：删课次的语句写死 `AND deleted = 1`，被未删除补课课次引用的跳过）。新增同类"父行有删除入口、子行是软删"的表照此办理——消息要说清是谁在挡，并给一条只碰墓碑的清理路径，不要只回一句"已被引用，请改为停用"。（守卫列配方与四类判定见本节末尾「逻辑删除唯一键收口」。）
4. **先查后插必须有兜底**：冲突校验（排课撞教师/教室/班级、绑定唯一性）selectCount 后 insert 存在并发竞态，必须配数据库唯一约束或显式锁；多表写操作必须 `@Transactional`，注意同类内自调用事务不生效。
5. **MyBatis-Plus 置空字段**：`updateById` 默认忽略 null，想把字段设为 NULL 必须用 `UpdateWrapper.set(col, null)` 或字段加 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`。
6. **缓存现状**：项目**未启用** `@EnableCaching`，`@Cacheable`/`@CacheEvict` 目前是空注解。启用缓存前先补配置并验证序列化；不要依赖注解暗示“已有缓存”。
7. **敏感字段**：手机号等敏感数据出入参遵循 `docs/操作留痕与敏感数据说明.md` 的脱敏/加密链路（`SensitiveCipher`），新增明文外泄即视为缺陷。
8. **底座的机构/账号/角色三处接口不分机构范围，所以"机构侧预设角色"只能给业务页面**（V74 的 `INSTITUTION_ADMIN`，id 110，显示名「机构运营」）：`InstitutionServiceImpl#listInstitutions` 返回全库机构、`tenant:write` 还带无平台侧限制的建机构 POST；`SystemUserServiceImpl#pageUsers` 不加机构条件、`createUser` 只校验"机构存在"（能把账号建进别人机构）；`SystemRoleServiceImpl#updateRole` 按 id 改任意角色（含 `PLATFORM_ADMIN`），而 `sys_role` 本就没有机构列。**结论：在给这三条链路补上 `AuthContext` 机构比对（与上面第 1 条同口径，另批）之前，不许给任何非平台角色勾 1「系统设置」目录、2 用户管理、3 角色管理、8 机构资料、114 机构资料维护**——勾上即跨机构读写。放开时同步在 V74 尾注的号段（7101~7106）补授权行。
9. **注销账号是物理删，且"先清谁"是硬顺序**：入口 `SystemUserServiceImpl#deregisterUser`（`DELETE /api/system/users/{id}`，权限沿用 `system:user:manage`、不新增权限码；列表按钮走配置，双轨种子教培 `V94` + core `V112` 的 `user-deregister`）。**四道守卫**：不能注销自己、只能注销本机构（持 `PLATFORM_ADMIN` 者豁免，与第 1 条同口径）、**必须先停用再注销**、不能注销最后一个可用的平台管理员。为什么物理删：`uk_jp_user_username(username, deleted)` 含 `deleted`，软删行会永久占住这个账号名（同第 3 条）。执行顺序固定为**失效会话 → 清引用行 → 删账号**，令牌先清是因为账号行没了以后残留 JWT 里的权限快照没人能复核。**指向 `sys_user_account` 的外键共五张表**（拿 `information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_NAME='sys_user_account'` 复核，别信这份清单的时效性）：`sys_user_role_rel`、`sys_auth_invitation` 由底座自己清；`jp_user_student_rel`、`jp_wechat_binding`（education）、`jp_user_campus_pref`（organization）由 `UserAccountDeregisteredEvent` 的监听器清，与底座同事务、任一失败整体回滚。**新增任何指向账号的表必须同批在对应侧补一条 `physicalDeleteByUserId`**，漏了的症状不是静默失败而是注销直接报 1451——`sys_auth_invitation` 与 `jp_user_campus_pref` 就是首批漏掉的：小程序账号压根不会有这两类行，所以自助注销这条路从来没暴露过，PC 代注销一上就撞。业务档案（教师/学员）走**解除归属**（`releaseOwnerByUserId`）不是删除，历史课次与考勤要留得住。小程序自助注销与管理端代注销**共用同一个监听器**，业务侧清理只维护一份；前端对"启用中"只给提示不做置灰，权威校验在后端。

### 逻辑删除唯一键收口：先分类，再选守卫列或物理删除

第 3 条给了两条路，动手前先按「这张表删了之后还要不要被看见/引用」分类，不要一律改物理删除：

| 类型 | 特征 | 处置 |
| --- | --- | --- |
| A | 删除入口本来就是物理删（mapper 上有 `physicalDeleteById`） | 不动 |
| B | 唯一键已含 `deleted`，只容一条墓碑 | 删除改物理删（第 3 条） |
| C | 唯一键不含 `deleted`，删除走 `@TableLogic`，业务上需要删后重建同键 | 守卫列配方 |
| D | 唯一键不含 `deleted`，但表根本没有删除入口 | 不动，等真加删除入口再收口 |

C 类不能直接改物理删除：被外键引用的表，软删子行仍占着外键（1451），只能留墓碑。

守卫列配方（先例 `V72`、`V103`~`V106`、`V108`）：

```sql
ALTER TABLE `jp_xxx`
    ADD COLUMN `xxx_code_guard` VARCHAR(96) COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (
            CASE WHEN `deleted` = 0 THEN CONCAT_WS('#', `scope_id`, `xxx_code`) END
        ) STORED COMMENT 'xxx编码防重键(未删除时取 范围ID#编码,软删墓碑为 NULL)',
    ADD KEY `idx_jp_xxx_code` (`scope_id`, `xxx_code`),
    ADD UNIQUE KEY `uk_jp_xxx_code_guard` (`xxx_code_guard`);

ALTER TABLE `jp_xxx` DROP INDEX `uk_jp_xxx_code`;
```

要点：

- 墓碑取 `NULL`，`NULL` 不参与唯一比对 → 可容纳任意多条墓碑，「删→重建同键→再删」不会像 `(业务键, deleted)` 那样第二次撞键。
- 业务键可空时（手机号一类）表达式必须写 `CASE WHEN deleted = 0 AND phone IS NOT NULL THEN ... END`，保住「多条空值可共存」的原语义。
- 先建新键再删旧键；新复合普通索引要能顺带承担旧唯一键原本兼任的外键最左前缀，否则 `DROP` 报 1553（例：`jp_teacher_institution` 靠 `uk_jp_teacher_code` 的最左前缀）。
- 守卫列长度按 `范围ID + '#' + 业务键` 估算：ID 最长 20 位，编码用 96、名称用 160 足够。
- `deleted` 目前全库 `NOT NULL`，表达式按 `deleted = 0` 写；将来若出现可空 `deleted`，改成 `deleted IS NOT TRUE`。
- 应用层预检（MyBatis-Plus 自动补 `deleted = 0`）与守卫键口径就此一致；`DuplicateKeyException` 仍要保留兜并发，日志打印原始约束名，用户提示按命中约束区分字段。
- **财务凭证类流水（收款单号等）即使唯一键已含 `deleted`（按上表是 B 类）也不要改物理删——墓碑要留痕**。换成守卫列即可；同时把编号生成改成「连墓碑取当天最大序号 + 1」，删掉的号不回收，否则「删除 → 再登记」会把同一个号发给两张单据（先例 `V108` / `FeePaymentMapper#maxPaymentNoSequence`）。父行（台账 / 台账明细）物理删除前只清墓碑（`AND deleted = 1`），活行仍由计数拦截。
- 生成列不能显式插入（`CREATE TABLE LIKE` 复制后 `INSERT ... SELECT *` 会报 3105）；已应用过的迁移禁止再改，补丁另起新版本号。
- **档案归属账号（`user_id` 全表唯一键）在逻辑删除时必须同一条语句解绑**：`jp_student` 与 `jp_teacher` 统一用 `softDeleteAndUnlink`（`UPDATE ... SET user_id = NULL, deleted = 1 WHERE id = ? AND deleted = 0`）。唯一键不含 `deleted`，墓碑若留着 `user_id`，这个账号就被一条看不见的档案永久占住，再建档案/再绑定会撞 1062。账号注销走 `releaseOwnerByUserId`，同一口径。存量墓碑由 V107 统一清空 `user_id`；新增任何"档案表 + 归属账号唯一键"的组合照此办理，不要只写 `deleteById`。

## 大 ID 跨端精度规范

雪花 ID 为 19 位 `Long`，超出 JS `Number.MAX_SAFE_INTEGER`，跨端处理机制：

- **后端入参：逐字段注解，不是全局注册**。`FlexibleLongIdDeserializer` 必须显式挂在每个接收 id 的字段上：单值用 `@JsonDeserialize(using = FlexibleLongIdDeserializer.class)`，`List<Long>` 用 `@JsonDeserialize(contentUsing = FlexibleLongIdDeserializer.class)`。它同时接受数字、纯数字字符串和业务 id 字符串（非纯数字、以数字结尾，由 `ExternalIdParser` 解析尾部数字）。新增/修改 `*Request` DTO（含其中的嵌套记录，如排课的 `ScheduleSlotCell`）时**必须补注解**——漏了不会立刻报错，只是退回 Jackson 默认的隐式字符串转数字（能跑但契约不显式，且不认业务编码），所以按下面的自查命令复核。解析失败的中文报错用字段名查词表，词表按归属分（底座三条 + 各模块 `ExternalIdFieldLabelContributor` 登记，见「底座边界」），新模块的字段名去自己模块注册，别往底座加。
- **前端请求侧：id 一律按字符串透传，禁止 `Number()` / `parseInt()`**。截断发生在浏览器内存里、请求发出之前，后端收不到原始值也还原不了；这不是前端替后端兜底，而是唯一能防住的位置。接口层已把 16 位以上的 `id/*Id/*Ids/*By` 归一成字符串，直接下发即可；拼参时用 `String(...)`，不要为了"类型对齐"转成 number。曾出过的事故：菜单新建/编辑把 `parentId` 写成 `Number(payload.parentId)`，种子数据菜单 id 是个位数所以开发环境正常，生产雪花 id 一选父节点就报「菜单不存在」。
- **响应出参**：后端仍按 JSON 数字输出 `Long`；`frontend/packages/core/src/api/http.ts` 与 `miniapp/src/utils/request.ts` 在 `transformResponse` 里用**字段名模式**兜底——匹配 `id`、`*Id`、`*Ids`、`*By` 且值为 16 位以上数字的，转成字符串再 `JSON.parse`。
- **结论：不需要维护字段白名单**（旧 `LARGE_ID_FIELDS` 白名单机制已废弃，文档旧描述作废）。新增雪花 ID 字段按 `xxxId`/`xxxIds`/`xxBy` 命名即被自动覆盖。
- **剩余边界**：若出现**不按 Id/By 命名**的 16+ 位 Long 响应字段（或嵌套在自由 JSON 字符串里的 id），会被静默截断精度。更彻底的替代方案是后端 Jackson 全局 `Long → ToStringSerializer`（响应统一输出字符串），但那会改变所有接口响应形态、前端两端要一起回归，仅在边界字段真的出现时再评估。
- 已排查现状：全仓库非 id 语义的 `Long` 字段均为计数/分页/时长（`count`/`total`/`page`/`durationMs` 等小数值），当前机制全覆盖，无未适配字段。
- **小号 id 不会被转字符串，所以前端接收 id 的 prop 类型是 `string | number`**：种子数据里机构/校区的 id 是个位数（`1`），不满足"16 位以上"这条判定，运行期就是 `Number`；同一字段换到生产（雪花）又变回 `String`。因此组件 prop 要声明联合类型（`module-education` 的 `ClassScopeValue = string | number | null | undefined`）、拼请求参数时 `String(...)`；`el-select` 的 `:value` 也要写成 `String(item.id)`，否则 `v-model` 拿数字 `1` 去比字符串 `"1"` 匹配不上，下拉显示成裸数字。
- 自查方式：
  - 响应侧：改相关字段后看真实响应——字段值应为字符串形式，且前端显示 id 与数据库一致。
  - 入参侧：`grep -rnE "\b(Long|List<Long>|Set<Long>) [a-zA-Z]" --include=*Request.java backend/*/src/main/java`，每个命中字段的上方都必须有 `@JsonDeserialize`（`List/Set` 用 `contentUsing`）。请求里的嵌套记录（`ScheduleSlotCell`、`SchedulePlanOccurrence`）不叫 `*Request`，改到它们时同样按这条查；`View`/`Response` 是出参，不需要。
  - 前端侧：`grep -rnE "Number\(|parseInt\(" --include=*.ts --include=*.vue frontend/packages frontend/src miniapp/src`，命中的变量若是 id 就是缺陷（年/月/宽高/排序号等非 id 数值不算）。

## Service 接口签名变更规范

修改 Service 接口方法签名后必须同步所有调用方，否则测试编译失败：Controller 注入调用处、测试里的 `given(...willReturn)` mock 与 `verify(...)`、跨模块的 Service 内部调用。验证用 `mvn -q test`（不是只 `mvn compile`）；`*ApiCompatibilityWebTest` 会显式 verify 各 Service 方法签名，是签名变更的安全网，改了签名必须同步改。

## 学期（term）必填关系

后端设计上学期是**可选维度**，前端不要误强制：

- 班级 `jp_class.term_id`：可为 NULL，创建班级学期可选。
- 报名/入班 `jp_class_student`：完全不涉及学期。
- 排课 `jp_schedule_lesson.term_id`：`NOT NULL`，唯一强制学期的实体。

前端范围栏的学期选择器是**筛选条件**，不应作为报名/入班前置校验；只有 `ScheduleAttendanceView.vue` 的排课弹窗校验学期是对的。

## 报名并入名册（必填，V109）

**不存在独立的报名实体。** V109 起 `jp_enrollment` 已删除，报名就是「分班与名册」（`jp_class_student`）里的一行：班级必填、入班日期即报名日期、`channel` 记来源渠道（字典 `enrollment_channel`，选填）、`remark` 记备注。原报名页 `/enrollments`、报名编号（ENR 编号规则）、报名状态（`submitted/confirmed/cancelled`，无流转接口）全部下线且**不要恢复**——它们只是同一件事的第二份台账，所有下游（收费/考勤/排课/公告/小程序）本来就只读名册。

落地要点：

- **一个入口**：`POST /api/education/class-students` 是唯一的报名/入班写入口；`ClassStudentServiceImpl#createClassStudent` 承担唯一性、容量与 `current_students` 重算。不要新增第二条"报名"链路，也不要再拆回两张表。
- **退班仍是物理删除**（`ClassStudentMapper#deletePhysicallyById`）：名册行消失，渠道与日期记录随之消失。这是合并后的既定语义，不保留"退过班的人"的台账痕迹；需要历史请另做独立报表，不要把删除改回软删（`uk_jp_class_student` 不含 `deleted`，改软删会让同班重入撞键）。
- **渠道只存字典值**：写入前经 `DictValueValidator` 按 `enrollment_channel` 校验，空值落 NULL；前端用 `DictSelect type-code="enrollment_channel"`。
- **不要给学员档案加报名字段**：学员是机构级档案，报名/在班关系全在名册行上。

## 学员档案不持有校区（必填，V89）

`jp_student.campus_id` 已删除，档案是**机构级**的，校区一律由班级推导（`jp_class_student.class_id` → `jp_class.campus_id`）。理由：一个学员可报多个班、班可分属不同校区，档案上的单值列把 1:N 压成 1:1，跨校区就读表达不了；而且改档案列不回写历史班级，"当时在哪个校区上的课"会变得不可追溯。落地要点：

- **不要再往学员侧加 `campusId`**：DTO（`StudentCreateRequest`/`StudentUpdateRequest`/`StudentPageQuery`）、`GET /education/students`、`/students/page`、`/students/options`、`StudentEntity` 都不接受也不返回它。
- **原先两处跨校区硬校验已解除**（原报名 `EnrollmentServiceImpl#createEnrollment`（V109 已删）、入班 `ClassStudentServiceImpl` 的"学员校区与班级校区不一致"）。它们挡的是自己造出来的矛盾——档案列可以随便改，班级才是事实源。将来若再加校验，校验的两端必须都是班级侧。
- **前端"按校区看学员"只有一条正解**：`/class-roster`（走 `class-students` 的班级联表，`campusId` 收窄的是**班级**不是学员）。`StudentSelect.vue` 因此没有 `campusId` prop；`/students` 页筛选栏只剩学号/姓名，弹窗表单无校区项。工作台「学员数量」是全机构口径，标签已写明（全机构）。
- 小程序自助建档本来就没写这一列（3583 条里 3 条 NULL 就是它建的，功能全程正常），`miniapp` 侧无需改动。

## 班级主讲教师口径（必填，死循环回归事故固化）

`jp_class_teacher`（`is_primary=1` 且 `teacher_role=lead`）是主讲的**唯一事实源**，`jp_class.teacher_id` 只是它回写出来的展示/责任列（`ClassChangeNoticeServiceImpl#hasPrimaryTeacher` 与班级页都先看关系、再兜主档列）。因此：

- 写主讲只有一条路：`ClassTeacherService#assignPrimaryTeacher(institutionId, classId, teacherId)`——创建/更新班级主档（`TeachingClassServiceImpl`）与「设为主讲」按钮都走它。**不要在班级主档的写入路径上直接 `setTeacherId` 落库**，那会造出"主档有人、关系表没人"的班级，详情页立刻报"班级未设置主讲教师"，而编辑弹窗又被拒改主讲，形成死循环（2026-09-25 修掉的回归；`docs/archive/班级正式维护与课次执行变更首批范围.md` 第 107/161 行早就要求"同一页面完成主讲调整"）。
- `teacherId` 传 `null` = **撤销主讲**：降级该班全部主讲关系并把主档列置 NULL。`updateById` 会忽略 null，清空必须走 `LambdaUpdateWrapper.set(...)`。撤销是用户明示意图，**不自动补位**（只重新出 `missing_primary_teacher` 通知）；删除主讲关系才走自动补位。
- 自动新建的主讲关系**不写 `joined_at`**（留 NULL）：`uk_jp_class_teacher_period(class_id, teacher_id, teacher_role, joined_at)` 含该列，填当天日期会在"同班同人同日同角色"上撞键。

## 排课的提示与冲突分层、课时目标口径（必填）

自动排工的前置能力（V75~V77）。判据只有一条：**会不会算出错数据**。会让课表出现同一格子两节课、或把资源排进别人机构的，是冲突（`conflict=true`，硬拦）；只是"这样排不太合适"的，是提示（`notices`，只摆出来不拦保存）。

- **硬拦**：班级/教室/教师同时段占用、命中启用中的禁排规则（`jp_schedule_rule.target_type` = class/teacher/**classroom**，教室经所属校区认机构，`ScheduleRuleServiceImpl`）。
- **只提示**：教师超 `max_weekly_lessons`/`max_daily_lessons`、学员在同日期同时段别的班也有课、课次落在 `jp_school_calendar_day` 停排日。这三条都是"人比机器更懂"的判断，硬拦会把可用配置判死。
- **停排日表 `campus_id=0` 表示全校区，不用 NULL**：NULL 在唯一键里互不相等，"全校区"能重复插 N 行。因此该列 `NOT NULL DEFAULT 0`，读取一律 `campus_id IN (0, 本校区)`，也**不能**给它建校区外键（0 那行指不到任何校区）。删除走 `deletePhysicallyById`（软删墓碑会占住 `(institution_id, campus_id, day_date, deleted)` 唯一键，见「安全与数据一致性约定」第 3 条）。
- **课时目标 NULL=未设定，不用 0**：与 `capacity_limit` 的"0=不限"故意不同——"每周应上 0 节"是非法值。业务侧一律判 `值 != null && 值 > 0`（`weekly_lessons`、`max_weekly_lessons`、`max_daily_lessons` 三列同口径），前端表单留空即取消，实体上必须配 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`，否则 `updateById` 把 null 忽略掉、根本清空不了。（"会被读成这周不该排课"是错的表述，2026-09-27 核对：读取侧的 `> 0` 判定使 0 与 NULL 行为完全一致，不用 0 的理由是它是非法值、会误导下一个读代码的人。）
- **录入口径与换算结果分列存**（V79）：`weekly_lessons` 永远是**换算后的每周节数**、也是完成度分母与补排输入的唯一来源；`weekly_lessons_mode`（weekly/monthly）与 `weekly_lessons_input`（录制原值）只是"操作员当初填了什么"，供编辑弹窗回显。**不要把 mode 做成 weekly/monthly/total 三选一**——总课时与周目标是两个独立维度（一个月 8 节的班照样有每周节奏），做成互斥会让填了总课时的班丢掉周分母。换算只在写入时做一次（`LessonTargetConverter.toWeekly`，式子 `每月 × 12 ÷ 52` 四舍五入，52 用整数常量以便操作员拿计算器复验），读取侧一律不重算。前端 `LessonTargetFields.vue` 里镜像了同一个式子做即时回显——**改式子必须两边一起改**，否则保存前看到的与保存后报表里的不是一个数。
- **折算不足 1 节时写 NULL 不写 0**：每月 1~4 节这类稀疏班按 `× 12 ÷ 52` 会塌到 0/1，塌成 0 的按"未设定周目标"处理（`weekly_lessons=NULL`、`weekly_lessons_input` 仍留原值），完成度回落计划展开口径，前端在换算回显里把原因说清楚。**不要**为了让报表有数就硬填 1——那会把一周的应排顶高一倍，天天报超额。
- **整班总课时 `total_lessons` 只喂进度、绝不进分母**：它回答的是"这一班还剩多少节、什么时候上完"，与"这一周该排几节"是两问。完成度行上的 `heldLessons`（已上课）口径**宽于**格子里的 `completed`：分子是"日期已过且未停课"的全量课次（不按统计窗口、不比对计划格子），因为计划外加的课、改期补的课同样消耗总课时；前端标签写「已上课」不写"已完成"。`estimatedFinishDate` 按**近四周**节奏（`近四周节数 ÷ 4`）推算，剩余为 0 或近四周零节都返回 NULL（后者是"没节奏可依"，给个日期是假精确）；必须与 `planEndDate` 并排显示才有意义——只有"约 X 上完、晚于计划的 Y"这种**可核对**的预警，不做"是否落后于按周均速的理论进度"那类不可核对的指标。
- **完成度报表的分母**：设了目标的班按 `weekly_lessons` 算应排（`denominatorSource=weekly_target`），否则按计划展开（`plan_expand`）。门槛是"**这一周计划有没有展开出格子**"，不是 `covered`（日期区间覆盖）——单双周计划的空周、整周被跳过的周，区间照样盖着，用 `covered` 判会把目标摊进这些本来一节都不排的周，报表天天假报缺课。目标口径下分子取"这周实际排了几节"（`actualKeys`）而不是命中的计划格子，所以 `4/3` 是真实超额，允许出现；格子口径下仍禁止 `generated > expected`（结课/停课会让出格子，同格多行只算一个格子）。`missing = max(0, 应排 − 已生成)`，可能多于 `gaps` 条数；前端「缺 N」读 `cell.missing`，明细弹窗只列有落点的 `gaps`，列不满时补一句说明。
- **教师占用没有唯一键可兜**：班级/教室占用是 V72 的生成列唯一键，教师跨 `schedule_lesson` + `schedule_teacher_rel` 两表建不了。所以写课次前在事务内 `SELECT id ... ORDER BY id FOR UPDATE` 取行锁（`ScheduleTeacherLock`）；**必须按教师 ID 升序**，乱序会让两个并发会话互持对方要锁而死锁。
- **自动排课新表的命名标识**：新表统一在名字里带 `auto_` 段（`jp_schedule_auto_batch` 批次表）；`jp_school_calendar_day` 是校区日历，与自动排无关，按此约定**不带** `auto_`。
- **批次归属落在课次，不落在计划**：`jp_schedule_auto_batch` 记批次，`jp_schedule_lesson.auto_batch_id` 记"这一节是哪一批生成的"。**不要**回头往 `jp_schedule_plan` 加 `auto_batch_id`——一个计划的缺口会分多批补，计划行没有"哪一批"这个身份，而撤销要判的是"本批生成、且还没上过"的单节课次，只有课次挂批次才有落点。引擎写的课次 **`plan_id` 留空**：完成度报表的缺口条目不带计划 ID，猜一个会让"撤销计划课次"的按 `plan_id` 删除误伤补排队。
- **一期只做补排，不改计划**：输入是完成度报表的 `cell.missing` + `cell.gaps`（未来 N 周窗口），输出填进课表，**不新建也不修改 `jp_schedule_plan`**——改计划等于让机器覆盖人工设定的周期，且撤销时分不清哪一格是机器改的。**同步跑完**，不起异步任务、不加 running 锁（一批几十节，在请求超时内跑完）；预览（`/preview`）与执行（`/run`）是两个接口、**各算一遍** `ScheduleGapPlanner`，所以操作员确认到执行之间若有别人插了一节课，以 `/run` 的结果为准。
- **引擎与手工的约束分级不同**：教师周/日上限对手工排课只是提示（见本节开头），对引擎升格为**硬约束**（机器没有人"就知道这样不合适"的判断力，超了会静默铺满教师）；停排日反过来降为**兜底档**（打分罚分而不是禁止，否则假期内有缺口的班永远补不满）。缺口优先补在计划格子上，落不到格子的转"这一周任意空闲时段"（`free_cell`），所以补出来的课次日期对得上课时目标、节次可能与周期计划不同。

## 枚举值 / 字典规范（必填，性别跨端不一致事故固化）

判断标准：**取值可枚举、给用户选择、运维可能扩充**的字段（如性别），唯一事实源是系统字典 `sys_dict_type`/`sys_dict_item`，禁止各端各自硬编码取值或中文标签。**边界**：由代码逻辑判定的状态值（`status=active/disabled`、课次状态、`deleted` 等）**不入字典**——它们在代码里分支比对，进了字典就等于允许运维在页面上改掉判定逻辑依赖的值。

1. **规范值是 `item_value`**：小写+下划线（如 `male`），入库、接口、前端状态全部用它；中文标签只来自 `item_name`，任何端不许自建映射（"自建"指在代码里写常量表；**从字典服务查 `item_name` 不算**，见第 5 条）。存量性别的大写值（`MALE/FEMALE/UNKNOWN`）已由 V51 归一。
2. **新增枚举字段必须五处同步**，缺一即视为未完成：
   - `app/` 迁移种入字典类型+条目（参照 V51）；
   - DB 列 COMMENT 与 DTO `@Schema` 写成「字典(类型码:值=含义)」格式并带 `allowableValues`（参照 gender 字段现状）；
   - 后端写入路径按字典校验，字典外取值 `BAD_REQUEST` 并在消息里列允许值（各业务模块各持一份通用 `DictValueValidator`，按 `(字典类型码, 字段中文名, 值)` 调用，参照 `module-education`/`module-organization` 的 `service/support/DictValueValidator`；下沉到 `cornerstone-core` 会把业务字典类型带进底座，不允许）；
   - PC 表单用 `DictSelect`（指定 `type-code`），列表/详情回显统一用 `dictStore.displayLabel(类型码, 值)`（core 的 dict store 提供：命中取 `item_name`，未命中回显原始值）。**表单默认值一律留空**，不把 `item_value` 预置进前端（条目被停用/删除时表单会提交无效值），需要默认值由后端取库默认（参照 `employment_type`/`teacher_role`/`campus_type`/`room_type` 的 create 分支）；
   - 小程序用 `GET /api/system/dicts/options?typeCodes=<码>` 拉选项（该接口登录即可访问，无需新增端点），参照 `miniapp/src/api/miniapp.ts` 的 `fetchGenderOptions()`。
3. **`item_value` 不允许原地改**（已上生产的同迁移脚本禁改规矩）：需要改名/换值时新增 `V<n+1>` 归一存量数据（UPDATE + 列注释），与代码同批发布。
4. **回显兜底口径：未知值显示原始值**，不显示空白也不硬映射成"未知"或某个合法值（PC 走 `dictStore.displayLabel`，小程序参照 `genderText`、`DictSelect` 的 `keepUnknownValue`）——脏数据要可见而不是被静默吞掉。各端**不再写字典值的静态中文映射兜底**（V52/V53 收口时已删），字典拉不到就显示原始 `item_value`，宁可难看也不许两处漂移。
5. **文件出口（导出 xlsx、导入模板、校验报错）也算"给人看的地方"，字典列一律 `item_name`**，落库/接口传输才用 `item_value`。凡是做了导入或导出的字典字段，下面几条一起守（参照教师档案的 `gender`/`employment_type`）：
   - 导出翻译走 `DictValueValidator.labels(类型码)`（`item_value → item_name`），**整批取一次建映射**，不许逐格查字典；字典里没有的值原样导出（同第 4 条）。
   - 模板下拉与说明页给中文条目名；导入侧先 `valueByLabel(类型码)`（`item_name → item_value`）反查再校验，因此**中文和 `item_value` 都认**——"导出 → 改几格 → 原样传回"是最常见用法，只认代码等于逼操作员先把整列翻译回去。
   - 报错里的允许值写成 `中文(代码)`（`DictValueValidator#describe`）：模板下拉显示"男"、报错说"可选值：male/female/unknown"，操作员不知道自己哪儿错。
   - **留空/为 null 的值不参与翻译**：不可变 `Map`（`Map.of`）对 null 键直接抛 NPE，症状是"字典列留空反而 500"，查表前先挡掉 null 与空串（参照 `TeacherImportServiceImpl#labelToValue`）。测试里用真实 `Map.of` 当 stub 才能复现，别写成对 null 宽容的假实现把它盖掉。
   - **代码判定的状态值不翻译**（如教师 `status=active/suspended/left`）：导出与模板都写代码值，别为了"好看"把它塞进字典（边界见本节开头）。

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
- **扩展点**（`packages/core/src/extension/module-extensions.ts`，对应后端的事件/SPI）：`registerListRenderer`/`getListRenderer`/`listRendererChoices`（列表单元格渲染器）、`registerPermissionLabels`（业务权限码中文名与分组）、`registerNavItems`（业务模块登记自己的侧边栏入口）、`registerBranding`（壳工程登记登录页能力卡；系统名称等文字类文案走系统参数 `brand.*`，见「品牌文案」）、`onInstitutionReset`/`notifyInstitutionReset`（机构切换/登出广播，业务 store 自行清理）、`registerPageSlot`/`getPageSlots` + `onPageRefresh`/`notifyPageRefresh`（页面插槽注入 + 宿主刷新广播）。阶段五-0d 删掉了"数据目录 provider"（`registerDirectory`/`loadDirectory` 与 `RenderContext.directories`）：0a 之后没有任何宿主再注入目录，业务下拉的正确通道是「数据源配置」（`sys_data_source`），列表编辑器里直接输入编码即可。
  - **侧边栏导航（2026-09-29 起完全由菜单驱动）**：顶层组**一律由 `sys_menu` 的顶层节点决定**——组名取 `menu_name`、组间顺序取 `sort_no`、图标取 `icon`，组内页面与顺序取其子节点，再按当前用户**已授权**的菜单树裁剪。`registerNavItems` 因此只剩一种用途：**没有菜单节点可挂的落地页**（工作台是跨模块聚合页、不对应任何权限码），由壳 `modules.ts` 登记并用 `groupOrder` 钉位。业务模块的 `index.ts` **不再登记导航项**，也不要再写 `group`/`groupLabel`/`groupOrder`/`prefixes`——那四样是前端第二份事实源，"客户在后台改了 `sort_no`、界面不动"就从这里来。新增业务页面要进导航，改的是菜单节点（「菜单管理」或「导航编排」），不是 core、也不是模块 `index.ts`。
    **一个 `sys_menu` 目录 = 侧边栏一组（V92 定稿）**：业务导航不放散页——当初为免滚轮逐页拆分，每拆一页就往顶层铺一个叶子节点，顶层 13 项里 8 项是散页、图标导航一次铺 12 个入口，客户反馈"菜单一堆、不知道点哪个"。现在的形态是恒定两层（组 → 页面），组名取目录节点 `menuName`、组内顺序取子节点 `sortNo`、组图标取目录节点 `icon`（顶层散页自己成组时取它自己的 `icon`），后台改名/改序/换图标即生效；完整导航在"单项目录自成一组"时不重复渲染组标题（`isGroupTitleRedundant`，与窄栏浮层同一条判断）。**组顺序按"多久用一次"排**（排课与上课 → 学员与班级 → 基础资料 → 消息通知），不按对象分类排——操作员要找的是下一步动作，不是同一张表的兄弟页。拆出新页面时先问"它是哪条链的哪一步"，进对应目录（15 排课与上课 / 139 学员与班级 / 130 基础资料），**不要**再往顶层加散页；目录里没有的位置就新建目录节点，并**同批授权给所有在职角色**（V92 那三条 `sys_role_menu` 就是这件事，含"勾了子页却没勾父目录"的兜底）——漏授权的表现是那几张页面的入口整块消失且不报错，见 `docs/前端交付说明.md` 3.4。**「导航编排」页把这条授权做成自动的**（新建分组即同事务授满），「菜单管理」手工建目录仍然不授，别把两处当成一回事。
    **客户自助改导航走「导航编排」页（2026-09-29）**：`GET|PUT /api/system/navigation` + `POST /api/system/navigation/catalogs`，实现 `core/system/service/impl/SystemNavigationServiceImpl`，页面 `core/views/system/NavigationView.vue`，菜单节点 140、权限串 `system:nav:manage`（读写共用一个码、挂页面节点，见「权限码与菜单节点的分配规则」第 4 条；教培 V93 / core V111 双轨）。**只有五列可写**：名称、图标、顺序、所属分组、侧边栏可见。路由、权限码、类型、编码**一律不在这里**——那属「菜单管理」（平台管理员），改错了就是 403 或空白页，而现场操作员无从判断。校验只拦"改完找不到这一页"那一类：底座「系统设置」整棵子树在本页只读（用户管理/角色管理/机构资料后端还没比机构范围，放开挪动等于绕过「安全与数据一致性约定」第 8 条那条硬约束）、侧边栏只到两层（顶层目录不许再套进目录、已在目录里的目录不许当父级、页面不能挂进底座段）、**顶层入口不许同名**（两颗一样的图标等于白改，新建与保存两处都拦）。三条实现口径别改回去：① 目录行**不给"可见"开关**，因为 `buildTree` 会把父节点未授权的子节点提升到根，藏一个目录等于把它下面的页面炸成一排散页图标；② 新建分组是**立即写库**（页面要拿到真实 id 才能往里挪东西），同事务授满所有在职角色并把条数回显出来；保存才是批量写，且整批校验通过才落库（半批生效会和页面上的草稿对不上、且不报错）；③ 结构改动**不踢会话**——导航树每次进系统现拉，保存后只刷新操作者自己的侧边栏，别人下次进入才看到；`SystemMenuEntity` 的 `parentId`/`icon` 配了 `FieldStrategy.ALWAYS`，否则"提升为顶层分组"（写 NULL）会被 `updateById` 静默忽略。
    **入口显隐一律走菜单节点，不许再写 `alwaysVisible`**：单页入口在 `NavItemDefinition.menuCode` 里填自己的 `sys_menu` 页面节点 code，`AppLayout` 按"当前用户已授权菜单树里有没有这个节点"决定显隐，侧边栏名与页签名同时取节点 `menuName`（后台改名即生效，见 V61 的五个业务页 + 排课三页）。`alwaysVisible: true` 现在只剩壳的工作台在用（工作台是落地页，没有对应权限）。写 `alwaysVisible` 的业务入口等于绕过授权：没授权的人照样看得见入口。**V113 起业务页面节点不再挂读取码**（`permission_code` 留空），入口显隐只认"当前用户已授权菜单树里有没有这个节点"，读取接口登录即可，不再有"看得见却读不了"的中间态；写权限仍是隐藏按钮节点，授权弹窗勾页面时一并授出。注意预设权限与菜单两条路取并集，撤销要两边都撤。
  - **品牌文案（2026-09-26 起文字类走系统参数）**：系统名称、定位副标题、登录页介绍与表单标题读 `brand.*`（`packages/core/src/composables/use-branding.ts` 拉 `GET /api/system/branding`，登录页与后台横幅共用同一份请求结果，模块级单例、不落 localStorage——它是服务端数据，缓存本机只会让"改完看不到生效"），运维在「系统管理 → 系统参数」改名即生效、不用重新构建。**未配置就是未配置**：显示 `未配置(brand.name)`，**不回落构建期默认名**（用户 2026-09-26 明确不要悬停 tooltip 那类额外指引，占位文案里带参数键名即可）。`registerBranding` 扩展点现在只留**登录页能力卡 `loginFeatures`**（对象数组，系统参数页的单值输入框塞不下，core 默认空数组即整块不渲染），`BrandDefinition` 已随之收窄。登录页表单下方那句提示语走 `auth.login.page_tip`（空串即不渲染，与 `brand.*` 的"未配置要提示"是两套口径，别改成一个）；账号密码预填只在 `import.meta.env.DEV` 下生效，且**底座不写死任何项目的口令**：值取壳工程自己 `.env.development` 里的 `VITE_DEV_LOGIN_USERNAME` / `VITE_DEV_LOGIN_PASSWORD`（该文件已 gitignore，本仓库那份写着 dev admin），没配就是空框，生产构建更不含。**浏览器标签 `<title>` 同口径**：`loadBranding()` 拿到结果后把 `document.title` 设成 `brand.name`（未配置/拉不到时同样是 `未配置(brand.name)`），不回落 `frontend/index.html` 里的静态值——那行只是请求回来前的首帧占位，改完参数标签也会跟着变。**只有全站页名拼接（`当前页 - 系统名`）没做**。**其余没做成参数的两处**：小程序的导航栏标题与 App 名（`pages.json` 的 `globalStyle.navigationBarTitleText`、`manifest.json` 的 `name`，是构建期清单、运行期改不了；小程序登录页与首页问候语已走 `brand.name`，见「小程序（工作目录 `miniapp/`）」）——仍是构建期硬编码，要跟着变得各开注入通道。
  - **列表渲染器**：`sys_list_config` 的 `renderer` 名字先查 core 内置表（只剩机构/权限/密码态这类底座概念），查不到再回退模块注册表。业务渲染器**自己取名称数据**（读模块自己的 store/接口，或改用 `type: 'map'`、字典列），core 不再透传业务目录；模块未装配时该列退回普通渲染（显示原始 id），列表配置编辑器也不会给出这个候选（`rendererChoices()` 只合并已登记项）。新增业务渲染器要同步两处：模块入口 `registerListRenderer` 登记、`sys_list_config` 种子里的 `renderer` 名保持一致。
  - **权限码标签**：core 的 `constants/permissionLabels.ts` 只列底座码（`system:*` / `tenant:*`，与 core 各 Controller 的 `@RequiresPermission` 对齐），业务模块在自己入口 `registerPermissionLabels` 贡献本域的**写码**（如 organization 的 `organization:write` → 组织资料维护）。V113 起业务读码已清零，**不再登记读码标签**。**这张登记表是权限码中文名的权威源**，不是节点名：同一个写码可以服务多个页面节点（如 `education:profile:write` 同时服务教师/学员页），节点名说的是"挂在哪"、不是"码叫什么"，所以展示时先查表、查不到才用后端给的节点名、最后回退裸码。**新增权限码必须同步补一条登记**，否则界面显示 `education:profile:write(education:profile:write)` 这种裸码。读取处一律**现算**，不要在模块加载时取快照——注册晚于快照就会漏。
  - **数据源候选**：`OPTIONS_LOADER_OPTIONS` 只列 `institutions`/`roles`/`status`；业务编码（如 `campuses`）在数据源配置页维护，编辑器下拉用 `allow-create` 直接输入。新增编码不改 core 代码，运行期命中不了宿主注入就自动走 `sys_data_source` 定义。
  - 页面插槽用于"业务页签注入到别的模块页面"。**本仓库当前没有使用方**：唯一一处（校区页里的课程/课程分类/班型三张卡片，内容来自 education 包）在 V85 已经独立成 `/courses` 页并删掉 `registerPageSlot('campus-detail')`——课程本来就是**机构级**档案，跟校区没有从属关系，挂在校区页只是当初没别的页面可放。扩展点本身留在 core（底座对外能力，下游 achievement 可能用），要再用时按下面的老约定写：宿主 `refreshAll` 末尾 `notifyPageRefresh(pageKey)`，插槽子组件订阅并自己拉数；**`onPageRefresh` 返回注销函数，必须在 `onUnmounted` 里调用**，否则订阅者随打开次数累积。**判据：注入卡片会让宿主页纵向翻倍（校区页当时六张卡竖排、实测 3606px），"跨模块注入"不是省预算的办法，拆页才是。**
  - `registerPageSlot` 幂等、按 `order` 排序；插槽子组件依赖全局 `core/src/styles/index.css` 里的 Element Plus 布局类（不在 scoped 样式内），组件不要再自带 reset。
- **接口归属**：机构（=租户）的列表接口在底座 `core/src/api/tenant.ts`（阶段二A 后端把它从 organization 迁回 tenant，前端同步；请求路径 `/organizations/institutions` 未变）。`module-organization/src/api/organization.ts` 只留校区/教室/学期/时间段。
- **新增页面落包判断**：换一个行业后还常用 → `packages/core`；否则按域放对应 `module-*`；跨模块聚合页（工作台等）放壳 `frontend/src/views/`。业务模块专属依赖放各自 `package.json`，不要塞进 core。

## 前端 / 小程序约定

动手写页面前先读 `docs/前端页面开发手册.md`：那是本节的**执行细则**（元素落点表、反模式对照、四型页面骨架、新建一页装配清单、与配置化的差距），不新增口径、只回答"该写在哪一行、抄哪个页面"；与本节冲突一律以本节为准。

- API 层在 `frontend/packages/core/src/api/`：`http.ts`（axios 实例 + 大 ID 兜底）、`request.ts`（`unwrap` 解包 `ApiResponse.data`，非 success 抛 `ApiError`），按模块拆 `auth/tenant/system/...`；业务接口在各自 `frontend/packages/module-<name>/src/api/`（organization、education）。
- 状态：Pinia，登录态持久化在 `localStorage` key `cornerstone.auth.session`（`utils/auth-session.ts`），`http.ts` 从该处取 `Authorization`；未登录时不带该头，没有构建期固定头的兜底（旧的 `VITE_API_AUTHORIZATION` 已删除）。
- 路由 `router/index.ts`：`/login`、`/change-password` 公开；其余挂在 `AppLayout` 下；系统管理多 tab 复用 `SystemManagementView.vue`，靠 `meta.systemTab` 区分。
- 路径别名 `@` → `frontend/src`（vite + tsconfig 均配置）。
- 页面写操作按钮要按权限码显隐（与后端 `@RequiresPermission` 同一套码）；列表页优先使用 `ConfigurableCrudPage`/`ConfigurableList`（自带请求序列号守卫，切机构不竞态）。
- **下拉框不许拉全量数组**：档案类下拉（学员/教师/班级…）条数随业务涨到几千，`el-select` 即便折叠也会把每个 `el-option` 渲染进 DOM，整页会卡数秒（实测 3583 个学员选项让 `/students` 停 10 秒）。正确形态是**服务端按需搜索**：后端加 `GET /xxx/options`，只回"下拉与回显需要的字段"（学员的是 `id/studentNo/studentName`，**不要把监护人电话一类敏感字段带进下拉载荷**）、`keyword` 模糊匹配、`.last("LIMIT 30")` 封顶（参照 `ScheduleAutoBatchServiceImpl.BATCH_LIST_LIMIT`、`/api/system/dicts/options` 的写法）；前端把 `filterable remote` 收进一个组件（`module-education` 的 `views/components/StudentSelect.vue`、`ClassSelect.vue` / `ClassMultiSelect.vue`），打开时先给一屏、输入时 300ms 防抖。**档案数组不要留在页面/范围 store 里**：`useEducationScope` 只装校区与学期，班级曾经被每一页 `loadScope()` 拉成整机构全量（279 条），现已彻底移除。
- **按需下拉的"表格行只有 id"要有批量回查接口**：排课/考勤/完成度的行 DTO 只带 `classId`，名称不能靠"当前这屏搜索结果"命中。约定是给 options 接口加一个 `ids` 参数（`GET /education/classes/options?ids=1,2,3`、`GET /education/students/options?ids=1,2,3`，逗号分隔 → Spring 自动绑 `List<Long>`，**上限 200、传了 ids 就忽略 `keyword`**），视图在 `rows` 加载后调一次 `ensureClassLabels(institutionId, ids)` / `ensureStudentLabels(institutionId, ids)` 把名称补进缓存。**不要为了显示名字去改分页/报表 DTO 加 `className` 冗余列**——那要动一堆 mapper、测试与响应结构，而名称只在几个界面出现。
- **按需下拉的"已选项"名称必须有本地缓存兜底**：选中项不在当前这批搜索结果里时（换了关键字、弹窗从筛选栏预填、行内动作带进来的 id），`el-select` 收起后会显示成裸 ID。约定是把"见过的 id → 显示名"存进模块级 `Map`（`composables/studentLabels.ts` 的 `rememberStudentOptions`/`studentLabelOf`），组件在拉到选项时写入、宿主在拿到表格行时补写，查不到就回显 `学员 #id`（同「字典回显兜底」口径：脏数据要可见，不硬猜）。**不要把名称当 prop 一层层传**——缓存是唯一事实源，组件自己读，否则每个调用点都要记得传、漏一处就露 ID。显示形态统一带学号（`姓名（学号）`），同名学员才分得开。
- **名称晚补回来时，`el-option` 的 `:key` 必须带上标签**（`:key="`${item.id}-${item.label}`"`）：Element Plus 只在选项实例增减时重算框内显示，key 只写 id 的话同一实例换了 `label` 也不会重算，按 id 补取成功后下拉仍然停在裸 ID（点开一次列表才刷回来）。`ClassSelect`/`StudentSelect`/`ClassMultiSelect` 三个组件统一这么写。
- **档案下拉的宽度由组件自己管，不在宿主页面写**：组件根节点挂 `.archive-select`（`frontend/packages/module-education/src/views/components/`），默认 `width:100%`（弹窗表单占满整行），在 `.filter-bar` / `.page-toolbar-filters` / `.table-filters` 里收成 240px（`FilterBar` 内由 core 的 `.filter-bar-filters .archive-select` 兜住）。**不要退回内联 `style="width:100%"`**——内联优先级压过页面规则，历史上就是这么把班级、学员筛选挤成两行的。
- **校区范围全站只有一个入口：横幅那颗芯片（`module-organization/src/views/components/CampusScopeChip.vue`，经 `registerBannerChips` 登记，core 不认识"校区"这个词）**。状态源是 `useEducationScopeStore`（`stores/educationScope.ts`）的 `campusId`：页面一律 `storeToRefs(scopeStore)` 读它当查询条件，**不要再在自己的 FilterBar 里放校区下拉**（原来那种"页面各选各的、还都不自动触发"的形态已收掉）。**不要把这一项塞进 JWT**：`jp_user_campus_pref` 是 V39 刚从底座用户表迁出的业务列，底座 `AuthenticatedUser`/`JwtTokenService` 不许重新认识校区；而且偏好不是权限，为它调 `invalidateUserSessions` 等于"改一次默认校区就把人踢下线一次"。
  - **`campusId=null` 是「全部校区」这一档，不是"还没选"**：芯片下拉第一项就是它，不收窄、页面显示「全部校区」。它取代了旧的"默认校区 → 第一个校区"静默回落——替用户猜一个校区会把别的校区的行藏掉，而现在"看不看得到"是操作员自己选出来的、看得见的状态。没设过偏好就停在这一档。
  - **初值由芯片的 `onMounted` + 机构 watch 调 `initScope(institutionId)` 落**（拉校区清单 + `GET /organizations/campus-preferences/me`）；同机构并发只跑一趟（in-flight 共用），想要首屏那趟查询按已落定的范围取数就 `await scopeStore.initScope(institutionId)`（工作台是这条参照）。**不要直接给 `campusId` 赋值，也不要把 `applyDefaultCampus` 那套回落加回来**（该方法已删）；换机构的清理走 `onInstitutionReset` → `reset()`，页面自己的机构 watch 只管本页关键字/分页。
  - **改范围只走 `setCampus(id|null)`，它内部 `tabs.refreshAll()`**：页签是 keep-alive 的，失活页面不会补跑 watch，不换 key 重挂就还是上一个校区的数。所以**也别再往 `?campusId=` 传参做跨页同步**（执行页 ↔ 排课管理当年带过，现已删——两页读的是同一个 store，URL 上那份是第二个事实源）。代价是改一次范围全部页签重挂，所以"选中的班级/学员草稿"会丢，这是有意的。
  - **表单里的「归属校区」预填一律走 `prefillCampusId`**（已选校区优先，「全部校区」时回落账号默认校区），不要在页面里再拼一次 `campusId ?? preferredCampusId`。
  - **跟随范围 ≠ 收窄名称字典**：排课三页、课表与点名页的课次查询跟着 `campusId` 走，但 `fetchTeachers` / `fetchClassrooms` / `fetchTimeSlots` 仍按机构全量拉——那三份数组兼作课次行上教师名/教室名/节次名的反查字典，课次可以来自任何校区，收窄会让外校区课次渲染成 `教师 #裸id`（判据见下一条）。教师档案页同理：范围收窄的是"档案列表"，弹窗里那份校区清单永远不收窄。
  - **新建档案落到别的校区、当前范围又收窄着 → `setCampus(created.campusId)` 把范围跟过去**（班级档案、教师档案两处已写），不然用户以为没建成。范围是「全部校区」时不切——那条本来就在结果里。
  - **校区维护页（`/campus`）保存/删除后要 `scopeStore.loadCampuses(institutionId, true)` 强制重取**全站那一份清单，芯片和其余页面的名称回显才不停在旧列表；删掉的正好是当前范围就回落「全部校区」。`/campus-accounts` 是**唯一不接受「全部校区」的页面**（偏好是按校区成组维护的），做法是范围空时把「设为本校区默认」禁用 + 空态提示"请先在顶部把范围收到具体校区"，不额外给一颗校区下拉。
  - **遗留**：偏好表唯一键是 `(user_id, deleted)`、不含机构，所以一人只有一条不分机构的偏好；切到第二个机构时那条属于别的机构，`initScope` 认不出来就停「全部校区」。要"每机构一条"得改唯一键并新增迁移，当前未做。
- **同一份档案数组，"管理页按校区收窄"与"别的页当 id→名称字典不能收窄"是两种用途，不要一视同仁地全传 `campusId`**。时间段是当前的例子：`GET /organizations/time-slots` 的 `campusId` 口径是**本校区 + 机构通用（`campus_id IS NULL`）**，与自动排课引擎取节次的口径一致，所以校区管理页与时间段模板页传它（961 行收成 51~90 行）。但**排课管理页与课表与点名页仍然不传**：`ScheduleRuleListDialog`/`SchedulePlanListDialog` 拿这份数组反查节次名（查不到就渲染 `时段#裸id`），`WeekTimetableGrid` 用它铺周课表的行，这两处的行可以来自任何校区，收窄会让别校区的规则/计划名字塌成裸 ID。**判据：这份数组是"只操作本校区资源的列表"还是"跨校区反查名称的字典"？后者不收窄。** 新增按校区筛的接口时，先把所有调用点按这两类分一遍再决定改不改。
- **主数据编号输入框一律用 `CodeInput`（`packages/core/src/components/CodeInput.vue`），不要再写裸 `el-input`**：`<CodeInput v-model="form.xxxCode" resource="campus" :mode="dialogMode" />`，`resource` 用后端 `CodeRuleContributor` 登记的资源码（campus / classroom / term / time_slot / teacher / student / class / course / course_category / class_type，见 `docs/后端交付说明.md` 的编号规则表）。该资源**启用了**自动编号才会预填建议号、才会出现「重新生成」按钮（`GET /api/system/code-rules/next`，只读不落库），拉不到清单就当普通输入框用。
  - **12 条规则默认全部启用**（教培 `V91`，双轨 core `V110`）。建表时是"不写行=未启用"，结果全站看不见这个功能、被现场当成"编号没实现"，所以按代码登记的默认格式把行种满。**底座迁移只种底座自己登记的 `institution`**——其余资源是行业清单，写进 core 等于把教培的资源表塞给第二个消费方（守护：`ExternalIdVocabularyGuardTest` 扫 core 目录的 SQL）；新项目要开箱有号，在自己项目的迁移里补自己那批。两份都用 `INSERT IGNORE` 撞唯一键，现场改过格式或手动停用过的资源不会被覆盖。
  - **编号必须让客户看得见，不许后端悄悄生成**（用户 2026-09-28 明确否决"留空由服务端补号"："这个编码不是系统后端悄咪咪的生成，要让用户知道是什么，客户会骂娘的"）。所以 `CodeInput` 在**新增弹窗打开时**自己取一个建议号填进输入框（占位文案写成「系统给的建议号，可自己改」，按钮在有值时读「重新生成」），操作员存下去的就是他眼前这个号。**编码一律必填**，页面校验不许再为"启用自动编号"放行空值；请求回来时若客户已经自己打了字，预填**不覆盖**他的输入。服务端 `resolveForCreate` 的"留空即补号"分支只留给**根本没有编号输入框**的入口（小程序自助建档走 `autoCodeIfEnabled`）与批量/模板路径，PC 永远不靠它。
  - **没有 `useOptionalCode` 这个判据了，别把它加回来**：显隐只有一个来源 `useAutoCode(resource)`（`composables/use-code-rules.ts`）——它同时决定"打开时预填"和"要不要显示「重新生成」"。
  - **启用集合是模块级单例，一次请求喂全站**（口径同 `composables/use-branding.ts`）：拉不到就按"全没启用"处理、不重试不弹错——这颗按钮与这条预填都只是快捷入口，没有它页面照样能用；但**绝不能默认全开**，没启用的资源点了必报"XX未启用自动编号"。编号规则维护页保存成功后要调 `invalidateEnabledCodeResources()`，否则各新增页的按钮显隐要重进页面才跟上。
  - **新增带编号的档案页**：前端挂 `CodeInput`（打开即预填、保存前必填）+ 后端 Service 走 `resolveForCreate`，创建 DTO **不要写 `@NotBlank`**（守护：`CodeRuleWiringTest#每个登记资源的创建请求都不许再挂非空校验`；校验落 Service 才能让小程序/批量入口与 PC 入口各走各的判空）。例外是查找键（小程序绑号 `studentNo`）与"编码由别处给定"的入口（时间段模板、教师批量导入），它们不经引擎。

### 应用外壳：横幅 + 导航栏（方案 B，2026-09-25 落地）

结构在 `AppLayout.vue`，样式在 `packages/core/src/styles/index.css` 的「外壳」段，令牌是 `--shell-bg` / `--banner-*` / `--nav-*`。三层，从上到下：

1. `.shell-banner`（**64px，全站唯一一条深色带**，`linear-gradient(105deg, brand-navy, brand-navy-2 46%, #2a5182)` + 44px 网格底纹）：左=品牌（`useBranding()` 的 `brandMark`/`brandName`/`brandTagline`，值来自系统参数 `brand.*`），中=当前页上下文（`.banner-crumb` 来路 + `.banner-title` 页名），右=`.banner-actions`（全局搜索、外观三档 `.banner-theme`、机构胶囊、账号下拉）。下面紧跟 1px `.banner-seam` 金色接缝。**深色只到接缝为止**，导航栏与内容区一律浅色，别再往横幅下面塞深色块。
2. `.shell-body` 是 `grid: var(--nav-col) minmax(0,1fr)`，`--nav-col` 由 `.app-shell.is-rail`（**68px，默认**）/ `.is-full`（232px）决定。**默认图标导航**：判据是横幅已经给出当前页与来路，那 232px 更该留给宽表。档位存 `cornerstone-nav-mode`，在账号下拉里切；`readStoredNavMode()` 只在值恰为 `full` 时才走宽栏，其余（含脏值）回落 rail。
3. `.app-main` 与导航列、横幅**直接拼合**（`margin:0`、无自己的边框/圆角，`padding:10px 16px 12px`），第一行是页签栏。2026-11-19 起不再是浮在 `--shell-bg` 上的卡片——外圈那条缝只占地方，客户明确要求"边边空隙不要了，完全合并上"。

要守的口径：

- **`TableFillBox` 量到的是内容卡里页签行以下那一段**（现状：横幅 64 + 接缝 1 + 内容卡上外边距 0 + 页签行 28 + 页签行下间距 12 + 内容卡上内边距 10）。2026-09-27 的实测基线是内容区 **1294 × 729**；2026-11-19 外壳留白与控件高度收紧后（外边距 14→0、页签下间距 14→12、上内边距 14→10），内容区宽高都会再增加，**回归测量时按当次实测记，不要沿用旧数**。往横幅或页签行里加东西可以，**在它们下面再加一行不行**。
- **主操作区必须免滚轮**（客户反馈：有人的鼠标滚轮是坏的，反馈很不好）。纵向预算、页面分型与处置杠杆统一见下一节「页面纵向预算与分诊」，本页不再重复展开；本节只锁死**外壳的纵向占位**：往横幅或页签行里加东西可以，**在它们下面再加一行不行**。
- **窄栏的二级页靠悬停浮层**（`.rail-popover`）。它必须用 `position: fixed` 且锚在 `.app-nav` 上——`.app-nav` 的 `backdrop-filter` 会创建 containing block，`fixed` 因此相对导航列定位；写成 `absolute` 会被 `overflow` 裁掉、写在全局 `fixed` 又会跟着页签跑。**纵向起点是 `--rail-pop-top`**：由 `AppLayout` 在 `mouseenter`/点击时量出被悬停那一格相对本列顶部的偏移（`trigger.rect.top - .app-nav.rect.top`，并用 `navBox.height - 180` 兜住列底），`max-height` 再用同一个变量截断，所以浮层贴着图标出现。**不要把 top 写回固定值**——早先它是列顶 `14px`，图标越靠下（如「基础资料」）浮层与图标离得越远，看起来像弹错地方。**浮层换宿主时必须重量一次**（`activeRailPopoverKey` 的 watch）：钉住（`pinnedRailKey`）只作回落，显示优先级是**悬停 > 钉住**，否则"点了 A 组再移到 B 组"会出现 top 跟着鼠标走、内容却仍是 A 组；移出导航栏后由钉住接管，那一次没有 `mouseenter` 可量，不重算就会停在 B 组的偏移上。新增 rail 分组时不要动这条定位。
- **悬停不是可达路径**：图标导航必须同时提供 `Ctrl/⌘+K` 全局搜索兜底（`layouts/NavSearchPalette.vue`，数据来自 `registerNavItems` 的授权项，含分组与父级来路，空格分词全命中即匹配）。砍掉这个兜底就等于把二级页藏进只有老手知道的悬停里。
- 机构与账号在深色横幅上做成**浅色胶囊**（`.banner-chip`，`rgba(255,255,255,.94)`），Element Plus 的 `el-select` 塞进芯片里并把 `.el-select__wrapper` 打成透明无边框——直接在深色背景上铺 EP 输入框永远调不清对比度，别改回去。
- 面包屑只装"来路"（分组 / 父目录），末级名已经在 `.banner-title` 上，**不重复第三遍**；顶层项自己就是组名时用 `Set` 去重（`crumbText`、`trailOf` 两处都要去重，`.rail-pop-group` 靠 `v-if` 隐藏同名组头）。
- 横幅上的浅色文字统一走 `--banner-ink-dim`（alpha 0.8）。改浅之前先算对比度：底色取渐变里最浅的那一档 `rgb(42,81,130)` 叠加，正文要 ≥4.5。

### 页面纵向预算与分诊（免滚轮口径，2026-09-27 全站实测后定稿）

起因：现场客户**鼠标滚轮是坏的**，只能拖滚动条，"要滚轮才点到保存"被反馈"很不好"。所以纵向不是审美问题，是可达性问题：**全站按同一套判据分诊、同一套杠杆处置**，不许逐页吵。

**自测判据（一句话）**：鼠标进这一页，不碰滚轮能不能点到"保存 / 新增 / 提交"？能 = 达标；不能 = 要改，改动按下面的杠杆顺序走。

**预算（内容区 = `.app-content`，页签行以下那一段）**：

| 屏幕 | 预算 | 口径 |
| --- | --- | --- |
| 1440×900 | **729px** | **达标线**：主操作区一律排在这里 |
| 1366×768 | **597px** | 允许差一行：保存按钮与它上一行的参数必须可见，再往下可以拖滚动条 |

**页面只有四型，一型一条标准处置**：

| 型 | 长相 | 处置 | 参照 |
| --- | --- | --- | --- |
| **A 单主表页** | 按钮行 + 筛选 + 一张表 + 分页 | `TableFillBox` 吃满剩余，天然达标，**不再讨论** | 17 个页面已如此（教室/节次明细/教师/字典/用户…） |
| **B 表单/设置页** | 一堆参数，一次保存 | 标签与控件同行、分组横排；字段 >12 个分两栏；保存固定在表单尾 | `/time-slots` 的 `TimeSlotConfigForm` |
| **C 多件事堆叠页** | 主档 + 关系表 + 风险 + 另一张关系表 | **按"动作"拆页互跳**，每页一件事 | `/time-slots` ↔ `/time-slot-list`（V82） |
| **D 只读汇总页** | 工作台、总览、日课表、报表 | **允许滚，但每个块自己封顶**（定高内滚 / 只给前 N 条 + "查看全部"），首屏那块要能做出下一步动作 | `/schedule-completeness`、`/system/permission-overview` |

C 与 D 的边界判据是**"这几个块有没有非看不可的关联"**：改完主档马上要核对主讲 → 同页；只在"给某个学员打电话"时才看家长联系人 → 拆走。**不要因为"它们是同一张表的数据"就同屏**，那是后端视角。

**四级杠杆（省不出预算只许往下走一级，不许反过来）**：

1. **压排版**：标签同行、去掉重复行标题、矩阵行头压成单行、单元格 `padding: 2px 6px`（十行从 570 收到 330）——一般省 100~250px。
2. **收内容**：未选上游时**整卡收成一行提示**（空白卡照样占 48~195px）、长列表只给前 N 条 + "查看全部"跳转、动态区 `max-height` 内滚——省 150~700px。
3. **拆页互跳**：真的两件事才拆。**`el-collapse` 折叠不算解决**（展开照样逼出滚轮，用户明确退回过一次）。拆完两页顶上一颗互跳按钮，跳转把共用的 `educationScope` 校区范围带过去，落地不许停在别的校区；**菜单节点要一起授权**，只勾一边就是死链（写法见 V82：新页面节点挂读码、写码沿用既有按钮节点、`SELECT DISTINCT` 从兄弟节点带授权）。
4. 拆完还超 → 承认装不下，把入口做成跳转，而不是让用户在长页里找按钮。

**反复出现的毛病 → 统一处置**（同类一律同一改法，不要个别页先自由发挥）：

| 毛病 | 出现页 | 规则 |
| --- | --- | --- |
| 筛选栏控件挤成两行（140px） | `/classes` `/students` `/schedule` | 根因是档案下拉组件自带 `style="width:100%"`，在筛选栏里独占一行；宽度收在组件的 `.archive-select` 上（筛选栏 240px、弹窗表单仍 100%）。`FilterBar` 一行最多 4 个控件（`collapseAt`），第 5 个起自动折叠成「展开（N）」；折行说明这屏混了两类筛选，次级的那个该跟着拆走 |
| 卡片标题下又一行按钮、又一行说明 | `/classes`（三处）、`/campus` | 一屏每张卡最多 1 行标题 + 1 行按钮：**标题行只放标题与动态回显，按钮一律落在下一行的 `.table-toolbar`**（旧写法"主操作并进 `.education-table-head` 右侧"作废——那让 `/courses` 与 `/campus`、`/system/dicts` 长成两种样子，客户直接指认"没按规范"）。例外仍是"选中上游才长出来的工作区动作行"（班级主档），同行是为了不产生空行 |
| 标题离按钮一大截、按钮又顶着表格 | `/courses` `/campus` `/system/dicts`（2026-09-29 统一前） | 根因是每张卡自己写了一套 `gap`（16 / 12 / 卡片压根不是 flex 的 0），再叠 `.panel-title` 在 flex 行里那条 16px 下边距（行高被撑到 43px、标题比同排按钮高半截）。一律走 `--card-flow-gap`(12px)，**页面不许再覆盖 `gap`/`padding`**；按钮尺寸同理全站两档——卡内工具栏与行内操作用默认尺寸（高 32），`size="small"` 只留给弹窗表格行、树行内箭头一类密集控件（改前只有字典页用错） |
| 依赖未选的上游、空卡照样占位 | `/classes` 班级主档 / 家长联系人 | 未选中时不渲染整卡，给一行提示，选中才铺开 |
| 页面总高随数据量长 | `/schedule`（1414~2726）、`/dashboard`（3388） | 任何列表/明细块必须有 `max-height`，内部自己滚，页面总高不许被数据顶开 |
| 同一句上下文在每个卡头各写一遍 | `/campus`（三张卡各写「当前机构：X」）、`/classrooms` | 页面级信息（机构、来路、口径）只在页面按钮行右侧写一次；卡头 `.education-table-head` 只留卡标题 + 卡内动作。纯回显不了数据的开关（如校区页的「统计」展开条）直接删，它占一行却没有任何信息是别处看不到的 |
| 同类并列卡竖排 | `/campus`（曾 6 张 3606px，V85 拆走课程三张成 1156，V86 再把默认账号拆走） | 同类卡两列并排；跨类（校区学期 vs 课程班型 vs 账号归属）必须换页。**两列并排时不要用 core 的 `.resource-grid`**，它给末张卡 `grid-column:1/-1`（整行铺开）又给表体钉 398px 十行保底，两张卡照它排反而装不进 729；页面自定 grid（参照 `/campus` 的 `.campus-grid`），并写 `grid-template-rows:minmax(0,1fr)`，否则行高由内容撑开、`TableFillBox` 量不到剩余空间。**并排不是目的，"同屏"才是**：辅助卡只有在"主表显示它 / 主表弹窗选它"时才同屏——V87 把 `/courses` 的班型卡拆成 `/class-types` 就是这条（课程表没有班型列、课程弹窗没有班型字段，班型的消费方是班级档案；两张卡并排各封顶三行、谁都看不全）。剩下的课程分类是课程表的列 + 弹窗下拉，所以留在同屏并独占整行补出备注列 |

**紧凑密度约定（2026-11-19 客户两轮反馈收口，新增页面一律照此写）**：

- **一行能说完的就不许两行**：分区/卡片头的标题、动态回显、一句说明排在同一行（`align-items:baseline`），容器放不下用 `white-space:nowrap + text-overflow:ellipsis` 截断并给 `title` 兜全文——**不许为了放说明折行，也不许把说明另起一行**。`/schedule`（课表与点名）的「今日执行分区」就是反例改完的参照。
- **空态不许独立成块**：表格空数据一律走 `el-table` 的 `:empty-text`（文案写在表体中央，加载中传 `''` 防闪烁），表格外**不再渲染单独的空态行**；非表格列表/卡片用一行小字（`padding:2px 0`、12.5px），不许再铺一条带底色的横幅。
- **表格不再套第二层卡**：`.app-main` 已是整块白底内容区（`margin:0`、无边框/圆角），列表页的 `.panel-card.education-panel` 与 `/courses` 的分类卡 `.course-category-card` 一律去边框、圆角、阴影并 `padding:0`，表格与上方工具栏同轴；**新增页面不要再用 `panel-card` 把表格圈起来**。
- **控件按场景分三档**：工具栏/筛选栏的按钮与输入框 **28px**、分页条 **26px**、弹窗表单与正文 **32px（EP 默认）**；`size="small"` 仍只留给密集的行内控件。新加的工具栏控件若用了默认 32px，就是这一条没遵守。
- **统计卡不做大块**：`.stat-card` 只有紧凑一档（`padding:8px 12px`、标签与数值同行、数值 20~22px、说明整行 12px），整卡约 55~60px；统计条是辅助信息，不许再按 30px 数值 + 20px 内边距铺成 110px 高的卡片，也不要为某个统计页单独放大（页面级覆盖同样走这一档）。
- **自测线**：1440×900 首屏必须看到表头 + 至少 4~5 行数据；按钮行、说明行、空态行这些"页面外壳"合计不许超过 2 行高度。超出先按四级杠杆压排版，不要先动表格行高。

**量高探针**（内置浏览器只有 568×553 的可视面，没法真开 1440 窗口）：`evaluate_script` 注入 `.app-shell{width:1440px !important; height:900px !important} body{min-width:0 !important}`，此后 `.app-content` ≈ **1286×729**（即 1440×900 的内容区）。**别用 `html{zoom}` 放大模拟**——zoom 只放大布局盒，媒体查询仍按未缩放的 `window.innerWidth`（568）判定，于是 core 的 `@media (max-width:1280px)`（`.education-table-head` 折成竖排、`.dashboard-grid` 收成两列）与页面自己的窄屏档全部命中，量出来的总高全是"降级版式"，比真实 1440 高一截（`/classes` 曾被这个假信号量成 1140px 的两卡行）。**代价：强制宽度后媒体查询依旧按 568 判定**，所以被这些窄屏档改过版式的块要额外用 `!important` 把宽屏那档的规则原样补一遍再量（`/classes` 要补 `.education-table-head{flex-direction:row}`、`.class-profile-grid{两列 + max-height}`、`.class-profile-summary{三列}`）。**要补的断点不止 1280**：core 的 `.resource-grid` 在 `@media (max-width:1400px)` 收成单列，V85 量 `/campus` 时没补这条，三张卡量成 1742（单列），补上两列才是真实的 1156。凡是量多列卡片页，先把 `.resource-grid{grid-template-columns:repeat(2,minmax(0,1fr)) !important}` 加上（页面自定的 `.course-aux-grid`/`.campus-grid` 同理，它们各自还有 `≤1280`/`≤1400` 的单列档，不补就会把并排卡量成竖叠、总高虚高 350px 以上）。**同理，按钮行的右端对齐也要补**：`.table-toolbar > .note-hint`/`.education-table-head > .note-hint` 的 `margin-left:auto` 与 `.education-table-head{flex-direction:row}` 在窄屏档里被改成 `margin-left:0` + 竖排，不补 `!important` 就会量成"问号说明没贴右端"的假缺陷（2026-09-28 这两条各骗过我一次）。另外四条陷阱：① `getBoundingClientRect()` 与 `offsetHeight` 同一坐标基准，别再叠 zoom；② `evaluate_script` 有 **15 秒超时**，一次最多跳 2~3 个路由、每页等 1000~1200ms，循环更多页就分批调用；③ 取路由用 `document.querySelector('#app').__vue_app__.config.globalProperties.$router`，`router.push()` 是 SPA 跳转（keep-alive 与探针样式都保得住），`navigate_page` 是整页重载（会话与缓存全清）。④ **这个内置浏览器页的 `document.visibilityState` 恒为 `hidden`，`ResizeObserver` 回调与 `requestAnimationFrame` 一次都不跑**（实测 `{roFired:false, rafWorks:false}`）。因此两种"看起来是 bug"的现象不必去修：`TableFillBox` 在探针里停在首次同步量到的尺寸、盒子里的 `el-table` 高度比盒子小一截（V 型布局变了也不会重测），以及 `el-dialog` 点了取消后遮罩仍留在 DOM（Vue 的过渡要 nextFrame，rAF 不回来，离场动画卡住）。**判据：一律读组件 `setupState` 或查库确认真实状态，别拿 DOM 上的动画残留当功能坏了**（`section.__vueParentComponent.setupState` 在 dev 构建可直接取）。测完移除探针 style 节点。

**现状基线（2026-09-28 复测；2026-11-21 V109 后 `/enrollments` 下线，达标页数由 25 降为 24，回归时对照）**：达标 24 页 = `/classrooms` `/time-slots` `/time-slot-list` `/teachers` `/scheduling` `/schedule-completeness` `/announcements` **`/courses` 729（V87：班型卡拆走，课程分类卡独占整行 = 229、主表 480，表体 245≈6.8 行；2026-09-29 两行表头 + 统一节奏后为 分类卡 271、主表 440、表体 200≈4.5 行，整页仍 729）** **`/class-types` 729（A 型单主表，表体 603≈16 行，9 条班型一屏看全）** **`/classes` 240+309→729（档案两卡封顶 280）** **`/class-roster` 729（V109 起报名并入本页，新增入班即报名）** **`/students`（学员档案）729（V84 把原来 1401px 的三张主表拆成两页，家长联系人进抽屉；V109 起报名页下线，本页行上「在班」跳名册）** **`/campus` 729、`/campus-accounts` 729（V86：校区默认账号独立成页，校区与学期两卡两列并排，页面自定 `.campus-grid` 绕开 core 的 398px 十行保底）** + `/system/` 的 users roles menus dicts config data-sources invitations list-configs operlogs permission-overview。超预算 3 页 = `/dashboard` **3144**、`/schedule`（课表与点名）**1434**、`/system/institution` **1155**（最后这页只有一个保存，按 D 型豁免）。`/classes` 与 `/class-roster` 在 1366×768（预算 597）仍差约 130px，与时段模板页同档：一屏要放筛选 + 两卡 + 主表，砍到 597 就得把风险卡收进弹窗，暂不做。**同日按「按钮行 → 筛选行 → 表格」重排了 10 页**（公告中心、课程档案、校区与学期、教室、时段模板、节次明细、教师档案、排课管理、课表与点名、课时完成度 + 工作台），全站已无右端按钮；`core/styles` 的 `.page-toolbar-filters`、`.page-toolbar-actions` 两条随 `.table-filters` 一起成为无引用样式（`.table-filters` 为下游兼容保留，这两条本仓库与 achievement 均无使用方，已删）。

### 外观档与配色令牌（明亮 / 夜晚 / 跟随系统）

状态源只有一份：`packages/core/src/composables/use-theme.ts`（模块级单例，`import` 即按本机存档把 `html.dark` + `color-scheme` 刷上，不等组件 `onMounted`，所以首帧不闪）。登录页与外壳读的是同一个 `isDark`，切档内外联动。要守的：

- **取色一律走令牌，业务页不许写死浅色字面量**。`:root` 里那组语义令牌在夜档整组翻：`--page-bg` / `--shell-bg` / `--card-bg` / `--subtle-bg` / `--hover-bg` / `--tint-bg` / `--surface` / `--nav-*` / `--text-primary|-secondary|-muted|-faint|-strong` / `--tint-ink` / `--accent` / `--border` / `--line` / `--shadow-*` / `--table-*` / `--soft-{warn,danger,success,info}-{bg,ink}`。写死 `#fff`、`#fdf3cf`、`#64748b` 这类值，夜档要么是白块要么是深字压深底，**软色状态块尤其要走 `--soft-*-bg` + `--soft-*-ink` 成对取值**。
- **两组常量故意不随主题翻**：横幅的 `--banner-*` / `--chip-*`（深色带上的浅色胶囊，翻了就成"深底深字"），认证页的 `--login-*`（自己有明暗两套，靠根上的 `is-light` 覆盖）。新增样式别挪用这两组，也不要给它们加 `html.dark` 分支。
- **`html.dark` 覆写 EP 的引入顺序是硬约束**：`frontend/src/main.ts` 里 `element-plus/theme-chalk/dark/css-vars.css` 必须**早于** `@cornerstone/core/styles/index.css`。同为 `html.dark` 选择器时靠后者取胜，调个顺序暗色就整片回落成 EP 的纯灰皮。
- **夜档的实心主按钮是"亮蓝底 + 近黑字"，而且必须限定在 `.app-shell` 内**：单个主色不可能同时满足"白字压在实心主色上 ≥4.5"和"主色当文字压在深卡上 ≥4.5"（要 L≤0.183 与要 L≥0.232 互斥），夜档里主色当文字的地方更多，所以主色提亮、实心按钮反过来压深字。认证卡的主按钮自带 `--login-*` 深蓝渐变（两档都是深底白字），漏掉 `.app-shell` 限定会把它的字也压成近黑。
- **新增 `html.dark` 下的语义令牌时，同时检查 `:root` 有没有对应项**；只在一处写等于另一档拿到 `unset`。改配色后自查：内置浏览器无可视表面，用 `evaluate_script` 遍历文本节点、按 `getComputedStyle` 沿父链叠出有效背景算 WCAG 相对亮度比，`14px` 以下要 ≥4.5（`.is-disabled` / 占位符 / 灰字这类非活动组件按 1.4.3 豁免）。

### 列表页与表格视图统一规范

- **表体高度用 `TableFillBox`**（`packages/core/src/components/TableFillBox.vue`）：它自量容器高度、以**真实像素**传给默认插槽的 `:height`。手写页面的表格包一层 `<TableFillBox v-slot="{ height }">`，配置化列表已内置。**不要再给主列表传 `height="100%"`**：Element Plus 会把 `.el-table__inner-wrapper` 锁成 `bodyScrollHeight`，而父链只有 `min-height`（百分比按 auto 解析），结果是一两行数据时表体缩到内容高度、横向滚动条贴在最后一行下面而不是列表底部。弹窗内/Tab 叠放的次要表格继续用 `utils/table-view.ts` 的 `TABLE_VIEWPORT_MAX_HEIGHT`（6 行）/`TABLE_STACK_MAX_HEIGHT`（10 行）封顶。
- **宽表的读行定位（不开斑马纹）**：主列表一律 `border row-key="id" highlight-current-row`，并把身份列（通常是第一列）`fixed="left"` 固定，滚动看后面列时行身份与当前行不丢。**不加 `stripe`**——表头全站固定居中之后，竖线（`border`）已经把表切成一格一格，再叠斑马纹就成了小房间格纹，排课页那种 9~12 列的宽表最明显；行节奏交给「悬停整行 + 当前行左边线」这两条更强的信号。判据：**同一张表要么靠竖线读列、要么靠斑马读行，不要两个都要。** 配置化列表走 `ListColumnConfig.fixed`（`'left'|'right'`，对应 `sys_list_config.columns_json` 的 `fixed` 键），列表配置编辑器有「固定列」下拉，后端 `ListConfigServiceImpl.validateColumn` 只放行 `left`/`right`/不填。
- **数据格对齐（量值右对齐）**：表头全站固定居中（`styles/index.css` 那条 `th.el-table__cell { text-align:center }`），所以对齐只作用于数据格。判据是"**这列的数是不是拿来比大小/求和的**"——人数、课时、时长、容量、排序号、耗时、金额一律 `align="right"`（位数从右对齐才看得出量级）；**标识符不对齐**：id、手机号、微信号、编码、学年是"抄下来拨出去 / 逐字核对"的字符串，右对齐反而难核，保持默认左对齐。行号这类序号也走 right（要比较先后）。手写页面直接在 `<el-table-column>` 上写 `align="right"`；配置化列表走 `sys_list_config.columns_json` 的 `align` 键（`ListColumnConfig.align`，`ConfigurableList` 透传），列表配置编辑器有「对齐」下拉，后端 `ListConfigServiceImpl.validateColumn` 只放行 `left`/`center`/`right`/不填。**已知代价**：表头居中 + 数字右对齐会让标题与数字错开半格，这是这个组合固有的，不额外补偿（补偿方案是悬停整列淡底，属可选增强）。
- **默认不加勾选列**：本仓库后端**没有任何批量写接口**，行内操作（删除/启停）都是单行且可逆，加 checkbox 属于伪需求。出现真批量端点时再全系统统一加 `type="selection"`，不要个别页面先加。选中态现在的用途是"钉住正在看的行"，不是批量入口。
- **表格上方的顺序固定为「按钮行 → 筛选行 → 表格」**：第一行 `.table-toolbar` = 本页全部按钮（**一律靠左**）+ 说明或当前范围回显（靠右）；第二行 `FilterBar` = 筛选控件（左）+「查询」「重置」（右，固定排在行末）。**按钮不许出现在行右端，也不许塞进筛选行的 `#extra`**——`#extra` 只留给"这一屏一颗按钮都没有"的说明（如操作日志的脱敏提示）。一屏多表的页面每张表各有一组表头，**卡内固定两行**：`.education-table-head` 只放标题（+ 靠右的动态回显/问号说明），按钮另起一行 `.table-toolbar`（`.education-table-head` 是 `justify-content:flex-start`，不许用 `space-between` 把按钮顶到右边）。**按钮不许与标题同行**（2026-09-29 客户口径：`/courses` 的「新增分类」贴在标题右边被指"没按规范"）；唯一例外是"选中上游才长出来的工作区动作行"（班级主档一类），它同行是为了不产生空行。
- **按钮行内部以「圆形刷新」为分隔位**（用户 2026-09-28 定稿，判据是**点下去会不会离开这一页**）：`[:icon="Refresh" circle]` 左边 = 留在本页的动作（新增/编辑/删除/导入/导出/统计开关/**打开弹窗**的入口，主按钮 `type="primary"` 排最前），右边 = 会跳去别处的按钮（`router.push` 的互跳入口，如「分班与名册」「查看课表」「节次明细」「校区默认账号」）。用户就靠这颗圈圈判断"哪些按钮会把带走"。三条推论：① **每页都必须有这颗圈圈**，包括 `ConfigurableList`（刷新按钮写在它的 `.table-toolbar` 里，绑 `fetchPage`，所以配置化列表页的按钮行恒渲染、不再判 `toolbarActions.length || note`）与只读页（权限总览的「刷新」、机构资料的「刷新机构资料」都换成圈圈）；② **弹窗入口算"留在本页"，放左边**（`模板维护`/`周期计划`/`排课规则`/`一键补排`），只有真正换路由的才放右边；③ 页面只有跳转、没有本页动作时（工作台、校区页）圈圈放最前，跳转按钮紧随其后。例外仍是「选中上游才出现的工作区动作行」（时段模板的生成/保存、校区默认账号的设为本校区默认、校历面板）与弹窗内工具条——它们不进按钮行这套排序，但**内嵌面板自己的刷新按钮保持原样**（教师匹配账号弹窗的「重新匹配」是动作而非重载整页）。
- **筛选栏统一用 `FilterBar`（`packages/core/src/components/FilterBar.vue`），一行、只有按钮触发查询**：栏内左=筛选控件、右=「查询」「重置」。**`@change`/`@clear` 一律不许绑查询**（原来"选一个发一次请求"是三种落点混出来的根因），输入框只保留 `@keyup.enter`；`resetFilters()` 清空本页控件后立即生效并保留分页 size。超过 `collapseAt`（默认 4）个自动收成一行、给「展开（N）」，展开只撑 `.filter-bar-filters`，右侧按钮不跟着下沉。**控件放默认插槽，一个控件 = 一个顶层子节点**：Vue 3 不允许重复具名插槽，所以**不是** `#item` 可重复插槽（`v-for` 的 Fragment 会被摊开逐个计数）；"标题 + 控件"并排时两者必须包在同一个 `<span class="filter-bar-group">` 里，否则标题被当成一个控件多算一次、折叠点也错。保持自动触发的**两类例外**：① 日期前后翻页条（排课/执行页的「前一天/下一周」是导航不是筛选条件，点了就查合理）；② 弹窗内小工具条与只读面板（`SchedulePlanListDialog`/`ScheduleRuleListDialog`、校历的年份切换），没有"改完再决定"的语义。`ConfigurableList` 的查询区已换成它，`queryFields` 一律渲染成 `.filter-bar-group`。`.table-filters` 的 markup 全站不再使用（core 样式为下游兼容保留，新页面不要再写）。整页级开关（统计条展开）与页面跳转按钮同在按钮行；卡片内搜索框不挂标题右侧（`.panel-head-actions` 已删）；行内操作仍在操作列用 `.table-actions`，不参与本条。另有第三类保持原位：**选中上游才出现的工作区动作行**（时段模板的 生成/添加节次/保存、校区默认账号的 选账号 + 设为本校区默认），它们不是列表的查询条件，跟着自己的表走。
- **上游选择器本身也不进 `FilterBar`，而且必须「选中即载入」**：它回答的是"我要编辑哪一个"，不是"筛掉哪些行"。塞进 `FilterBar` 就意味着要点「查询」才生效，而点之前编辑器里是**默认参数 + 空网格**，客户会读成"这个模板底下没有节次"、于是又新建一个模板（`/time-slots` 的现场反馈就是这么来的，已改成下拉 `@change` 直接载入，整页没有查询条件也就不再渲染 `FilterBar`，形态是「按钮行 → 选择器行 → 编辑器」）。配套的口径：**还原出来的网格要说清来源**，已存过节次显示节次数与「改动点保存生效」，没存过就显示"这是按参数生成的、点保存才写入"——不能让"草稿"和"空"长得一样。
- **标题与条数都不重复展示**：横幅左侧的大标题显示的就是当前菜单节点名（`AppLayout.vue` 的 `.banner-title`，原来的 `h1.page-title` 已随多页签删除），所以**以页面为单位**判断——一屏只有一张主表的页面不渲染 `.panel-title`（连同空掉的 `.education-table-head` 一起删，配置化列表页由 `ConfigurableCrudPage` 统一不渲染）；一屏并排/叠放多张表的页面每张表必须保留标题作区分（校区页两张卡、课程档案页的两张辅助卡 + 主表、学员页三张表、时间段编辑器的两个面板都是）。条数全站不显示：分页表看分页器的总数，不分页的表都是刻意选定的小体量表（判据见「前端 / 小程序约定」分页条目）。`.panel-count` 类已删，唯一例外是编辑器里的规模回显（如时间段"N 个节次 × M 天"），那种要并进 `.education-context-text` 而不是复活计数类。`page_json` 的 `title`/`showCount`/`countFormat` 三键随之前端渲染一起删除（V49 清存量），**界面不渲染的配置键一律不留**。
- **卡内纵向节奏只有一个数：`--card-flow-gap`（8px，`core/src/styles/index.css` 的 `:root`；2026-11-19 由 12px 收紧）**。标题行 → 按钮行 → 筛选行 → 表格，每一步都用它，`.education-panel` 与 `.resource-card` 的 `gap` 都指向这颗 token。页面**不许再自己写 `gap:` / `padding:` 覆盖**：2026-09-29 统一前 `/courses` 的分类卡自带 `gap:12 + padding:14 16`、`/campus` 用 16、`/system/dicts` 的两张卡干脆不是 flex（工具栏与表格贴死、标题却又自带 16px 下边距），三页三种节奏，客户看到的就是"有的页标题离按钮一大截、有的页按钮顶着表格"。配套一条：`.education-table-head > .panel-title { margin-bottom: 0 }`——`.panel-title` 那 16px 下边距只属于"标题自己就是卡片首行"的静态块，放进 flex 行会把行高凭空撑到 43px、标题还比同排按钮高半截（这一条同时修掉了 `/classes`、`/schedule` 那种"标题与按钮不在一条线上"）。
- **弹窗的位置与尺寸不写在模板里**：全站 `el-dialog` 由壳工程 `src/main.ts` 统一开 `dialog.draggable`（拖标题移动），右下角拖拽改尺寸由 `packages/core/src/utils/dialog-resize.ts` 全局接管（按坐标认右下角 16px 热区，抓手样式是 core 的 `.el-dialog::after`）。**新增弹窗不要再单独写 `draggable`，也不要自己实现缩放**；全屏与 `align-center` 弹窗不参与缩放。拖拽后的尺寸写成弹窗元素的内联 `width/height/margin-left`，Element Plus 关闭弹窗只是隐藏、不销毁元素，所以同一实例重开会保留上次尺寸；`destroy-on-close` 的弹窗会回到模板尺寸（无持久化，本机刷新即重置）。
- **按钮尺寸按场景分三档（2026-11-19 起）**：工具栏（`.table-toolbar` / `.page-toolbar` / `.education-table-head`）与 `FilterBar` 里的按钮、输入框统一 **28px**（由 core 样式收口，页面不要再自己写高度），分页条 **26px**，弹窗表单与正文用 **默认 32px**；`size="small"` 只留给弹窗表格行、树行内上下箭头这类密集控件。行内操作用 `.system-operation-group` 包一层。新增工具栏控件若用了默认 32px，就是没遵守这条。
- **说明文案落点与口径**：**说明永远落在按钮行的右侧**（`.table-toolbar` 或 `.education-table-head` 的最后一个子元素，class 仍是 `.education-context-text`；`styles/index.css` 有 `.table-toolbar > .education-context-text { flex:1 1 auto; min-width:0; text-align:right }` 与 `.education-table-head > .education-context-text` 同一条）。**单独占一行的说明一律不许出现**：整段口径说明收进问号浮层 `NoteHint`（`packages/core/src/components/NoteHint.vue`，悬停展开、`label` 给短语、`width` 控宽），公告中心与课时完成度已按这条改。**这一屏一颗按钮都没有**时（只读表、弹窗内说明），短句才用 `FilterBar` 的 `#extra` 挂右侧——此时必须有 `.filter-bar-tail > .education-context-text { flex:0 1 auto; min-width:180px; text-align:right }` 这条：写成 `flex-shrink:0` 时长文案会把 `.filter-bar-filters` 挤到 518/1250px、筛选控件被迫折两行。这样同屏从「说明 → 按钮 → 筛选」三行收成两行，省下的那一行给表格。**卡片标题下的副行动态回显**（班级详情三张表）仍允许；**"副行"只在动态回显时才允许**：校区页当年三张卡各写一行「当前机构：X」属于把页面级信息重复三遍，V86 已删成卡标题 + 卡内动作一行。配置化列表走这条：`ConfigurableCrudPage` 把 `page_json.description` 作为 `note` prop 交给 `ConfigurableList`，由它在自己的按钮行右侧渲染，宿主页面不再自己铺一行。口径：**只写"不看就会用错"或"改完怎么生效"**，例如"在角色管理的「分配菜单」里勾选按钮即授权""保存后需重新登录生效""敏感信息已脱敏展示"。删掉的是两类：把按钮名字复述一遍的（"支持创建、编辑、启停"）、以及功能说明伪装成指引的（"邀请码供新账号自助注册；激活码供预建账号自设密码启用""维护全局字典类型与条目"）。**动态回显不是文案**——"当前机构：X""当前模板：Y"是数据，一律保留，同样落按钮行右侧。`page_json.description` 同理（V48 清掉 users/roles 两条，V49 收掉壳配置三键）。
- **列表排序（单列，全站口径）**：判据是"**该列只展示一个字段 ∩ 后端白名单里有这个键**"，两者缺一就不加表头箭头。列在展示但后端白名单没有——**扩白名单，不搞页面特例**（如 `lessonWeekday`、排课规则的 `remark`）。
  - **后端**：分页查询 DTO 一律 `extends PageQuery`（自带 `sortField`/`sortOrder`），Service 里持有 `Map<String, SFunction<T, ?>>` 白名单并调 `PageSortSupport.apply(wrapper, query, sortableMap, Entity::getId)`；返回 `false` 表示"客户端没排序"，调用方继续走自己的默认序。客户端字符串只当 map 的键用，**绝不拼进 ORDER BY**；主键兜底 tiebreaker 保证同值行分页不重不漏。`PageSortSupportTest` 拦住非白名单字段与非法 `sortOrder`。**新增分页接口不接这条链路 = 静默不能排序**（表头点了没反应，前端也不知道后端缺白名单），落库前自查 `PageSortSupport.apply` 调用数是否等于 `extends PageQuery` 数。报错文案把可选值列出来（`不支持的排序字段：X，可选值：a、b、c`），操作员不用猜。
  - **前端**：服务端分页表用 `sortable="custom"` + `composables/use-table-sort.ts`（`useTableSort`，'descending'→'desc' 的映射、无序时不传参、`clearSort()` 回调三件事只写一份），请求参数展开 `...sort.sortParams()`；**插槽列也要写 `prop`**，否则 `sort-change` 的 payload 拿不到字段名。一次性全量加载的小表（校区页两张卡、课程档案页的分类与班型两张辅助卡、班级详情三张表、日课表、字典/数据源/列表配置管理页）用 Element Plus 原生 `sortable` 走本地比较，**不发请求**。`destroy-on-close` 的弹窗重开时表头箭头会消失，所以 `handleOpen()` 里要 `resetSort()`；常驻页面的表额外传 `clearTableSort`，让清筛选按钮一并清掉排序状态。**树表（菜单管理、功能权限总览）与矩阵表（课时完成度的动态周列）不加排序**——行序即层级/透视结构。
  - **配置化列表**：`sys_list_config.columns_json` 的列加 `sortable: true`，`ListConfigServiceImpl.validateColumn` 只放行真布尔（非 bool 直接 `BAD_REQUEST`，不接受字符串 `"true"`）。`ConfigurableList` 按该键渲染 `sortable="custom"` 并把排序参数并进请求。改底座持有的那几行配置要双轨各写一份迁移且内容一字不差（教培 `V70` + core `V106`），且**用 `JSON_SET(columns_json,'$[n].sortable',TRUE)` 按索引精修**，不要整值覆盖——两份基线的 `columns_json` 存量并不完全相同，整值覆盖会让下游漂移。
  - **表头排序指示器的形状与摆位**（`styles/index.css` 的「排序指示器」段）：形状是一枚紧凑的 **⇅——上下两个 1px 细线尖对（各 9×5、中间 2px 缝），不带长杆，也不是 EP 的实心三角**（用户 2026-09-26 两轮口径："细的、不要三角形" + "不要太明显，有个地方能看出来字段能排序就够了"；这也是 shadcn / lucide `ChevronsUpDown` 的通用做法）。用 `mask-image` 画而不是 border：描边宽度与拐点精确到像素，viewBox 与元素同为 9×5 所以 `mask-size: contain` 即 1:1 落位。拐点画在 **x=4.5**（1px 描边压在 4..5 这一整列上才是实线，压在 4.5..5.5 会跨住像素边界、渲成两列半透明灰、看着像 2px 的糊线），两端收在 x=0.5 / 8.5 不外溢。**摆位靠两件事配平**：`.is-sortable` 的 `.cell` 右侧多留内边距（不对称内边距让标题轴左移一半的量），箭头盒 9px、`margin-left: 3px`、`margin-right: -22px`（行盒净占位 3+9-22 = −10px，正好把标题推回列中心），再加 `translateY(-2.3px)` 与标题共轴——EP 原本 24px 宽的 `.caret-wrapper` 会把标题推离列中心 12.5px，而绝对定位挂到列右边在竖线一开时读起来像**下一列**的符号（外部口径同样是"chevron 别干扰表头与列内容的对齐"）。**裁切线余量 = 列宽/2 − 文字宽/2 − 左外边距 − 盒宽，与 padding-right 无关**（加内边距只是把整行连同裁切线一起左挪），所以列宽安全线是 `列宽 ≥ 表头文字宽 + 24`，实库里最紧的是 90px 的「每页条数」（余量 +6.5）。盒高 12 仍在 `line-height: 23` 之内（上下各余 5.5），**加高箭头前先确认这个数不为负**。负边距是这套摆位的唯一支点，**改内边距或箭头宽度必须同步改 `margin-right`**；复测在 `docs/mockups/sort-header-check.html`（真 EP CSS，页面底部把摆位数打印出来，还会把两支 mask 画进 canvas 数像素）。当前基线（两档主题一致）：列中心偏移 −0.5px、标题与箭头净间隙 3.0px、**静置⇅与生效单支的"栈中心差"都是 0**、65px 最窄列余量 +7.0、表头行高 34px。
  - **三态语义要能被看见**：第三击回到无序 = 不传排序参数、由后端走自己的默认排序，此时表头什么都不留，光看表格无法确认"现在到底按什么排"。所以排序一旦生效，除了列头标题变主色还给**整列一层淡底**（`sort.cellClass` 出的 `col-sorted`）。淡底的优先级靠声明顺序，不用 `!important`：**悬停 > 选中行 > 排序列**（选中行是"钉住正在看的行"，比列提示更该显眼）。
  - **箭头颜色只有三档，都不许转主色**：静置用 `--table-sort-idle`（明亮 `#818c9f`、夜档 `#667589`，压在 `--table-head-bg` 上实测 3.22 / 3.20，刚过 WCAG 图形 3:1 线——不抢眼但"这列能点"看得见）；鼠标进这一列（`th.is-sortable:hover`）或排序生效时升到 `--table-head-ink`（5.13 / 7.14），`transition: color .12s`。**别改回 EP 默认的 `--el-text-color-placeholder`**（表头底上只有 2.18:1，等于没有信号）；**也不许给生效那支涂主色**（用户原话"不要亮了"），"正在按这列排"由标题主色 + 整列淡底承担，箭头只管方向。两档 idle 是各解一遍对比度得到的颜色，**不是同一个透明度**——深色底上照抄透明度会掉到 3:1 以下。
  - **方向只由形状表达，且常驻不许空**：静置 ⇅ 两支都在；排序生效后**反方向那支 `visibility: hidden`**（槽位留着、标题一格不跳），留下的那支挪到槽位正中（`(12-5)/2 = 3.5px`）与标题共轴；第三击两支都回来。**常驻可以淡，但不许空**——"图标只在悬停时出现"（MUI `hideSortIcon`、Carbon 旧实现）正是被外部投诉的那条："a sortable table is indistinguishable from a non-sortable table until a user hovers"，触屏与键盘用户无从得知，Carbon 官方因此拍板改回常驻指示器。**这条形态是拍板过的，别再往"加字 / 加颜色"方向改回去**，同一诉求四轮里被依次退回：整句常驻（判"常显太丑"）→ 小胶囊（仍占分页行）→ 悬停文字气泡（"移除掉 不要悬停提示"，`SortHeaderHint.vue` 已删）→ **纯形状**。判据：**信号能用形状自己扛就到此为止，不落成文字，也不给形状再叠一层颜色。** 实现是纯 CSS，视图与 `useTableSort` 零接线，一次性全量加载的小表与弹窗里的表自动同形态。已知缺口：第三击回到后端默认序后界面不说现在按什么排，要补得让每个分页接口报出自己的默认序，属另一批。

### 多页签与页面缓存（keep-alive）

内容区第一行是页签栏（`packages/core/src/stores/tabs.ts` + `AppLayout.vue`）：打开过的页面各占一个页签，来回切换不丢现场（表单草稿、已加载数据、滚动位置），刷新只在页签右键菜单里，**不做进入页签自动重拉**。

- **页签按 `route.path` 唯一**，同一路径换查询参数（工作台跳课表带 `?date=`、列表带 `?editId=`）不另开页签，只更新该页签记的 `fullPath`。所以**新页面的路由必须有 `name`**，没有 name 的记录（纯 redirect）不登记页签。
- **缓存 key 是 `path#seq`**：`seq` 在刷新、关闭后重开时自增，换号即让 keep-alive 认不出旧实例、重挂干净页面。**不用 `include`**，因为 include 认组件 name 而路由组件全是懒加载的，页签只拿得到路径；代价是关掉/刷新过的页签会留一个失活实例在缓存里，由 `:max`（`MAX_CACHED_PAGES = MAX_OPEN_TABS + 2`）按 LRU 挤出，这是可接受的。
- **页面不能再依赖"每次进入都重新挂载"来拉数**：进页面即拉数的写法只在首次生效，切回来是旧数据。要在同页查询参数变化时响应，就 `watch(() => route.query, ...)` 并**必须用 `if (route.name !== '自己的路由名') return` 守卫**（失活期间路由也会变，排课 `ScheduleManageView`、考勤 `ScheduleAttendanceView` 是参照实现）。
- **切换/重置机构要 `tabs.refreshAll()`**：失活页面的 `watch(currentInstitutionId)` 在缓存期间不会补跑，不换 key 就会把上一个机构的数据留在页签里。退出登录 `tabs.clear()`。
- **页签交互固定四件**（照浏览器习惯，别自创）：左键切换、中键关闭、拖拽换顺序、右键菜单。**页签上只暴露 × 一个操作图标，刷新不放外面**（用户反馈：外露的刷新按钮总是误操作，右键菜单里已经有了）。菜单项就五条且顺序不动：**刷新页面 / 关闭当前 / 关闭其他 / 关闭左侧 / 关闭右侧**，范围一律**相对被右键的那个页签**算（和浏览器一致，也对齐 `closeGroup(mode, path)` 的四种 mode）；用不了的置灰而不是隐藏。菜单是自绘的 `.tab-context-menu`（`position: fixed` 跟随鼠标），不用 `el-dropdown`：页签行有 `overflow-x: auto`，弹层会被裁掉。
- **不提供"关闭全部"**：底座不知道首页在哪（`/dashboard` 是壳工程 `src/router/index.ts` 的 redirect，core 不许 import 壳），关空了内容区就没东西可渲染。同理**只剩一个页签时不给关闭**（× 不渲染、菜单四条关闭全置灰）。要加这条必须先给 core 一个"回得去的那一页"的扩展点，别在个别页面偷偷写死 `/dashboard`。
- 拖拽排序只改列表顺序，**不改缓存 key**（key 是 `path#seq`），所以挪页签不会把页面重挂一遍。
- 页签列表存本机 `localStorage` key `cornerstone-open-tabs`（与菜单偏好、导航模式同类，只影响当前浏览器，不随账号同步），上限 `MAX_OPEN_TABS = 10`，淘汰最早打开且非当前页的页签；只剩一个页签时不提供关闭（底座不知道首页路径）。
- **「最近查看」入口固定在页签行尾**（`tabs.history` + `.app-tab-history`）：页签会被上限静默淘汰、也会被自己关掉，这条列表是"回得去刚才那几页"的唯一线索。口径：按路径去重、每次访问提到最前（**是 LRU 不是访问堆栈**），上限 `MAX_VISITED_PAGES = 20`，存本机 `cornerstone-visited-pages`，`clear()` 时和页签一起清（同属上一个账号的浏览现场），`onMounted` 与页签一样用 `router.resolve(...).matched` 剪掉进不去的条目。条目标题同样走 `titleOfPath`（菜单树晚到时 `renameVisits` 补）。**已经开着的条目前面点圆点**——点它是切回原现场，不是新开一页。**它不参与横向滚动**：`.app-tabbar` 是行容器，`.app-tab-strip` 才是滚的那一段，入口在滚动区外面（否则页签一多它就被滚走，失去意义）。下拉用自绘的 `.tab-context-menu` 复用样式，理由同右键菜单。加/减这类"访问现场"记录只动 `stores/tabs.ts`，不要在页面里另起一份。
- **页签行是内容卡的第一行，不是顶栏**：`.app-tabbar` 是行容器（不滚），`.app-tab-strip` 才是横向滚的那一段，行尾的「最近查看」入口在滚动区外面。页签是胶囊样式（高 28，选中态深底白字），**不再压下划线**——选中信息由深色胶囊给出，行本身无底边，压边只会和内容卡多一条缝。往这一行里加东西可以，另起一行不行（纵向 chrome 见「应用外壳」）。

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
@Operation(summary = "新建教师", description = "按登录机构落库，姓名与手机号必填")
@PostMapping
@RequiresPermission("education:profile:write")
public ApiResponse<TeacherEntity> create(@Valid @RequestBody TeacherUpsertRequest request) { ... }
```

- 业务读取接口按 V113 不挂码、也不写 `@RequiresPermission`，但 `@Operation` 仍必须写；演示注解时用写接口举例。

- `summary` 简洁中文短语，展示在 Swagger 列表与「功能权限总览」页（`PermissionOverviewController` 靠 `@RequiresPermission` + `@Operation.summary` 生成“受控接口”说明；缺 `@Operation` 总览页“说明”列为空）。`description` 可选，补充入参/筛选细节；两者都用中文。
- 公开接口（如 `HealthController`）同样建议补，保持文档完整。
- 覆盖度自查（注意类级 `@RequestMapping` 会被计入 mapping 数，应相差 1）：

  ```bash
  grep -c "@Operation" **/*Controller.java
  grep -cE "@(Get|Post|Put|Delete|Request)Mapping" **/*Controller.java
  ```

- 验证：`http://localhost:9080/swagger-ui.html` 接口显示中文 summary；总览页“说明”列非空即为补齐。

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
- `docs/前端页面开发手册.md` — 前端规范的执行细则（落点表 / 反模式对照 / 四型骨架 / 新建页 checklist），写页面前先读
- `docs/miniapp.md`、`docs/list-config-guide.md`、`docs/config-contract.md`
- `docs/操作留痕与敏感数据说明.md` — 审计与脱敏链路
- `docs/生产部署说明.md`、`docker/生产环境MySQL重建与安全加固方案.md`、`docker/prod.env.example` — 生产部署（`docker-compose.prod.yml.example` 模板 + 各服务器本地实例 `docker-compose.prod.yml`（gitignore）+ `deploy.sh`）
- `docs/archive/` — 历史过程方案归档（首批建模、菜单重组、班级变动提示范围等），勿作为现行口径引用
