package com.galleryorganizer.app

import com.galleryorganizer.contract.ManifestStore
import com.galleryorganizer.contract.PermanentDeleter
import com.galleryorganizer.contract.SourceReader
import com.galleryorganizer.contract.WorkspaceGateway
import com.galleryorganizer.data.AdapterBindings

/**
 * The app module is the only composition root. Real adapters are deliberately
 * absent until their dependent device gates are approved.
 */
class AppContainer(
    sourceReader: SourceReader,
    workspaceGateway: WorkspaceGateway,
    permanentDeleter: PermanentDeleter,
    manifestStore: ManifestStore,
) {
    val adapters = AdapterBindings(
        sourceReader = sourceReader,
        workspaceGateway = workspaceGateway,
        permanentDeleter = permanentDeleter,
        manifestStore = manifestStore,
    )
}
