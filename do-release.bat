@echo off
cd /d C:\Users\ljh\AndroidStudioProjects\Michedule
git add -A
git commit -m "v2.5.0: unified solo cell, partner view toggle, remove 7:3 split"
git tag v2.5.0
git push origin master --tags
gh release create v2.5.0 app/build/outputs/apk/release/app-release.apk --title "v2.5.0" --notes "Unified solo cell, partner view toggle, remove 7:3 split"
