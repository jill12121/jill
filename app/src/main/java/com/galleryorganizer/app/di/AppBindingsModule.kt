package com.galleryorganizer.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Metadata discovery has an explicit factory. Mutation ports and byte-level
 * source access remain unbound.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppBindingsModule
