@echo off
cd /d C:\Users\ljh\AndroidStudioProjects\Michedule
git add -A
git commit -m "v2.3.0: widget redesign, sync pause, partner visibility, shift cycle order"
git push origin master
git tag -a v2.3.0 -m "v2.3.0"
git push origin v2.3.0
gh release create v2.3.0 "app\build\outputs\apk\debug\app-debug.apk#Michedule-v2.3.0.apk" --title "v2.3.0" --notes "Widget 2x2/4x2 redesign, sync pause toggle, partner name sync, calendar partner visibility, shift cycle order fix, room code join UX"
