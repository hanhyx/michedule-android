@echo off
cd /d C:\Users\ljh\AndroidStudioProjects\Michedule
git add -A
git commit -m "v2.2.2: version bump for update"
git push origin master
git tag -a v2.2.2 -m "v2.2.2"
git push origin v2.2.2
gh release create v2.2.2 "app\build\outputs\apk\debug\app-debug.apk#Michedule-v2.2.2.apk" --title "v2.2.2" --notes "Sync pause, compact widgets, room code join fix"
