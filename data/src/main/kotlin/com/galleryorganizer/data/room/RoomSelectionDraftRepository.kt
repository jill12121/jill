package com.galleryorganizer.data.room

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.galleryorganizer.domain.DraftSelectionAction
import com.galleryorganizer.domain.FrozenSelectionPlan
import com.galleryorganizer.domain.OrgPayload
import com.galleryorganizer.domain.SelectionDraft
import com.galleryorganizer.domain.SelectionDraftRepository
import com.galleryorganizer.domain.SourceDecisionValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun createSelectionDraftRepository(context: Context): SelectionDraftRepository =
    RoomSelectionDraftRepository(organizerMetadataDatabase(context).metadataDao())

class RoomSelectionDraftRepository(
    private val dao: OrganizerMetadataDao,
) : SelectionDraftRepository {
    override suspend fun create(draft: SelectionDraft): Boolean = withContext(Dispatchers.IO) {
        try {
            dao.createSelectionDraft(draft.entity(), draft.actionEntities())
            true
        } catch (_: SQLiteConstraintException) {
            false
        }
    }

    override suspend fun find(draftId: String): SelectionDraft? = withContext(Dispatchers.IO) {
        dao.findDraft(draftId)?.takeIf { it.state == "EDITING" }?.let { entity ->
            SelectionDraft(
                entity.draftId,
                entity.scopeId,
                entity.revision,
                dao.findDraftActions(draftId).map {
                    DraftSelectionAction(
                        it.fileId,
                        SourceDecisionValue.valueOf(it.action),
                        it.displayOrder,
                        it.isCover,
                    )
                },
            )
        }
    }

    override suspend fun replace(
        draft: SelectionDraft,
        expectedRevision: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        dao.replaceSelectionDraft(draft.entity(), expectedRevision, draft.actionEntities())
    }

    override suspend fun savePlan(frozen: FrozenSelectionPlan): Boolean =
        withContext(Dispatchers.IO) {
            try {
                dao.insertPlanForRevision(
                    PlanEntity(
                        frozen.plan.planId,
                        frozen.draftId,
                        frozen.plan.type.name,
                        frozen.plan.snapshot.draftRevision,
                        frozen.selection.toJson(),
                        frozen.toPayloadJson(),
                        "CREATED",
                    ),
                )
            } catch (_: SQLiteConstraintException) {
                false
            }
        }

    private fun SelectionDraft.entity() = DraftEntity(
        draftId, "BATCH", scopeId, revision, "BATCH:$scopeId", "EDITING",
    )

    private fun SelectionDraft.actionEntities() = actions.map {
        DraftActionEntity(draftId, it.fileId, it.action.name, it.displayOrder, it.isCover)
    }
}

private fun com.galleryorganizer.domain.SelectionSnapshot.toJson(): String =
    """{"scanGeneration":$scanGeneration,"draftRevision":$draftRevision,"items":[${
        items.joinToString(",") {
            """{"sourceKey":${it.sourceKey.json()},"displayName":${it.displayName.json()}}"""
        }
    }]}"""

private fun FrozenSelectionPlan.toPayloadJson(): String {
    val payload = plan.payload as OrgPayload
    val imports = payload.imports.joinToString(",") {
        """{"sourceRef":${it.source.ref.stableKey.json()},"sizeBytes":${it.source.fingerprint.sizeBytes},"modifiedAtEpochMillis":${it.source.fingerprint.modifiedAtEpochMillis},"mimeType":${it.source.fingerprint.mimeType?.json() ?: "null"},"target":${it.target.value.json()},"strategy":${it.strategy.name.json()}}"""
    }
    val steps = plan.steps.joinToString(",") {
        """{"index":${it.index},"type":${it.type.name.json()},"targetId":${it.targetId.json()}}"""
    }
    return """{"schemaVersion":1,"createdAtEpochMillis":${plan.createdAtEpochMillis},"albumId":${payload.albumId.json()},"immutableDir":${payload.immutableDir.value.json()},"imports":[$imports],"steps":[$steps]}"""
}

private fun String.json(): String = buildString {
    append('"')
    this@json.forEach {
        when (it) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (it.code < 32) append("\\u%04x".format(it.code)) else append(it)
        }
    }
    append('"')
}
