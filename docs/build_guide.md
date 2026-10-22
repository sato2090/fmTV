# FongMi TV (蜂蜜电视) 本地打包与定制指南

本文档详细记录了在本地 Linux (Ubuntu 22.04+) 环境下，对 **FongMi TV (Leanback 电视版)** 进行源码分析、国内网络适配、纯净去联网依赖，并最终打包与签名生成 Android TV 专用安装包 (`.apk`) 的完整步骤。

---

## 目录
- [一、 基础开发环境要求](#一-基础开发环境要求)
- [二、 核心依赖与原项目架构说明](#二-核心依赖与原项目架构说明)
- [三、 关键定制化修改说明](#三-关键定制化修改说明)
- [四、 本地打包与构建方案](#四-本地打包与构建方案)
  - [方案 A：基于发布包的快速注入重打包（生产推荐）](#方案-a基于发布包的快速注入重打包生产推荐)
  - [方案 B：从源码编译打包流程](#方案-b从源码编译打包流程)
- [五、 安装包验证与部署安装](#五-安装包验证与部署安装)
- [六、 常见踩坑与问题排查 (FAQ)](#六-常见踩坑与问题排查-faq)

---

## 一、 基础开发环境要求

| 组件 | 最低版本要求 | 本地安装与配置命令 |
| :--- | :--- | :--- |
| **操作系统** | Ubuntu 22.04 LTS+ / Linux x86_64 | - |
| **JDK** | **OpenJDK 21** (强制要求，AGP 9.4 依赖) | `sudo apt install openjdk-21-jdk` |
| **Android SDK** | `platforms;android-37.0` | `sdkmanager "platforms;android-37.0"` |
| **Build Tools** | `build-tools;36.0.0` (含 `zipalign`, `apksigner`) | `sdkmanager "build-tools;36.0.0"` |
| **工具包** | `apktool` (v3.0.3+), `keytool`, `python3` | 见后文下载与配置 |

### 环境变量设置
在终端执行编译或打包前，请确保设置了正确的 JDK 21 路径：
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_SDK_ROOT=/home/admin/data/flutterDev/android-sdk
export PATH=$ANDROID_SDK_ROOT/build-tools/36.0.0:$PATH
```

---

## 二、 核心依赖与原项目架构说明

### 1. 为什么直接 `git clone` 无法直接通过 Gradle 编译？
* **核心 AAR 依赖被作者 `.gitignore`**：
  原作者将核心播放层库全部命名为 `lib-*-release.aar`（共 20 个，包含魔改版 ExoPlayer/Media3、mpvplayer 播放引擎、FFmpeg 硬件解码胶水层等），并且将其写入了 `.gitignore`。
* **作者本地开发链路**：
  原作者在另一套私有项目 `media` 中单独编译这些 AAR 库，并通过 `move.bat` 脚本将 20 个 AAR 文件拷贝至 `TV/app/libs`。
* **官方对外发布渠道**：
  作者通过 GitHub Release 仓库直接对外发布编译并混淆好的 release APK 安装包。

### 2. 涉及的 20 个核心播放层 AAR 列表
```text
lib-common-release.aar
lib-container-release.aar
lib-database-release.aar
lib-datasource-release.aar
lib-datasource-okhttp-release.aar
lib-datasource-rtmp-release.aar
lib-decoder-release.aar
lib-decoder-ffmpeg-release.aar
lib-decoder-mpegh-release.aar
lib-effect-release.aar
lib-exoplayer-release.aar
lib-exoplayer-dash-release.aar
lib-exoplayer-hls-release.aar
lib-exoplayer-rtsp-release.aar
lib-exoplayer-smoothstreaming-release.aar
lib-extractor-release.aar
lib-mpvplayer-release.aar
lib-session-release.aar
lib-ui-release.aar
lib-ui-danmaku-release.aar
```

---

## 三、 关键定制化修改说明

本项目针对国内电视网络环境进行了深度纯净化处理，修改主要体现在以下方面：

### 1. 默认直播订阅源改为国内直连 M3U
* **修改目标**：解决原版使用 GitHub 原始地址导致国内无代理/无 IPv6 无法播放的问题。
* **新订阅源地址**：`https://gitee.com/kinnday/publicInfos/raw/master/ipm3u`（涵盖央视高清各频道及全国主要卫视）。
* **代码修改**：[`app/src/main/java/com/fongmi/android/tv/bean/Config.java`](file:///home/admin/data/flutterDev/miTv/fmTV/app/src/main/java/com/fongmi/android/tv/bean/Config.java)
  ```java
  public static final String DEFAULT_LIVE = "https://gitee.com/kinnday/publicInfos/raw/master/ipm3u";

  public static Config live() {
      String url = Prefers.getString("config_1", DEFAULT_LIVE);
      Config item = TextUtils.isEmpty(url) ? null : AppDatabase.get().getConfigDao().find(url, 1);
      if (item == null) item = AppDatabase.get().getConfigDao().findOne(1);
      return item == null ? create(1, DEFAULT_LIVE, "央视卫视直播") : item;
  }
  ```

### 2. 彻底拔除 GitHub 自动升级检查
* **修改目标**：关闭每次开机向 `api.github.com/repos/FongMi/Release/releases` 发送请求；防止用户误点更新导致定制内容被官方原版覆盖。
* **代码修改**：
  * [`app/src/main/java/com/fongmi/android/tv/Updater.java`](file:///home/admin/data/flutterDev/miTv/fmTV/app/src/main/java/com/fongmi/android/tv/Updater.java)：`start()`、`checkRelease()` 直接置空，不触发网络检查。
  * [`app/src/main/java/com/fongmi/android/tv/utils/Github.java`](file:///home/admin/data/flutterDev/miTv/fmTV/app/src/main/java/com/fongmi/android/tv/utils/Github.java)：`getReleases()` 返回空字符串 `""`。

### 3. 彻底禁用外部插件运行时清单（Runtime Catalog）
* **修改目标**：点播模块中包含的 Python/Node.js/迅雷插件需要从 GitHub 下载，纯看电视直播完全不需要，直接禁用该功能以杜绝后台下载。
* **代码修改**：[`app/build.gradle`](file:///home/admin/data/flutterDev/miTv/fmTV/app/build.gradle)
  ```groovy
  // 移除 https://github.com/FongMi/Release/releases/download/module/catalog.json
  manifestPlaceholders.runtimeCatalog = providers.gradleProperty('tvRuntimeCatalog').getOrElse('')
  ```

### 4. 开启“开机直达直播画面”
* **修改目标**：开机后无需在主页手动点击“直播”，直接进入全屏电视频道播放，遥控器直接上下键换台；按“返回键”可退回点播/设置主页。
* **代码修改**：
  * [`app/src/main/java/com/fongmi/android/tv/setting/LiveSetting.java`](file:///home/admin/data/flutterDev/miTv/fmTV/app/src/main/java/com/fongmi/android/tv/setting/LiveSetting.java)：默认开机自启设为 `true`：
    ```java
    public static boolean isBoot() {
        return Prefers.getBoolean("boot_live", true);
    }
    ```
  * [`app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java`](file:///home/admin/data/flutterDev/miTv/fmTV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java)：在 `initView` 初始化中直接调起直播 Activity：
    ```java
    if (savedInstanceState == null && LiveSetting.isBoot()) {
        LiveActivity.start(this);
    }
    ```

---

## 四、 本地打包与构建方案

### 方案 A：基于发布包的快速注入重打包（生产推荐）

> **为什么推荐方案 A**：
> 由于官方原版作者未开源部分私有 Media3 扩展（如 `iso` 蓝光菜单与 `libass` 特殊字幕桥接），方案 A 能够直接复用官方验证过的完整原生解码能力，同时 100% 达成我们所有的国内定制与去外网依赖要求，打包耗时仅需数十秒。

#### 步骤 1：下载官方基础安装包与 Apktool
```bash
mkdir -p /tmp/pack_work && cd /tmp/pack_work
curl -L -o apktool.jar https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar
curl -L -o leanback-arm64_v8a.apk https://github.com/FongMi/Release/releases/download/5.7.8/leanback-arm64_v8a.apk
curl -L -o leanback-armeabi_v7a.apk https://github.com/FongMi/Release/releases/download/5.7.8/leanback-armeabi_v7a.apk
```

#### 步骤 2：解包 APK
```bash
java -jar apktool.jar d leanback-arm64_v8a.apk -o apk_arm64
java -jar apktool.jar d leanback-armeabi_v7a.apk -o apk_arm32
```

#### 步骤 3：注入定制修改
1. **修改直播默认源**：在 `smali/com/fongmi/android/tv/bean/Config.smali` 的 `live()` 方法中，将默认返回源修改为 `https://gitee.com/kinnday/publicInfos/raw/master/ipm3u`。
2. **关闭更新检测**：在 `smali/h34.smali` 的 `:pswitch_1` 入口处添加 `return-void`，直接中断 GitHub 更新轮询。
3. **禁用插件清单**：在 `AndroidManifest.xml` 中将 `com.fongmi.runtime.catalog` 的 `android:value` 设置为空。
4. **设置开机直接进入直播**：在 `smali/com/fongmi/android/tv/ui/activity/HomeActivity.smali` 的 `l0(Landroid/os/Bundle;)V` 方法开头增加：
   ```smali
   if-nez p1, :cond_boot_skip
   invoke-static {p0}, Lcom/fongmi/android/tv/ui/activity/LiveActivity;->j2(Landroid/content/Context;)V
   :cond_boot_skip
   ```

#### 步骤 4：重构打包
```bash
java -jar apktool.jar b apk_arm64 -o /tmp/arm64-unsigned.apk
java -jar apktool.jar b apk_arm32 -o /tmp/arm32-unsigned.apk
```

#### 步骤 5：Zipalign 4字节对齐与签名
```bash
# 1. 4字节对齐
zipalign -f -p 4 /tmp/arm64-unsigned.apk /home/admin/data/flutterDev/miTv/fmTV/Release/apk/leanback-arm64_v8a.apk
zipalign -f -p 4 /tmp/arm32-unsigned.apk /home/admin/data/flutterDev/miTv/fmTV/Release/apk/leanback-armeabi_v7a.apk

# 2. 使用 release.keystore 进行 V2 + V3 签名
apksigner sign --ks /home/admin/data/flutterDev/miTv/fmTV/release.keystore \
               --ks-pass pass:123456 \
               --ks-key-alias tv \
               --key-pass pass:123456 \
               /home/admin/data/flutterDev/miTv/fmTV/Release/apk/leanback-arm64_v8a.apk

apksigner sign --ks /home/admin/data/flutterDev/miTv/fmTV/release.keystore \
               --ks-pass pass:123456 \
               --ks-key-alias tv \
               --key-pass pass:123456 \
               /home/admin/data/flutterDev/miTv/fmTV/Release/apk/leanback-armeabi_v7a.apk
```

---

### 方案 B：从源码编译打包流程

#### 步骤 1：准备签名证书与 local.properties
生成 keystore：
```bash
keytool -genkeypair -v -keystore release.keystore -alias tv -keyalg RSA -keysize 2048 -validity 10000 -storepass 123456 -keypass 123456 -dname "CN=tv, OU=tv, O=tv, L=tv, ST=tv, C=CN"
```
在工程根目录配置 `local.properties`：
```properties
sdk.dir=/home/admin/data/flutterDev/android-sdk
storeFile=/home/admin/data/flutterDev/miTv/fmTV/release.keystore
storePassword=123456
keyAlias=tv
keyPassword=123456
```

#### 步骤 2：补齐 20 个 AAR 依赖
必须确保 `/home/admin/data/flutterDev/miTv/fmTV/app/libs/` 目录下放置完整的 20 个 AAR：
* 从 `FongMi/media` 构建并提取：
  `lib-common-release.aar`、`lib-session-release.aar`、`lib-ui-release.aar`、`lib-ui-danmaku-release.aar` 等。
* 注意 `lib-decoder-ffmpeg-release.aar` 与 `lib-mpvplayer-release.aar` 必须包含符合 `VerifyMpvNativeTask` 校验规则的统合版 `libffmpeg.so`（见 FAQ）。

#### 步骤 3：执行 Gradle 任务编译
```bash
cd /home/admin/data/flutterDev/miTv/fmTV
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

./gradlew :app:assembleLeanbackRelease
```
编译产物位于 `app/build/outputs/apk/leanback/release/`。

---

## 五、 安装包验证与部署安装

### 1. 验证签名完整性
Android 7.0+ 及智能电视要求严谨的 V2/V3 签名机制，执行：
```bash
apksigner verify -v /home/admin/data/flutterDev/miTv/fmTV/Release/apk/leanback-arm64_v8a.apk
```
输出必须包含：
```text
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
```

### 2. 电视端部署安装
* **方式 1（U盘安装）**：
  将 `leanback-arm64_v8a.apk`（新电视）或 `leanback-armeabi_v7a.apk`（老机顶盒）拷入 U 盘，插到电视上通过“文件管理器”直接点击安装。
* **方式 2（ADB 无线安装）**：
  电视设置中打开“开发者模式”和“ADB 调试”，获取电视局域网 IP（例如 `192.168.1.100`）：
  ```bash
  adb connect 192.168.1.100:5555
  adb install -r /home/admin/data/flutterDev/miTv/fmTV/Release/apk/leanback-arm64_v8a.apk
  ```

---

## 六、 常见踩坑与问题排查 (FAQ)

### Q1: Gradle 报 `AarToClassTransform: Zip file ... already contains entry '.../R.class'`？
* **原因**：某些手动打包或工具提取的 AAR，其 `classes.jar` 内部错误地包含了 `R.class`，而 AGP 在处理 AAR 时会根据 `R.txt` 重新生成 `R.class`，导致 Zip 写入重复条目报错。
* **解决**：从 AAR 的 `classes.jar` 中剔除所有 `R.class` 或 `R$*.class` 文件后重新打包 AAR。

### Q2: 编译提示 `resource drawable/media3_icon_heart_filled not found`？
* **原因**：缺少 `lib-session-release.aar` 依赖，该图标属于 Media3 Session 模块资源。
* **解决**：将编译出的 `lib-session-release.aar` 复制放入 `app/libs/`。

### Q3: 报错 `VerifyMpvNativeTask: Split FFmpeg libraries remain`？
* **原因**：项目中的 `AbiApkPackaging` 会强制校验 `libmpv.so` 和 `libplayer.so` 是否使用了统合版 `libffmpeg.so`。如果 AAR 中包含的是 `libavcodec.so`、`libavformat.so` 等分散库，校验任务会主动抛出异常终止构建。
* **解决**：必须使用统合版编译输出的 `libffmpeg.so` 与 `libffmpegJNI.so`。

### Q4: 电视安装报“解析软件包出现问题”或“签名无效”？
* **原因**：现代 Android TV 拒绝仅有 V1 签名或未经过 4 字节对其的 APK 安装包。
* **解决**：务必使用 Android SDK 36.0.0 的 `zipalign -p 4` 进行对齐，并使用 `apksigner` 附带 V2+V3 签名。
