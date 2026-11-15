# 简画 · EasyDraw

> 一款给孩子的极简手绘画板。轻量、无广告、无内购、无账号、不联网。

![平台](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![体积](https://img.shields.io/badge/APK-约%204.4%20MB-blue)
![许可](https://img.shields.io/badge/License-MIT-green)

---

## 为什么做这个

孩子想在平板上随手涂鸦，市面上的绘图 App 要么塞广告、要么引导内购、要么动辄上百兆、要么要注册账号。
**简画**只做一件事：打开就能画，画完就能存。

- ✅ **轻量**：APK 约 4.4 MB，无第三方统计 SDK
- ✅ **无广告**：没有任何广告位、弹窗、推荐
- ✅ **无内购**：全部功能免费，没有付费解锁
- ✅ **不联网**：不申请网络权限，画作只存在本机
- ✅ **无账号**：无需注册登录，装好即用
- ✅ **边界安全**：无社交、无分享到公开平台、无相机麦克风权限
- ✅ **儿童友好**：大按钮、纯中文、跟随系统深色模式

---

## 功能

### 画笔

| 工具 | 说明 |
|---|---|
| 铅笔 | 平滑曲线，笔画连续不颗粒 |
| 马克笔 | 半透明叠加，适合涂色 |
| 橡皮 | 圆形 / 方形笔头可切换；像素擦除 / 整笔删除两种模式 |
| 填充 | 一键填充封闭区域 |
| 直线 / 矩形 / 椭圆 / 箭头 | 支持空心 / 实心 |
| 取色器 | 从画面吸取任意颜色 |

笔头大小 1~60dp 无级调节，左侧实时预览当前笔头形状与尺寸。

### 颜色

- 预设 **24 色**常用色盘，一点即选
- **自定义颜色对话框**（仿 Windows 颜色选择器）：
  - **标准**页：蜂窝六边形色轮 + 九级灰阶
  - **自定义**页：饱和度/明度方块 + 色相条 + RGB 数值 + 十六进制输入 + 新旧色对比

### 图层

- 新建 / 复制 / 删除 / 上移 / 下移 / 显示隐藏
- 每个图层独立不透明度
- 点选切换当前绘制层，长按重命名

### 画布与保存

- 画布铺满全屏，横竖屏切换不影响笔迹位置
- **自动保存**：落笔停顿 1.2 秒自动存档，退出不丢
- **画廊**：所有画作缩略图平铺，点开继续画，长按重命名 / 删除
- **导出 PNG**：一键保存到系统相册 `Pictures/简画`
- **分享**：调用系统分享面板（不内置任何社交 SDK）

### 界面

- 工具栏 / 调色板各自带**图钉**：钉住常驻，取消则落笔自动隐藏
- 右下角**浮动胶囊**：长按可拖动到任意位置，点开是「新建 / 保存 / 返回」
- 所有工具图标下方都有中文标签，点按还会弹出气泡提示
- 浅色纸白 + 深色细线图标，跟随系统深色模式

---

## 安装

1. 前往 [Releases](../../releases/latest) 下载最新 APK（当前：[v1.10.0](https://github.com/thefallenelf/easydraw/releases/download/v1.10.0/jianhua-v1.10.0.apk)）
2. 拷贝到平板 / 手机，点击安装（首次需允许「安装未知来源应用」）
3. 打开即可画，无需任何设置

**系统要求**：Android 8.0（API 26）及以上，推荐在 10 英寸以上平板使用。

---

## 隐私

简画**不申请网络权限**，也不含任何统计、广告或崩溃上报 SDK。

- 画作数据保存在应用私有目录，仅在卸载时删除
- 导出的 PNG 会写入系统相册 `Pictures/简画`
- 分享功能通过系统 FileProvider 提供临时只读链接，仅作用于当次分享

---

## 从源码构建

```bash
git clone git@github.com:thefallenelf/easydraw.git
cd easydraw
```

在项目根目录创建 `local.properties`：

```properties
sdk.dir=/path/to/Android/Sdk
# 可选：自己的签名配置（不填则产出未签名 release 包）
RELEASE_STORE_FILE=/path/to/your.jks
RELEASE_STORE_PASSWORD=******
RELEASE_KEY_ALIAS=******
RELEASE_KEY_PASSWORD=******
```

然后：

```bash
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

环境：JDK 17+、Android SDK Platform 35、Build-Tools 35.0.0。

---

## 工程结构

```
app/src/main/java/com/zhangchuang/nianchu/
├── MainActivity.kt      画布页：覆盖层组装 / 自动保存 / 导出分享 / 设置
├── GalleryActivity.kt   画廊（2 列网格）
├── PaintController.kt   手势 → 操作状态机；撤销重做；填充；取色
├── PaintView.kt         画布合成与触摸（转屏显示层补偿）
├── Drawing.kt           文档模型：seed 栅格 + 操作回放
├── Ops.kt               可回放操作（笔 / 马克笔 / 橡皮 / 填充 / 形状）
├── LayerPanel.kt        图层面板
├── ColorDialog.kt       颜色对话框（标准 / 自定义两个 Tab）
├── HexWheelView.kt      蜂窝色轮
├── ColorPickerView.kt   SV 方块 + 色相条
├── ToolBar.kt           底部工具栏
├── TopBar.kt            顶部工具栏
├── BrushBar.kt          笔刷属性条
├── Palettebar.kt        24 色盘
├── FloatingCapsule.kt   可拖动浮动胶囊
├── ProjectIO.kt         工程保存 / 载入（meta.json + 分层 PNG）
├── ExportUtil.kt        相册导出 / FileProvider 分享
├── Thumbs.kt            画廊缩略图
├── Prefs.kt             偏好持久化
└── Ico.kt               细线图标
```

渲染核心是「**seed 栅格 + 操作回放**」双源模型：
每个图层 = 一张基底位图 + 一串可回放的绘制操作。
撤销 / 重做只增删操作，无需像素快照，内存占用极低。

---

## 许可

MIT License，详见 [LICENSE](LICENSE)。

---

<p align="center">用简单的方式，留住每一次涂鸦。</p>
