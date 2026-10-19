# 交付计划 v0.3.1

## Spike go/no-go

| Spike | Agent | 人工真机 | Go | No-go |
|---|---|---|---|---|
| S01 SAF | APK/runbook | 11/current/2 OEM | Download/GalleryOrganizer 在所有目标机可创建、part rename、授权恢复 | 回退 Pictures/GalleryOrganizer；part rename 不可靠则 direct write+readback |
| S02 MediaStore | 检测工具 | 11/13/14/15 | 目录发现、覆盖度、清源授权分块可用 | P0a 隐藏 MediaStore MOVE，仅 COPY |
| S03 OCR | benchmark | 中端/无GMS | bundled 离线、P/R/P95达标 | build flag 关闭深度OCR |
| S04 Manifest | 故障注入 | provider 验证 | 所有中断至少可恢复一份 Manifest | Journal 内嵌 Manifest 快照 |
| S05 Thumbnail | fetcher bench | 滚动验证 | 预算内无 OOM/掉帧 | 降低预热、缓存和 cell 分辨率 |

估算：S≤2天，M=3–5天，L=6–10天。Spike 结果必须回填 go/no-go。

## Foundation

| ID | 规模 | 依赖 | 输出 |
|---|---:|---|---|
| F01 | S | - | Android 工程、CI、Manifest/依赖检查 |
| F02 | S | F01 | 分层、DI、Konsist |
| F03 | M | F02 | 状态机、错误映射、权限矩阵、Plan schema |
| F04 | M | F03 | Room/Fake/FaultPlan/唯一约束 |
| F05 | M | F02,S01,S02 | Onboarding、已有根目录接管、Settings |
| F06 | S | F01 | fixture、黄金 Manifest、递进 E2E |
| N01 | S | F05 | 扫描范围选择、根散图 virtual batch |

## Import/Archive

| ID | 规模 | 依赖 | 输出 |
|---|---:|---|---|
| I01 | M | F04,F05,S02,N01 | scanner、coverage、排除规则 |
| I02 | S | F05,S01 | workspace 初始化/.nomedia |
| I03 | M | I01,F04 | Batch、SourceDecision、ImportRecord 已导入判断 |
| I04 | M | I01,S05 | thumbnails/Paging |
| I05 | M | I03,F04 | Draft/Plan/selection snapshot |
| J1 | M | F04,S04 | disk+Room Journal engine |
| J2 | L | J1,I02,S01 | part copy、tee/readback SHA、Manifest executor |
| J3 | M | J1,J2,S04 | reconciler/recovery engine |
| I06b | M | I05,J2 | PlanConfirmation UI |
| I06c | M | I03,I04,I05 | Inbox/Library/Album/Metadata UI |
| I06d | M | J2,J3,I06b,I06c | COPY/MOVE E2E |
| X01 | M | J2,S02 | SourceCleanup plan、分块授权、todo queue |
| I07 | S | I04 | ImageViewer |

## Recovery/Trash/Duplicate

| ID | 规模 | 依赖 | 输出 |
|---|---:|---|---|
| R01 | M | J3,F05 | Recovery Center / pending auth todo |
| R02 | M | J2 | Trash/Review/sidecar |
| R03 | M | R02 | expiry plan、strong confirm |
| R04 | M | F04,I01,J2 | exact duplicate fpMethod/SHA/import exclusion |
| R05 | M | R02,R04 | Review Center |
| R06 | M | R01–R05 | release E2E/perf/device gate |

## 发布阻断

- S01/S02/S04 真机 go；
- SourceDecision/ImportRecord/SourceCleanup 状态机测试；
- J1/J2/J3 故障注入与恢复矩阵；
- FULL/PARTIAL coverage 正确；
- COPY/MOVE/授权拒绝/Room重建 E2E；
- 10k 预算、Android11/current/2OEM；
- 无网络、无全盘权限。
