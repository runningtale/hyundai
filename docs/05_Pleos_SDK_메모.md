# Pleos SDK 메모

> 2026-11-12 확인. SDK AAR 파일을 직접 열어 본 것 + https://document.pleos.ai API 레퍼런스

## 1. 설정

```kotlin
// settings.gradle.kts
maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }

// app/build.gradle.kts
implementation("ai.pleos.playground:Vehicle:2.3.2")
implementation("ai.pleos.playground:NaviHelper:3.0.5")
implementation("ai.pleos.playground:TextToSpeech:3.3.1")
```

- minSdk 28, compileSdk 34, 에뮬레이터 AVD는 Pleos Connect (API 34)
- 공모전 개발 단계에서는 매니페스트 퍼미션 선언만으로 사용 가능

```xml
<uses-permission android:name="pleos.car.permission.CAR_LIGHTS" />
<uses-permission android:name="pleos.car.permission.CONTROL_CAR_LIGHTS" />
<uses-permission android:name="pleos.car.permission.CAR_CLIMATE" />
<uses-permission android:name="pleos.car.permission.CONTROL_CAR_CLIMATE" />
<uses-permission android:name="pleos.car.permission.CAR_SEATS" />
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE" />
<uses-permission android:name="pleos.car.permission.TTS_SERVICE" />
```

## 2. Vehicle

```kotlin
val vehicle = Vehicle(context)
vehicle.initialize()          // 연결 콜백 없음
val light = vehicle.getLight() // getHVAC(), getSeat(), getGear(), getWiper(), getDoor() ...
vehicle.release()
```

- 모든 좌석·구역은 `SeatArea(SeatValue.ROW_2_LEFT)` 형식. `SeatValue`: `ROW_1~3` × `LEFT/CENTER/RIGHT`
- 실제 지원 구역은 `check…Capabilities` 결과로만 알 수 있음
- 모든 호출은 비동기 콜백: `onComplete`, `onFailed: (Exception) -> Unit`

| 기능 | API |
|---|---|
| 독서등 | `setReadingLightSwitch(area, LightSwitch.ON/OFF, onComplete, onFailed)`, `getReadingLightSwitch`, `checkReadingLightSwitchCapabilities`. 밝기 조절 없음 |
| 전조등 상태 | `registerLowBeamLightState(LightStateListener)` → `onLightStateUpdated(LightState)` (`OFF`, `ON`, `DAYTIME_RUNNING`) |
| 풍량 | `setHvacFanSpeed(area, Int, onComplete: (SeatArea, Int?) -> Unit, onFailed)`, `getHvacFanSpeed`, 범위 `checkHvacFanSpeedCapabilities` → `Map<SeatArea, AreaBounds<Int>>` |
| 공조 상태 | `getHvacPowerState(area, ...)`, `getHvacAutoState(area, ...)`, `setHvacAuto(area, Boolean, ...)` |
| 착석 | `registerSeatOccupancy(area, SeatOccupancyListener)` → `onSeatOccupancyUpdated(Boolean?)` |
| 안전띠 | `getCurrentSeatBeltBuckledState(area, ...)` |
| 기어 | `getGear().registerGearSelection(GearSelectionListener)` → `onSelected(VehicleGear)` (`GEAR_PARK`, `GEAR_DRIVE`, `GEAR_REVERSE`, `GEAR_NEUTRAL` ...) |
| 와이퍼 | `registerWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), WiperStateListener)` → `onWiperStateUpdated(WiperState)` (`OFF`, `ON`, `SERVICE`, `OTHER`) |
| 차속 | Vehicle SDK에 없음 (2.3.2에서 삭제). AAOS `CarPropertyManager` `PERF_VEHICLE_SPEED` |

## 3. NaviHelper

```kotlin
val navi = NaviHelper(applicationContext)
navi.addListener(listener)                      // NaviHelperEventListener (모든 메서드 기본 구현 있음)
navi.initialize(onConnected = { navi.getDestinationInfo() }, onFailed = { })
navi.release()
```

