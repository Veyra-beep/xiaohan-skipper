# 小霜跳过 · Xiaohan Skipper

自动跳过 App 开屏广告的 Android 应用。用系统 **无障碍服务（AccessibilityService）** 识别「跳过」按钮并自动点击，**不需要 root、不需要 Xposed、不联网、不申请任何网络权限**。

> 参考「李跳跳」的思路重写：纯本地规则、几百 KB 体积、数据物理上出不了设备。

| | |
|---|---|
| 包名 | `com.xiaohan.skipper` |
| 版本 | 1.3（versionCode 4） |
| 最低系统 | Android 7.0（API 24） |
| 目标 SDK | 34（Android 14） |
| 体积 | 约 141 KB |
| 权限 | **只有 `VIBRATE`（可选震动反馈）** —— 没有 INTERNET |
| 签名 | 自签 v2 + v3 |

---

## 一、功能

- **自动点「跳过」**：App 切到前台的保护窗口（默认 12 秒）内自动查找并点击跳过按钮，过窗口即停手，避免在正常页面误点。
- **多规则匹配**：文字 / contentDescription / view id / 右上角纯数字倒计时，四级兜底。
- **点击方式双保险**：优先对可点击祖先节点 `ACTION_CLICK`；拿不到节点时退化为 `dispatchGesture` 坐标点击。
- **防误触**：只在刚切前台时工作、一次前台切换最多点一次、宽松规则必须落在右上角、跳过系统界面和输入法。
- **按应用控制**：全部应用生效，或只对勾选的应用生效（列表从已安装应用里勾选）。
- **可调参数**：保护窗口 3–30 秒、点击延迟 0–2 秒（应对「跳过按钮 2 秒后才可点」的广告）。
- **运行日志**：最近 300 条命中记录，界面内可查看，便于排障。

---

## 二、安装

1. 到 [Releases](../../releases) 下载最新的 `XiaohanSkipper-vX.Y.apk`，在手机上安装。
   - 系统提示「未知来源」时选「允许本次安装」。
   - 小米/红米需关闭「纯净模式」；华为/荣耀需关闭「纯净模式」；OPPO/vivo 可能要求输入锁屏密码。
2. 打开 App → 点「去开启无障碍服务」→ 在无障碍列表里找到 **「小霜跳过 · 开屏广告」** → 打开。
   - Android 13+ 会弹「受限设置」被拦截，去 设置 → 应用 → 小霜跳过 → 右上角 ⋮ → 「允许受限设置」，再回来开启。
3. **保活**（强烈建议，否则会被系统清理）：
   - 设置 → 应用 → 小霜跳过 → 电池 → 选「无限制 / 不优化」；
   - 最近任务里给小霜跳过加锁；
   - 部分机型需在「自启动管理」里放行。

装好后打开一个有开屏广告的 App 验证：开屏广告右上角出现「跳过」时会被自动点掉。

---

## 三、从源码构建

**不需要 Gradle、不需要 Android Studio**，只用 JDK + Android SDK build-tools，靠 `aapt2 → javac → d8 → zipalign → apksigner` 手工流水线（见 `build.ps1`）。

### 1. 准备工具链目录

构建脚本约定工具链放在 `toolchain/` 下：

```
toolchain/
├─ jdk21/                      # JDK 21
└─ sdk/
   ├─ build-tools/
   │  └─ 35.0.0/               # aapt2 / d8 / zipalign / apksigner
   └─ platforms/
      └─ android-34/           # 含 android.jar
```

下载源（国内镜像，直连可用）：

```powershell
# JDK 21（华为云镜像，约 192 MB）
# https://mirrors.huaweicloud.com/openjdk/21.0.2/openjdk-21.0.2_windows-x64_bin.zip

# Android build-tools 35（腾讯云镜像，约 57 MB）
# https://mirrors.cloud.tencent.com/AndroidSDK/build-tools_r35_windows.zip
# 注意：zip 解压后内层目录名是 android-15，需改名为 35.0.0

# Android platform 34（腾讯云镜像，约 60 MB）
# https://mirrors.cloud.tencent.com/AndroidSDK/platform-UpsideDownCake_r04.zip
# 解压后目录名是 android-14，需改名为 android-34
```

也可以用官方 `sdkmanager` 装 `build-tools;35.0.0` 和 `platforms;android-34`，效果一样。

> ⚠️ **build-tools 请用 35.0.0，不要用 34.0.0**。34.0.0 的 `d8` 处理含匿名内部类的 class 会内部崩溃：
> `java.lang.NullPointerException: Cannot invoke "String.length()" because "<parameter1>" is null`。
> 本项目已实测踩过这个坑，换 35.0.0 直接通过。

### 2. 构建

```powershell
pwsh -File .\build.ps1 -Clean
```

脚本会依次输出 `[STAGE]` 日志，最后 `OK -> ...\XiaohanSkipper.apk`。

- 版本号只由 `app/AndroidManifest.xml` 的 `versionCode` / `versionName` 决定。
- 首次构建如果找不到 `keystore.jks`，脚本会自动用 `keytool` 生成一个自签证书（口令见脚本内）。
- 正式分发请换成自己的 keystore 并妥善保管；**换签名后覆盖安装会失败，需先卸载旧版**。

