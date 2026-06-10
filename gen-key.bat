@echo off
keytool -genkeypair -v -keystore michedule-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias michedule -storepass michedule123 -keypass michedule123 -dname "CN=LJH, OU=Dev, O=Michedule, L=Seoul, ST=Seoul, C=KR"
