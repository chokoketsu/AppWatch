# AppWatch — 本地应用存在性监控

一个只有一件事要做的 Android 应用：**盯着某一个指定的软件在不在手机上**，通过桌面小组件显示状态，在它「从未安装变成已安装」时发一条通知。

全程本地运行，**不声明 `INTERNET` 权限，不引入任何网络库**。

---

## 一、监控目标：默认为 SystemHelper，可在应用内切换

打开 App 就能看到「监控目标」卡片，右侧有 **切换目标** 按钮，里面是内置的可选清单：

| 默认 | 其余可选 |
|---|---|
| `com.mobiletools.systemhelper`（SystemHelper · 联通设备注册） | 系统广告服务、小米分析统计、MSA 广告标识、应用中心推荐、快应用框架、小米视频 / 资讯 / 浏览器、游戏中心、游戏服务框架、服务与反馈、小米应用商店、讯飞输入法、小米清理 |

首次启动默认就是 `com.mobiletools.systemhelper`。

清单末尾还有一项 **「＋ 自行添加…」**，点开输入任意包名（如 `com.android.settings`）即可加入列表并立即开始监控。自定义项会排在内置项之后，选中后可以随时「移除此自定义目标」。

切换后：

- 立即以新目标重新检查一次
- **历史状态会被清空**（否则「目标 A 已安装 → 目标 B 未安装」会被误判成状态跳变、误发通知）
- 桌面小组件同步显示新目标

### 清单改在哪

`gradle.properties`：

```properties
appwatch.defaultPackage=com.mobiletools.systemhelper
appwatch.defaultName=SystemHelper
appwatch.candidates=com.mobiletools.systemhelper,com.miui.systemAdSolution,...
```

`candidates` **必须与 `AndroidManifest.xml` 里 `<queries>` 的 `<package>` 列表一致**。

自行添加的包名不需要改这里——manifest 里额外声明了 `<intent>`（MAIN + LAUNCHER），
所以普通带桌面图标的应用都能查到。**无图标的后台类系统应用仍然不可见**，
想监控那种必须把包名写进 `<queries>` 再重新编译。

原因：Android 11+ 只能查询到 manifest 声明过的包，没声明的包查询结果与"未安装"**无法区分**。所以想把某个包加进可选项，必须同时在两处登记，然后重新编译。运行期临时输入任意包名是查不到的——这是系统机制，不是实现缺陷。

---

## 二、它做了什么

| 能力 | 实现 |
|---|---|
| 每次打开 App 立即检查 | `MainActivity.onStart()` → `MonitorRepository.performCheck()`（IO 线程） |
| 后台每天中午检查一次 | WorkManager `PeriodicWorkRequest`，周期 24h，首次延迟到下一个 12:00 |
| 状态分三态 | `INSTALLED` / `NOT_INSTALLED` / `UNKNOWN`，异常绝不并入"未安装" |
| 状态变化通知 | 仅 `NOT_INSTALLED → INSTALLED` 时发一条 |
| 通知去重 | 持久化的 `lastValidStatus` 作为唯一依据，落盘后再判断，重复执行天然不重复通知 |
| 桌面小组件 | `RemoteViews` 渲染本地状态，检查完成后主动推送刷新；**在系统「小组件」里添加**，应用内不提供入口 |
| 重启恢复 | WorkManager 自身持久化 + `BootReceiver` 幂等补排程 |
| 完全离线 | manifest 无 `INTERNET`，无网络 SDK，无云端 |

### 状态与通知对照

| 变化 | 小组件 | 通知 |
|---|---|---|
| 未安装 → 已安装 | 刷新 | **发** |
| 已安装 → 未安装 | 刷新 | 不发 |
| 未安装 → 未安装 | 刷新 | 不发 |
| 已安装 → 已安装 | 刷新 | 不发 |
| 查询异常 | 显示「检查异常」 | 不发，且**不覆盖**上一次有效状态 |

---

## 三、权限说明

`AndroidManifest.xml` 里只声明了两个权限：

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

**没有 `INTERNET`，也没有 `QUERY_ALL_PACKAGES`。** 后者用 `<queries>` 精确声明单个包替代。

最终 APK 里会多出三个权限，它们来自 WorkManager 的 manifest 合并，无法避免：

| 合并进来的权限 | 来源 | 能否传数据 |
|---|---|---|
| `WAKE_LOCK` | WorkManager 执行期间持有 CPU | 否 |
| `ACCESS_NETWORK_STATE` | WorkManager 的约束追踪器需要读网络状态 | 否，只读状态，不能发起连接 |
| `FOREGROUND_SERVICE` | WorkManager 的加急任务分支 | 否 |

真正决定能不能联网的是 `INTERNET`，它不在清单里，所以**这个 App 在系统层面就没有联网能力**。

> 若一定要连这三个也去掉，就得放弃 WorkManager 改用 `AlarmManager` + 静态广播；
> 代价是后台可靠性下降（国产 ROM 对 AlarmManager 的限制更强）。当前实现按需求优先选 WorkManager。

### 包可见性

`<queries>` 只声明了 `com.mobiletools.systemhelper` 一个包，所以：

- 能查询目标包 ✅
- 查不到、也扫不到其他任何应用 ✅

副作用：如果把 `appwatch.targetPackage` 改成别的包，**必须重新编译**（因为 `<queries>` 是编译期写死进 manifest 的）。运行期临时输入任意包名是查不到的——这是 Android 11+ 的机制决定，不是实现缺陷。

---

## 四、文件清单

