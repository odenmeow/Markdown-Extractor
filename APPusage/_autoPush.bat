@echo off
chcp 65001 >nul

:: 快速 push 腳本
echo 開始執行
cd C:\Users\onilin\MyNote
git add .
git commit -m "quick push"
git push
echo 執行完畢
echo.
pause