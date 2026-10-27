# DriveDNA

**정차 후, 근거와 함께 내 운전 습관을 보여 주는 차량용 앱.**
주행 중에는 화면 조작 없이 속도·기어·브레이크 신호를 기록하고, 주차(P)하면 급가속·급감속 이벤트와 점수, 그 근거, 나의 이전 주행과의 비교, 다음 주행을 위한 제안 한두 가지를 보여 줍니다.

2026 현대자동차그룹 차량용 앱 개발 공모전 출품작 · Pleos Connect(Android Automotive OS) 대상.

## 핵심 기능
| 기능 | 설명 |
|---|---|
| Live 주행 기록 | 기어가 P를 벗어나면 포그라운드 서비스가 기록을 시작하고, P로 돌아오면 분석·저장합니다. 주행 중 화면 상호작용은 필요 없습니다. |
| 데이터 품질 검증 | 중복·역전·결측·범위 밖 샘플과 1초 넘는 수신 끊김을 표시하고 계산에서 뺍니다. 데이터가 부족하면 점수를 만들지 않습니다(0점으로 위장하지 않음). |
| 설명 가능한 점수 | 1초 평활 가속도로 급가속(≥ +3.0 m/s²)·급감속(≤ −3.5 m/s²)을 검출하고, 거리·시간으로 정규화한 감점 산식(score-v2)과 각 감점의 근거를 표시합니다. |
| My DNA | 같은 출처·같은 산식의 이전 주행 3회 이상이 쌓이면 중앙값 기준선과 비교하고, 비교 조건(표본 수·거리 범위)을 함께 보여 줍니다. |
| 코칭·주간 추세 | 측정값 근거가 있는 제안을 최대 2개, 기록이 있는 주만 주간 평균으로 보여 줍니다. |
| 데모 리플레이 | 같은 경로를 다르게 운전한 합성 주행 3종(Normal / Harsh / Improved)과 데모 이력으로 차량 없이 전체 흐름을 시연합니다. |
| 출처 배지 | 모든 화면에 `DEMO · SIMULATED` 또는 `LIVE · VEHICLE`을 표시해 합성 데이터와 차량 데이터를 구분합니다. |
| 주행 제한 잠금 | 주행 중이거나 주행 상태를 알 수 없으면 상세 화면을 잠급니다(안전 우선 기본값). |
| 데이터 삭제 | 설정에서 모든 기록과 설정을 지우며, DB 파일에 삭제된 데이터가 남지 않도록 VACUUM합니다. |

## 사용하는 차량 데이터 (읽기 전용)
| 신호 | API | 용도 |
|---|---|---|
| 차량 속도 | AAOS `CarPropertyManager` `PERF_VEHICLE_SPEED` (권한 `CAR_SPEED`) | 가감속 계산 (에뮬레이터 실측 약 10 Hz) |
| 기어 | AAOS `GEAR_SELECTION` (`CAR_POWERTRAIN`) | 주행 시작·종료 판단 |
| 주행 제한 상태 | AAOS `CarUxRestrictionsManager` | 상세 화면 잠금 |
| 브레이크 페달 | Pleos Vehicle SDK 2.3.2 `getBrake().registerBrakePedalStatus` (`pleos.car.permission.CAR_DRIVE`) | 기록 (점수에는 미사용) |
| 주행거리계 | Pleos Vehicle SDK `getOdometer().getCurrentDistanceDriven` | 속도 적분 거리와 교차검증 (경고만) |

차량 제어 API, 위치(GPS), 네트워크(INTERNET 권한)는 사용하지 않습니다. 모든 데이터는 차량 안의 앱에만 저장됩니다.