### 3. 换桌面图标（可选）

图标由 `app/tools/make_icon.ps1` 生成，输入是 `app/tools/icon-src/source.jpg`：

```powershell
# 只预览到临时目录，不改 res
pwsh -File .\app\tools\make_icon.ps1 -Preview

# 正式写入 app/res/mipmap-*/ic_launcher.png
pwsh -File .\app\tools\make_icon.ps1

# 参数：-CropX / -CropY / -CropSide 控制方形裁切窗口，-CornerRadius 控制圆角（0 = 直角）
pwsh -File .\app\tools\make_icon.ps1 -CornerRadius 0
```

处理流程只有三步：居中方形裁切 → 单次双三次等比缩放（不拉伸）→ 圆角遮罩（路径外透明）。

---

## 四、项目结构

```
skipper/
├─ app/
│  ├─ AndroidManifest.xml
│  ├─ res/
│  │  ├─ mipmap-*/ic_launcher.png      # 5 个密度图标
│  │  ├─ values/strings.xml
│  │  └─ xml/accessibility_service_config.xml
│  ├─ src/com/xiaohan/skipper/
│  │  ├─ MainActivity.java             # 界面（纯代码构建，无布局 XML）
│  │  ├─ SkipService.java              # 无障碍服务：轮询 + 查找 + 点击
│  │  ├─ Rule.java                     # 单条规则 + 右上角判定
│  │  ├─ Rules.java                    # 内置规则表
│  │  ├─ Prefs.java                    # 设置存储
│  │  └─ SkipLog.java                  # 环形日志
│  └─ tools/
│     ├─ make_icon.ps1                 # 图标生成
│     ├─ make_icon_whale.ps1           # 备用的纯代码绘制版（早期方案）
│     ├─ ascii_preview.ps1             # 图标字符画判形工具
│     └─ icon-src/source.jpg
├─ build.ps1                           # 手工构建流水线
└─ dist/                               # 发布产物 + 安装说明
```

---

## 五、追加检测规则

内置 5 条规则（`app/src/com/xiaohan/skipper/Rules.java`），命中即停：

| # | 类型 | 正则 | 要求右上角 |
|---|---|---|---|
| 1 | text | `跳\s*过\|略\s*过\|skip\|SKIP` | 否 |
| 2 | text | `关闭广告\|关\s*闭\|close ad\|Close` | 是 |
| 3 | contentDescription | `跳\s*过\|skip\|关闭广告\|关闭` | 是 |
| 4 | view id | `(?i).*(skip\|skip_?btn\|skip_?ad\|ad_?skip\|close_?ad\|tt_?skip\|ksad_?skip\|gdt_?skip\|splash_?skip).*` | 是 |
| 5 | text（兜底） | `^\s*\d{1,2}\s*[sS秒]?\s*$`（右上角纯数字倒计时） | 是 |

追加一条只要加一行：

```java
LIST.add(new Rule("某某广告", "跳过|跳过广告", null, null, false));
//                                   文字   描述     id   是否要求右上角
```

改完重新 `build.ps1` 即可。当前版本规则写死在代码里；要做在线热更新规则，得另加下载与解析层（这会引入网络权限，和「不联网」的设计取向相悖，故未做）。

---

## 六、原理与为什么不联网

无障碍服务收到 `TYPE_WINDOW_STATE_CHANGED` / `TYPE_WINDOW_CONTENT_CHANGED` 后，在保护窗口内用 `120ms`（前台后 5 秒内）/ `320ms`（之后）的节奏轮询当前窗口的节点树，逐条规则匹配 `text`、`contentDescription`、`viewIdResourceName`，命中就对节点（或其可点击祖先）发 `ACTION_CLICK`；节点拿不到时用 `dispatchGesture` 按坐标落点。

因为整个判断完全发生在本地、规则表编进 APK，所以**应用不需要 INTERNET 权限** —— 没有网络权限的 App 在 Android 上物理上无法外发任何数据。这也是敢让人打开无障碍服务的底气：无障碍权限能读屏幕内容，一旦配合联网就是隐私灾难。

---

## 七、已知限制

- **无真机回归**：本项目在无 Android 设备的机器上开发，只做了构建期静态校验（`aapt2 dump badging` / `apksigner verify` / APK 内容比对），实际跳过效果需自行装机验证。
- 部分 App 会**检测无障碍开启状态后换用另一套 UI 或干脆不渲染跳过节点**，这类情况无解（这也是原版李跳跳停更后逐渐失效的原因之一）。
- 自绘广告 / 摇一摇广告（点击任意位置跳转）不在本方案的覆盖范围。
- 强杀后台的机型（部分 MIUI / EMUI / ColorOS）需要手动做保活设置，否则服务被清理后不再生效。

---

## 八、免责声明

仅供个人学习与自用，用于跳过自己设备上打扰性的开屏广告。请勿用于绕过付费内容、刷量或任何违反目标应用服务条款的用途。使用本软件产生的一切后果由使用者自行承担。

## 许可证

MIT