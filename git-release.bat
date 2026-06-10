@echo off
cd /d C:\Users\ljh\AndroidStudioProjects\Michedule
git add -A
git commit -m "v2.2.1: replace deep link with room code sharing for better compatibility"
git tag -a v2.2.1 -m "v2.2.1"
git push origin master --tags
