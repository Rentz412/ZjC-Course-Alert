# 华珠课表 · ZjC

华南农业大学珠江学院课表 App：通过喜鹊儿（金智教务移动端）协议自动拉取课表、
还原真实作息时间、周课表视图与上课提醒。

> 非官方客户端，仅账户持有人个人使用。与学校及金智教育无任何关联。

## 功能

- **学号密码一键登录**（喜鹊儿协议，学校内置为华南农业大学珠江学院，无需配置任何地址）
- **课表同步**：当前周真实课表（课程/教师/教室/周次），Room 离线缓存，自动重登重试
- **今日页**：上课中/课间倒计时卡片 + 今日课次列表
- **周课表**：大节网格对齐、今日高亮、周切换
- **上课提醒**：精确闹钟，提前分钟数可调，开机自启重排
- **今日休息**：一键静音当天全部提醒，仅当日生效，跨零点自动失效
- **桌面小组件**（Glance）：今日课程 + 下节课高亮
- **导出到日历**：当前学期课表导出为 `.ics`
- **应用内更新**：GitHub Release 检查更新，SHA-256 校验后转交系统安装器
- **首次使用向导**：登录 → 权限 → 提醒 三步
- **后台同步**：WorkManager 每 12h 一次（低频防风控）

## 构建

```bash
./gradlew assembleDebug          # 调试包
./gradlew testDebugUnitTest      # 单元测试（签名固定向量 + 学期语义 + 真实探针）
```

> `XqE2eProbeTest` 是真实端到端探针：需要本地凭据文件
> `%LOCALAPPDATA%/hermes/secrets/xiqueer-runtime.env`，文件不存在时自动跳过。

Release 签名与 CI 发布流程同 [GBU-Course-Alert](https://github.com/HuanLinOTO/GBU-Course-Alert)。

## 数据源说明

- 协议：喜鹊儿移动端（九键签名信封：param/param2/timestamp/echo/encrptSecretKey/xqerSign）
- 管理端：`https://api.xiqueer.com/manager`（学校发现、登录）
- 教务子系统：登录后由服务端下发（课表 `mycourseschedule.action`）
- 周次语义：服务端 `zc` 为准；`qssj/jssj` 是**响应所在周**的起止日期，
  第 1 周周一 = `qssj - (zc-1)` 周
- 作息网格：`sjhjinfo` 为空时（本校实测如此）按 `jcsw/jcxw/jczw` 分节数推导

## 隐私

- 凭据仅存本机（EncryptedSharedPreferences / Android Keystore 加密）
- 不写日志、不上传任何数据到第三方服务器
- 课表明文 HTTP 通道与官方 App 拓扑一致（`http:801`），设置里可收紧

## 技术栈

Kotlin 2.4 · Jetpack Compose (BOM 2026.08) · Material 3 · OkHttp · Room · WorkManager + AlarmManager · Glance Widget · AGP 9.4
