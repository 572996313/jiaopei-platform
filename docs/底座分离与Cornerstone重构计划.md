# 底座分离与 Cornerstone 重构计划

> 状态：**阶段一、二A/二B/二C、阶段三（前端 workspace 化）、阶段四（改名 Cornerstone）代码已完成（2026-09-20），阶段五（第二项目验证）待做**。阶段四的生产切换（FLUSHDB + 全员重登录）尚未执行，步骤见 `docs/生产部署说明.md`。立项背景：项目未来可能更换业务方向（不再做教培），但登录/权限/菜单/多租户这套"架子"要沉淀为可复用底座，且支持不同功能模块的组合装配。
> 已完成阶段的落地记录见 §6 各阶段进度注记；现行架构约定以 AGENTS.md「模块划分与持久化」「模块装配与开关」「底座边界」「前端模块划分与装配」为准。本文对剩余阶段仍是执行依据，开工前逐项确认 §8「待决策事项」剩余项（D3）。

## 1. 目标与非目标

### 目标

1. 将仓库拆为 **底座（cornerstone-core）** 与 **业务模块** 两层，底座代码中不出现任何教培行业概念（学员/教师/排课/校区…）。
2. 功能可按项目装配：一个业务模块 = 代码 + 建表迁移 + 菜单/权限种子，**开则自动装、关则自动卸**。
3. 底座以**版本化依赖包**分发（Maven jar + npm 包），各项目通过升版本号统一升级底座。
4. 建立防回潮机制（ArchUnit 规则），禁止业务代码再次混入底座。

### 非目标

- **不做微服务**：仍是单体应用，只拆 Maven 模块。
- **不做插件热加载**（类加载器隔离、运行时装卸等），复杂度不值当。
- 本期不追求把 core 发布到公网仓库，内网/本地仓库发版即可。

## 2. 命名

| 项 | 现名 | 新名 |
|---|---|---|
| 底座代号 | — | **Cornerstone（基石）** |
| 后端包名 | `com.jiaopei.platform`（改名前） | `com.cornerstone.core.*`（底座）/ `com.cornerstone.module.<name>.*`（业务） |
| Maven groupId | `com.jiaopei`（改名前） | `com.cornerstone` |
| 前端 npm scope | — | `@cornerstone/core`、`@cornerstone/module-education` … |
| 数据库表前缀 | `sys_`（系统）/ `jp_`（业务） | 底座沿用 `sys_`；业务前缀由各项目自定（教培继续 `jp_`） |

改名动作**固定放在最后一个阶段**执行，避免与搬包混在同一次提交里。

## 3. 底座 / 业务边界判定标准

唯一尺子：**换一个行业（如健身房、连锁门店）后这段代码是否还有用？**

| 归底座 cornerstone-core | 归业务模块 |
|---|---|
| 登录、JWT、Redis 会话、改密、强制下线 | 教师、学员、班级、课程、报名、排课、考勤、变动通知 |
| 用户/角色/菜单/权限码体系、权限总览 | 家长绑定学员、小程序课表/考勤打卡 |
| **机构（租户）本身**：租户隔离机制、用户挂机构 | 校区、教室、学期、时间段（教务主数据） |
| 统一响应、全局异常、traceId、大 ID 序列化 | 具体字典枚举值（出勤状态等） |
| 审计留痕、敏感脱敏、系统参数、Flyway 骨架 | 各业务的页面、表单、校验规则 |

> 注：`organization` 包需要拆开——"机构"是多租户底座概念留 core；"校区/教室/学期/时间段"是教培教务概念进业务模块（module-organization 或并入 module-education）。此拆分见 §8 待决策。

## 4. 现状耦合清单（第一阶段要解决的点）

当前为 Maven 单模块、按包分界，已发现 **底座 → 业务** 的反向 import：

