package com.galleryorganizer.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The composition root is intentionally empty until F03 defines fake bindings.
 * Production Android adapters remain blocked by S01/S02/S04 device gates.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppBindingsModule
