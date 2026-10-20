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
    fun `active draft key is unique and released by invalidation or archival`() {
        dao.insertDraft(draft("draft-1", "scope-1"))
        assertConflict {
            dao.insertDraft(draft("draft-2", "scope-1"))
        }

        assertEquals(1, dao.invalidateDraft("draft-1"))
        dao.insertDraft(draft("draft-2", "scope-1"))

        dao.insertDraft(draft("draft-3", "scope-2", state = "STALE"))
        assertEquals(1, dao.archiveDraft("draft-3"))
        dao.insertDraft(draft("draft-4", "scope-2"))
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

    private fun fileFact(fileId: String) = FileFactEntity(
        fileId = fileId,
        kind = "DOWNLOAD",
        rootId = "root-1",
        relativePath = "album/image.jpg",
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

    private fun evidence(evidenceId: String) = EvidenceEntity(
        evidenceId = evidenceId,
        contentKey = "content-1",
        type = "RULE",
        score = 0.9,
        maskedDetail = "masked",
        analysisVersion = 1,
        evidenceKey = "stable-key",
    )

    private fun journalStep(type: String) = JournalStepEntity(
        operationId = "operation-1",
        stepIndex = 0,
        type = type,
        expectedFingerprint = "fingerprint",
        state = "PLANNED",
        undoInfo = null,
    )

    private fun draft(
        draftId: String,
        activeKey: String,
        state: String = "EDITING",
    ) = DraftEntity(
        draftId = draftId,
        scopeType = "BATCH",
        scopeId = activeKey,
        revision = 1,
        activeKey = activeKey,
        state = state,
    )

    private fun importRecord() = ImportRecordEntity(
        importId = "import-1",
        sourceFileId = "source-file",
        targetFileId = "target-file",
        imageId = "image-1",
        disposition = "COPY_KEEP_SOURCE",
        sourceLocator = "locator",
        sha256 = "sha256",
        state = "COMMITTED",
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
