# 微信小程序端（miniapp）

本项目新增的微信小程序端 **「教培学堂」**,基于 **uni-app(Vue 3 + TypeScript + Vite)** 构建,定位
**家长/学生移动端服务 + 企业端收费与档案工作台**;与管理后台(frontend,「教培管理平台」)并行,复用同一套 Spring Boot 后端接口。

> 小程序名称在 `miniapp/src/manifest.json`(name)与 `miniapp/src/pages.json`(navigationBarTitleText)统一维护。

## 目录结构

```
jiaopei-platform/
├── backend/    # Spring Boot 后端(新增微信登录链路)
├── frontend/   # 管理后台(Vue 3 + Element Plus,不改动)
├── miniapp/    # 微信小程序端(uni-app,本目录)
└── docs/
```

miniapp 内部结构:

```
miniapp/src/
├── config.ts               # API 基地址(可用 VITE_API_BASE_URL 环境变量覆盖)
├── utils/
│   ├── auth-session.ts     # 登录态存取(uni storage,key: cornerstone.mini.auth.session)
│   └── request.ts          # uni.request 封装:ApiResponse 信封解包 + 雪花 ID 精度保护 + 401 统一处理
├── api/auth.ts             # 微信登录 / 账号密码登录 / 自助注册 / 注销 / 当前用户
├── api/miniapp.ts          # 学员档案关联 + 课表/同学名单/首页概览查询
├── api/enterprise.ts       # 企业端收费台账/收款/出勤/逐月/收费项目/价目
├── api/archive.ts          # 企业端学员/家长联系人/班级/入班/课程班型教师下拉
├── components/PickerField.vue  # 底部搜索选择器(学期/班级/学员/收费项目/校区)
├── stores/auth.ts          # Pinia 登录态(含 loginMode / portal)
└── pages/
    ├── login/index.vue     # 微信一键登录(启动页)+「企业用户登录」入口
    ├── enterprise/login/index.vue  # 企业端账号密码登录
    ├── enterprise/fee-ledger/index.vue     # 收费台账列表/筛选
    ├── enterprise/fee-ledger/create.vue    # 单个新增 / 按班批量建账
    ├── enterprise/fee-ledger/detail.vue    # 台账详情(明细/收款/出勤/逐月/设置)
    ├── enterprise/fee-ledger/edit.vue      # 台账明细整表编辑
    ├── enterprise/fee-ledger/payment.vue   # 收款登记/修改/删除
    ├── enterprise/fee-item/index.vue       # 收费项目维护
    ├── enterprise/fee-price/index.vue      # 收费价目维护(四维)
    ├── enterprise/student/index.vue        # 学员分页列表/筛选
    ├── enterprise/student/form.vue         # 新增/编辑学员档案
    ├── enterprise/student/detail.vue       # 学员详情 + 家长联系人 + 所在班级
    ├── enterprise/class/index.vue          # 班级列表/筛选
    ├── enterprise/class/form.vue           # 新增/编辑班级档案
    ├── enterprise/class/detail.vue         # 班级详情 + 名册 + 入班/移出
    ├── register/index.vue  # 首次使用选择身份(学生/家长)自动注册
    ├── index/index.vue     # 首页 tab(只读:欢迎语 + 今日课程 / 企业收费概览)
    ├── workbench/index.vue # 工作台 tab(操作入口:企业收费/学员/班级;学生家长学习服务)
    ├── bind-student/index.vue  # 绑定学员档案(按学号/监护人手机号)
    ├── student-profile/index.vue   # 学生端:绑定/新建/编辑自己的档案
    ├── timetable/index.vue     # 课程表(按天列表)
    ├── classmates/index.vue    # 我的同学(按班级分组的同班姓名名单)
    └── mine/index.vue      # 我的 tab(学员管理 + 退出/注销)
```

## 后端新增内容