| 文件（backend/src/main/java/com/jiaopei/platform/） | 问题 | 处理 |
|---|---|---|
| `auth/service/impl/MiniappServiceImpl.java` | import 了 education 的 12+ 实体/mapper 与 organization 的 Classroom | 整体迁到 education 包（本质是教培小程序业务，穿错了衣服） |
| `auth/service/impl/WechatAuthServiceImpl.java` | import StudentEntity/StudentMapper | 微信登录留 core（通用身份），"绑定学员"部分拆到 education；拆不开的整体迁 education |
| `auth/service/impl/SystemUserServiceImpl.java` | import InstitutionEntity/CampusEntity 及 mapper | 机构引用属租户概念（留 core，随 §3 拆分归位）；对 Campus 的引用改为 core 定义接口、业务模块实现（SPI/事件） |
| `system/controller/SystemInstitutionController.java` | import organization 包 | 机构管理归 core 后自然消除 |
| `config/properties/JiaopeiProperties.java` | `modules.*-enabled` 仅是 bootstrap 展示标志，无 `@ConditionalOnProperty` | 假开关改真开关（见 §5.2） |
| `config/OpenApiConfig.java`、`system/controller/PlatformBootstrapController.java` | 硬编码 organization/education 模块文案 | 改为从模块注册表读取（见 §5.2） |

前端耦合：`frontend/src/router/index.ts` 全量静态硬编码，底座页面与业务页面混排；菜单虽由 `sys_menu` 驱动，但路由与组件是写死的。

Flyway 耦合：`V1~V37` 底座表与业务表混在一条序列；菜单/权限种子写在迁移里（模式是对的，缺分目录）。

## 5. 目标架构

### 5.1 后端仓库结构（阶段二后）

```
backend/
├── cornerstone-core/          # 底座 jar：auth + system + common + config + 机构(租户)
├── module-organization/       # 教务主数据：校区/教室/学期/时间段（视 §8 决策）
├── module-education/          # 教务业务：教师/学员/班级/课程/排课/考勤/小程序业务接口
└── app/                       # 启动器：@SpringBootApplication + application.yml + 组装依赖
```

依赖方向单向：`app → module-* → core`；**core 不得 import 任何 module 类**（ArchUnit 强制）。

### 5.2 模块装配机制（"功能组合"的落点）

1. **真开关**：每个业务模块自带 `XxxModuleAutoConfiguration`，标注
   `@ConditionalOnProperty(prefix = "cornerstone.modules.education", name = "enabled")`；
   注册走 `META-INF/spring/...AutoConfiguration.imports`。关闭后 Controller/Service 不注册，接口 404。
2. **模块自带迁移**：core 迁移放 core jar 的 `db/migration/core/`，模块迁移放各自 jar 的 `db/migration/<module>/`。
   `app` 的 yml 按开关拼接 `spring.flyway.locations`。版本号规则：**各模块独立前缀段**（core 用 V1xx、education 用 V2xx…），避免合入冲突；不再共用一条 V1~V37 序列（存量脚本按表归属拆入各段，拆法见阶段二任务）。
3. **菜单/权限随模块走**：约定每个功能交付物 = 建表 SQL + `sys_menu`/权限码种子 SQL，同放模块迁移目录。启用模块后菜单自动出现，无需改 core。
4. **模块注册表**：定义 `ModuleDescriptor` 接口（code/名称/版本/权限前缀），core 的 bootstrap 汇总页与 OpenApiConfig 注入 `List<ModuleDescriptor>` 动态渲染，替换现在硬编码的 `ModuleSummaryView`。
5. **跨模块规则**：只允许 id 引用，不建跨模块物理外键；core 需感知业务时（如删除前占用检查）用**事件发布（core 定义事件类）+ 业务模块监听**，或 core 定义 SPI 接口、业务模块提供实现 Bean。

### 5.3 前端结构（阶段三，已按此落地）

```
frontend/                        # pnpm workspace；根目录本身就是壳工程（index.html/vite.config/tsconfig 原位不动）
├── src/                         # 壳：main.ts、router/index.ts、装配清单 modules.ts、跨模块工作台
├── packages/core/               # @cornerstone/core：http.ts/request.ts/auth-session/AppLayout/
│                                #   ConfigurableCrudPage/ConfigurableList/系统管理页/路由骨架/扩展点注册表
├── packages/module-organization/ # 校区/教室/学期/时间段 + educationScope（范围筛选 store）
└── packages/module-education/    # 教师/学员/班级/排课/考勤
```

