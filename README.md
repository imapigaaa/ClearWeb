# 净览 ClearWeb

一个极简 Android WebView 壳：加载站点移动版页面，**网络层拦广告域名 + DOM 层删广告节点**，去掉 Cookie 横幅、PWA 安装条、会员升级按钮、头部付费入口。

不解析、不爬取、不重排页面 —— 直接渲染官方移动版，所以站点改版不会让应用失效。

---

## 一、5 分钟拿到 APK（不需要装 Android Studio）

### 第 1 步：建一个仓库

打开 https://github.com/new ，填名字（比如 `clearweb`），选 **Public** 或 **Private** 都行，不要勾选任何初始化文件（README / .gitignore 都不勾），点 **Create repository**。

### 第 2 步：上传文件

在空仓库页面点 **uploading an existing file**，把本文件夹里的**全部内容**拖进去：

```
.github/workflows/build.yml   ← 必须传，否则没有自动编译
app/                          ← 源码
build.gradle
settings.gradle
gradle.properties
.gitignore
```

> 网页上传支持直接拖文件夹，`.github` 这种点开头的目录也能传上去。
> 传完检查一下仓库首页是否能看到 `.github` 目录 —— 看不到就是没传上。

点 **Commit changes**。

### 第 3 步：等它编译

- 如果这是新仓库第一次跑 Actions，会看到一条提示，点 **I understand my workflows, go ahead and enable them**。
- 进 **Actions** 标签页，应该有一个 `Build APK` 在跑（约 2–4 分钟）。
- 没自动跑就点 **Run workflow** 手动触发。

### 第 4 步：下载

编译成功后进 **Releases**（仓库右侧），点 `v1.0`，下载 **ClearWeb-v1.0.apk**。

手机上传到设备安装即可（需要允许「安装未知来源应用」）。

---

## 二、改配置

| 想改什么 | 改哪里 |
|---|---|
| 起始网址 | `app/src/main/java/com/clearweb/app/MainActivity.java` 第 40 行 `HOME_URL` |
| 广告域名黑名单 | `app/src/main/java/com/clearweb/app/AdBlocker.java` 的 `AD_HOSTS` |
| 要隐藏的页面元素 | `app/src/main/java/com/clearweb/app/PageCleaner.java` 的 `CSS` 和 `cleanScript()` |
| 版本号 | `app/build.gradle` 的 `versionCode` / `versionName`，以及 workflow 里的 `v1.0` 和 APK 文件名 |

改完 push 到 main，Actions 会自动重新编译并覆盖 Release 里的 APK。

---

## 三、去广告是怎么做的

**第一层：网络层拦截**（`AdBlocker.java`）

`shouldInterceptRequest` 里判断域名，命中广告商就返回空响应 —— 广告脚本、图片、iframe 一个字节都不下载。这层最关键，能从源头掐掉弹窗和追踪。

已覆盖：`trafficjunky.net`（该站主广告商）、`adtng.com`、`dzhjmp.com`、`exoclick`、`juicyads`、`popads`、`doubleclick`、`googletagmanager`、`adnxs`、`pubmatic`、`criteo`、`taboola`、`outbrain` 等 40 多个。

站点自己的 CDN `phncdn.com` 必须放行，否则缩略图和视频全废 —— 这条已写进白名单。

**第二层：DOM 层清理**（`PageCleaner.java`）

页面加载时注入 CSS 提前隐藏，加载完成后用 JS 把广告节点连同它的父容器一起删掉（避免留下空白），并用 `MutationObserver` 盯着无限滚动新插入的内容持续清理。

选择器不是拍脑袋写的，是抓了真实移动版页面 DOM 反推出来的：

| 目标 | 实际选择器 | 首页命中 | 播放页命中 |
|---|---|---|---|
| 广告位 | `ins.adsbytrafficjunky` / `ins[data-spot-id]` | 7 | 5 |
| 广告容器 | `.watchpageAd` / `.middleVideoAdContainer` / `.ad-tabSplit` | 1 | 5 |
| 会员升级按钮 | `.adsRemoveButton` / `.js-phpAdsRemoveButton` | 3 | 3 |
| 头部付费入口 | `.gtm-event-header-paidtabs` / `.js-liveCam` | 5 | 5 |
| PWA 安装条 | `.js-closePWABanner` | 2 | 2 |

「头部付费入口」就是 Live Cams、AI Sex Slave、Porn Girls、Hookups 那一排 —— 它们的链接全部指向 `ads.trafficjunky.net`，删掉不影响站内导航（Straight/Gay/Sapphic/Trans 这些正经分类保留）。

---

## 四、已知限制

1. **需要能访问站点本身**。应用不内置代理，网络不通它也没办法。
2. **站点若加强 WebView 检测可能失效**。UA 已伪装成普通 Chrome 移动版，但如果对方改成检测 `window.chrome` 之类特征，需要再调整。
3. **只能在线看，不带下载功能**。要下载建议用 Seal（yt-dlp 的安卓 GUI）。
4. **不拦截登录**。账号登录照常可用，Cookie 存在 WebView 里。
5. **站点改版后选择器可能失效**。这种情况广告会重新出现，但页面功能不受影响 —— 改 `PageCleaner.java` 里的选择器重新编译即可。
6. 签名密钥存在仓库 Secrets（`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`），所以后续版本可以直接覆盖升级。**但 v1.0 → v1.1 这一次仍需先卸载旧版**，因为 v1.0 用的是临时密钥。

---

## 五、免责

- 仅供个人学习 Android WebView 开发使用。
- 应用本身不包含、不存储、不分发任何内容，只是渲染你指定网址的网页。
- 去广告会减少站点广告收入，属于个人客户端侧行为；请勿分发此 APK 或用于商业用途。
- 内容合规性、年龄限制、所在地区法律，由使用者自行负责。
