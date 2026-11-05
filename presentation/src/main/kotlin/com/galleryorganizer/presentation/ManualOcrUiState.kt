package com.galleryorganizer.presentation

import com.galleryorganizer.domain.ManualOcrRunFailure
import com.galleryorganizer.domain.ManualOcrRunResult
import com.galleryorganizer.domain.ManualOcrScanRequest
import com.galleryorganizer.domain.ManualOcrScanState

enum class ManualOcrAvailability {
    DISABLED_PENDING_NO_GMS_EVIDENCE,
    REVIEW_ONLY,
}

enum class ManualOcrPhase {
    IDLE,
    AWAITING_CONFIRMATION,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

enum class ManualOcrUiFailure {
    RECOGNIZER_FAILURE,
    INVALID_RESULT,
}

@ConsistentCopyVisibility
data class ManualOcrUiState internal constructor(
    val availability: ManualOcrAvailability,
    val request: ManualOcrScanRequest? = null,
    val phase: ManualOcrPhase = ManualOcrPhase.IDLE,
    val processedSubjects: Int = 0,
    val evidenceCount: Int = 0,
    val failure: ManualOcrUiFailure? = null,
) {
    val canRequest: Boolean
        get() = availability == ManualOcrAvailability.REVIEW_ONLY &&
            phase == ManualOcrPhase.IDLE

    companion object {
        fun production() = ManualOcrUiState(
            availability = ManualOcrAvailability.DISABLED_PENDING_NO_GMS_EVIDENCE,
        )

        internal fun reviewOnly() = ManualOcrUiState(
            availability = ManualOcrAvailability.REVIEW_ONLY,
        )
    }
}

sealed interface ManualOcrEvent {
    data class Request(val request: ManualOcrScanRequest) : ManualOcrEvent
    data class Confirm(val sessionId: String) : ManualOcrEvent
    data class Cancel(val sessionId: String) : ManualOcrEvent
    data class RunFinished(
        val sessionId: String,
        val result: ManualOcrRunResult,
    ) : ManualOcrEvent

    data object Dismiss : ManualOcrEvent
}

object ManualOcrReducer {
    fun reduce(state: ManualOcrUiState, event: ManualOcrEvent): ManualOcrUiState =
        when (event) {
            is ManualOcrEvent.Request ->
                if (state.canRequest) {
                    state.copy(
                        request = event.request,
                        phase = ManualOcrPhase.AWAITING_CONFIRMATION,
                    )
                } else {
                    state
                }
            is ManualOcrEvent.Confirm ->
                if (state.matches(event.sessionId, ManualOcrPhase.AWAITING_CONFIRMATION)) {
                    state.copy(phase = ManualOcrPhase.RUNNING)
                } else {
                    state
                }
            is ManualOcrEvent.Cancel ->
                if (state.request?.sessionId == event.sessionId &&
                    state.phase in setOf(
                        ManualOcrPhase.AWAITING_CONFIRMATION,
                        ManualOcrPhase.RUNNING,
                    )
                ) {
                    state.copy(phase = ManualOcrPhase.CANCELLED)
                } else {
                    state
                }
            is ManualOcrEvent.RunFinished -> finish(state, event)
            ManualOcrEvent.Dismiss ->
                if (state.phase in setOf(
                        ManualOcrPhase.COMPLETED,
                        ManualOcrPhase.CANCELLED,
                        ManualOcrPhase.FAILED,
                    )
                ) {
                    ManualOcrUiState(availability = state.availability)
                } else {
                    state
                }
        }

    private fun finish(
        state: ManualOcrUiState,
        event: ManualOcrEvent.RunFinished,
    ): ManualOcrUiState {
        if (!state.matches(event.sessionId, ManualOcrPhase.RUNNING)) {
            return state
        }
        val request = state.request ?: return state
        val resultSession = when (val result = event.result) {
            is ManualOcrRunResult.Completed -> result.session
            is ManualOcrRunResult.Failed -> result.session
        }
        val evidence = when (val result = event.result) {
            is ManualOcrRunResult.Completed -> result.evidence
            is ManualOcrRunResult.Failed -> result.evidence
        }
        val validResult = resultSession.request == request &&
            evidence.all { it.contentKey in request.contentKeys } &&
            when (event.result) {
                is ManualOcrRunResult.Completed ->
                    resultSession.state == ManualOcrScanState.COMPLETED
                is ManualOcrRunResult.Failed ->
                    resultSession.state == ManualOcrScanState.FAILED &&
                        event.result.contentKey in request.contentKeys
            }
        if (!validResult) {
            return state.copy(
                phase = ManualOcrPhase.FAILED,
                processedSubjects = 0,
                evidenceCount = 0,
                failure = ManualOcrUiFailure.INVALID_RESULT,
            )
        }
        return when (val result = event.result) {
            is ManualOcrRunResult.Completed -> state.copy(
                phase = ManualOcrPhase.COMPLETED,
                processedSubjects = result.session.processedSubjects,
                evidenceCount = result.evidence.size,
            )
            is ManualOcrRunResult.Failed -> state.copy(
                phase = ManualOcrPhase.FAILED,
                processedSubjects = result.session.processedSubjects,
                evidenceCount = result.evidence.size,
                failure = result.failure.toUiFailure(),
            )
        }
    }

    private fun ManualOcrUiState.matches(
        sessionId: String,
        expectedPhase: ManualOcrPhase,
    ) = phase == expectedPhase && request?.sessionId == sessionId

    private fun ManualOcrRunFailure.toUiFailure() = when (this) {
        ManualOcrRunFailure.RECOGNIZER_FAILURE -> ManualOcrUiFailure.RECOGNIZER_FAILURE
        ManualOcrRunFailure.INVALID_RESULT -> ManualOcrUiFailure.INVALID_RESULT
    }
}