- 路由改为**模块清单驱动**：每包 `index.ts` 导出 `xxxRoutes: RouteRecordRaw[]`（component 懒加载），壳工程 `src/modules.ts` 里登记「import 模块入口 + 路由进 `projectChildRoutes`」两件事，缺一即"装了但没挂上"。
- 构建期模块裁剪：**没有做 `.env` / `modules.manifest.ts` 开关**，装配清单就是 `modules.ts` 这一个是真源的 TS 文件——删掉模块条目与其 import，vite tree-shaking 后产物即不含业务 chunk（实测全挂 54 assets/13 业务 chunk，只挂 core 33 assets/0 业务 chunk）。理由：本仓库当前只有一个壳工程，多一层构建期配置文件换来的是"配置与代码可能不一致"的新风险，收益不足；真出现同仓库多产品形态时再引入 manifest。
- **源码级引用，不单独构建各包**：包无 `dist`，vite `resolve.alias` + tsconfig `paths` 把 `@cornerstone/*` 指到 `packages/*/src`（别名顺序：`@` 必须排最后）。省掉每包一份构建配置与版本联动，代价是包暂时不能独立发布——与 §5.5"workspace 内先用相对引用"一致。
- 底座↔模块解耦用**扩展点注册表**（`packages/core/src/extension/module-extensions.ts`，镜像后端事件/SPI）：数据目录 provider（`registerDirectory`/`loadDirectory`）、机构切换广播（`onInstitutionReset`/`notifyInstitutionReset`）、页面插槽 + 刷新广播（`registerPageSlot`/`getPageSlots`/`onPageRefresh`/`notifyPageRefresh`）。注册是各模块 `index.ts` 的 import 副作用。
- 边界由 ESLint `no-restricted-imports` 分块强制（core ↛ `module-*`、organization ↛ education、`@/*` 只在壳），等价后端 ArchUnit 规则。
- 菜单显隐仍以 `sys_menu` 下发为准，与后端开关同源。

### 5.4 小程序（miniapp）

本期**不强制拆包**，只做纪律：小程序页面全部视为业务层，`src/utils/request.ts` 等通用件暂留原地；待 core npm 包成型后再评估下沉。

### 5.5 底座分发方式

- core jar：发布带版本号的构件（内网 Nexus 或本地 `mvn install` + git tag 起步），业务项目以 `<cornerstone.version>` 引用。
- 前端 core：workspace 内先用相对引用，跨仓库分发时走私有 npm 或 git 依赖。
- **明确放弃**"复制仓库当模板"的方案：一旦分叉，统一升级即失效。

## 6. 分阶段实施计划

> 每阶段独立可交付、可回滚；未开始下一阶段前，线上行为不变。

### 阶段一：划界止血（已完成 2026-09-20，回滚锚点 tag：`cornerstone-phase1-anchor`）

代码仍在现单模块内，只搬包 + 立规矩：

1. 按 §4 清单迁移 `MiniappServiceImpl`、`WechatAuthServiceImpl` 等到 education 包；`SystemUserServiceImpl` 对 Campus 的依赖改接口化。
2. `ArchitectureRulesTest` 新增规则：`com.jiaopei.platform.(auth|system|common|config)..`（阶段四后为 `com.cornerstone.core.*`）不得依赖 `..education..` / `..organization..`（机构类暂豁免，随阶段二拆分归位）。
3. 前端：路由文件按「底座路由 / 业务路由」拆成两个数组再合并注册（为阶段三铺路）。

**验收**：`mvn -q test` 通过（含新 ArchUnit 规则）；`pnpm build`、`pnpm lint` 通过；接口行为无变化。**回滚**：单分支可整体 revert。

<details><summary>实际落地记录（与计划的差异）</summary>

- Miniapp 四件套（Service/Impl/Controller/Test）+ 6 个学员 DTO（`auth/dto` 下 Bind/Student*）+ `UserStudentRelEntity`/`UserStudentRelMapper` 全部迁至 education 包，URL 与权限码未动。
- `WechatAuthServiceImpl` 未按"整体搬迁"，改为计划中的事件方案：新增 `auth.event.UserAccountDeregisteredEvent`，education 侧 `StudentDeregistrationListener` 监听清理学员归属与关联（同步事件，同事务）。
- `SystemUserServiceImpl` 校区校验改 `auth.service.CampusBelongingValidator` SPI，`organization.service.impl.CampusBelongingValidatorImpl` 提供实现，`ObjectProvider` 可选注入。
- ArchUnit 对 organization 的豁免按"类名含 Institution"实现（覆盖 CurrentInstitutionView 等），阶段二迁机构回底座后移除。
- 前端拆为 `router/platform-routes.ts` + `router/business-routes.ts`，index.ts 只做组装；`InstitutionProfileView`（机构=租户）归底座侧，校区/时间段归业务侧。
- 未决遗留：登录响应 `LoginResponse/CurrentUserResponse` 的"所属端"枚举含 student/parent 字符串（无 import 依赖，不阻塞 ArchUnit），列入阶段二 core 泛化项。
- AGENTS.md 已新增「底座边界」章节固化约定。

