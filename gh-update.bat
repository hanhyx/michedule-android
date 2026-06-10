@echo off
cd /d C:\Users\ljh\AndroidStudioProjects\Michedule
git add -A
git commit -m "v2.1.0: fix shift label+time spacing in calendar cell"
git tag -f v2.1.0
git push origin master --tags -f
gh release delete v2.1.0 --yes
gh release create v2.1.0 "C:\Users\ljh\Desktop\michedule-v2.1.0.apk" --title "v2.1.0" --notes "Widget couple view, alba toggle, shift time edit, partner push notify, compact cell layout"
