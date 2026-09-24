# TCP load measurement

Executed: 2026-09-20T14:22:35.065287300Z

Environment: Windows 11 amd64; Java 25.0.3; logical CPUs 16; max heap MiB 4018

Loopback TCP sockets, server and load clients in one JVM; SQLite WAL; fake accounts. Login excluded from measurement.
Each client sends 30 public-room messages, 128 ASCII payload characters, one outstanding request per client. All clients start together. ACK means persisted, not read by recipient.

|Clients|Messages ACKed|Errors|Elapsed ms|Messages/s|Mean ACK ms|P95 ACK ms|
|---:|---:|---:|---:|---:|---:|---:|
|2|60|0|522.42|114.85|17.17|21.17|
|16|480|0|5421.30|88.54|178.16|229.82|