</details>

### 阶段二：后端 Maven 多模块 + 真开关（预计 3~5 天，风险中）

> 进度：二A 已完成（2026-09-20）——`InstitutionEntity/Mapper/Service/Impl、CurrentInstitution*、InstitutionCreateRequest、InstitutionController` 共 8 类从 organization 迁至底座 `com.jiaopei.platform.tenant` 包（阶段四后为 `com.cornerstone.core.tenant`）；`MybatisPlusConfig` @MapperScan 补 `tenant.mapper`；ArchUnit 移除 Institution 豁免并把 `tenant..` 纳入底座包清单；AGENTS.md 同步。全量测试通过。
> 因生产 V38 半实施恢复进行中，本阶段**不动存量 V1~V38 迁移脚本**（不重排、不搬家），模块专属迁移目录仅对今后新脚本生效（D2 按"A+基线保留"执行）。
> 进度：二B 已完成（2026-09-20）——`backend/` 改为 Maven 聚合工程（`jiaopei-platform-backend-parent`，`packaging=pom`），子模块 `cornerstone-core`（auth/system/common/config/tenant + 全部第三方 starter）、`module-organization`（→core）、`module-education`（→core、→organization）、`app`（主类 + 全部资源 + 全部测试 + `spring-boot-maven-plugin`）。
> - app 的 artifactId 保留 `jiaopei-platform-backend`，jar 文件名不变，产物路径改为 `backend/app/target/`；`scripts/restart-backend.sh`、`backend/Dockerfile`（按模块分层 COPY pom/src）、`backend/.gitignore`（`/target/` → `target/` 以覆盖各子模块）同步。
> - 依赖方向已实测：education→organization 5 处、organization→education 0 处，故 org/edu 可分模块。
> - 与计划的差异：**Flyway 迁移与测试暂全部留在 `app/` 模块**（原计划二B 就把迁移拆到各模块 `META-INF`，因生产 V38 恢复未完成而推迟到二C/阶段五）；`MybatisPlusConfig` 的 `@MapperScan` 仍在 core 里统一扫五个 mapper 包，按模块拆分留到二C。
> - 验收：`mvn -q test` 166 个测试全绿；`mvn -q -DskipTests install` 四模块成功。开关矩阵（关模块启动）属二C。
> - 操作提醒：`spring-boot:run` 必须在 `app/` 下或 `-pl app`（不带 `-am`），对 core 执行该 goal 会因无主类失败；改 core/业务模块代码后需先 `install` 再 run，或直接用 `scripts/restart-backend.sh`。
> 进度：二C 已完成（2026-09-20）——模块开关从"只影响 bootstrap 展示"变成真开关。
> - 装配两条路（有意不对称）：底座由 app 启动类 `@SpringBootApplication(scanBasePackages = {auth,system,tenant,audit,common,config})` 显式扫描，保证 `DataSource`/`SqlSessionFactory` 早于 Spring Boot 与 MyBatis-Plus 的自动配置出现（否则后者 `@ConditionalOnMissingBean` 判定失效、多出重复数据源）；业务模块走各自 jar 的 `META-INF/spring/...AutoConfiguration.imports` → `XxxModuleAutoConfiguration`（`@AutoConfiguration` + `@ConditionalOnProperty` + `@ComponentScan` + `@MapperScan`），加/减模块只改 app 的 pom。
> - 开关：`jiaopei.modules.{organization,education}-enabled`（可用 `MODULE_*_ENABLED` 环境变量覆盖），缺省开启。core 的 `@MapperScan` 收缩到 auth/system/tenant，业务 mapper 由各自模块声明。
> - `ModuleDescriptor` 注册表替换 `PlatformBootstrapController` / `OpenApiConfig` 里硬编码的模块清单；`jiaopei.flyway.locations` 支持按模块追加迁移目录（当前仍只有 `classpath:db/migration`）。
> - 与计划的差异：① 底座没用自动配置装配（理由如上，属实测后的必要偏离）；② `@WebMvcTest` 切片会关闭自动配置，三个业务控制器切片测试改为把控制器显式列进 `@Import`，否则症状是断言 `200` 实际 `404`；③ 前缀仍用 `jiaopei.`（`cornerstone.` 统一留到阶段四改名一起做，避免半改状态）；④ `wx-java-miniapp` 依赖仍留在 core，随模块迁移推到阶段四。
> - 验收：`mvn -q test` 169 全绿（新增 ArchUnit `productionClassesMustResideInAssembledPackages` 与 `ModuleSwitchGuardTest` 三条守护）。运行时三组合实测：全开（bootstrap 列 auth/organization/education，`/v3/api-docs` 含 `/api/education/*`）→ `MODULE_EDUCATION_ENABLED=false`（清单只剩两项，`/api/education/*` 全部消失，campuses 不受影响）→ 两个都关（只剩 auth，auth/tenant 接口完好，10.3s 启动无 ERROR）。依赖方向 edu→org，关 org 必须同时关 edu。

