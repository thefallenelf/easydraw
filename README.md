# 念初的画画板

适用于安卓平板/手机的极简手绘画板 APP（Kotlin 自绘，无重型依赖）。

## APK 位置

- `E:\apk-build-tmp\念初的画画板_v1.0.0.apk`（release 签名包，可直接安装，约 4.4MB）
- 构建产物：`C:\Users\ZC\画板\app\build\outputs\apk\release\app-release.apk`

签名：自签名（开发者 ZhangChuang），keystore 在 `E:\apk-build-tmp\keystore\nianchu-release.jks`，
密码写在 `app/build.gradle.kts` 的 signingConfigs 中。

## 功能

- **工具**：铅笔（压感）、马克笔（半透明叠加）、橡皮（像素 / 笔画可选）、填充（容差泛洪）、
  直线 / 矩形 / 椭圆 / 箭头（空心 / 实心）、取色器
- **画笔**：粗细滑杆（归一化 0~1）
- **调色板**：预设 24 色 + 自定义 RGB 取色，默认常驻、设置里可隐藏
- **图层**：新建复制删除移动、可见性、不透明度；当前层高亮
- **撤销 / 重做**（操作级）、清空画布
- **横竖屏 / 窗口尺寸变化**：画面内容等比适配不丢失（操作回放 + seed 缩放）
- **防误触**（手掌忽略，笔优先）、跟随系统深色（浅色纸白纸面 / 深色画布）
- **保存**：增量自动保存到应用私有目录，退出不丢
- **导出 / 分享**：一键导出 PNG 到相册 `Pictures/念初的画画板`，可系统分享
- **画廊**：网格展示所有画作（缩略图 + 标题 + 时间），点击续画，长按重命名 / 删除

## 构建方式（本机）

```bash
cd C:\Users\ZC\画板
set JAVA_HOME=E:\Tools\jdk-21.0.13+9
set GRADLE_USER_HOME=D:\gradle-home       # Gradle 缓存放 D 盘
E:\gradle-dist\gradle-8.9\bin\gradle.bat :app:assembleRelease
```

SDK 在 `E:\android-sdk`（platform 35 + build-tools 35 + platform-tools + cmdline-tools）。
`gradle.properties`（项目内）与 `D:\gradle-home\gradle.properties` 已配置 Java/路径相关项。

## 工程结构

```
app/src/main/java/com/zhangchuang/nianchu/
  MainActivity.kt      画布页：覆盖层组装 / 自动隐藏 / 自动保存 / 导出分享 / 设置
  GalleryActivity.kt   画廊（2 列网格）
  PaintController.kt   手势→操作 状态机；撤销重做；笔画橡皮；填充；取色
  PaintView.kt         合成 + 触摸 + 双指检查 + 边缘唤出
  Drawing.kt           Document：seed 栅格 + ops 回放 双源图层模型 / 等比适配
  Ops.kt               可回放操作（笔/马克笔/橡皮/填充/形状/箭头）+ 泛洪填充
  LayerPanel.kt        图层面板
  ToolBar / TopBar / BrushBar / Palettebar   自绘极简细线 UI（跟随深色）
  ProjectIO.kt         工程保存/载入（meta.json + 分层 PNG）
  ExportUtil.kt        相册导出 / FileProvider 分享
  Thumbs.kt            画廊缩略图
  Prefs.kt             偏好持久化
  Icons.kt             细线图标
```

## 已知边界

- 形状/填充/像素橡皮/取色按「操作回放」实现，撤销是操作级（非像素级）。
- 图层内容以 PNG 落盘（保留透明通道），因此旋转重载即回到旧分辨率；跨屏适配在会话内完成。
- 笔迹坐标未做高频采样合并上限，长笔画会把点阵平铺到 op 内（可接受范围）。