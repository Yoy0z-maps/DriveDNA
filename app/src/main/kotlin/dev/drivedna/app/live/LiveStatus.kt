package dev.drivedna.app.live

sealed interface LiveStatus {
    data object Off : LiveStatus

    /** Service running, parked: a trip starts when the gear leaves P. */
    data object WaitingForDrive : LiveStatus

    data class Recording(val tripId: Long) : LiveStatus

    data class Error(val reason: String) : LiveStatus
}
