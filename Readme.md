# Corda 5-barrel RPC client

Provided you have 5 nodes on localhost with RPC ports 10002, 10012, ..., 10042 with user `userN` and a common password configured on each node, you may run certain command on each node in parallel, e.g. `./gradlew run --args="flows"` to see a list of flows on each node.
