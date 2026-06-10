@echo off
cd /d C:\Users\ljh\AndroidStudioProjects\Michedule
git add -A
git commit -m "v2.4.0: partner detail badges, mood/todo sync, widget redesign, signed APK"
git tag -d v2.4.0 2>nul
git push origin :refs/tags/v2.4.0 2>nul
git tag v2.4.0
git push origin master --tags
gh release delete v2.4.0 -y 2>nul
gh release create v2.4.0 app/build/outputs/apk/release/app-release.apk --title "v2.4.0" --notes "Partner detail badges, mood/todo sync, widget redesign"