| 项 | 说明 |
| --- | --- |
| `V26__create_wechat_binding_table.sql` | `jp_wechat_binding` 表:openid ↔ 系统用户绑定关系 |
| `V27__wechat_self_register.sql` | `password_hash` 改可空、`register_source` 来源列、预置 `MINIAPP_STUDENT`/`MINIAPP_PARENT` 两个角色 |
| `POST /api/auth/wechat/login` | code 换 openid;已注册签发 JWT,未注册返回 `registered=false` |
| `POST /api/auth/wechat/register` | 自助注册:选身份(学生/家长)+昵称自动建档、分配角色并签发 JWT;重复注册幂等。携带 `invitationCode` 时走邀请码注册:机构/角色由邀请码固定(仅限学生/家长两个小程序角色,其余返回 403),无需选身份 |
| `GET /api/education/wechat/invitations/{code}/wxacode` | 管理端生成邀请码小程序码(PNG,scene 携带凭证编码,服务端缓存 30 天;权限 `education:class:write`) |
| `pages/register/index` | 注册页支持扫小程序码直达:启动参数 `scene` 携带邀请码,自动预检后进入邀请注册模式(显示机构与角色、免选身份) |
| `POST /api/auth/wechat/deregister` | 账号注销:删除当前微信注册的账号(含身份角色与绑定),令牌立即失效;注销后同一微信重新登录为未注册状态,可重新注册 |
| `UserAccessTokenService` | 从 `AuthApplicationServiceImpl` 抽出的统一令牌签发组件,账号密码登录与微信登录共用 |
| `wx.miniapp.*` 配置 | `application.yml`,通过环境变量 `WECHAT_MINIAPP_APPID/SECRET` 注入 |

自助注册的用户不设密码(`password_hash` 为空),不能走账号密码登录,只能微信免密登录;
机构归属取系统配置 `auth.register.default_institution_id`(默认 1,可在后台系统配置里调整)。

微信登录复用现有 JWT + Redis 会话体系,签发的令牌与管理后台完全同构,
小程序端可直接调用受 `@RequiresPermission` 保护的接口。

## 开发环境准备

