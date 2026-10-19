package com.galleryorganizer.domain

import com.galleryorganizer.contract.FsResult
import com.galleryorganizer.contract.ScanScope
import com.galleryorganizer.contract.SourceBatch
import kotlinx.coroutines.flow.Flow

interface Scanner {
    fun scan(scope: ScanScope): Flow<FsResult<SourceBatch>>
}

data class AnalysisRequest(val subjectId: String)

data class AnalysisSummary(val subjectId: String)

interface Analyzer {
    suspend fun analyze(request: AnalysisRequest): AnalysisSummary
}

data class AlbumEditRequest(val albumId: String)

data class AlbumEditPreview(val albumId: String)

interface AlbumEditor {
    suspend fun preview(request: AlbumEditRequest): AlbumEditPreview
}

data class SourceCleanupRequest(val sourceCount: Int)

data class SourceCleanupPreview(val sourceCount: Int)

interface SourceCleanup {
    suspend fun preview(request: SourceCleanupRequest): SourceCleanupPreview
}
