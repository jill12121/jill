# Android 图集整理器 PRD v0.3.1

## 1. 定位

浏览器负责下载；用户把图片手动归到 `Download/<日期目录>/`；App 负责扫描、图集归档、精确重复、Review、Trash 与本地筛选。产品不是下载器，不接管网页访问或浏览器任务。

目标：在不上传图片和不依赖云端的前提下，把大量下载图片整理为可恢复、可浏览、可追踪的图集。

## 2. 路径、扫描范围与批次

```text
Download/
├── 2026-10-16/                  # 用户日期批次
│   ├── siteA/a.webp
│   └── siteB/b.webp
├── Screenshots/                  # 默认不监控，用户可选
└── GalleryOrganizer/             # organizerRoot；能力失败则回退 Pictures/
    ├── .gallery-organizer/
    │   ├── root.json
    │   └── journal/<operationId>.json
    ├── Albums/<date>__<albumId>/.nomedia
    ├── Review/.nomedia
    └── Trash/{files,meta}/.nomedia
```

首次扫描流程：

```text
列出 Download 第一层目录与图片数
→ 用户勾选需要监控的目录
→ 根目录散图以“散图”虚拟批次呈现，默认不启用
→ 只扫描已监控目录
→ 新目录出现时提示是否纳入
```

规则：

- 第一层非 organizerRoot 目录 = Batch；
- 深层目录递归归入 Batch，保存 `subgroupRelativePath`；P0 平面展示；
- `scanProtectedDirectories` = 用户维护的排除目录清单，不等同 organizerRoot 排除；
- organizerRoot 前缀强制排除，不依赖 MediaStore 是否能看到 Manifest；
- `coverage=PARTIAL_USER_SELECTED` 时显示扫描不完整，禁止批次整体整理，仅允许已选图片导入。

## 3. 源阶段决策

收件箱中的 Download/MediaStore 文件尚未归档，可能只有 `fileId`，没有 `imageId`。源阶段决策必须使用 `SourceDecision`：

| 用户选择 | 含义 |
|---|---|
| KEEP | 纳入当前 Draft，归档为图集成员 |
| REVIEW | 不导入，保留源文件，写 SourceDecision(REVIEW)，下次仍显示待审 |
| TRASH | 不导入，写 SourceDecision(TRASH)，不再进入批次；不复制进 App Trash |

源阶段“清理源文件”是独立功能，需要 SourceCleanupPlan 与 Android 用户授权；不能将只读源文件直接移动到 Trash。

## 4. 导入策略

| 策略 | 归档 | 源处理 |
|---|---|---|
| COPY_KEEP_SOURCE | 复制目标并校验 | 源保留，写 ImportRecord |
| MOVE_SOURCE | 复制、目标读回 SHA、Manifest COMMITTED | 单独 SourceCleanupPlan，用户授权后清源；拒绝则源保留 |
| INDEX_IN_PLACE | 建立图集关系 | 文件不移动；高级入口，P0 默认隐藏 |

复制生成 ImportRecord。后续扫描源 fingerprint 未变化时，该源显示“已导入”，不重新进入新批次，也不进入普通重复组。

## 5. 图集、元数据与查看器

- 图集目录名不可变：`YYYY-MM-DD__albumId`；改标题不改目录；
- 文件名为 `<shortImageId>.<ext>`；顺序只存 AlbumMembership/Manifest；
- Metadata 修改写 MetadataDraft，点击保存或防抖合并后生成 MetadataPlan，走 Journal；
- ImageViewer P0a：单图全屏 fit、基础缩放、元数据与脱敏 Evidence、返回原滚动位置；不做连续翻图；
- Albums、Review、Trash 默认 `.nomedia`，避免系统图库显示源与归档副本双份。

## 6. Trash

仅 workspace 内文件可进入 App Trash。每项写：

```text
Trash/files/<trashEntryId>.<ext>
Trash/meta/<trashEntryId>.json
```

sidecar 保存：原图集、原路径、原 order、Manifest 项快照、sha256、size、mtime、原因、进 Trash 时间、到期时间、onlyCopy。

默认策略：临时保留 12 小时。到期后：

```text
PermanentDeletePlan
→ 校验目标仍位于 Trash/files
→ sidecar 存在
→ size/mtime 一致
→ Journal 删除
```

不重新全库扫描或 Hash。`onlyCopy=true` 项在进入 Trash 时给显著警告：“可能为唯一副本，将于 12 小时后永久删除”；用户确认后仍按默认 12h 策略执行。手动批量永久删除必须有强确认（数量、总大小、不可恢复）。

## 7. P0a / P0b

P0a：Onboarding、扫描范围、批次、归档、Manifest、Journal、Recovery Center、Trash、精确重复、最小查看器、后台选择。

P0b：杂项 Evidence、QR Evidence、手动 OCR 深度广告扫描。OCR 不自动执行，不阻塞 P0a。

## 8. 后台执行

用户发起可能耗时的扫描、归档、Hash、Trash 清理、OCR 时，先显示：

```text
留在应用内执行 | 进入后台执行 | 取消
```

- 用户发起批量 I/O 可转 foreground-capable Worker；
- 无通知权限时提示后台可靠性下降；可保存 checkpoint，回 App 后继续；
- 待系统授权任务进入 `WAITING_FOR_USER_AUTH`，Recovery Center 的待办操作区也必须可进入，通知不是唯一入口；
- 到期 Trash 清理、启动恢复等系统触发任务不弹 TaskExecutionDialog，记录通知/待办即可。

## 9. P0a 验收

1. 首次扫描可选择 Download 目录，根目录散图默认不批量导入；
2. PARTIAL 照片权限不 sweep，且不能批量整理不可见目录；
3. 源阶段 REVIEW/TRASH 不产生无意义副本；
4. COPY/MOVE 写 ImportRecord，导入副本不污染重复中心；
5. MOVE 清源前目标读回 SHA 一致、源指纹复核、系统授权分块；
6. 卸载/重装后选择已有 organizerRoot，可从 root.json + disk Journal + IMPORTING 进入恢复；
7. Trash 12h 删除只处理 sidecar/目标一致项，手动永久删除强确认；
8. JPEG/PNG/WebP 强验收；其他格式安全保留；
9. Android 11、current target、至少两类 OEM 真机通过发布回归。
