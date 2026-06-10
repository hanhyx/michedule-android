@echo off
cd /d C:\Users\ljh\AndroidStudioProjects\Michedule
git add -A
git commit -m "v2.3.1: compact UI, maximize calendar area"
git push origin master
git tag -a v2.3.1 -m "v2.3.1"
git push origin v2.3.1
gh release create v2.3.1 "app\build\outputs\apk\debug\app-debug.apk#Michedule-v2.3.1.apk" --title "v2.3.1" --notes "Compact banner/tabs/stats, maximize calendar cell height, partner name sync fix"
