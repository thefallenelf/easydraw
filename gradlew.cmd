@echo off
rem 直接调用 E 盘本地的 Gradle 8.9(本机 services.gradle.org 下载被墙,不用 wrapper 下载)
call E:\gradle-dist\gradle-8.9\bin\gradle.bat %*