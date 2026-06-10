@echo off
cd /d C:\Users\ljh\AndroidStudioProjects\Michedule
git add -A
git commit -m "v2.0.1: full-height calendar, today hero banner, compact stats"
git tag v2.0.1
git push origin master --tags
copy app\build\outputs\apk\debug\app-debug.apk michedule-v2.0.1.apk
gh release create v2.0.1 michedule-v2.0.1.apk --title "v2.0.1" --notes "full-height calendar, today hero banner, compact stats bar"
del michedule-v2.0.1.apk
del release.bat
