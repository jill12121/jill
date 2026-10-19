package com.galleryorganizer.domain

import com.galleryorganizer.contract.ManifestStore
import com.galleryorganizer.contract.PermanentDeleter
import com.galleryorganizer.contract.SourceReader
import com.galleryorganizer.contract.WorkspaceGateway

interface Scanner {
    val sourceReader: SourceReader
}

interface Analyzer

interface AlbumEditor {
    val workspaceGateway: WorkspaceGateway
    val manifestStore: ManifestStore
}

interface SourceCleanup {
    val permanentDeleter: PermanentDeleter
}
