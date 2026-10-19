package com.galleryorganizer.data

import com.galleryorganizer.contract.ManifestStore
import com.galleryorganizer.contract.PermanentDeleter
import com.galleryorganizer.contract.SourceReader
import com.galleryorganizer.contract.WorkspaceGateway

/**
 * Data adapters remain declarations until their corresponding storage or
 * device capability is approved. No Android or filesystem API is used here.
 */
data class AdapterBindings(
    val sourceReader: SourceReader,
    val workspaceGateway: WorkspaceGateway,
    val permanentDeleter: PermanentDeleter,
    val manifestStore: ManifestStore,
)
