package dev.drivedna.core.domain.model

/** Where a value came from. Every sample, trip and screen carries one (FR-002, NFR-009). */
enum class DataSource {
    /** Synthetic or replayed data. Never shown as real vehicle data. */
    MOCK,

    /** Read from the vehicle through an official API whose capability was verified at runtime. */
    LIVE,
}

enum class TripStatus { IDLE, RECORDING, PROCESSING, COMPLETED, INTERRUPTED, FAILED }

enum class AnalysisAvailability { AVAILABLE, PARTIAL, UNAVAILABLE }

enum class CapabilityStatus { SUPPORTED, UNSUPPORTED, PERMISSION_DENIED, UNKNOWN }

enum class VehicleSignal { SPEED, GEAR, BRAKE_PEDAL, ODOMETER, UX_RESTRICTION }

data class VehicleCapability(val signal: VehicleSignal, val status: CapabilityStatus, val detail: String? = null)
