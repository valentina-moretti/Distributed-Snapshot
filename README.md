# Java library for distributed snapshot
Assignment: 
Implement in Java a library that offers the capability of storing a distributed snapshot on disk.
The library should be state and message agnostic.
Implement also an application that uses the library to cope with node failures (restarting from
the last valid snapshot).
Assumptions
● Nodes do not crash in the middle of the snapshot.
● The topology of the network (including the set of nodes) does not change during a
snapshot.