| 기능 | API |
|---|---|
| 목적지 | `getDestinationInfo()` → `onDestinationInfo(DestinationInfo)`: `name`, `address`, `remainDistance`(m), `remainTime`(초), 좌표. **장소 종류 필드 없음** |
| 턴바이턴 | `getTBTInfo()` → `onTBTInfo(List<TBTInfo>)`: `type: TurnType`, `distance`(m), `description`. `TURN_TUNNEL`, `TURN_TOLL`, `TURN_ENTER_SERVICEAREA`, `TURN_RESTAREA` 있음 |
| 도착 | `onDestinationArrived(DestinationArrivedInfo)` |
| 즐겨찾기, 최근 목적지 | `getBookmarkInfo()`(분류: `HOME`, `OFFICE` 등), `getRecentDestinationInfo()` |
| 모의 주행 | 시작 API 없음. `getRouteStateInfo()` → `naviMode == SIMULATION_GUIDING`으로 상태만 확인 |

## 4. TextToSpeech

```kotlin
val tts = TextToSpeech(applicationContext, Mode.ON_DEVICE)  // ON_DEVICE는 앱 등록 불필요
tts.addEventListener(eventListener)                          // onStart, onReady, onDone, onError, onStop, onUpdatedRms
tts.initialize(object : OnInitializeListener { ... })
tts.setLocale(Locale.KOREA)
tts.setSpeakerType("haeun")                                  // seoyoon, eunwoo, haeun(기본), sanghyeok
tts.speak("문장", false)
tts.stop(); tts.release()
```

- `SERVER`, `HYBRID` 모드는 `registerApp(clientId, clientSecret, listener)` 필요 (Pleos Playground My Project)
- `setAudioZoneId(Int)`: 에뮬레이터는 오디오 존 0번 하나뿐

## 5. 주행 규제와 오디오 앱 요건

- 주행 중 사용(DO) 선언이 없는 앱은 주행 중 사용 불가
- 오디오 앱: `android:appCategory="audio"`, MediaBrowserService/MediaSession 구현, 핸들 미디어 키 지원, 오디오 포커스 처리 필수, **자동 재생 금지**. Gleo 호출 등으로 `AUDIOFOCUS_LOSS_TRANSIENT`를 받으면 즉시 멈춤
- 주행 중 동영상, 게임, 광고 금지
- 한국은 "차량 설정"(`UX_RESTRICTIONS_NO_SETUP`)이 주행 중 금지. 앱의 공조·조명 제어가 해당되는지는 확인 필요
- 외부 통신: HTTPS, TLS 1.2 이상, API 키 하드코딩 금지

## 6. 에뮬레이터

```bash
# 실행 (Windows)
"%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd Pleos_Connect

# Virtual Console (브라우저에서 차량 값 바꾸기)
adb forward tcp:8088 tcp:8088     # 이후 http://127.0.0.1:8088

# 주행 규제 켜기/끄기
adb shell cmd hmg_car_service --service drivingstate driving_regulation on
adb shell cmd hmg_car_service --service drivingstate driving_regulation off
```

| Virtual Console 카드 | 좌석 |
|---|---|
| 착석 (Seat Occupancy) | 운전석, 조수석, 뒤 왼쪽, 뒤 오른쪽 |
| 독서등 (Reading Lights) | 앞·뒤 좌우 |
| 창문, 도어 | 네 자리 |
| 시트 열선·통풍, 목표 온도, 풍향 | 운전석, 조수석만 |
| 풍량 (Fan Speed) | 구역 없이 0~8단 |
| 기어, 와이퍼, 전조등, 방향지시등, 경고등 | |

- 에뮬레이터 화면은 가운데 화면 하나(2560×1440)와 계기판. 뒷좌석 화면, 뒷좌석 오디오 존 없음