1. **注册微信小程序账号**:到 [mp.weixin.qq.com](https://mp.weixin.qq.com) 注册,
   获取 **AppID** 与 **AppSecret**(个人主体即可用于开发验证)。
2. **安装微信开发者工具**:微信官方 IDE,用于运行和调试小程序。
3. **后端配置**:

   ```bash
   # 启动后端前设置环境变量(或在 IDE 运行配置中添加)
   WECHAT_MINIAPP_APPID=wx你的appid
   WECHAT_MINIAPP_SECRET=你的secret
   ```

   未配置时后端可正常启动,调用微信登录接口会返回明确错误提示。

   生产环境(docker 部署):把同样两个变量写入 `docker/prod.env`(该文件已 gitignore,只留服务器),
   `docker-compose.prod.yml` 的 backend 服务已透传这两个变量,`docker/prod.env.example` 有对应模板。

## 小程序本地运行

```bash
cd miniapp
pnpm install
pnpm dev:mp-weixin     # 开发模式,产物输出到 dist/dev/mp-weixin,监听文件变更
# 或
pnpm build:mp-weixin   # 生产构建,产物输出到 dist/build/mp-weixin
```

然后:

1. 打开**微信开发者工具** → 导入项目 → 目录选择 `miniapp/dist/dev/mp-weixin`
   → AppID 填自己的小程序 AppID(或选"测试号")。
2. 在 `src/manifest.json` 的 `mp-weixin.appid` 填入 AppID。
3. 开发者工具右上角 **详情 → 本地设置 → 勾选"不校验合法域名…"**
   (开发阶段直连 `http://127.0.0.1:8080`,正式发布必须 HTTPS 域名)。
4. 确认本机 MySQL/Redis/后端已启动,模拟器中即可走通登录流程。

| `V28__user_student_rel.sql` | 用户-学员关联表 `jp_user_student_rel`;小程序角色追加 `miniapp:access` 权限码 |
| `GET/POST /api/miniapp/students` | 绑定学员档案(按学号/监护人手机号)与当前用户已绑学员列表 |
| `DELETE /api/miniapp/students/{id}` | 解绑学员档案(物理删除关联,解绑后可重新绑定) |
| `GET /api/miniapp/student/profile` | 学生端-我的档案(未绑定 bound=false) |
| `POST /api/miniapp/student/bind-self` | 学生端-按学号+姓名绑定自己的档案(双因子,绑定后不可换绑) |
| `POST /api/miniapp/student/profile` | 学生端-新建(无档案时自动建档并归属)或编辑本人档案(仅姓名/性别/生日/监护人) |
| `GET /api/system/dicts/options?typeCodes=gender` | 性别字典选项(底座字典接口,登录即可访问,小程序与 PC 同源) |
| `GET /api/miniapp/students/{id}/lessons` | 学员周课表(联查班级/课程/教室/教师),仅限已绑定的当前用户 |
| `GET /api/miniapp/students/{id}/classmates` | 我的同学:该学员在班班级按班分组的同班姓名名单(不含本人),**只返回姓名,学号/性别/生日一律不下发** |
| `GET /api/miniapp/home/overview` | 首页概览:名下全部学员的今日课次、本周节数、下一节预告、未分班学员标记 |

## 档案关联与课程表

- **绑定学员**:「我的」→「绑定学员档案」,支持按**学员编号**或**监护人手机号**两种方式;手机号方式通过 `jp_student_parent.parent_phone` 匹配。**双因子校验**:除编号/手机号外必须填写**学员姓名**,字段与档案不一致(或该手机号下无此姓名)时拒绝绑定,防止陌生人仅凭传播的学号/手机号绑定查看学员信息。一个账号可绑多个学员,一个学员可被多个家长绑定。
- **绑定的学员**:家长端「我的」→「我的学员」列表,点学员进课表,点「解绑」解除关联(有二次确认,解绑后可重新绑定)。
- **学生端「我的」**:按是否绑定档案展示——未绑定引导「按学号绑定已有档案」或「自助创建档案」;已绑定展示本人档案(姓名/性别/生日/班级)+「我的同学」「编辑档案」入口(课表入口只在首页,不在这里重复给)。学生档案**绑定后不可换绑**,注销账号时自动清除档案归属(user_id 置空,档案本身保留可由后续绑定)。
- **性别取值统一走 `gender` 字典**(male=男 / female=女 / unknown=未知,系统字典管理页可维护):小程序选项与 PC 学员/教师表单同源,后端写入前由 `DictValueValidator` 校验,非法值报「性别取值无效」;V51 已把存量大小写混用(`MALE`/`FEMALE`/`UNKNOWN`)归一为小写。
- **账号显示名跟随档案**:绑定关系一变化就重算 `sys_user_account.display_name`——学生端取自己档案姓名,家长端取已绑学员姓名按绑定先后顿号连接再加「家长」(如「小明、小红家长」),注册时填的微信昵称会被覆盖,管理端账号列表与小程序「我的」看到的就是"这个人是谁"。姓名多到超过 `display_name` 的 128 列宽时按能放下的孩子数截断并补「等」(「小明、小红等家长」),连一个孩子姓名都放不下时截姓名保住后缀。**没有档案可依据时保留现有名字**(解绑最后一个孩子、学生尚未归档),不清空也不写占位文案。「我的」页 `onShow` 会重拉 `/api/auth/me`,改完当场可见,不必退出重登。
- **数据隔离**:所有 `/api/miniapp` 接口都经 `@RequiresPermission("miniapp:access")` 且按**当前登录用户**过滤——只能查到自己绑定的学员,不能越权看别人。
- **课表**:按当前用户所选学员,取其在班班级(`jp_class_student` status=`active`,小写规范值)→ 查询该班课次(`schedule_lesson`)并按日期范围返回,联查出班级名、课程名、教室名、教师称呼。停课(cancelled)课次在页面置灰标识。
- **给家长/学生的信息按最小化收**:教师只下发**姓 + 老师**(如「王老师」,`MiniappLessonView.teacherName`),**电话不再下发**;同学名单结构上就是 `List<String>` 姓名,接口层带不出学号。**同学名单为什么不给学号**:`POST /api/miniapp/students/bind` 是「学号 + 姓名」双因子校验,名单一旦同时给出这两个因子,等于把绑定凭据整包发出去,任何人都能绑走别人家孩子的档案。
- **我的同学**:一个学员可在多个班,所以一次返回「班级 → 名单」分组,页面顶部切班不再打接口;名单 200 条封顶。入口在课表页卡片头与学生档案卡。**监护人姓名/电话可由学生在编辑档案页自填或清空**,写入 `jp_student.guardian_name/guardian_phone`(镜像列);家长绑定走的是 `jp_student_parent.parent_phone`,所以这里改动能不能绑到人、由 PC 侧「家长联系人」决定,与它无关。**注意**:PC 维护家长联系人时 `StudentParentServiceImpl#syncStudentGuardian` 会用主家长行覆盖这两列。

## 企业端（收费与档案工作台）

- **不新建账号体系**:企业用户直接用管理后台的账号密码登录(`POST /api/auth/login`),
  `/api/auth/me` 返回 `portal=admin` 即进入企业工作台;学生/家长端仍是微信一键登录。
  详细设计与页面矩阵见 [`docs/小程序企业端收费台账.md`](小程序企业端收费台账.md)。
- **端判定与会话**:登录态快照新增 `loginMode`(`wechat` / `password`)与 `portal`。
  密码登录的 401 **不静默重登**——同一微信可能还注册过学生/家长账号,静默重登会悄悄换人;
  只有微信登录才走 `wx.login` 静默恢复。
- **覆盖的收费台账操作**:台账列表/筛选、单建账、按班建账、明细整表编辑、状态与备注、删除、
  收款登记/修改/删除、出勤查看/同步/覆盖/撤销、逐月发生额查看/覆盖/撤销/按出勤重算、重新取价、
  收费项目与四维价目维护。全部复用 PC 已有接口,金额一律服务端现算。
- **学员 / 家长 / 班级 / 入班**:收费台账依赖的底座也在小程序维护——学员分页列表与档案增删改、
  家长联系人增删改与主联系人切换、班级增删改、名册与入班/移出。学员列表 → 详情 → 编辑之间
  用一次性 storage 交接（后端没有按 id 取学员详情的接口）；班级详情走 `GET /education/classes/{id}`。
- **企业端不调用小程序专属接口**:首页、「我的」按 `portal` 分支,管理后台账号不会去打 `/api/miniapp/**`(那条链路只认 `miniapp:access` 与当前微信用户),避免 403。
- **权限**:读取接口登录即可（V113 起业务读码已清零，不再需要 `education:read`/`organization:read`）、
  台账写 `education:fee:write`、项目/价目配置 `education:fee:config:write`、
  学员与家长写 `education:profile:write`、班级与入班写 `education:class:write`;
  数据范围由后端 `InstitutionScopeGuard` / `CampusScopeGuard` 兜底。
  学员与班级的新增接口要求显式传 `institutionId`,小程序取 `/auth/me` 的 `institutionId`（为空回落 `orgId`）。

### 底部导航：看与做分开

| Tab | 企业账号 | 学生 / 家长 |
| --- | --- | --- |
| 首页 | **只读**：台账总数/在收/已结清 + 最近在收台账（点行进详情） | 今日课程与本周提示 |
| 工作台 | **操作入口页**：收费台账（含新增/按班建账/收款）、收费项目、价目、学员档案（含新增与家长联系人）、班级与入班 | 课程表、消息通知、同班名单、我的档案 / 绑定孩子 |
| 我的 | 身份、所属机构、退出 | 学员管理、本人档案、退出/注销 |

- 企业账号登录成功后直接落在**工作台**；学生/家长仍落首页。
- 首页不再放新增台账、收款、维护学员/班级的按钮；所有写入口集中在工作台及其下一级页面，避免看数据和改数据混在一起。
- 工作台只放一级入口，不重复列表页里已有的「新增台账 / 按班建账 / 新增学员 / 新增班级」，同一件事只保留一个入口。

## 登录与注册流程

```
启动 → 登录页 → uni.login 取 code
     → POST /api/auth/wechat/login
         ├─ registered=true  → 保存令牌 → 进入首页(微信免密登录)
         └─ registered=false → 注册页:选身份(学生/家长)+填昵称
                             → POST /api/auth/wechat/register → 自动建档+分配角色 → 进入首页

扫邀请码小程序码 → 直达注册页(scene 携带凭证编码)
     → GET /api/auth/register/precheck?code=scene
         ├─ 有效(INVITE) → 页面切邀请模式:展示机构与角色、免选身份
         │                 → POST /api/auth/wechat/register(带 invitationCode)
         │                 → 无密码建档(机构/角色由邀请码固定)+绑定微信 → 进入首页
         └─ 无效/非邀请码 → toast 原因,回落普通身份选择注册

扫公告小程序码 → 直达公告详情页(scene=a=公告ID)
     → GET /api/education/announcements/mine/{id}(首次打开即打读)
         ├─ 已登录且是接收人 → 展示正文
         └─ 未登录 → 请求层先静默重登;仍不行则跳登录页并记下这条地址,
                     登录/注册成功后自动回到这条公告(不会把人丢在登录页)
```

- 首次使用自动注册进系统(分配 MINIAPP_STUDENT / MINIAPP_PARENT 角色),之后微信免密登录。
- 管理员在「注册凭证」页创建邀请码(角色限学生/家长)后点「小程序码」生成太阳码,发给学员/家长扫码即可带角色注册;扫码注册与普通注册同样幂等、同样无密码、可自助注销。
- 一个微信对应一个账号,重复提交注册时按已注册用户直接登录(幂等)。
- 注册用户无密码,不能走账号密码登录;注册身份不可自行更改,需要调整找管理员。
- 退出登录调用 `/api/auth/logout`,服务端令牌(Redis)立即失效。
- 「我的」页提供**注销账号**:删除当前微信注册的账号及绑定关系,注销后同一微信重新登录即为全新未注册用户,可重新选择身份注册。注销操作不可恢复,仅小程序自助注册的账号可自助注销。

## 公告(消息中心)

- **两个页面**:`pages/messages/index` 收件箱(全部/未读双栏、下拉刷新、触底翻页),`pages/messages/detail` 详情。
  首页未读角标与列表都来自 `/api/education/announcements/mine/**`,**只认登录态、不挂权限码**——
  教师和家长走同一套接口,各自只拿得到自己那份。
- **打开即已读**:进详情页的那个 GET 就是打读动作,服务端靠 `read_at IS NULL` 保证只记首次时间。
- **公告类型中文名走 `announcement_type` 字典**,与 PC 端同源(`GET /api/system/dicts/options`),小程序不写字典值的静态中文映射。
- **管理员把公告发到家长群**:PC「公告中心」→ 查看 → 「生成小程序码」,拿到 PNG 与一段可直接粘贴的文案,
  图和文案一起发群。只有**已发布**的公告能出图(草稿没有接收人、撤回后扫码看不到内容,后端同一口径硬拦)。
- **老师在小程序里转发**:公告详情页支持「转发给好友/群聊」,卡片标题带公告标题,点开直达这一条而不是消息列表。
- **扫码却看不到正文**是正常结论:公告只发给解析出的接收人(已绑定学员的家长、已建档并绑定账号的教师),
  刚注册还没绑定学员的家长扫码会被引导到「绑定学员」页。

## 关键约定(与 frontend 保持一致)

- **ApiResponse 信封**:统一解包 `success/message/data`,`success=false` 抛 `ApiError`
  并展示中文 message。
- **雪花 ID 精度保护**:后端 19 位 Long 以 JSON 数字传输会超出 JS 安全整数范围,
  `utils/request.ts` 关闭自动 JSON.parse,先用与 `frontend/src/api/http.ts` 相同的
  正则把 `id/*Id/*Ids/*By` 字段转字符串再解析。
- **401 处理**:微信登录态在令牌失效(过期或被管理端踢下线)时,请求层先**静默重登一次**
  (`wx.login` + `/auth/wechat/login`,并发请求共用同一次重登)并重放原请求;重登仍失败才
  清除本地会话并跳回登录页。**账号密码登录(企业端)不走静默重登**,401 直接回登录页重新输密码。
- **未登录回跳**:`goToLogin()` 在跳登录页前把当前页地址存进 storage(`cornerstone.mini.pending-route`),
  登录/注册成功后 `takePendingRoute()` 取回并清除——扫码直达类入口不能把人丢在登录页。
- **会话只存 storage 一份**:`utils/auth-session.ts` 是唯一真相,`useAuthStore().isLoggedIn()`
  现读现判。**不要在 store 里再缓存一份 session 快照**——401 只会清 storage,内存副本清不掉,
  登录页与首页的 `onShow` 判据会互相矛盾、来回 `reLaunch`。

## 文件上传与头像

小程序端已接入**本人头像**，通用文件接口仍可作为其它业务的上传入口：`POST /api/system/files`（multipart，字段名 `file`，可选 `bizType`/`bizId`），返回 `data.fileId`。

### 头像(已接入)

- 上传：`uni.chooseImage`（`sizeType: ['compressed']`）拿到临时路径 → `utils/request.ts` 的 `requestUpload()` 调 `POST /api/auth/me/avatar`。该接口只要求登录，**不挂 `system:file:upload`**（学生/家长角色不一定持有该码）。
- 显示：`<image>` 发裸 GET、带不上 Authorization，必须先经 `utils/file-image.ts` 的 `fetchImageTempPath(fileId)`（内部 `uni.downloadFile` 带 header）换成本地临时路径；同一 fileId 在会话内缓存。
- 上传成功后 `auth.refreshUserInfo()` 并重拉头像；失败或无头像时保持显示名首字占位。

### 通用文件上传口径

- 用 `uni.uploadFile`，`header.Authorization` 与普通请求相同；返回体是字符串，仍需按「雪花 ID 精度保护」同一套规则把 `fileId` 转成字符串后再解析。
- **业务只保存 `fileId`**，不要保存临时路径、对象键或 URL；读取用 `GET /api/system/files/{fileId}/content`（后端校验机构），下载加 `?download=true`。
- 账号角色需具备 `system:file:upload`（种子已授给上线时的全部在职角色，新增角色在角色授权里勾「文件上传」）。
- 正式环境要在微信后台「开发管理 → 开发设置」把上传域名加入 **uploadFile 合法域名**（与 request 域名同源即可）。

## 常见问题

- **真机预览连不上后端**:手机无法访问电脑的 `127.0.0.1`,需把基地址改为电脑局域网 IP
  (通过 `VITE_API_BASE_URL` 环境变量或 `src/config.ts`),且手机与电脑同一网络;
  正式环境必须配置 HTTPS 域名并在小程序后台加入 request 合法域名。
- **code 无效/已被使用**:`wx.login` 的 code 一次性有效,报"微信登录失败"时重试即可,
  勿在前后端重复使用同一 code。
- **jwt 已过期**:默认 12 小时(系统配置 `auth.token.expire_hours`),过期后请求层会静默
  重登并重放请求,用户无感;只有重登失败(账号已注销、微信侧异常)才会退回登录页。

## 后续迭代方向

- 按身份开放功能:学生端/家长端各自的功能页,后端配套权限码(`miniapp:student:*` / `miniapp:parent:*`,在角色管理里追加即可)。
- 注册用户与教育域档案关联:家长可按 `jp_student_parent.parent_phone / wechat_no` 匹配孩子;学员需给 `jp_student` 加 `user_id` 列。
- 手机号一键获取(需企业认证小程序)。
- 后台用户管理按 `register_source` 筛选小程序注册用户。
- tabBar 图标(当前为纯文字 tab,可在 `pages.json` 的 `tabBar.list` 中补充 iconPath)。
- 需要输出 H5/App 时,uni-app 一套代码直接切换编译目标;届时 H5 端需后端补 CORS 配置。
