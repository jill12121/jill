package com.galleryorganizer.domain

import java.security.MessageDigest
import java.util.concurrent.CancellationException

private const val MAX_MANUAL_OCR_SUBJECTS = 100
private const val OCR_ADVERTISEMENT_TYPE = "OCR_ADVERTISEMENT"
private const val OCR_ADVERTISEMENT_MASKED_DETAIL = "Potential advertisement text (masked)"

data class ManualOcrScanRequest(
    val sessionId: String,
    val contentKeys: List<String>,
    val userInitiated: Boolean,
) {
    init {
        require(sessionId.isNotBlank()) { "Session id is required." }
        require(userInitiated) { "OCR scans must be explicitly user initiated." }
        require(contentKeys.isNotEmpty()) { "At least one OCR subject is required." }
        require(contentKeys.size <= MAX_MANUAL_OCR_SUBJECTS) {
            "Manual OCR subject limit exceeded."
        }
        require(contentKeys.all { it.isNotBlank() }) { "Content keys must not be blank." }
        require(contentKeys.toSet().size == contentKeys.size) {
            "Content keys must be unique within a scan."
        }
    }
}

enum class ManualOcrScanState {
    AWAITING_CONFIRMATION,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED,
}

@ConsistentCopyVisibility
data class ManualOcrScanSession private constructor(
    val request: ManualOcrScanRequest,
    val state: ManualOcrScanState,
    val processedSubjects: Int,
    val evidenceCount: Int,
) {
    val totalSubjects: Int
        get() = request.contentKeys.size

    fun start(): ManualOcrScanSession {
        check(state == ManualOcrScanState.AWAITING_CONFIRMATION)
        return copy(state = ManualOcrScanState.RUNNING)
    }

    fun pause(): ManualOcrScanSession {
        check(state == ManualOcrScanState.RUNNING)
        return copy(state = ManualOcrScanState.PAUSED)
    }

    fun resume(): ManualOcrScanSession {
        check(state == ManualOcrScanState.PAUSED)
        return copy(state = ManualOcrScanState.RUNNING)
    }

    fun record(evidenceCount: Int): ManualOcrScanSession {
        require(evidenceCount >= 0)
        check(state == ManualOcrScanState.RUNNING)
        check(processedSubjects < totalSubjects)
        return copy(
            processedSubjects = Math.addExact(processedSubjects, 1),
            evidenceCount = Math.addExact(this.evidenceCount, evidenceCount),
        )
    }

    fun complete(): ManualOcrScanSession {
        check(state == ManualOcrScanState.RUNNING)
        check(processedSubjects == totalSubjects)
        return copy(state = ManualOcrScanState.COMPLETED)
    }

    fun cancel(): ManualOcrScanSession {
        check(
            state == ManualOcrScanState.AWAITING_CONFIRMATION ||
                state == ManualOcrScanState.RUNNING ||
                state == ManualOcrScanState.PAUSED,
        )
        return copy(state = ManualOcrScanState.CANCELLED)
    }

    fun fail(): ManualOcrScanSession {
        check(state == ManualOcrScanState.RUNNING || state == ManualOcrScanState.PAUSED)
        return copy(state = ManualOcrScanState.FAILED)
    }

    companion object {
        fun create(request: ManualOcrScanRequest) = ManualOcrScanSession(
            request = request,
            state = ManualOcrScanState.AWAITING_CONFIRMATION,
            processedSubjects = 0,
            evidenceCount = 0,
        )
    }
}

data class ManualOcrRuleHit(
    val contentKey: String,
    val ruleId: String,
    val scorePermille: Int,
    val analysisVersion: Int,
) {
    init {
        require(contentKey.isNotBlank()) { "Content key is required." }
        require(RULE_ID.matches(ruleId)) { "Rule id has an invalid format." }
        require(scorePermille in 0..1_000) { "Score must use permille." }
        require(analysisVersion > 0) { "Analysis version must be positive." }
    }

    private companion object {
        val RULE_ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    }
}

data class ManualOcrEvidenceDraft(
    val evidenceId: String,
    val contentKey: String,
    val type: String,
    val scorePermille: Int,
    val maskedDetail: String,
    val analysisVersion: Int,
    val evidenceKey: String,
)

object ManualOcrEvidenceFactory {
    fun create(hit: ManualOcrRuleHit): ManualOcrEvidenceDraft {
        val evidenceKey = sha256(
            "${hit.ruleId}|$OCR_ADVERTISEMENT_TYPE|$OCR_ADVERTISEMENT_MASKED_DETAIL",
        )
        return ManualOcrEvidenceDraft(
            evidenceId = "ocr:${sha256("${hit.contentKey}|$evidenceKey")}",
            contentKey = hit.contentKey,
            type = OCR_ADVERTISEMENT_TYPE,
            scorePermille = hit.scorePermille,
            maskedDetail = OCR_ADVERTISEMENT_MASKED_DETAIL,
            analysisVersion = hit.analysisVersion,
            evidenceKey = evidenceKey,
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
}

sealed interface ManualOcrRecognition {
    data class Hits(val values: List<ManualOcrRuleHit>) : ManualOcrRecognition {
        init {
            require(values.size <= 16) { "OCR rule-hit limit exceeded." }
        }
    }

    data object Failed : ManualOcrRecognition
}

interface ManualOcrRecognizer {
    suspend fun recognize(contentKey: String): ManualOcrRecognition
}

enum class ManualOcrRunFailure { RECOGNIZER_FAILURE, INVALID_RESULT }

sealed interface ManualOcrRunResult {
    data class Completed(
        val session: ManualOcrScanSession,
        val evidence: List<ManualOcrEvidenceDraft>,
    ) : ManualOcrRunResult

    data class Failed(
        val session: ManualOcrScanSession,
        val contentKey: String,
        val failure: ManualOcrRunFailure,
        val evidence: List<ManualOcrEvidenceDraft>,
    ) : ManualOcrRunResult
}

class ManualOcrRunner(private val recognizer: ManualOcrRecognizer) {
    suspend fun run(request: ManualOcrScanRequest): ManualOcrRunResult {
        var session = ManualOcrScanSession.create(request).start()
        val evidence = linkedMapOf<String, ManualOcrEvidenceDraft>()
        request.contentKeys.forEach { contentKey ->
            val recognition = try {
                recognizer.recognize(contentKey)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return failed(
                    session, contentKey, ManualOcrRunFailure.RECOGNIZER_FAILURE, evidence,
                )
            }
            when (recognition) {
                ManualOcrRecognition.Failed -> return failed(
                    session, contentKey, ManualOcrRunFailure.RECOGNIZER_FAILURE, evidence,
                )
                is ManualOcrRecognition.Hits -> {
                    if (recognition.values.any { it.contentKey != contentKey }) {
                        return failed(
                            session, contentKey, ManualOcrRunFailure.INVALID_RESULT, evidence,
                        )
                    }
                    recognition.values.map(ManualOcrEvidenceFactory::create).forEach {
                        evidence.putIfAbsent(it.evidenceId, it)
                    }
                    session = session.record(recognition.values.size)
                }
            }
        }
        return ManualOcrRunResult.Completed(session.complete(), evidence.values.toList())
    }

    private fun failed(
        session: ManualOcrScanSession,
        contentKey: String,
        failure: ManualOcrRunFailure,
        evidence: Map<String, ManualOcrEvidenceDraft>,
    ) = ManualOcrRunResult.Failed(
        session.fail(),
        contentKey,
        failure,
        evidence.values.toList(),
    )
}
