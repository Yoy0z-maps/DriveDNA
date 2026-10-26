# DriveDNA

정차 후 주행 습관을 근거와 함께 보여주는 차량용 앱 (2026 현대자동차그룹 차량용 앱 공모전 출품 준비 중).
현재 상태: **Phase 4 — score-v2 · My DNA · 코칭 · 주간 추세** (기획 문서 `00_`–`07_`, 검증 결과 `docs/`).

## 빌드 / 실행
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew lintDebug testDebugUnitTest :core:domain:test assembleDebug
./gradlew connectedDebugAndroidTest   # 에뮬레이터 필요
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
테스트 환경: Pleos Connect Emulator v2.1.0 (API 34, arm64). 설치 방법은 `docs/environment-report.md`.

## 모듈
| 모듈 | 역할 |
|---|---|
| `app` | Compose UI, S01–S08 내비게이션, 출처 배지, 주행 제한 잠금 |
| `core:domain` | 순수 Kotlin 모델·저장소 인터페이스·단위 변환 |
| `core:data` | Room(Trip, VehicleSample, DrivingEvent, TripSummary), DataStore 설정 |
| `vehicle` | `VehicleDataSource` / `DrivingStateMonitor`, Mock 리플레이, AAOS Car API, Pleos Vehicle SDK (읽기 전용) |
| `tools/probe` | Phase 1 신호 검증용 독립 프로브 앱 (빌드에 포함되지 않음) |

차량 제어 API는 사용하지 않으며, API 키나 비밀값은 저장소에 두지 않습니다.
