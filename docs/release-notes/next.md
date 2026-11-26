# 下一版本
<!-- date: 2026-11-26 -->

## 简体中文

### 修复

- 修复 NeoForge 平台客户端在 `mcphone/wallpapers/` 目录存在自定义 `.png` 壁纸时，启动阶段于并行工作线程创建 `DynamicTexture` 导致 `IllegalStateException: Rendersystem called from wrong thread` 或 LWJGL 原生闪退的问题。
- 将壁纸启动扫描放入主渲染线程队列执行，并在 `WallpaperStore.refresh()` 增加主线程安全转发兜底。

## English

### Fixed

- Fixed a startup crash on NeoForge when custom `.png` wallpapers exist in `mcphone/wallpapers/`, caused by creating `DynamicTexture` off the render thread during `FMLClientSetupEvent` (`IllegalStateException: Rendersystem called from wrong thread` or LWJGL native abort).
- Queued initial wallpaper scanning onto the render thread via `enqueueWork` and added a main-thread dispatch guard in `WallpaperStore.refresh()`.
