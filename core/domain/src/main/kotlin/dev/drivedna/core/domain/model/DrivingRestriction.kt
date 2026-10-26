package dev.drivedna.core.domain.model

/** Whether detailed UI may be shown. UNKNOWN is treated like RESTRICTED (safety-first default). */
enum class DrivingRestriction {
    UNKNOWN,
    UNRESTRICTED,
    RESTRICTED;

    val allowsDetailedUi: Boolean get() = this == UNRESTRICTED
}
