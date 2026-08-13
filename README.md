# 提瓦特祈愿簿

提瓦特祈愿簿是一款用于查看、管理和统计《原神》祈愿记录的 Android 应用，应用包名为 `com.YSNB.yuanshen`。

应用支持通过米哈游官方通行证页面或米游社扫码完成登录，读取账号绑定的《原神》角色，并由用户手动同步祈愿记录。祈愿数据保存在本机，可按卡池查看历史、垫抽进度和五星统计。

## 主要功能

- 通过米哈游官方通行证页面登录，支持米游社扫码登录。
- 自动识别账号绑定的《原神》角色，支持多角色切换。
- 由用户主动同步祈愿记录，进入应用和切换角色时不会自动同步。
- 分别展示角色活动祈愿、武器活动祈愿、常驻祈愿、初行者推荐祈愿和集录祈愿。
- 记录页支持按卡池筛选，并可在卡片视图与垫抽视图之间切换。
- 概览页展示各卡池的累计抽数、当前垫数以及五星、四星数量。
- 统计页展示五星平均抽数和五星记录，支持按时间正序或倒序排列。
- 支持导入通用抽卡记录 JSON 文件；重复或冲突批次不会覆盖本地记录。
- 登录凭证使用 Android Keystore 加密保存，祈愿记录通过 Room 保存在本机。

## 运行环境

- Android 8.0（API 26）及以上
- Android SDK 36
- JDK 17
- Android Gradle Plugin 9.1.0

## 构建项目

克隆仓库：

```powershell
git clone https://github.com/max-whisky/YSNB.git
cd YSNB
```

在项目根目录创建不纳入版本控制的 `keystore.properties`：

```properties
storeFile=你的密钥库路径
storePassword=你的密钥库密码
keyAlias=你的密钥别名
keyPassword=你的密钥密码
```

Windows 下构建 Debug APK：

```powershell
.\gradlew.bat assembleDebug
```

构建 Release APK：

```powershell
.\gradlew.bat assembleRelease
```

APK 输出目录：

```text
app/build/outputs/apk/
```

`keystore.properties`、`*.jks` 和 `*.keystore` 已在 `.gitignore` 中排除，请勿将签名密码或密钥库提交到仓库。

## 使用方法

1. 打开应用，通过官方通行证页面或米游社扫码完成登录。
2. 选择需要查看的《原神》角色。
3. 点击主界面的“同步”，由用户主动获取最新祈愿记录。
4. 在底部导航栏的“概览”“记录”和“统计”页面查看数据。
5. 如需导入已有数据，可在“设置”中选择通用抽卡记录 JSON 文件。

## 数据与隐私

- 账号密码仅在米哈游官方通行证页面中输入，应用不直接读取或保存账号密码。
- 应用只保存读取祈愿记录所需的最少登录信息，并使用 Android Keystore 加密。
- 祈愿记录保存在设备本地，项目不提供云端上传或跨设备同步服务。
- 退出登录会清除本机保存的登录凭证，但会保留已同步的祈愿记录。

## 项目结构

```text
app/src/main/java/com/example/yuanshen/
├─ core/       数据模型、网络请求与接口配置
├─ data/       登录、祈愿同步、JSON 导入与 Room 存储
├─ domain/     垫抽时间线与统计计算
├─ ui/         页面状态、记录适配器与卡池样式
└─ MainActivity.java

app/src/main/res/
├─ layout/     页面和列表布局
├─ drawable/   界面背景与图形资源
├─ menu/       底部导航栏
└─ values/     字符串与主题
```

## 免责声明

本项目是非官方开源工具，与米哈游及其关联公司无关。《原神》及相关名称、商标和素材归其权利人所有。相关接口或登录流程发生变化时，部分功能可能暂时不可用。请合理控制同步频率，并自行承担使用风险。

## 许可证

本项目基于 [Apache License 2.0](LICENSE) 开源。
