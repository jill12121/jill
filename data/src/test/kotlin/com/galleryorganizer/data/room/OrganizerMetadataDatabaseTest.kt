package com.galleryorganizer.data.room

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class OrganizerMetadataDatabaseTest {
    private lateinit var database: OrganizerMetadataDatabase
    private lateinit var dao: OrganizerMetadataDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrganizerMetadataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.metadataDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `file fact rejects duplicate root and relative path`() {
        dao.insertFileFact(fileFact("file-1"))

        assertConflict {
            dao.insertFileFact(fileFact("file-2"))
        }
    }

    @Test
    fun `album membership rejects duplicate album and image`() {
        dao.insertAlbumMembership(AlbumMembershipEntity("album-1", "image-1", 0))

        assertConflict {
            dao.insertAlbumMembership(AlbumMembershipEntity("album-1", "image-1", 1))
        }
    }

    @Test
    fun `asset file rejects duplicate image and file`() {
        dao.insertAssetFile(AssetFileEntity("image-1", "file-1", "ALBUM_COPY"))

        assertConflict {
            dao.insertAssetFile(AssetFileEntity("image-1", "file-1", "TRASH"))
        }
    }

    @Test
    fun `evidence rejects duplicate stable evidence identity`() {
        dao.insertEvidence(evidence("evidence-1"))

        assertConflict {
            dao.insertEvidence(evidence("evidence-2"))
        }
    }

    @Test
    fun `user state rejects duplicate image primary key`() {
        dao.insertUserState(UserStateEntity("image-1", "KEEP"))

        assertConflict {
            dao.insertUserState(UserStateEntity("image-1", "TRASH"))
        }
    }

    @Test
    fun `source decision rejects duplicate source key primary key`() {
        dao.insertSourceDecision(SourceDecisionEntity("source-1", "KEEP", 1))

        assertConflict {
            dao.insertSourceDecision(SourceDecisionEntity("source-1", "REVIEW", 2))
        }
    }

    @Test
    fun `batch member rejects duplicate batch and file`() {
        dao.insertBatchMember(BatchMemberEntity("batch-1", "file-1"))

        assertConflict {
            dao.insertBatchMember(BatchMemberEntity("batch-1", "file-1"))
        }
    }

    @Test
    fun `draft action rejects duplicate draft and file`() {
        dao.insertDraftAction(DraftActionEntity("draft-1", "file-1", "KEEP", 0, false))

        assertConflict {
            dao.insertDraftAction(DraftActionEntity("draft-1", "file-1", "TRASH", 1, true))
        }
    }

    @Test
    fun `journal step rejects duplicate operation and step index`() {
        dao.insertJournalStep(journalStep("COPY_PART"))

        assertConflict {
            dao.insertJournalStep(journalStep("PROMOTE_PART"))
        }
    }

    @Test
    fun `trash entry rejects duplicate primary key`() {
        dao.insertTrashEntry(trashEntry("file-1"))

        assertConflict {
            dao.insertTrashEntry(trashEntry("file-2"))
        }
    }

    @Test
    fun `import record rejects duplicate primary key without replacing original`() {
        dao.insertImportRecord(importRecord())

        assertConflict {
            dao.insertImportRecord(
                importRecord(
                    sourceFileId = "different-source",
                    targetFileId = "different-target",
                    state = "FAILED",
                ),
            )
        }

        val original = dao.findImportRecord("import-1")
        assertEquals("source-file", original?.sourceFileId)
        assertEquals("target-file", original?.targetFileId)
        assertEquals("COMMITTED", original?.state)
    }

    @Test
    fun `draft state and active key combinations are validated`() {
        assertFailsWith<IllegalArgumentException> {
            draft("draft-stale", "scope-stale", state = "STALE")
        }
        assertFailsWith<IllegalArgumentException> {
            draft("draft-archived", "scope-archived", state = "ARCHIVED")
        }
        assertFailsWith<IllegalArgumentException> {
            draft("draft-editing", null, state = "EDITING")
        }
        assertFailsWith<IllegalArgumentException> {
            draft("draft-unknown", null, state = "UNKNOWN")
        }
    }

    @Test
    fun `inactive draft insertion accepts null active key`() {
        dao.insertInactiveDraft(draft("draft-stale", null, state = "STALE"))
        dao.insertInactiveDraft(draft("draft-archived", null, state = "ARCHIVED"))

        assertEquals("STALE", dao.findDraft("draft-stale")?.state)
        assertNull(dao.findDraft("draft-stale")?.activeKey)
        assertEquals("ARCHIVED", dao.findDraft("draft-archived")?.state)
        assertNull(dao.findDraft("draft-archived")?.activeKey)
    }

    @Test
    fun `active draft key is unique and released by invalidation or archival`() {
        dao.insertEditingDraft(draft("draft-1", "scope-1"))
        assertConflict {
            dao.insertEditingDraft(draft("draft-2", "scope-1"))
        }

        assertEquals(1, dao.invalidateDraft("draft-1"))
        val stale = dao.findDraft("draft-1")
        assertEquals("STALE", stale?.state)
        assertNull(stale?.activeKey)
        dao.insertEditingDraft(draft("draft-2", "scope-1"))

        dao.insertEditingDraft(draft("draft-3", "scope-2"))
        assertEquals(1, dao.invalidateDraft("draft-3"))
        assertEquals(1, dao.archiveDraft("draft-3"))
        val archived = dao.findDraft("draft-3")
        assertEquals("ARCHIVED", archived?.state)
        assertNull(archived?.activeKey)
        dao.insertEditingDraft(draft("draft-4", "scope-2"))
    }

    @Test
    fun `composite constraints allow each component to vary independently`() {
        dao.insertFileFact(fileFact("file-base"))
        dao.insertFileFact(fileFact("file-root", rootId = "root-2"))
        dao.insertFileFact(fileFact("file-path", relativePath = "album/other.jpg"))

        dao.insertAlbumMembership(AlbumMembershipEntity("album-1", "image-1", 0))
        dao.insertAlbumMembership(AlbumMembershipEntity("album-1", "image-2", 1))
        dao.insertAlbumMembership(AlbumMembershipEntity("album-2", "image-1", 0))

        dao.insertAssetFile(AssetFileEntity("image-1", "file-1", "ALBUM_COPY"))
        dao.insertAssetFile(AssetFileEntity("image-1", "file-2", "ALBUM_COPY"))
        dao.insertAssetFile(AssetFileEntity("image-2", "file-1", "ALBUM_COPY"))

        dao.insertEvidence(evidence("evidence-base"))
        dao.insertEvidence(evidence("evidence-content", contentKey = "content-2"))
        dao.insertEvidence(evidence("evidence-type", type = "MODEL"))
        dao.insertEvidence(evidence("evidence-version", analysisVersion = 2))
        dao.insertEvidence(evidence("evidence-key", evidenceKey = "other-key"))

        dao.insertBatchMember(BatchMemberEntity("batch-1", "file-1"))
        dao.insertBatchMember(BatchMemberEntity("batch-1", "file-2"))
        dao.insertBatchMember(BatchMemberEntity("batch-2", "file-1"))

        dao.insertDraftAction(DraftActionEntity("draft-1", "file-1", "KEEP", 0, false))
        dao.insertDraftAction(DraftActionEntity("draft-1", "file-2", "KEEP", 1, false))
        dao.insertDraftAction(DraftActionEntity("draft-2", "file-1", "KEEP", 0, false))

        dao.insertJournalStep(journalStep("COPY_PART"))
        dao.insertJournalStep(journalStep("COPY_PART", operationId = "operation-2"))
        dao.insertJournalStep(journalStep("COPY_PART", stepIndex = 1))
    }

    @Test
    fun `metadata transaction rolls back earlier inserts when a later insert conflicts`() {
        dao.insertAssetFile(AssetFileEntity("image-transaction", "file-conflict", "EXISTING"))

        assertConflict {
            dao.insertCommittedImportMetadata(
                imageAsset = ImageAssetEntity("image-transaction", 1, "operation-1"),
                targetFile = fileFact(
                    fileId = "file-transaction",
                    relativePath = "album/transaction.jpg",
                ),
                assetFile = AssetFileEntity("image-transaction", "file-conflict", "NEW"),
                membership = AlbumMembershipEntity(
                    "album-transaction",
                    "image-transaction",
                    0,
                ),
                importRecord = importRecord(importId = "import-transaction"),
            )
        }

        assertNull(dao.findImageAsset("image-transaction"))
        assertNull(dao.findFileFact("file-transaction"))
        assertNull(dao.findAlbumMembership("album-transaction", "image-transaction"))
        assertNull(dao.findImportRecord("import-transaction"))
        assertEquals(
            "EXISTING",
            dao.findAssetFile("image-transaction", "file-conflict")?.relation,
        )
    }

    @Test
    fun `required lookup indexes use the frozen columns`() {
        dao.insertImportRecord(importRecord())
        dao.insertPlan(plan())
        dao.insertJournal(journal())
        dao.insertTrashEntry(trashEntry("file-index"))

        assertIndex("import_records", "index_import_records_source_file_id", "sourceFileId")
        assertIndex("import_records", "index_import_records_target_file_id", "targetFileId")
        assertIndex("plans", "index_plans_draft_id_type", "draftId", "type")
        assertIndex("journals", "index_journals_plan_id", "planId")
        assertIndex("trash_entries", "index_trash_entries_file_id", "fileId")
    }

    private fun fileFact(
        fileId: String,
        rootId: String = "root-1",
        relativePath: String = "album/image.jpg",
    ) = FileFactEntity(
        fileId = fileId,
        kind = "DOWNLOAD",
        rootId = rootId,
        relativePath = relativePath,
        displayName = "image.jpg",
        sizeBytes = 100,
        mtimeNormalized = 200,
        mtimePrecision = "MILLIS",
        mimeType = "image/jpeg",
        decodeState = "SUPPORTED",
        scanGeneration = 1,
        fingerprintMethod = "H64",
        quickFingerprint = "quick",
        sha256 = null,
        contentKey = fileId,
    )

    private fun evidence(
        evidenceId: String,
        contentKey: String = "content-1",
        type: String = "RULE",
        analysisVersion: Int = 1,
        evidenceKey: String = "stable-key",
    ) = EvidenceEntity(
        evidenceId = evidenceId,
        contentKey = contentKey,
        type = type,
        score = 0.9,
        maskedDetail = "masked",
        analysisVersion = analysisVersion,
        evidenceKey = evidenceKey,
    )

    private fun journalStep(
        type: String,
        operationId: String = "operation-1",
        stepIndex: Int = 0,
    ) = JournalStepEntity(
        operationId = operationId,
        stepIndex = stepIndex,
        type = type,
        expectedFingerprint = "fingerprint",
        state = "PLANNED",
        undoInfo = null,
    )

    private fun draft(
        draftId: String,
        activeKey: String?,
        state: String = "EDITING",
    ) = DraftEntity(
        draftId = draftId,
        scopeType = "BATCH",
        scopeId = activeKey ?: "inactive-scope",
        revision = 1,
        activeKey = activeKey,
        state = state,
    )

    private fun importRecord(
        importId: String = "import-1",
        sourceFileId: String = "source-file",
        targetFileId: String = "target-file",
        state: String = "COMMITTED",
    ) = ImportRecordEntity(
        importId = importId,
        sourceFileId = sourceFileId,
        targetFileId = targetFileId,
        imageId = "image-1",
        disposition = "COPY_KEEP_SOURCE",
        sourceLocator = "locator",
        sha256 = "sha256",
        state = state,
        verifiedAtEpochMillis = 1,
    )

    private fun plan() = PlanEntity(
        planId = "plan-1",
        draftId = "draft-1",
        type = "ORG",
        draftRevision = 1,
        scanSnapshot = "snapshot",
        payloadJson = "{}",
        state = "CREATED",
    )

    private fun journal() = JournalEntity(
        operationId = "operation-index",
        planId = "plan-1",
        state = "PLANNED",
        diskJournalPath = "Journal/operation-index.json",
        watermark = 0,
    )

    private fun trashEntry(fileId: String) = TrashEntryEntity(
        trashEntryId = "trash-1",
        fileId = fileId,
        imageId = null,
        sidecarPath = "Trash/meta/trash-1.json",
        expiresAtEpochMillis = 1,
        state = "ACTIVE",
    )

    private fun assertConflict(block: () -> Unit) {
        assertFailsWith<SQLiteConstraintException>(block = block)
    }

    private fun assertIndex(
        table: String,
        indexName: String,
        vararg expectedColumns: String,
    ) {
        val sqlite = database.openHelper.readableDatabase
        val actualColumns = sqlite.query("PRAGMA index_info(`$indexName`)")
            .use { cursor ->
                buildList {
                    val nameColumn = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) {
                        add(cursor.getString(nameColumn))
                    }
                }
            }
        assertEquals(expectedColumns.toList(), actualColumns, "Missing or invalid index on $table")
    }
}
