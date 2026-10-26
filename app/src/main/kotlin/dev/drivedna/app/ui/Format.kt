package dev.drivedna.app.ui

import dev.drivedna.core.domain.model.AnalysisAvailability
import dev.drivedna.core.domain.model.QualityFlag
import dev.drivedna.core.domain.model.TripStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateTime = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)

fun formatDateTime(epochMs: Long): String = dateTime.format(Date(epochMs))

fun formatDuration(seconds: Long?): String = when {
    seconds == null -> "알 수 없음"
    seconds < 60 -> "${seconds}초"
    else -> "${seconds / 60}분 ${seconds % 60}초"
}

fun formatDistance(km: Double?): String = km?.let { String.format(Locale.ROOT, "%.2f km", it) } ?: "측정 불가"

/** Score text that never shows 0 for "unknown" (FR-007). */
fun formatScore(score: Int?): String = score?.let { "${it}점" } ?: "점수 없음"

fun TripStatus.label(): String = when (this) {
    TripStatus.IDLE -> "대기"
    TripStatus.RECORDING -> "기록 중"
    TripStatus.PROCESSING -> "분석 중"
    TripStatus.COMPLETED -> "완료"
    TripStatus.INTERRUPTED -> "중단됨(복구)"
    TripStatus.FAILED -> "분석 실패"
}

fun AnalysisAvailability.label(): String = when (this) {
    AnalysisAvailability.AVAILABLE -> "분석 완료"
    AnalysisAvailability.PARTIAL -> "일부 데이터로 분석"
    AnalysisAvailability.UNAVAILABLE -> "데이터 부족으로 분석 불가"
}

fun QualityFlag.label(): String = when (this) {
    QualityFlag.DUPLICATE_TIMESTAMP -> "중복 시각"
    QualityFlag.OUT_OF_ORDER -> "순서 역전"
    QualityFlag.GAP_TOO_LONG -> "수신 끊김"
    QualityFlag.OUT_OF_RANGE -> "범위 밖 값"
    QualityFlag.MISSING_VALUE -> "값 없음"
    QualityFlag.ODOMETER_MISMATCH -> "주행거리계 불일치"
}
