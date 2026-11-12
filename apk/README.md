# apk

제출용 release APK를 이 폴더에 둡니다.

- 파일명: `팀명_앱이름_release.apk`
- 빌드: `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`를 복사해 이름 변경
- release 빌드에는 API 키가 들어가지 않으므로 데모 모드(미리 만든 이야기)로 동작합니다
- 용량이 크면 GitHub Releases에 올리고 저장소 `README.md`에 링크를 적습니다