1. 建 parent pom，拆 `cornerstone-core` / `module-*` / `app` 四模块，包随模块迁移（IDE 移动保 git 历史）。
2. Flyway 存量 V1~V37 按表归属拆分为 core 段与各模块段（新号段重排；**只拆脚本内容归属、不改表结构**，已在环境执行过的库靠 `flyway_schema_history` 按新 location 重新 baseline 的策略需专项设计，见 §8）。
3. 各模块补 `AutoConfiguration` + `@ConditionalOnProperty`；yml 拼接 `spring.flyway.locations`；`ModuleDescriptor` 注册表替换 bootstrap 硬编码。
4. 验证"关模块"：`cornerstone.modules.education.enabled=false` 时后端可正常启动、core 功能完整、education 接口 404、无 education 菜单下发。

**验收**：`mvn -q test` 全绿；模块开关矩阵（全开/只开 core/开 org 不开 edu）三种组合均可启动并冒烟；`scripts/restart-backend.sh` 适配新产物路径。

### 阶段三：前端 workspace 化（预计 3~4 天，风险中，可与阶段二并行）

> 进度：已完成（2026-09-20，分两次提交：`783ceaf` workspace 骨架 + 抽 core；阶段三(二) 业务包拆分与页面插槽）。
> - `frontend/` 转 pnpm workspace（`pnpm-workspace.yaml` 声明 `packages/*`），抽 `@cornerstone/core`、`@cornerstone/module-organization`、`@cornerstone/module-education` 三包；壳即 `frontend/` 根（`index.html`/`vite.config.ts`/`tsconfig.*` 原位不动）。
> - 与计划的差异：① **未建 `app/` 子目录**（原 §5.3 设想把壳挪进 `app/`），把壳留在根可让既有文档、`scripts/`、`frontend/Dockerfile` 路径基本不变，代价是根 `src/` 与 `packages/` 并存；② **源码级引用、各包不单独构建**（无 per-package build / 无 `dist`），跨包靠 vite alias + tsconfig paths 解析，包独立发布推到阶段五真需要时再做；③ **未做构建期 `.env` manifest**，装配清单就是 `frontend/src/modules.ts` 一个文件（见 §5.3）；④ `@` 别名保留给壳工程，不改成按包引用。
> - 底座解耦三处（core 不再 import 任何业务代码）：机构列表接口迁 `core/src/api/tenant.ts`（对齐后端阶段二A）；校区下拉改扩展点 `registerDirectory('campus')`，由 organization 包注册；机构切换清理范围 store 改 `onInstitutionReset` 广播。
> - 页面插槽落地两处决策：校区管理页里教务侧的「课程/课程分类/班型」三张卡片改**页签插槽注入**（宿主 organization，内容 education 包 `views/campus/EducationMasterDataCards.vue`，靠 `registerPageSlot`/`notifyPageRefresh` 联动）；范围筛选 store `educationScope`（campusId+termId）**归 module-organization**，教务包复用，避免两侧各存一份。
> - 边界守护：`eslint.config.js` 新增 `boundary/core`、`boundary/module-organization`、`boundary/module-education` 三块 `no-restricted-imports` 规则（core ↛ `@cornerstone/module-*`、organization ↛ education、`@/*` 仅壳可用），已用探针文件验证会触发报错。
> - 验收：`pnpm build`（vue-tsc + vite）与 `pnpm lint` 全绿；全挂时产物 54 assets / 13 业务 chunk，临时把 `modules.ts` 改成只挂 core 后 33 assets / **0** 业务 chunk，登录与系统管理（用户/角色/菜单/列表配置/系统参数）页在 dev 上实测可用——校区列降级为 `校区 #{id}`（无目录 provider，属预期的功能裁剪），业务路由整页消失。恢复全挂清单后再次构建通过。
> - 构建链同步：`frontend/Dockerfile` 已补 `COPY frontend/pnpm-workspace.yaml`、三个包的 `package.json`（install 前）与 `COPY frontend/packages`（build 前），否则 workspace 依赖解析不到；`scripts/ci-check.sh` 无需改（pnpm 命令都在 `frontend/` 根执行）。镜像构建尚未实测（本机无 docker 构建流程在本次范围内）。

