package com.galleryorganizer.data.room

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.galleryorganizer.domain.AlbumEditor
import com.galleryorganizer.domain.DefaultAlbumEditor
import com.galleryorganizer.domain.MetadataPayload
import com.galleryorganizer.domain.MetadataPlanRepository
import com.galleryorganizer.domain.OrganizerPlan
import com.galleryorganizer.domain.PlanType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun createAlbumEditor(context: Context): AlbumEditor =
    DefaultAlbumEditor(RoomMetadataPlanRepository(organizerMetadataDatabase(context)))

class RoomMetadataPlanRepository(
    private val database: OrganizerMetadataDatabase,
) : MetadataPlanRepository {
    override suspend fun save(plan: OrganizerPlan): Boolean = withContext(Dispatchers.IO) {
        val payload = plan.payload as? MetadataPayload ?: return@withContext false
        if (plan.type != PlanType.METADATA) return@withContext false
        try {
            database.metadataDao().insertPlan(
                PlanEntity(
                    plan.planId,
                    null,
                    plan.type.name,
                    null,
                    """{"manifestRevision":${plan.snapshot.scanGeneration}}""",
                    plan.toPayloadJson(payload),
                    "CREATED",
                ),
            )
            true
        } catch (_: SQLiteConstraintException) {
            false
        }
    }
}

private fun OrganizerPlan.toPayloadJson(payload: MetadataPayload): String {
    val steps = steps.joinToString(",") {
        """{"index":${it.index},"type":${it.type.name.json()},"targetId":${it.targetId.json()}}"""
    }
    return """{"schemaVersion":1,"createdAtEpochMillis":$createdAtEpochMillis,"albumId":${payload.albumId.json()},"manifestPatch":${payload.manifestPatch.json()},"steps":[$steps]}"""
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
            else -> append(it)
        }
    }
    append('"')
}
