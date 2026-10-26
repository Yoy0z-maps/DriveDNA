package dev.drivedna.app.navigation

/** S01–S08 from 04_UI_UX_SPEC. [detailed] screens are locked while driving or when state is unknown. */
enum class Destination(val route: String, val title: String, val detailed: Boolean) {
    Onboarding("s01_onboarding", "S01 시작하기", detailed = false),
    ModeStatus("s02_mode_status", "S02 모드·연동 상태", detailed = false),
    Home("s03_home", "S03 홈", detailed = false),
    TripReplay("s04_trip_replay", "S04 주행 리플레이", detailed = true),
    TripResult("s05_trip_result/{tripId}", "S05 주행 결과", detailed = true),
    MyDna("s06_my_dna", "S06 My DNA", detailed = true),
    History("s07_history", "S07 기록", detailed = true),
    Settings("s08_settings", "S08 설정", detailed = true);

    companion object {
        const val TRIP_ID_ARG = "tripId"

        fun tripResult(tripId: Long) = "s05_trip_result/$tripId"
    }
}
