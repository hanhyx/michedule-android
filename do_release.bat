@echo off
cd /d C:\Users\ljh\AndroidStudioProjects\Michedule
git add -A
git commit -m "v2.0.0: full-height calendar, today hero banner, compact stats"
git tag v2.0.0
git push origin master --tags
gh release create v2.0.0 michedule-v2.0.1.apk --title "v2.0.0" --notes "full-height monthly calendar, today hero banner, couple cells, mood tracking"
del michedule-v2.0.1.apk
del do_release.bat
