@echo off
REM 設定命令提示字元的編碼為 UTF-8
chcp 65001
REM 切換 + 執行 Java 程式
cd ../artifacts/MarkdownExtractor
java -Dfile.encoding=UTF-8 -jar MarkdownExtractor.jar