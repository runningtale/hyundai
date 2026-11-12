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
<uses-permission android:name="pleos.car.permission.CAR_DRIVE" />    <!-- 기어 -->
<uses-permission android:name="pleos.car.permission.CAR_DOORS" />    <!-- 도어 -->
<uses-permission android:name="pleos.car.permission.CAR_WINDOWS" />  <!-- 와이퍼 -->
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

## 6. 에뮬레이터에서 직접 확인한 결과 (2026-11-12)

| 항목 | 결과 | 비고 |
|---|---|---|
| 미디어 화면에 앱 표시, 재생 | 됨 | Pleos 미디어 앱은 루트 자식을 탭으로 보여 줌 → 루트 → 탭(BROWSABLE) → 재생 항목(PLAYABLE) 2단 구조 필요 |
| TTS 한국어 낭독, 화자 바꾸기 | 됨 | 문장당 2~3초. `addEventListener`는 `initialize` 성공 뒤에 호출해야 함 (먼저 호출하면 앱이 죽음). `speak(text, true)`여야 `onDone`이 옴 |
| 기어, 뒷좌석 착석, 뒷문, 와이퍼, 전조등 이벤트 | 됨 | 퍼미션 `CAR_DRIVE`(기어), `CAR_DOORS`, `CAR_WINDOWS`(와이퍼) 추가 필요. 없으면 `SecurityException`으로 앱이 죽음 |
| 리스너 등록 시간 | 느림 | 등록에 10초 넘게 걸림 → 메인 스레드가 아닌 곳에서 등록 |
| 공조 리스너 초기값 | 안 옴 | 값이 바뀔 때만 옴 → 시작할 때 `get…`으로 한 번 읽어야 함 |
| 공조 구역 | 기능마다 다름 | 풍량은 앞좌석 묶음(0x5), 전원·AUTO는 다른 구역. 같은 구역으로 읽으면 `IllegalArgumentException` → 기능마다 `check…Capabilities`로 구역 조회 |
| 풍량 효과 | 됨 (정차·주행 모두) | 3 → 4 → 원래 3으로 복귀 확인 (Virtual Console) |
| 독서등 효과 | **정차 중만 됨** | 주행 상태(D + 가속)에서는 `IllegalArgumentException: 50001`로 거부됨 → 주행 중에는 쓰지 않음 |
| 터널 미리 생성 → 진입 시 끼어들기 | 됨 | 이벤트 주입으로 확인 |
| 비 시작·그침 끼어들기 | 됨 | |
| 도착 시 효과 복귀, 다시 재생하면 새 이야기 | 됨 | |
| 주행 규제 상태에서 낭독 | 됨 | `DO: true UxR: 80` 상태에서 확인 |
| `cmd hmg_car_service ... driving_regulation` | 이 이미지에 없음 | Virtual Console의 D + ACCEL로 주행 상태를 만듦 |
| adb 미디어 키(`KEYCODE_MEDIA_PLAY`) | 앱까지 안 감 | 앱은 사용자 10, adb 키는 다른 사용자 → 화면 탭으로 재생 |
| 재생 카드 제목·부제 | 됨 | `MediaMetadataCompat`의 TITLE, ARTIST가 미디어 화면 왼쪽 아래 카드에 보임 |
| 재생 카드·목록 그림 | **주소(URI)로만 됨** | 비트맵(`ALBUM_ART`, `setIconBitmap`)은 무시하고 음표 아이콘을 보여 줌. `android.resource://패키지/drawable/이름` 주소로 넘기면 보임. 그림은 PNG여야 함 (벡터 XML은 주소로 못 읽음) |
| 경로 안내 없을 때 목적지 | 빈 이름, 0초 | `getDestinationInfo`가 5초마다 빈 값을 줌 → 그대로 넘기면 알던 목적지가 지워져서 `NaviGateway`에서 건너뜀 |
| 앱 다시 설치 후 미디어 화면 | 계속 로딩 | 미디어 앱이 끊긴 연결을 붙잡고 있음 → `adb shell am force-stop ai.umos.mediaapp` 후 다시 열기 |
| 공조 패널 화면 터치 | 반응 없음 | 공조 켜기는 Virtual Console에서 |
| 효과 쿨다운 | 됨 | 도착 후 다시 재생했을 때 3분 안의 같은 독서등은 건너뜀 (로그 `EffectController`) |

## 7. 에뮬레이터

```bash
# 실행 (Windows)
"%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd Pleos_Connect

# Virtual Console (브라우저에서 차량 값 바꾸기)
adb forward tcp:8088 tcp:8088     # 이후 http://127.0.0.1:8088

# 주행 규제 켜기/끄기 (공식 문서 명령. 지금 에뮬레이터 이미지에는 없음 → Virtual Console의 D + ACCEL 사용)
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