## 실행 방법
### 1. APK 설치
릴리스 APK: [`apk/Yoy0z-maps_DriveDNA_release.apk`](apk/Yoy0z-maps_DriveDNA_release.apk) (서명된 release 빌드, v0.5.0)
```bash
adb install -r apk/Yoy0z-maps_DriveDNA_release.apk
```
### 2. 테스트 환경
| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (Android 14 / API 34, arm64-v8a, `android-automotive`) |
| 개발 PC | macOS (Apple Silicon, arm64) |
| Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` |
| 빌드 | JDK 17, Gradle 8.10.2(wrapper), AGP 8.8.2, Kotlin 2.0.21, compileSdk 35, minSdk 28 |

Pleos Connect 에뮬레이터 설치는 Pleos Playground 개발자 문서의 안내를 따릅니다. macOS arm64에서는 SDK Manager가 Pleos 이미지를 나열하지 않아, 시스템 이미지(`system-images;android-34;pleos_car_emulator;arm64-v8a`)를 직접 받아 SDK 폴더에 풀고 AVD를 만들어 사용했습니다.

### 3. 앱 사용 흐름 (데모)
1. 첫 화면에서 **동의 없이 데모만 보기** 또는 **동의하고 Live 기록 사용**을 고릅니다.
2. **S04 주행 리플레이** → **① 데모 이력 만들기**(지난 2주 합성 주행 4회) → **Improved** 실행 → 결과(S05)에서 점수와 근거 확인.
3. **S06 My DNA**에서 기준선 비교와 제안을 확인합니다.
4. Normal / Harsh를 실행하면 같은 경로에서 운전 방식만 바꿨을 때 점수와 이벤트가 어떻게 달라지는지 볼 수 있습니다.

### 4. Live 기록 (Virtual Console)
1. **동의하고 Live 기록 사용** → 시스템 권한 창에서 허용합니다.
   - 차량 속도 권한(`CAR_SPEED`)은 시스템 창에 **'위치 정보에 액세스'** 로 표시될 수 있습니다. DriveDNA는 속도 값만 읽고 위치는 읽지 않습니다. 거부해도 데모 리플레이는 사용할 수 있습니다.
2. 에뮬레이터 Virtual Console을 엽니다: `adb forward tcp:8088 tcp:8088` 후 브라우저에서 `http://127.0.0.1:8088`.
3. Virtual Console에서 기어 **D** → **ACCEL** 길게 누르기(가속) → **BRAKE** 길게 누르기(감속) → 기어 **P**.
   - 주행 중(D)에는 시스템이 일반 앱 화면을 가리지만 기록은 백그라운드에서 계속됩니다.
4. P로 돌아오면 분석이 끝나고 홈·기록(S07)에 `LIVE · VEHICLE` 주행이 추가됩니다.

### 5. 소스에서 빌드
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew testDebugUnitTest assembleDebug          # 디버그 APK + 단위 테스트
./gradlew connectedDebugAndroidTest                # 에뮬레이터 필요 (실행 후 앱이 제거됨)
./gradlew assembleRelease                          # 서명 설정이 없으면 서명되지 않은 APK
```
릴리스 서명 값은 저장소 밖의 properties 파일(기본 `~/.android-keys/drivedna/signing.properties`, `-Pdrivedna.signing=<경로>`로 변경)에서 읽습니다. Pleos Maven 저장소는 인증 없이 접근됩니다.

## 모듈
| 모듈 | 역할 |
|---|---|
| `app` | Compose UI, S01–S08 화면·내비게이션, 출처 배지, 주행 제한 잠금, Live 기록 서비스 |
| `core:domain` | 순수 Kotlin: 모델·단위 변환, 합성 리플레이, 샘플 검증, 이벤트 검출, 점수, 기준선·코칭·추세 |
| `core:data` | Room(Trip·Sample·Event·Summary), DataStore(동의·설정) |
| `vehicle` | `VehicleDataSource` 구현: Mock 리플레이, AAOS Car API, Pleos Vehicle SDK |
| `tools/probe` | 신호 검증용 독립 프로브 앱 (빌드에 포함되지 않음) |

## 알려진 한계
- 에뮬레이터(Virtual Console)로만 검증했으며 **실차에서는 검증하지 않았습니다.**
- 급가속·급감속 임계값과 점수 가중치는 데모용 튜닝값이며, 공식 안전 기준이나 보험·안전 등급 평가가 아닙니다.
- 데모 리플레이와 데모 이력은 실제 운전 기록이 아닌 합성 데이터입니다.
- My DNA는 '운전 습관 디지털 프로필'로, 행동 예측·시뮬레이션 모델을 갖춘 디지털 트윈이 아닙니다.
- GPS를 쓰지 않으므로 도로 종류·시간대 같은 맥락은 비교에 반영하지 않습니다(거리 유사성만 사용).
- 에뮬레이터의 주행거리계는 주행해도 늘지 않아, 주행거리 교차검증은 에뮬레이터에서 항상 불일치 경고를 냅니다(점수에는 반영하지 않음).
- Virtual Console의 가속(약 +4.9 m/s²)·제동(약 −12 m/s²) 강도가 고정이라, Live 기록에서는 실제 운전보다 이벤트가 많이 나옵니다.
