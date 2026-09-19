# AGENTS.md — 项目经验与约定

面向 AI agent / 新协作者的关键知识。面向用户的功能说明见 [README.md](README.md)。
**本文件不写任何账号口令。**

## 项目血缘

GBU-Course-Alert（大湾区大学课表，iAAA+教务 HTML）为骨架，数据层整体替换为
喜鹊儿协议（`data/remote/xq/`）。UI/提醒/小组件/ICS/更新层保持 GBU 结构。

## 喜鹊儿协议要点（全部实测验证，勿凭想象改）

- **九键信封**：`param/param2/timestamp/echo/encrptSecretKey/xqerSign/token/appinfo/appsjxh`，
  POST form。签名实现 `XqSigner`，固定向量单测锁行为（与 Dart/Python/Node 三版逐字节一致）
- **param2 删位 {2,9,16,24}**：Java `split("")` 前置空元素 off-by-one；管理端不校验，
  教务子系统严格校验
- **登录后管理端请求**必须追加 `&xqerxm={转义姓名}&uuid={uuid}&md5=`（`signedBodyWithProfile`），
  缺了回「口令失败」
- **子系统请求**只发 param/param2，加密原文**剔除 action/step/isZLPar**（路由只进 URL），
  多拼字段被静默拒（0 字节响应）
- **登录**：`getLoginInfoNew` + 明文密码 + `pwdsfzm=1`（AES 密文反而被拒）；
  登录不重试（防账号锁定）
- **api.login() 不装配内部会话 —— 之后必须 adopt(session)**，否则后续请求空会话 → HTTP 500
- 服务端相同缓存键 ~1 秒防重，连发同参请求会失败
- 响应形态混乱：text/html 里裹 JSON / HTML 实体 / URL 编码 / 整包 AES base64 /
  `*_desn` 字段加密 —— 全走 `XqWire.decodeResponse`，别绕过

## 周次与学期语义（用户定案，勿改）

- 学期 dm：`yyyy+q`，q=0 第一学期（秋）、1 第二学期（春）
- 服务端学期列表只给当前 2 个；更早的按 dm 规则往前推（`XqSemesterRules.earlierSemesters`）
- **`qssj/jssj` 是响应所在周的起止日期，不是学期起点！**
  第 1 周周一 = `qssj - (zc-1)` 周（实测 2026-09-15：qssj=09-14、zc=3 → 第1周周一=08-31 ✅）
- 假期：服务端回 `zc > maxzc` 且 weekN 全空。**假期就显示假期，不回退到有课周**
- 排查「周次不对」：先抓课表响应看 zc 与 qssj，按上面公式手算

## 课表字段实测形态（珠江学院）

- 课程条目：`kcmc/rkjs(教师)/skdd(教室)/jcxx("3-4"字符串节次)/skzs(周次)/kcdm/skbj`
  —— 数字节次 qsjc/jsjc **不存在**，从 jcxx 提取（`Course.fromJson` 已兼容两套）
- `sjhjinfo` **为空**（本校实测）：节次时间用内置 `TimeGrid.SCHOOL_DEFAULT`
  华珠作息表（用户口述定案 2026-09-15：节1 8:30/节5 14:30/节9 18:40 起，
  小课 40min 课内间 10min 大课间 20min；晚上大课 80min 连上大课间 10min，
  末节 21:30 下课）
- `zc` 可能是 `"03"` 字符串形态（前导零），解析走容错 int

## 工程坑（已踩过，勿再踩）

- **JVM 单测的 org.json 是 stub**：`testImplementation("org.json:json:...")` 必须保留，
  否则 JSON 解析静默失败（异常被解码管线的 catch 吞掉，表现为「服务器返回的不是可识别的数据」）
- Kotlin 字符串模板 `"$what失败"` 会把 `what失败` 当标识符 → 必须写 `"${what}失败"`
- 同包嵌套类 import 要写全限定名（`import pkg.XqSessionCodec.Session`）
- git-bash 里 `ren` 删过 manifest 尾部标签会留悬空 `<activity` —— 删 activity 后检查 manifest 完整性
- 测试向量手抄会抄错字符（实测漏一个 `6`）——向量从原文件程序化提取，不要手打
- **周次文案 `1-5,7-17周` 是区间+枚举混合**：逗号分段逐段解析（`parseWeeksOf`），
  勿用「抓全部数字」法（会丢 2/3/4 周 → 点「本周」课程消失，真机踩过）
- **rwh 是课程唯一标识，不含周次**（课程代码+教学班+学期+周几+节次）。
  同门课不同周次段共享 rwh（颜色/详情一致）；周次差异由 Meeting 行携带。
  rwh 混入周次会导致同门课多色卡片（真机踩过）
- 课块定位按**节次行**直接对齐，勿按时间插值（华珠课全整节对齐，插值会把
  11:50 下课的块压到 1.5 格高）
- 课块渲染用 Box+clip，勿用 Card（12 节×N 列网格里 Surface 开销显著，滑动卡）
- **UI 以 Vetta 画布设计为准**（用户最新定案）：使用 Compose Foundation 自定义控件，不使用 Miuix 或 Material 的默认视觉组件。
  - 统一由 `CampusTheme`、`CampusControls` 和线性 `CampusIcons` 驱动配色、字号、控件尺寸与交互。
  - 首页仅保留画布中的日期条，不添加「上一周 / 下一周 / 回到今天」按钮。
  - 周课表必须先测量课程名称、教室、教师的实际文字高度，再统一调整节次行高；禁止固定高度裁切或以缩小字号掩盖溢出。
  - 保留普通返回与手势返回统一的右滑淡出过渡。
- **小组件是 RemoteViews 方案**（`TodayWidgetProvider`），不用 Glance：Glance 会话在
  「App 运行中添加小组件」时会死锁在 loading（真机踩过，超时兜底也救不了）。
  RemoteViews 布局**只能用白名单控件**（Layout/TextView/ImageView…），`<Space>` 不在
  白名单 → 桌面 inflate 抛异常显示「载入窗口小部件时出现问题」（真机踩过）；
  占位用 0dp+weight 空 TextView。主题 attr（?attr/colorOnSurface）也解析不了，
  显式色 + values-night 深浅色资源。现为不透明纸白 / 墨绿底、24dp 圆角的「日程手账」。
  - Android 12+ 与基础 provider 配置均指向 `widget_agenda`，禁止恢复 Glance loading 初始页。
  - 可滚动课程用 ListView；ScrollView 也不在 RemoteViews 白名单中，不能使用。
  - Android 12+ 用 RemoteCollectionItems，Android 8–11 用受 BIND_REMOTEVIEWS 保护的适配服务。
  - 日期按 appWidgetId 独立保存；未指定日期时跟随今天，固定浏览日期不随跨日改变。
  - 添加入口必须展示预览、请求结果和手动添加说明；requestPinAppWidget 返回 true 仅表示请求被接受。

## 真机调试

- 凭据存 EncryptedSharedPreferences，无法从外部写入；登录走 app 内输入
- 真实探针：`XqE2eProbeTest`（读本地 secrets env，无凭据自动 skip）；
  每轮协议改动跑一次，不靠 mock 绿

## 验证循环

```
./gradlew testDebugUnitTest        # 115 个，含课表布局与小部件日期/资源测试
./gradlew assembleDebug assembleRelease
```

## 项目命名

- 项目/仓库：`ZjC-Course-Alert`（Zj = 珠江，C = Course/课表；**不是** ZJKB）
- 包名/applicationId：`com.rentz.zjkb`（历史沿用，不改）
- 应用名：华珠课表
