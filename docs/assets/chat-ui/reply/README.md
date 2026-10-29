# 引用图标：圆环左引号

根据用户 2026-10-20 参考图调整为圆圈内的左引号，不沿用此前候选 A 的右引号方向。

原稿来自 Lucide 0.468.0：
- https://raw.githubusercontent.com/lucide-icons/lucide/0.468.0/icons/quote.svg
- https://raw.githubusercontent.com/lucide-icons/lucide/0.468.0/icons/circle.svg

quote-original.svg / circle-original.svg 保留下载原字节；LICENSE.txt 为该版本的 ISC 许可及 Feather MIT 声明。
reply.svg 组合两份原稿：外圈不变，引号以 (12,12) 为中心旋转 180°、缩放 0.6 并填实；不重画 path。
输出为 96×96 透明白图，使用现有菜单 8×8 逻辑槽位，与文字共用纵向中心，无额外方框或蓝色底。
生成及缩小预览由配套 rasterize.cjs 执行，需要开发环境的 sharp；游戏和 Gradle 不依赖它。
