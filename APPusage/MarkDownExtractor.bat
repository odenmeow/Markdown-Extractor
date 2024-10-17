@echo off
REM Set code page to UTF-8
chcp 65001

REM Navigate to the directory containing the JAR
cd C:\CodeSForGit\Markdown-Extractor\out\artifacts\MarkdownExtractor

REM Run the JAR with UTF-8 file encoding
java -Dfile.encoding=UTF-8 -jar MarkdownExtractor.jar

pause
