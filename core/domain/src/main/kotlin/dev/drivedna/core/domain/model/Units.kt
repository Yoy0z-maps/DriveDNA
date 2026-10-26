package dev.drivedna.core.domain.model

/** AAOS PERF_VEHICLE_SPEED is reported in m/s (measured on the Pleos emulator, Phase 1 probe). */
fun metersPerSecondToKph(mps: Float): Double = mps * 3.6

/** AAOS GEAR_SELECTION values (VehicleGear constants). */
fun gearFromAaos(value: Int): Gear = when (value) {
    0x0001 -> Gear.NEUTRAL
    0x0002 -> Gear.REVERSE
    0x0004 -> Gear.PARK
    0x0008 -> Gear.DRIVE
    in setOf(0x0010, 0x0020, 0x0040, 0x0080, 0x0100, 0x0200, 0x0400, 0x0800, 0x1000) -> Gear.MANUAL
    else -> Gear.UNKNOWN
}
