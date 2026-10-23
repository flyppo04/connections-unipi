# Connections

A client/server version of the Connections word game, written in Java.

Players connect to a server, receive 16 words and have to find the four groups of four
that share a theme. Games are timed: when a game ends, every player gets the solution
and the results through a UDP notification, and a new game starts. The server keeps a
leaderboard and per-player statistics, saved as JSON files.

University project for the Laboratorio III course (University of Pisa, 2025/2026).
The server uses Java NIO (a single Selector thread plus a worker pool), a JSON protocol
over TCP and UDP for asynchronous notifications.

## Build and run

```
mvn package                       # builds target/server.jar and target/client.jar
java -jar target/server.jar       # reads server.config
java -jar target/client.jar       # reads client.config; optional argument: UDP port
```

Run both from the project folder, where the config files and `puzzles.json` are.
To start more clients on the same machine, give each one a different UDP port
(for example `java -jar target/client.jar 6001`).