```
AppWatch/
├── gradle.properties                     ★ 改监控目标就在这里
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml             版本目录（AGP 9.4.1 / Kotlin 2.2.10 / Compose BOM 2026.02.01）
└── app/
    ├── build.gradle.kts                  注入 targetPackage → manifest 占位符 + BuildConfig
    └── src/main/
        ├── AndroidManifest.xml           无 INTERNET；<queries>；组件注册
        ├── java/com/koketsu/appwatch/
        │   ├── AppWatchApp.kt            建通知渠道 + 幂等排程
        │   ├── MainActivity.kt           onStart 立即检查；权限与设置入口
        │   ├── core/
        │   │   ├── Targets.kt            目标包名/名称统一出口
        │   │   └── TimeFmt.kt            本地时间格式化
        │   ├── data/
        │   │   ├── MonitorStatus.kt      三态枚举
        │   │   ├── CheckResult.kt        单次查询结果
        │   │   ├── PackageChecker.kt     PackageManager 查询 + 异常归类
        │   │   ├── MonitorStore.kt       SharedPreferences 持久化
        │   │   └── MonitorRepository.kt  落盘 → 判定跳变 → 通知 → 刷小组件
        │   ├── work/
        │   │   ├── CheckScheduler.kt     WorkManager 排程（下一个中午）
        │   │   └── MonitorWorker.kt      后台检查
        │   ├── notify/Notifier.kt        通知渠道与发送
        │   ├── receiver/BootReceiver.kt  开机/更新后恢复
        │   ├── widget/
        │   │   ├── MonitorWidgetProvider.kt
        │   │   └── WidgetRenderer.kt     RemoteViews 渲染
        │   └── ui/
        │       ├── UiState.kt
        │       ├── MonitorScreen.kt      Compose 主界面
        │       └── theme/                Color / Type / Theme
        └── res/
            ├── layout/widget_monitor.xml 小组件布局
            ├── xml/monitor_widget_info.xml
            ├── drawable/                 背景、状态圆点、通知图标（全部矢量，无位图）
            └── values/                   strings / colors / themes
```

---

## 五、构建

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

环境：JDK 17 运行 Gradle；Gradle 9.6.0；AGP 9.4.1；compileSdk 37；minSdk 24；targetSdk 37。

已实测：`BUILD SUCCESSFUL`，38 个任务全部通过。

---

## 六、验收对照

| 验收项 | 结论 |
|---|---|
| 每次打开 App 立即检查 | ✅ `MainActivity.onStart()` 直接触发，不等后台任务 |
| 后台每天中午附近检查一次 | ✅ WorkManager 24h 周期 + 延迟到下一个 12:00 |
| 未安装→已安装时发通知 | ✅ `MonitorRepository` 中唯一发通知的分支 |
| 不会重复通知 | ✅ 先落盘再判定；`lastValidStatus` 变 INSTALLED 后，下次是 INSTALLED→INSTALLED |
| 小组件显示实时状态 | ✅ 全部读本地存储，检查后主动 push 刷新 |
| 后台检查后刷新小组件 | ✅ `performCheck()` 末尾调用 `WidgetRenderer.refreshAll()` |
| 重启后恢复后台任务 | ✅ WorkManager 持久化 + `BootReceiver` 幂等补排程 |
| 检查异常不误判为未安装 | ✅ 只有 `NameNotFoundException` 才是 NOT_INSTALLED，其余一律 UNKNOWN |
| Manifest 无 INTERNET | ✅ 已用 aapt2 核验 |
| 不扫描其他应用 | ✅ 只 `getPackageInfo` 单个包，`<queries>` 只声明一个包 |

---

## 七、按需求刻意没做的事

不做拦截/阻止安装、不卸载其他应用、不要 Root、不接 ADB、不做流量监控与行为分析、不做安装来源追踪与签名审计、不做全量扫描、不做云同步与远程控制。

这是一个存在性监控器，不是一个手机安全管理软件。

---

## 八、免责声明

<sub>本项目仅读取「某个包名是否存在」，不做行为分析、不做安全判定、不具备卸载/禁用/拦截能力。点开可看完整条款。</sub>

<details>
<summary><b>展开完整免责条款（7 条）</b></summary>

1. 本应用仅用于监控你本人拥有或有权管理的设备，不得用于监控他人设备。
2. 应用只读取「某个包名是否存在」，不具备卸载、禁用、拦截、阻止安装等任何能力。界面上出现的「恶意程序」等说法，仅指你自己指定要留意的目标，本应用不做行为分析、不做安全判定、不给出任何安全性结论。
3. 应用不声明网络权限，不联网、不上传、不收集任何数据；所有状态仅保存在本机，卸载应用即随之消失。
4. 受 Android 包可见性、厂商省电策略等系统机制影响，检查可能延迟或漏报，本应用不对监控结果的及时性与完整性作任何保证。
5. 是否移除某个应用由你自行判断并承担后果；因使用本应用造成的设备异常、数据丢失、业务中断等，作者不承担任何责任。
6. 被监控应用及其名称、商标、代码的知识产权归各自权利人所有，本项目与其权利人无任何关联。
7. 本项目以 MIT 协议开源，按「现状」提供，不附带任何明示或默示担保。

</details>

---

<sub>作者 <a href="https://github.com/chokoketsu">@chokoketsu</a> · 代码与文档在 AI 辅助下完成，功能均经模拟器与真机实测</sub>

<sub>AppWatch · 应用存在性监控 · com.mobiletools.systemhelper · 小米预装应用 卸载 · MIUI 广告 清理 · bloatware monitor</sub>
