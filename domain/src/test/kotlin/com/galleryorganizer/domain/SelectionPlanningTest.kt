package com.galleryorganizer.domain

import com.galleryorganizer.contract.FileFingerprint
import com.galleryorganizer.contract.RelativePath
import com.galleryorganizer.contract.SourceFileRef
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SelectionPlanningTest {
    private val repository = MemoryRepository()
    private val planner = DefaultSelectionPlanner(repository)

    @Test
    fun `draft revisions use optimistic locking and reject imported keep actions`() = runBlocking {
        val created = planner.createDraft("draft", batch(), listOf(action("file-1")))
        assertEquals(1, assertIs<SelectionPlanningResult.Success<*>>(created)
            .let { (it.value as SelectionDraft).revision })

        val revised = planner.reviseDraft(
            "draft", 1, batch(), listOf(action("file-2", SourceDecisionValue.REVIEW)),
        )
        assertEquals(2, assertIs<SelectionPlanningResult.Success<SelectionDraft>>(revised).value.revision)
        assertEquals(
            SelectionPlanningFailure.STALE_REVISION,
            assertIs<SelectionPlanningResult.Rejected>(
                planner.reviseDraft("draft", 1, batch(), listOf(action("file-1"))),
            ).failure,
        )
        assertEquals(
            SelectionPlanningFailure.INVALID_SELECTION,
            assertIs<SelectionPlanningResult.Rejected>(
                planner.createDraft(
                    "imported",
                    batch(imported = true),
                    listOf(action("file-1")),
                ),
            ).failure,
        )
    }

    @Test
    fun `plan freezes sorted exact selection at draft revision and scan generation`() = runBlocking {
        planner.createDraft(
            "draft",
            batch(),
            listOf(action("file-2"), action("file-1")),
        )

        val result = planner.freezePlan("draft", batch(), plan())

        val frozen = assertIs<SelectionPlanningResult.Success<FrozenSelectionPlan>>(result).value
        assertEquals(listOf("key-1", "key-2"), frozen.selection.items.map { it.sourceKey })
        assertEquals(7, frozen.selection.scanGeneration)
        assertEquals(1, frozen.selection.draftRevision)
        assertEquals(frozen, repository.plan)
    }

    @Test
    fun `plan rejects stale generation revision and mismatched imports`() = runBlocking {
        planner.createDraft("draft", batch(), listOf(action("file-1")))

        val stale = plan().copy(snapshot = PlanSnapshot(6, 1))
        val mismatch = plan(fileIds = listOf("file-2"))

        listOf(stale, mismatch).forEach {
            assertEquals(
                SelectionPlanningFailure.INVALID_PLAN,
                assertIs<SelectionPlanningResult.Rejected>(
                    planner.freezePlan("draft", batch(), it),
                ).failure,
            )
        }
    }

    private fun batch(imported: Boolean = false) = PersistedSourceBatch(
        "directory:set",
        listOf(
            item("file-1", "key-1", imported),
            item("file-2", "key-2", false),
        ),
    )

    private fun item(fileId: String, key: String, imported: Boolean) = PersistedSourceItem(
        "directory:set", fileId, key, "$fileId.jpg", 7, 1, 2, "image/jpeg", null, imported,
    )

    private fun action(
        fileId: String,
        value: SourceDecisionValue = SourceDecisionValue.KEEP,
    ) = DraftSelectionAction(fileId, value)

    private fun plan(fileIds: List<String> = listOf("file-1", "file-2")) = PlanBuilder.build(
        "plan", PlanType.ORG, PlanSnapshot(7, 1), 10,
        OrgPayload(
            "album",
            RelativePath("Albums/album"),
            fileIds.map {
                PlannedImport(
                    SourceLocator(SourceFileRef(it), FileFingerprint(1, 2, "image/jpeg")),
                    RelativePath("$it.jpg"),
                    ImportStrategy.COPY_KEEP_SOURCE,
                )
            },
        ),
        buildList {
            add(PlanStepType.CREATE_DIR to "album")
            add(PlanStepType.WRITE_NOMEDIA to "album")
            add(PlanStepType.WRITE_MANIFEST to "album")
            fileIds.forEach {
                add(PlanStepType.COPY_PART to it)
                add(PlanStepType.PROMOTE_PART to it)
            }
            add(PlanStepType.COMMIT_MANIFEST to "album")
        },
    )

    private class MemoryRepository : SelectionDraftRepository {
        private var draft: SelectionDraft? = null
        var plan: FrozenSelectionPlan? = null

        override suspend fun create(draft: SelectionDraft): Boolean {
            if (this.draft != null) return false
            this.draft = draft
            return true
        }

        override suspend fun find(draftId: String): SelectionDraft? =
            draft?.takeIf { it.draftId == draftId }

        override suspend fun replace(draft: SelectionDraft, expectedRevision: Long): Boolean {
            if (this.draft?.revision != expectedRevision) return false
            this.draft = draft
            return true
        }

        override suspend fun savePlan(frozen: FrozenSelectionPlan): Boolean {
            if (plan != null) return false
            plan = frozen
            return true
        }
    }
}