1. pnpm workspace 改造，抽 `@cornerstone/core` 包。
2. 路由改模块清单驱动（§5.3）；构建期模块 manifest。
3. 验证：只挂 core 时管理端可登录、用户/角色/菜单/系统配置页全可用。

### 阶段四：改名 Cornerstone（预计 0.5~1 天，风险低但 diff 大，单独提交）

> 进度：已完成（2026-09-20，三次提交 `18fb553` / `d325498` / `7293100`），**生产切换尚未执行**（需停机窗口，步骤见 `docs/生产部署说明.md`「底座改名（阶段四）发布切换步骤」）。
> - 包名：`com.jiaopei.platform.{auth,system,common,config,tenant,audit}` → `com.cornerstone.core.*`，`{organization,education}` → `com.cornerstone.module.*`，主类 → `com.cornerstone.CornerstoneApplication`（仍在包根，`@ConfigurationPropertiesScan` 默认扫子包的行为不变；`scanBasePackages` 仍显式列底座六个顶层包，未放宽）。371 个文件按 rename 记录，git 历史可追。
> - groupId → `com.cornerstone`；配置前缀 → `cornerstone.*`（`JiaopeiProperties` → `CornerstoneProperties`，两处 `@Value`、两个模块的 `@ConditionalOnProperty`、Hikari 池名、`cornerstoneOpenApi` bean 名同步）。
> - 运行时 key → `cornerstone:`（Redis 会话）与 `cornerstone.auth.session` / `cornerstone-nav-mode` / `cornerstone-menu-preferences`（管理端）、`cornerstone.mini.auth.session`（小程序）。**不加读旧 key 的兼容分支**：这次切换的目的就是让全员会话失效一次，兼容代码会长期误导。
> - 与计划的差异（本次决策）：**部署与业务层命名一律不动** —— artifactId 与 jar 名 `jiaopei-platform-backend`、`spring.application.name`、MySQL 库名 `jiaopei_platform` / 日志库 `jiaopei_log`、MySQL 账号、容器名 `jiaopei-prod-*`、镜像名、宿主机目录 `/mnt/software/jiaopei`、表前缀 `jp_`/`sys_` 全部保留；环境变量名（`AUTH_JWT_SECRET`/`DATA_ENCRYPT_KEY`/`DB_*`/`MODULE_*_ENABLED`）本来就是中性占位符，生产 `prod.env` 不用改。边界表落在 AGENTS.md「底座边界」。
> - 原计划第 3 项提到的 `VITE_API_AUTHORIZATION` 未改名：它是构建参数而非底座身份，改了要连 CI/镜像构建参数一起动，收益为零。
> - 验证：`mvn test` 169 全绿（含 ArchUnit 包白名单与 `ModuleSwitchGuardTest`）；`pnpm lint` + `pnpm build` 通过；本地重启实测 —— 未带 token 时 `/api/health` 200、`/api/system/users` 401（证明 `cornerstone.security.public-paths` 绑定生效），`MODULE_EDUCATION_ENABLED=false` 重启后 bootstrap 清单只剩 auth/organization 且 `/api/education/teachers` 404 而 `/api/organizations/campuses` 仍 200；FLUSHDB 后重新登录，Redis 只出现 `cornerstone:token:{jwt}` 与 `cornerstone:user:tokens:1`，旧 token 复放 401；浏览器改读新 key 后 `/campus` 数据正常渲染。
> - 遗留（阶段四未做，推阶段五或单排）：`wx-java-miniapp` 依赖仍在 core；core 泛化项（登录/当前用户响应的"所属端"枚举含 student/parent、`RenderContext` 里的 `campusOptions` 业务命名、`views/system/shared.ts` 的 `canWriteOrganization`、壳里未引用的 `ModulePlaceholderView.vue` / `HelloWorld.vue`）。

