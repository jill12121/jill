package com.galleryorganizer.presentation

import com.galleryorganizer.domain.AlbumEditor
import com.galleryorganizer.domain.Analyzer
import com.galleryorganizer.domain.Scanner

data class AppScreenDependencies(
    val scanner: Scanner,
    val analyzer: Analyzer,
    val albumEditor: AlbumEditor,
)
