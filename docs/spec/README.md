# Android 图集整理器 v0.3.1 开工文档包

> **状态**：唯一实施基线。v0.3 及以前文档只做历史参考，不得作为实现冲突时的依据。  
> **平台**：Android 11+，`minSdk 30`。  
> **隐私**：无账号、无上传、无 `INTERNET`、无 `MANAGE_EXTERNAL_STORAGE`、`allowBackup=false`。  
> **来源**：v0.3 基线 + Claude/DeepSeek v0.3 评审 + Execution Playbook 的已采纳实现契约。

## 阅读顺序

| 顺序 | 文件 | 目的 |
|---:|---|---|
| 1 | `01_product_prd_v0.3.1.md` | 产品范围、源阶段决策、批次、Trash、用户流程与验收 |
| 2 | `02_technical_architecture_v0.3.1.md` | 端口、数据模型、Journal、Import/MOVE、扫描、后台 |
| 3 | `03_delivery_plan_v0.3.1.md` | Spike、任务拆分、真人机分工、go/no-go、发布阻断 |
| 4 | `04_adrs_v0.3.1.md` | 冻结的产品/技术决策与受 Spike 约束项 |
| 5 | `05_work_agent_handoff_v0.3.1.yaml` | **程序可读执行入口** |
| 6 | `06_compose_ui_spec_v0.3.1.yaml` | **唯一完整 Compose 路由、Screen、State、Event 规格** |
| 7 | `07_contract_tables_v0.3.1.md` | 状态机、权限、错误码、Room、恢复、性能与命名契约 |
| 8 | `08_execution_playbook_v0.3.1.md` | 文件写入、恢复、清源、扫描、测试的实现级步骤 |

## 不可协商规则

1. S01/S02/S04 完成真人机回填前，不实现真实文件写入；
2. 源阶段只读 MediaStore 文件只能通过 `SourceReader` 读取；
3. Download `MOVE_SOURCE` 是“复制 + 源目标 SHA-256 校验 + 用户授权清源”，不能直接 move；
4. 源阶段的 `REVIEW/TRASH` 写 `SourceDecision`，不会复制无用源图进 App Trash；
5. 图集文件写入只允许：`Plan → disk Journal → WorkspaceGateway → Manifest → Room`；
6. Trash 到期删除走 `PermanentDeletePlan + Journal`，仅做 sidecar 与目标轻量核验；
7. `ImageAsset` 只在归档提交时创建；归档前仅使用 `fileId`、DraftAction 和 SourceDecision；
8. `ImportRecord` 关联的源/副本显示“已导入副本”，不进入普通重复清理；
9. OCR 是 P0b 手动功能，永不自动运行；
10. UI/ViewModel 禁止直接访问 DAO、ContentResolver、DocumentFile 或文件操作 Port。

## 文件树

```text
core/v0.3.1/
├── README.md
├── 01_product_prd_v0.3.1.md
├── 02_technical_architecture_v0.3.1.md
├── 03_delivery_plan_v0.3.1.md
├── 04_adrs_v0.3.1.md
├── 05_work_agent_handoff_v0.3.1.yaml
├── 06_compose_ui_spec_v0.3.1.yaml
├── 07_contract_tables_v0.3.1.md
└── 08_execution_playbook_v0.3.1.md
```
