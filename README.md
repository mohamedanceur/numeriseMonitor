# File Watcher Batch System (Spring Boot)

## Description
This project monitors a folder and processes files in real-time:
- .CRO → non processed operations
- .DONE → processed operations
- .ERREUR → failed operations

## Features
- File system monitoring (WatchService)
- SQL Server integration
- Duplicate prevention
- Crash recovery
- Batch processing logic

## Tech Stack
- Java 17
- Spring Boot
- JDBC
- SQL Server

## Architecture
Folder → Watcher → Parser → Database

## Author
Mohamed Anceur