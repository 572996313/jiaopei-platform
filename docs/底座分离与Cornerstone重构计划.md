# 底座分离与 Cornerstone 重构计划

> 状态：**计划方案，尚未开工**。立项背景：项目未来可能更换业务方向（不再做教培），但登录/权限/菜单/多租户这套"架子"要沉淀为可复用底座，且支持不同功能模块的组合装配。
> 本文档是重构的唯一执行依据，开工前需逐项确认「待决策事项」。

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
| 后端包名 | `com.jiaopei.platform` | `com.cornerstone.core.*`（底座）/ `com.cornerstone.module.<name>.*`（业务） |
| Maven groupId | `com.jiaopei.platform` | `com.cornerstone` |
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

### 5.3 前端结构（阶段三）

```
frontend/            # 转 pnpm workspace
├── packages/core/         # @cornerstone/core：http.ts/request.ts/auth-session/AppLayout/
│                          #   ConfigurableCrudPage/ConfigurableList/路由骨架/字典组件
├── packages/module-education/   # 导出 views + 路由清单 + 列表配置清单
├── packages/module-organization/
└── app/                   # 壳工程：组装模块清单，按清单生成路由
```

- 路由改为**模块清单驱动**：每个模块包导出 `routes: RouteRecordRaw[]`（component 用懒加载），壳工程聚合注册；构建期由 manifest（`.env` 或 `modules.manifest.ts`）决定打入哪些模块，未启用模块代码不进产物。
- 菜单显隐仍以 `sys_menu` 下发为准，与后端开关同源。
- `@` 别名调整为按包引用（`@cornerstone/core`）。

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
2. `ArchitectureRulesTest` 新增规则：`com.jiaopei.platform.(auth|system|common|config)..` 不得依赖 `..education..` / `..organization..`（机构类暂豁免，随阶段二拆分归位）。
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

> 进度：二A 已完成（2026-09-20）——`InstitutionEntity/Mapper/Service/Impl、CurrentInstitution*、InstitutionCreateRequest、InstitutionController` 共 8 类从 organization 迁至底座 `com.jiaopei.platform.tenant` 包；`MybatisPlusConfig` @MapperScan 补 `tenant.mapper`；ArchUnit 移除 Institution 豁免并把 `tenant..` 纳入底座包清单；AGENTS.md 同步。全量测试通过。
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

1. pnpm workspace 改造，抽 `@cornerstone/core` 包。
2. 路由改模块清单驱动（§5.3）；构建期模块 manifest。
3. 验证：只挂 core 时管理端可登录、用户/角色/菜单/系统配置页全可用。

### 阶段四：改名 Cornerstone（预计 0.5~1 天，风险低但 diff 大，单独提交）

1. `com.jiaopei.platform` → `com.cornerstone.*`（IDE 重构），groupId、artifactId、`jiaopei.*` 配置前缀 → `cornerstone.*`（`JiaopeiProperties` → `CornerstoneProperties`）。
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
| D1 | "校区/教室/学期/时间段"归属 | A. 独立 module-organization；B. 并入 module-education；C. 校区留 core | A（保持现包界，改动最小） |
| D2 | 存量 Flyway V1~V37 拆分后，已部署库如何对齐 | A. 新库用拆后脚本、老库不动不追拆；B. 重新 baseline | A（生产库保持原 history，拆分只作用于全新部署） |
| D3 | 底座版本发布渠道 | A. 先 `mvn install` + git tag；B. 内网 Nexus | 起步 A，出现第二个项目时升 B |
| D4 | miniapp 是否本期拆分 | 本期不动 / 一并拆 | 不动（§5.4） |
| D5 | Redis/localStorage key 改名造成的全员重登录窗口 | 随阶段四 / 推迟 | 随阶段四，一次阵痛 |

## 9. 风险与对策

- **搬包引发大面积 import 变更、merge 冲突**：阶段一/二选业务低峰窗口执行，期间冻结其他分支对后端的改动。
- **Flyway 拆分误伤已部署库**：执行 D2-A 策略，拆分脚本仅在 `docker compose` 全新库上验证 + 生产库演练备份先行。
- **开关矩阵测不全导致"关模块启动失败"**：阶段二验收强制三组合冒烟，后续可加入 CI。
- **本仓库当前有未完成的排课相关改动（工作区脏）**：本计划**必须等当前任务完成并合并后再启动**，阶段一第一步是对 master 打 tag 作为回滚锚点。
