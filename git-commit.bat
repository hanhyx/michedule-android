@echo off
cd /d C:\Users\ljh\AndroidStudioProjects\Michedule
git add -A
git commit -m "v2.1.0: widget couple view, alba toggle, shift time edit, partner push notify"
git tag v2.1.0
git push origin main --tags
