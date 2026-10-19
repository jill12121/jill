# v0.3.1 契约表

## 状态机

```text
Draft: EDITING -> STALE -> ARCHIVED
Plan: CREATED -> READY -> EXECUTING -> COMPLETED|PARTIAL|FAILED; input change -> STALE; conflict -> BLOCKED
Album: IMPORTING -> COMMITTED|RECOVERY_REQUIRED
ImportRecord: PENDING_COPY -> COPIED -> COMMITTED|COPIED_SOURCE_RETAINED|CLEANUP_PENDING|CLEANED|CLEANUP_DENIED|SOURCE_CHANGED|FAILED
TrashEntry: ACTIVE -> EXPIRED -> DELETE_PLANNED -> DELETED; ACTIVE/EXPIRED -> RESTORED; ACTIVE -> DELETE_PLANNED manual
SourceDecision: KEEP|REVIEW|TRASH; source fingerprint changes => decision not applied
```

## 错误映射

| code | 用户文案 | 动作 |
|---|---|---|
| PARTIAL_MEDIA_ACCESS | 扫描范围不完整 | 扩大授权或只导入已选图片 |
| SOURCE_MISSING | 原文件已不存在 | 重新扫描/移除该项 |
| SOURCE_CHANGED | 原文件已改变 | 重新预演 |
| TOCTOU_MISMATCH | 执行期间文件发生变化 | 阻断该项，保留其他成功项 |
| TARGET_CONFLICT | 目标名称冲突 | 重新生成 ID 或修改计划 |
| STORAGE_FULL | 存储空间不足 | 清理空间或改索引原地 |
| WRITE_PERMISSION_LOST | 整理目录权限失效 | 重新选择目录 |
| SOURCE_DELETE_AUTH_DENIED | 未授权清理原文件 | 已归档，源文件保留 |
| MANIFEST_RECOVERY_REQUIRED | 图集清单需要恢复 | 打开恢复中心 |
| JOURNAL_CORRUPT | 操作日志损坏 | 从 IMPORTING/目录重建 |
| SIDECAR_MISMATCH | 回收站文件已变更 | 手动确认处理 |
| TRASH_RESTORE_CONFLICT | 原位置不可恢复 | 选择新位置 |
| NOTIFICATION_DENIED | 后台通知不可用 | 留在前台或稍后继续 |

## 权限

| OS | 读取 | 部分访问 | 写 workspace | 后台通知 |
|---|---|---|---|---|
| 11–12 | READ_EXTERNAL_STORAGE | - | SAF | - |
| 13 | READ_MEDIA_IMAGES | - | SAF | POST_NOTIFICATIONS |
| 14 | READ_MEDIA_IMAGES / VISUAL_USER_SELECTED | PARTIAL 禁止 sweep | SAF | POST_NOTIFICATIONS |
| 15+ | 同14，按 target 验证 | PARTIAL 禁止批次整体整理 | SAF | POST_NOTIFICATIONS + foreground policy Spike |

## Room 约束

```text
FileFact UNIQUE(rootId, relativePath)
AlbumMembership UNIQUE(albumId, imageId)
AssetFile UNIQUE(imageId, fileId)
ImportRecord PRIMARY KEY(importId); INDEX(sourceFileId); INDEX(targetFileId)
Evidence UNIQUE(contentKey, type, analysisVersion, evidenceKey)
UserState PRIMARY KEY(imageId)
SourceDecision PRIMARY KEY(sourceKey)
BatchMember UNIQUE(batchId, fileId)
Draft UNIQUE(activeKey) where inactive activeKey=NULL
DraftAction UNIQUE(draftId, fileId)
Plan INDEX(draftId, type)
Journal INDEX(planId)
JournalStep UNIQUE(operationId, stepIndex)
TrashEntry PRIMARY KEY(trashEntryId); INDEX(fileId)
```

## 恢复矩阵

| Disk Journal | Manifest | Files | 恢复 |
|---|---|---|---|
| none | none | none | 无操作 |
| PLANNED | none | none | 重新预演 |
| PLANNED | IMPORTING | part files | 删除 part，继续/回滚 |
| PLANNED | IMPORTING | final files | 校验后标 DONE，继续 |
| PLANNED | COMMITTED | finals | 幂等 upsert Room，Journal COMPLETE |
| COMPLETE | COMMITTED | finals | 正常校验 |
| corrupt | IMPORTING | files | Recovery Center 从目录重建 |
| any | sidecar mismatch | Trash file | BLOCKED，禁止自动删 |

## 格式/性能/命名

- 强验收：JPEG/JPG/JFIF、PNG、静态 WebP；
- GIF/HEIF/AVIF/BMP 能力检测，动画仅首帧缩略图；
- SHA: size<128KB直接计算；HT64/H64不同方法直接SHA；
- 缩略图 key=fileId+mtimeNormalized，缓存512MB起始值，LRU，onTrimMemory；
- 10k：MediaStore≤15s起始目标、SAF≤60s、堆峰值≤300MB、Hash≥30MB/s、复制≥20MB/s；Spike后冻结；
- 名称做 NFC 归一、过滤非法/保留名/结尾点空格、按UTF-8字节截断、大小写冲突重生成 ID。
