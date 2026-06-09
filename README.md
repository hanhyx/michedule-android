# Michedule

교대근무 스케줄 관리 Android 앱

## 기능

- 월간/주간 캘린더 뷰
- 교대근무 타입 관리 (주간, 야간, 야간조, 비번)
- 커스텀 일정 추가
- 메모 기능
- D-day 카운터
- Supabase 실시간 동기화
- 홈 화면 위젯
- 자동 업데이트

## 기술 스택

- Kotlin + Jetpack Compose
- Room Database
- Supabase Realtime
- Glance Widgets
- Material 3

## 빌드

```bash
./gradlew assembleDebug
```

## 릴리즈

태그를 푸시하면 GitHub Actions가 자동으로 APK를 빌드하고 Release를 생성합니다.

```bash
git tag v1.0.0
git push origin v1.0.0
```
