package com.galleryorganizer.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.galleryorganizer.contract.SourceFileRef
import com.galleryorganizer.contract.ThumbnailRequest
import com.galleryorganizer.contract.ThumbnailResult
import com.galleryorganizer.data.mediastore.createMediaStoreScanner
import com.galleryorganizer.data.mediastore.createMediaStoreThumbnailSource
import com.galleryorganizer.data.room.createSourceMetadataRepository
import com.galleryorganizer.data.room.createAlbumEditor
import com.galleryorganizer.data.saf.createSafWorkspaceSetup
import com.galleryorganizer.domain.MediaReadGrant
import com.galleryorganizer.domain.AlbumEditRequest
import com.galleryorganizer.domain.AlbumEditResult
import com.galleryorganizer.domain.AlbumState
import com.galleryorganizer.domain.ScanCoverage
import com.galleryorganizer.domain.SourceBatchKind
import com.galleryorganizer.domain.SourceDiscoveryConfig
import com.galleryorganizer.domain.SourceDecision
import com.galleryorganizer.domain.SourcePageRequest
import com.galleryorganizer.domain.SourceScanRequest
import com.galleryorganizer.domain.SourceScanResult
import com.galleryorganizer.domain.WorkspaceAdoptionResult
import com.galleryorganizer.domain.WorkspaceSetupMode
import com.galleryorganizer.domain.WorkspaceSetupResult
import com.galleryorganizer.presentation.InboxReducer
import com.galleryorganizer.presentation.InboxBatchItem
import com.galleryorganizer.presentation.InboxEvent
import com.galleryorganizer.presentation.InboxPreviewItem
import com.galleryorganizer.presentation.InboxUiState
import com.galleryorganizer.presentation.ImageViewerEvent
import com.galleryorganizer.presentation.ImageViewerReducer
import com.galleryorganizer.presentation.ImageViewerUiState
import com.galleryorganizer.presentation.DiscoveredBatchOption
import com.galleryorganizer.presentation.LibraryReducer
import com.galleryorganizer.presentation.LibraryEvent
import com.galleryorganizer.presentation.PreviewAlbum
import com.galleryorganizer.presentation.PreviewAlbumImage
import com.galleryorganizer.presentation.LibraryUiState
import com.galleryorganizer.presentation.SetupReducer
import com.galleryorganizer.presentation.SetupScreen
import com.galleryorganizer.presentation.SetupEvent
import com.galleryorganizer.presentation.SetupMode
import com.galleryorganizer.presentation.SetupDestination
import com.galleryorganizer.presentation.SetupUiState
import com.galleryorganizer.domain.scanCoverageFor
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(SetupUiState.initial()) }
                var libraryState by remember { mutableStateOf(LibraryUiState.production()) }
                var catalogAlbums by remember {
                    mutableStateOf<Map<String, com.galleryorganizer.domain.WorkspaceAlbumCatalogItem>>(
                        emptyMap(),
                    )
                }
                var inboxState by remember { mutableStateOf(InboxUiState.production()) }
                var inboxThumbnails by remember {
                    mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
                }
                var imageViewerState by remember { mutableStateOf(ImageViewerUiState()) }
                var imageViewerPreview by remember { mutableStateOf<ImageBitmap?>(null) }
                val workspaceSetup = remember {
                    createSafWorkspaceSetup(applicationContext)
                }
                val scanner = remember { createMediaStoreScanner(applicationContext) }
                val thumbnailSource = remember {
                    createMediaStoreThumbnailSource(applicationContext)
                }
                val sourceMetadata = remember {
                    createSourceMetadataRepository(applicationContext)
                }
                val albumEditor = remember { createAlbumEditor(applicationContext) }
                val scope = rememberCoroutineScope()
                fun dispatchImageViewerEvent(event: ImageViewerEvent) {
                    val previousSourceId = imageViewerState.currentItem?.sourceId
                    imageViewerState = ImageViewerReducer.reduce(imageViewerState, event)
                    val currentItem = imageViewerState.currentItem
                    val shouldLoad = currentItem != null &&
                        currentItem.sourceId != previousSourceId
                    if (currentItem == null) {
                        imageViewerPreview = null
                    } else if (shouldLoad) {
                        imageViewerPreview = null
                        val requestedSourceId = currentItem.sourceId
                        scope.launch {
                            val result = thumbnailSource.load(
                                ThumbnailRequest(
                                    SourceFileRef(currentItem.sourceId),
                                    ImageViewerReducer.PREVIEW_EDGE_PX,
                                ),
                            )
                            if (imageViewerState.currentItem?.sourceId != requestedSourceId) {
                                return@launch
                            }
                            if (result is ThumbnailResult.Loaded) {
                                imageViewerPreview = result.value.asImageBitmap()
                                imageViewerState = ImageViewerReducer.reduce(
                                    imageViewerState,
                                    ImageViewerEvent.PreviewLoaded(requestedSourceId),
                                )
                            } else {
                                imageViewerState = ImageViewerReducer.reduce(
                                    imageViewerState,
                                    ImageViewerEvent.PreviewUnavailable(requestedSourceId),
                                )
                            }
                        }
                    }
                }
                suspend fun loadInboxPage(
                    batchKey: String,
                    afterFileId: String?,
                    append: Boolean,
                ) {
                    try {
                        val page = sourceMetadata.loadPage(
                            SourcePageRequest(batchKey, afterFileId, 24),
                        )
                        val items = page.items.map {
                            InboxPreviewItem(
                                sourceId = it.fileId,
                                sourceKey = it.sourceKey,
                                label = it.displayName,
                                detail = formatBytes(it.sizeBytes),
                                persistedDecision = it.decision,
                            )
                        }
                        inboxState = InboxReducer.reduce(
                            inboxState,
                            InboxEvent.PageLoaded(
                                batchKey,
                                items,
                                page.nextCursor?.fileId,
                                append,
                            ),
                        )
                        val visibleSourceIds = inboxState.previewItems
                            .mapTo(mutableSetOf(), InboxPreviewItem::sourceId)
                        inboxThumbnails = inboxThumbnails.filterKeys { it in visibleSourceIds }
                        page.items.forEach { item ->
                            if (item.fileId !in visibleSourceIds) return@forEach
                            val result = thumbnailSource.load(
                                ThumbnailRequest(SourceFileRef(item.fileId)),
                            )
                            if (result is ThumbnailResult.Loaded &&
                                inboxState.selectedBatchKey == batchKey &&
                                inboxState.previewItems.any {
                                    it.sourceId == item.fileId
                                }
                            ) {
                                inboxThumbnails = inboxThumbnails +
                                    (item.fileId to result.value.asImageBitmap())
                            }
                        }
                    } catch (_: RuntimeException) {
                        inboxState = InboxReducer.reduce(
                            inboxState,
                            InboxEvent.PageFailed(batchKey),
                        )
                    }
                }
                suspend fun runSourceScan(coverage: ScanCoverage) {
                    state = SetupReducer.reduce(state, SetupEvent.SourceScanStarted)
                    val organizerRoot = state.selectedRootRelativePath
                    if (organizerRoot == null) {
                        state = SetupReducer.reduce(state, SetupEvent.SourceScanFailed)
                        return
                    }
                    val protected = buildSet {
                        if ("screenshots" in state.protectedDirectoryIds) {
                            add("Download/Screenshots")
                        }
                        if ("downloads" in state.protectedDirectoryIds) add("Download")
                    }
                    state = when (
                        val result = scanner.scan(
                            SourceScanRequest(
                                rootRelativePath = "Download",
                                config = SourceDiscoveryConfig(
                                    organizerRootRelativePath = organizerRoot,
                                    protectedDirectoryPaths = protected,
                                    monitoredFirstLevelDirectories = emptySet(),
                                    coverage = coverage,
                                    completed = false,
                                ),
                            ),
                        )
                    ) {
                        is SourceScanResult.Completed -> try {
                            sourceMetadata.persistSnapshot(
                                result.snapshot,
                                scanGeneration = System.currentTimeMillis(),
                            )
                            SetupReducer.reduce(
                                state,
                                SetupEvent.SourceScanSucceeded(
                                    result.snapshot.batches.map {
                                        DiscoveredBatchOption(
                                            stableKey = it.stableKey,
                                            label = it.displayName,
                                            imageCount = it.imageCount,
                                            rootLooseImages =
                                                it.kind == SourceBatchKind.ROOT_LOOSE_IMAGES,
                                        )
                                    },
                                ),
                            )
                        } catch (_: RuntimeException) {
                            SetupReducer.reduce(state, SetupEvent.SourceScanFailed)
                        }
                        is SourceScanResult.Failed ->
                            SetupReducer.reduce(state, SetupEvent.SourceScanFailed)
                    }
                }
                suspend fun applyResult(result: WorkspaceSetupResult) {
                    state = when (result) {
                        is WorkspaceSetupResult.Accepted -> SetupReducer.reduce(
                            state,
                            SetupEvent.RootSelectionAccepted(
                                result.root.displayName,
                                if (result.root.mode == WorkspaceSetupMode.ADOPT_EXISTING) {
                                    SetupMode.ADOPT
                                } else {
                                    SetupMode.NEW
                                },
                                result.root.relativePath,
                            ),
                        )
                        is WorkspaceSetupResult.Rejected ->
                            SetupReducer.reduce(state, SetupEvent.RootSelectionFailed)
                        WorkspaceSetupResult.NoSelection -> state
                    }
                    if (result is WorkspaceSetupResult.Accepted &&
                        result.root.mode == WorkspaceSetupMode.ADOPT_EXISTING
                    ) {
                        state = SetupReducer.reduce(state, SetupEvent.AdoptionValidationStarted)
                        state = when (val adoption = workspaceSetup.validateAdoption()) {
                            is WorkspaceAdoptionResult.Verified -> SetupReducer.reduce(
                                state,
                                SetupEvent.AdoptionValidationSucceeded(
                                    adoption.albumCount,
                                    adoption.importingCount,
                                ),
                            ).also {
                                catalogAlbums = adoption.albums.associateBy { album ->
                                    album.manifest.albumId
                                }
                                libraryState = LibraryReducer.reduce(
                                    libraryState,
                                    LibraryEvent.AlbumsLoaded(adoption.albums.map { album ->
                                        PreviewAlbum(
                                            album.manifest.albumId,
                                            album.directoryName.substringBeforeLast("__"),
                                            album.directoryName.substringBeforeLast("__"),
                                            album.manifest.state != AlbumState.COMMITTED,
                                            album.manifest.state,
                                            album.manifest.images.sortedBy { image -> image.order }.map {
                                                PreviewAlbumImage(it.imageId, it.fileName, it.order)
                                            },
                                        )
                                    }),
                                )
                            }
                            is WorkspaceAdoptionResult.Rejected -> SetupReducer.reduce(
                                state,
                                SetupEvent.AdoptionValidationFailed,
                            )
                        }
                    }
                    if (result is WorkspaceSetupResult.Accepted) {
                        val coverage = currentScanCoverage()
                        if (coverage != ScanCoverage.UNKNOWN) {
                            state = SetupReducer.reduce(
                                state,
                                SetupEvent.MediaAccessResolved(coverage),
                            )
                            runSourceScan(coverage)
                        }
                    }
                }
                val rootLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    if (uri == null) {
                        state = SetupReducer.reduce(state, SetupEvent.RootSelectionCancelled)
                    } else {
                        val mode = if (state.mode == SetupMode.ADOPT) {
                            WorkspaceSetupMode.ADOPT_EXISTING
                        } else {
                            WorkspaceSetupMode.NEW
                        }
                        scope.launch { applyResult(workspaceSetup.select(uri.toString(), mode)) }
                    }
                }
                val mediaPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    val coverage = currentScanCoverage()
                    state = SetupReducer.reduce(
                        state,
                        SetupEvent.MediaAccessResolved(coverage),
                    )
                    if (coverage != ScanCoverage.UNKNOWN) {
                        scope.launch { runSourceScan(coverage) }
                    }
                }
                LaunchedEffect(Unit) {
                    applyResult(workspaceSetup.load())
                }
                LaunchedEffect(state.destination) {
                    if (state.destination == SetupDestination.INBOX) {
                        try {
                            val batches = sourceMetadata.loadBatchSummaries().map {
                                InboxBatchItem(
                                    it.batchKey,
                                    it.batchKey.batchLabel(),
                                    it.imageCount,
                                )
                            }
                            inboxState = InboxReducer.reduce(
                                inboxState,
                                InboxEvent.BatchesLoaded(batches),
                            )
                            val selected = inboxState.selectedBatchKey
                                ?.takeIf { key -> batches.any { it.batchKey == key } }
                                ?: batches.firstOrNull()?.batchKey
                            if (selected != null) {
                                inboxState = InboxReducer.reduce(
                                    inboxState,
                                    InboxEvent.SelectBatch(selected),
                                )
                                inboxThumbnails = emptyMap()
                                loadInboxPage(selected, null, append = false)
                            }
                        } catch (_: RuntimeException) {
                            inboxState.selectedBatchKey?.let {
                                inboxState = InboxReducer.reduce(
                                    inboxState,
                                    InboxEvent.PageFailed(it),
                                )
                            }
                        }
                    }
                }
                SetupScreen(
                    state = state,
                    libraryState = libraryState,
                    inboxState = inboxState,
                    imageViewerState = imageViewerState,
                    inboxThumbnails = inboxThumbnails,
                    imageViewerPreview = imageViewerPreview,
                    onEvent = { event ->
                        state = SetupReducer.reduce(state, event)
                        if (event == SetupEvent.ChooseNewRoot ||
                            event == SetupEvent.ChooseExistingRoot
                        ) {
                            rootLauncher.launch(null)
                        }
                        if (event == SetupEvent.RequestMediaAccess) {
                            mediaPermissionLauncher.launch(mediaPermissions())
                        }
                    },
                    onLibraryEvent = { event ->
                        val editable = libraryState.editableAlbum
                        libraryState = LibraryReducer.reduce(libraryState, event)
                        if (event == LibraryEvent.CreateMetadataPlan &&
                            editable != null &&
                            libraryState.metadataPlanStatus ==
                            com.galleryorganizer.presentation.MetadataPlanStatus.SAVING
                        ) {
                            val catalog = catalogAlbums[editable.albumId]
                            scope.launch {
                                val result = catalog?.let {
                                    albumEditor.create(
                                        AlbumEditRequest(
                                            it,
                                            editable.images.map(PreviewAlbumImage::imageId),
                                            System.currentTimeMillis(),
                                        ),
                                    )
                                }
                                libraryState = LibraryReducer.reduce(
                                    libraryState,
                                    if (result is AlbumEditResult.Success) {
                                        LibraryEvent.MetadataPlanCreated
                                    } else {
                                        LibraryEvent.MetadataPlanFailed
                                    },
                                )
                            }
                        }
                    },
                    onOpenImageViewer = { sourceId ->
                        dispatchImageViewerEvent(
                            ImageViewerEvent.Open(inboxState.previewItems, sourceId),
                        )
                    },
                    onImageViewerEvent = ::dispatchImageViewerEvent,
                    onInboxEvent = { event ->
                        when (event) {
                            is InboxEvent.SelectBatch -> {
                                val previous = inboxState.selectedBatchKey
                                inboxState = InboxReducer.reduce(inboxState, event)
                                if (inboxState.selectedBatchKey != previous) {
                                    inboxThumbnails = emptyMap()
                                    scope.launch {
                                        loadInboxPage(event.batchKey, null, append = false)
                                    }
                                }
                            }
                            InboxEvent.LoadNextPage,
                            InboxEvent.RetryPage,
                            -> {
                                val batchKey = inboxState.selectedBatchKey
                                val cursor = inboxState.nextCursorFileId
                                val append = inboxState.previewItems.isNotEmpty()
                                inboxState = InboxReducer.reduce(inboxState, event)
                                if (batchKey != null) {
                                    scope.launch { loadInboxPage(batchKey, cursor, append) }
                                }
                            }
                            is InboxEvent.SetDecision -> {
                                val item = inboxState.previewItems.firstOrNull {
                                    it.sourceId == event.sourceId
                                }
                                if (item != null) {
                                    scope.launch {
                                        try {
                                            sourceMetadata.saveDecision(
                                                SourceDecision(
                                                    item.sourceKey,
                                                    event.decision,
                                                    System.currentTimeMillis(),
                                                ),
                                            )
                                            inboxState = InboxReducer.reduce(inboxState, event)
                                        } catch (_: RuntimeException) {
                                            Unit
                                        }
                                    }
                                }
                            }
                            else -> inboxState = InboxReducer.reduce(inboxState, event)
                        }
                    },
                )
            }
        }
    }

    private fun currentScanCoverage(): ScanCoverage {
        val grants = buildSet {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
            ) add(MediaReadGrant.READ_EXTERNAL_STORAGE)
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
            ) add(MediaReadGrant.READ_MEDIA_IMAGES)
            if (Build.VERSION.SDK_INT >= 34 &&
                checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
                PackageManager.PERMISSION_GRANTED
            ) add(MediaReadGrant.VISUAL_USER_SELECTED)
        }
        return scanCoverageFor(Build.VERSION.SDK_INT, grants)
    }

    private fun mediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun String.batchLabel(): String = when {
    this == "virtual:download-root-loose-images" -> "Download root"
    startsWith("directory:") -> removePrefix("directory:").substringAfterLast('/')
    else -> this
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes bytes"
}
