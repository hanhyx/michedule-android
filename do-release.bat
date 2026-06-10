@echo off
cd /d C:\Users\ljh\AndroidStudioProjects\Michedule
git add -A
git commit -m "v2.2.1: sync pause, compact widgets, room code UX fix"
git push origin master
gh release delete v2.2.1 --yes 2>nul
git tag -d v2.2.1 2>nul
git push origin :refs/tags/v2.2.1 2>nul
git tag -a v2.2.1 -m "v2.2.1"
git push origin v2.2.1
gh release create v2.2.1 "app\build\outputs\apk\debug\app-debug.apk#Michedule-v2.2.1.apk" --title "v2.2.1" --notes "Sync pause, compact widgets, room code join fix"
