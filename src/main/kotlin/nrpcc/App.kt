package nrpcc

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: <binary> <cmd>\n" +
            "Example: <binary> exec MyFlow arg1: 10, toParty: PartyB" }
    val cmd = Commands.valueOf(args[0])
    val hostPort = "localhost:10002"
    val user = "userN"
    val password = "X"
    try {
        RpcCmdr(hostPort, user, password).cmd(cmd)
    } catch (e: net.corda.client.rpc.RPCException) {
        if (e.message.toString().startsWith("Cannot connect to server(s). Tried with all available servers.")) {
            println("Cannot connect to $hostPort")
        } else {
            println("${e.message}")
        }
    }
}