1. `com.jiaopei.platform` → `com.cornerstone.core.*` / `com.cornerstone.module.<name>.*`，groupId `com.jiaopei` → `com.cornerstone`；`jiaopei.*` 配置前缀 → `cornerstone.*`（`JiaopeiProperties` → `CornerstoneProperties`）。artifactId/表前缀/库名/容器与镜像名不动（见上方进度注记的本次决策）。
2. Redis key 前缀 `jiaopei:` → `cornerstone:`（**切换需 FLUSHDB，全员重新登录**，安排停机窗口）。
3. 前端 localStorage key `jiaopei.auth.session`、`VITE_API_AUTHORIZATION` 相关命名同步；`jp_` 表前缀不动（业务自有）。
4. 文档同步：AGENTS.md、docs/ 各交付说明里的包名与配置前缀。

### 阶段五：第二个项目验证（未来，非本期）

新开一个非教培项目（或 demo 模块），仅依赖 core 搭建，验证"底座可独立迁移"。此阶段暴露的问题回灌 core。**在这一步真实跑通前，不宣布底座可复用。**

## 7. 固化到 AGENTS.md 的新约定（阶段一随代码提交）

1. 底座包（auth/system/common/config）禁止 import 业务包，ArchUnit 强制。
2. 新增业务功能一律以"模块三件套"交付：代码 + 建表迁移 + 菜单/权限种子，且带模块开关。
3. 跨模块引用只用 id，不建物理外键；core 与业务的交互走事件/SPI。
4. 底座改动（core 包内任何修改）需在提交信息标注 `[core]`，提示影响所有下游项目。

## 8. 待决策事项（开工前确认）

| # | 事项 | 选项 | 建议 |
|---|---|---|---|
| D1 | "校区/教室/学期/时间段"归属 | A. 独立 module-organization；B. 并入 module-education；C. 校区留 core | **已定 A**（阶段二B 落地） |
| D2 | 存量 Flyway V1~V37 拆分后，已部署库如何对齐 | A. 新库用拆后脚本、老库不动不追拆；B. 重新 baseline | **已定 A+基线保留**（阶段二B，迁移暂留 app/） |
| D3 | 底座版本发布渠道 | A. 先 `mvn install` + git tag；B. 内网 Nexus | 起步 A，出现第二个项目时升 B（未定） |
| D4 | miniapp 是否本期拆分 | 本期不动 / 一并拆 | **已定不动**（§5.4） |
| D5 | Redis/localStorage key 改名造成的全员重登录窗口 | 随阶段四 / 推迟 | **已定随阶段四**（代码已改完，等停机窗口发布，见 `docs/生产部署说明.md`） |

## 9. 风险与对策

- **搬包引发大面积 import 变更、merge 冲突**：阶段一/二选业务低峰窗口执行，期间冻结其他分支对后端的改动。
- **Flyway 拆分误伤已部署库**：执行 D2-A 策略，拆分脚本仅在 `docker compose` 全新库上验证 + 生产库演练备份先行。
- **开关矩阵测不全导致"关模块启动失败"**：阶段二验收强制三组合冒烟，后续可加入 CI。
- **本仓库当前有未完成的排课相关改动（工作区脏）**：本计划**必须等当前任务完成并合并后再启动**，阶段一第一步是对 master 打 tag 作为回滚锚点。
