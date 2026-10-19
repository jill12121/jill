# ADR v0.3.1

| ADR | 决策 |
|---|---|
| ADR-01 | Android 11+ / minSdk30 |
| ADR-02 | 浏览器下载主导，第一次选择监控 Download 子目录 |
| ADR-03 | MediaStore MOVE = COPY+SHA+授权清源；不直接 move |
| ADR-04 | SourceDecision 管理归档前 KEEP/REVIEW/TRASH |
| ADR-05 | ImportRecord 排除源/副本重复中心 |
| ADR-06 | 图集目录不可变；标题只在 Manifest |
| ADR-07 | Journal disk+Room；IMPORTING/COMMITTED；磁盘不存 URI |
| ADR-08 | Trash 12h，目标/sidecar轻量核验；唯一副本警告后仍按用户选择执行 |
| ADR-09 | Albums/Review/Trash .nomedia |
| ADR-10 | JPEG/PNG/WebP 强验收；嵌套目录 subgroup |
| ADR-11 | P0a 最小 ImageViewer |
| ADR-12 | Task dialog 前台/后台；授权待办可从 Recovery Center 进入 |
| ADR-13 | 完整 UI spec 仅 v0.3.1，旧 delta 作废 |

## Spike 约束

- organizerRoot 仅在 S01/S02 全目标设备通过时使用 Download/GalleryOrganizer；
- Android 14/15 foreground/notification/transfer 行为按 S01/S03 冻结；
- OCR build flag 默认关闭，S03/B04 才可打开；
- MediaStore SourceCleanup 授权失败率高则 P0a 隐藏 MOVE。
