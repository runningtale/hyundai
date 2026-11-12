# 달리는 동화 (가칭)

**지금 달리는 길이 그대로 이야기가 되는 차량용 4D 오디오 동화.**
목적지와 남은 시간, 비, 터널 같은 실제 도로 상황을 실시간으로 이야기에 넣고, 장면에 맞춰 아이 자리의 독서등과 바람이 반응합니다. Pleos Connect 차량용 앱입니다.

> 현재 상태: 구조와 핵심 흐름을 잡은 초기 버전입니다. 각 파일의 `TODO(담당 N)`이 남은 작업입니다.

## 주요 기능

- **실시간 이야기 생성 (세 층)**
  - 출발할 때 뼈대와 1장
  - 달리는 동안 다음 장을 한 장씩
  - 터널·요금소·비 같은 도로 이벤트에 맞춘 끼어들기
- **4D 효과**: 아이 자리 독서등, 풍량, 등장인물별 목소리
- **화면 없음**: 차 기본 미디어 화면과 핸들 버튼으로 재생·일시정지
- **아이 정보 보호**: 아이 이름은 기기 밖으로 보내지 않음

자세한 내용은 [docs/01_기획서.md](docs/01_기획서.md)를 보세요.

## 테스트 환경

| 항목 | 내용 |
|---|---|
| 에뮬레이터 | Pleos Connect AVD (Android API 34) |
| Android SDK | compileSdk 34, minSdk 28, build-tools 34 |
| JDK | 17 이상 |
| Pleos SDK | Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1 |

## 실행 방법

처음이면 에뮬레이터 설치부터: [docs/00_개발환경_설정.md](docs/00_개발환경_설정.md)

1. 저장소를 받습니다. 경로에 한글이 있으면 빌드가 실패할 수 있어 영문 경로를 권장합니다.
   ```bash
   git clone https://github.com/runningtale/hyundai.git C:/dev/hyundai
   ```
2. (선택) 실제 이야기 생성을 쓰려면 `local.properties`에 LLM API 키를 넣습니다. 없으면 데모 모드로 동작합니다.
   ```properties
   LLM_API_KEY=여기에_키
   ```
3. 에뮬레이터를 켜고 설치합니다.
   ```bash
   ./gradlew :app:installDebug
   ```
4. 차 미디어 화면에 앱을 띄웁니다.
   ```bash
   adb shell am start -a android.car.intent.action.MEDIA_TEMPLATE \
     --es android.car.intent.extra.MEDIA_COMPONENT com.runningtale/.playback.StoryMediaService
   ```
5. "이야기" 탭 → **출발하며 듣기**를 누릅니다. 앱은 자동 재생하지 않습니다.

### 시연: 도로 이벤트 넣기 (debug 빌드)

모의 주행은 앱에서 시작할 수 없어서 adb로 이벤트를 넣습니다. 목적지를 **재생 전에** 넣어야 뼈대에 목적지가 반영됩니다.

```bash
R="-n com.runningtale/.debug.DebugEventReceiver -a com.runningtale.DEBUG_EVENT"

# 1) 미디어 화면에서 "달리는 동화"를 한 번 열어 앱을 실행한 뒤 (아직 재생은 누르지 않음)
#    공백이 있는 값은 작은따옴표를 큰따옴표로 한 번 더 감쌈 (adb shell이 공백에서 나누기 때문)
adb shell am broadcast $R --es type destination --es name "'강릉 경포해변'" --ei remain 9000

# 2) 미디어 화면에서 "출발하며 듣기" 재생 (adb 미디어 키는 에뮬레이터에서 앱까지 안 가서 화면을 누름)
adb shell input tap 1090 370

# 3) 주행 중 이벤트
adb shell am broadcast $R --es type gear --es value D
adb shell am broadcast $R --es type tunnel --ei distance 800   # 1km 안: 미리 생성
adb shell am broadcast $R --es type tunnel --ei distance 100   # 300m 안: 진입 → 끼어들기
adb shell am broadcast $R --es type rain --ez on true
adb shell am broadcast $R --es type arrived
```

독서등·풍량 변화는 Virtual Console(`adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088)에서 확인합니다.

## APK

- 위치: [apk/](apk/) (`팀명_앱이름_release.apk`)
- release 빌드는 API 키 없이 데모 모드로 동작합니다

## 구조

```
app/src/main/java/com/runningtale/
├── model/      공통 데이터, 주행 이벤트
├── config/     설정 상수, 시연용 아이 정보
├── story/      이야기 생성 (프롬프트, LLM, 파싱, 안전 검사, 대체 문장)
├── vehicle/    차량 이벤트, 4D 효과와 안전 규칙
├── navi/       목적지, 남은 시간, 터널 예고
└── playback/   StoryDirector(판단 중심), 낭독, 미디어 서비스
app/src/debug/  시연용 이벤트 주입
```

## 문서

| 문서 | 내용 |
|---|---|
| [00_개발환경_설정](docs/00_개발환경_설정.md) | 에뮬레이터 설치, 빌드, 앱 실행, Virtual Console |
| [01_기획서](docs/01_기획서.md) | 문제, 타깃, 핵심 가치, 사업성, 범위 |
| [02_구조설계](docs/02_구조설계.md) | 모듈 구성, 실시간 생성 흐름, 실패 대체 |
| [03_모듈간_약속](docs/03_모듈간_약속.md) | 데이터 형식, 이벤트, 인터페이스, LLM JSON |
| [04_이야기재료_효과](docs/04_이야기재료_효과.md) | 쓸 수 있는 차량·내비 데이터와 효과 |
| [05_Pleos_SDK_메모](docs/05_Pleos_SDK_메모.md) | 확인한 API, 퍼미션, 주행 규제, 에뮬레이터 검증 결과 |
| [06_역할분담_일정](docs/06_역할분담_일정.md) | 담당과 일정, 제출 전 점검 |
| [07_고도화_계획](docs/07_고도화_계획.md) | 이야기 만드는 법, 소리, 4D 연출, 화면·디자인, 시스템 고도화와 우선순위 |
| [proposals](docs/proposals/README.md) | 고도화 제안 올리는 법과 결정 규칙, 제안 목록 |

팀 작업 규칙(커밋 작성자, 브랜치, API 키)은 [CONTRIBUTING.md](CONTRIBUTING.md)에 있습니다.

## 라이선스

[MIT](LICENSE). 사용한 Pleos SDK는 Pleos 이용 약관을 따릅니다.
