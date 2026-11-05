package com.galleryorganizer.data

import com.galleryorganizer.contract.ManifestStore
import com.galleryorganizer.contract.PermanentDeleter
import com.galleryorganizer.contract.SourceReader
import com.galleryorganizer.contract.WorkspaceGateway

/**
 * File execution adapters remain declarations. F05.2 permits only the
 * separate SAF root-selection adapter; these mutation ports stay unbound.
 */
data class AdapterBindings(
    val sourceReader: SourceReader,
    val workspaceGateway: WorkspaceGateway,
    val permanentDeleter: PermanentDeleter,
    val manifestStore: ManifestStore,
)
