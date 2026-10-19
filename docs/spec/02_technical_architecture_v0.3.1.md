# 技术架构 v0.3.1

## 1. 端口与依赖

```text
presentation → domain ← data
```

```kotlin
interface SourceReader { enumerate; stat; openInput }
interface WorkspaceGateway { createDirectory; copyPart; renamePart; move; moveToTrash }
interface PermanentDeleter { deleteTrash; requestSourceCleanupAuthorization }
interface ManifestStore { writeImporting; commit; loadRecovery }
```

Konsist：Scanner/Analyzer/AlbumEditor 不依赖 PermanentDeleter；UI 不依赖 DAO/Port；domain 不 import Android/Room/Compose。

## 2. 模型职责

```text
ImageAsset(imageId, createdAt, createdByOperationId)
AlbumMembership(albumId, imageId, displayOrder)  UNIQUE(albumId,imageId)
UserState(imageId, decision)                     decision only
FileFact(fileId, kind, rootId, relativePath, displayName, size,
         mtimeNormalized, mtimePrecision, mime, decodeState,
         scanGeneration, fpMethod, quickFp, sha256, contentKey)
AssetFile(imageId, fileId, relation=ALBUM_COPY|TRASH)
ImportRecord(importId, sourceFileId, targetFileId, imageId, disposition,
             sourceLocator, sha256, state, verifiedAt)
SourceDecision(sourceKey, decision=KEEP|REVIEW|TRASH, updatedAt)
Evidence(evidenceId, contentKey, type, score, maskedDetail, analysisVersion, evidenceKey)
Draft(draftId, scopeType, scopeId, revision, activeKey)
DraftAction(draftId, fileId, action, displayOrder?, isCover?)
Plan(planId, type, draftRevision?, scanSnapshot, payloadJson, state)
Journal(operationId=planId, planId, state, diskJournalPath, watermark)
JournalStep(operationId, stepIndex, type, expectedFingerprint, state, undoInfo)
TrashEntry(trashEntryId, fileId, imageId?, sidecarPath, expiresAt, state)
```

ImageAsset 仅在归档提交时创建。源阶段只有 FileFact、SourceDecision、DraftAction。

Evidence 使用 `contentKey = sha256 ?: fileId`，复制副本复用已有 Evidence；`evidenceKey = stableHash(ruleId + type + maskedDetail)`。

## 3. Plan 类型

```text
ORG               批次归档
METADATA          标题/封面/顺序 Manifest 修改
TRASH_MOVE        workspace 文件移入 Trash
PERMANENT_DELETE  Trash 永久删除
SOURCE_CLEANUP    已归档后清理 Download/SAF 源
```

Plan payload 都包含：`planId/type/snapshot/createdAt/steps`；ORG 包含 albumId、immutableDir、源定位、目标名、导入策略；SOURCE_CLEANUP 包含 sourceLocator 分块；METADATA 包含 Manifest patch。

## 4. Journal 与恢复

顺序固定：

```text
Journal PLANNED (disk)
→ create dir
→ write .nomedia (Journal step 0)
→ Manifest IMPORTING
→ each .part copy / post-check / final rename
→ Manifest COMMITTED
→ Room upsert transaction
→ Journal COMPLETED
```

磁盘 Journal 保存 Plan 快照与定位信息，不保存 URI。细粒度 Room step 每 50 文件/30 秒更新；磁盘 journal 每 200 文件/2 分钟写 watermark，避免 SAF 写放大。

恢复依据：disk Journal + IMPORTING/COMMITTED Manifest + filesystem。Manifest COMMITTED 但 Room 未提交时，按 Manifest 幂等 upsert Room；Journal 有但 Manifest 无，提示重新预演；IMPORTING 无 Journal 时可从目录重建最小 Manifest。

## 5. COPY 与 MOVE

复制始终写 `<shortId>.<ext>.part`：

```text
source read + tee source SHA
→ target .part
→ target size check
→ MOVE 内读回 target SHA
→ rename .part to final; use returned actual displayName
```

MOVE 的清源另立 SOURCE_CLEANUP Plan：

```text
Manifest COMMITTED
→ verify target SHA == source SHA
→ verify source locator size/mtime unchanged
→ resolve current URI
→ chunks <= 300 URIs (S02 can adjust)
→ Activity authorization
→ re-query source; CLEANED / CLEANUP_DENIED / SOURCE_CHANGED
```

PendingIntent 不持久化；进程重启后由 sourceLocator 重新解析。授权失败不回滚归档。

## 6. 扫描

- Download 首次先目录选择；根目录散图虚拟 batch 默认关闭；
- MediaStore keyset pagination `_ID > ?`, LIMIT 500；相对路径 LIKE 必须 ESCAPE；
- ScanCoverage FULL/PARTIAL/UNKNOWN；仅 FULL+completed 执行 sweep；
- PARTIAL 不允许 Batch 整体整理，仅选中导入；
- organizerRoot 与 protected dirs 均排除；
- `SourceDecision.sourceKey = kind:root:path:size:mtimeNormalized`。

## 7. 重复与指纹

```text
size<128KB → SHA-256
seek available → fpMethod=HT64(first+last64KB)
seek unavailable → fpMethod=H64(first64KB)
fpMethod same + quickFp same → SHA
fpMethod different → SHA directly
```

ImportRecord 对应 source/target 标“已导入副本”，不进入待处理重复组。

## 8. Trash

只 workspace 文件进入 Trash。sidecar 保存 onlyCopy 与恢复快照。自动 purge 仅检查 sidecar + Trash 文件 size/mtime；手动 purge 强确认。sidecar mismatch → BLOCKED。

## 9. 后台

长用户任务 TaskExecutionDialog；系统任务不弹窗。通知拒绝则任务持久化状态，回到 App 提示 continue/abandon。Android 14/15 foreground 类型和用户发起传输作业由 S01/S03 go/no-go 决定。
