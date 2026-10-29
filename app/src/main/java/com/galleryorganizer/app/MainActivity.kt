package com.galleryorganizer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.galleryorganizer.presentation.LibraryReducer
import com.galleryorganizer.presentation.LibraryUiState
import com.galleryorganizer.presentation.SetupReducer
import com.galleryorganizer.presentation.SetupScreen
import com.galleryorganizer.presentation.SetupUiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var state by remember { mutableStateOf(SetupUiState.initial()) }
                var libraryState by remember { mutableStateOf(LibraryUiState.initial()) }
                SetupScreen(
                    state = state,
                    libraryState = libraryState,
                    onEvent = { state = SetupReducer.reduce(state, it) },
                    onLibraryEvent = {
                        libraryState = LibraryReducer.reduce(libraryState, it)
                    },
                )
            }
        }
    }
}
