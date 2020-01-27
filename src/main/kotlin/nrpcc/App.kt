package nrpcc

fun main(args: Array<String>) {
    val cmd: Command
    var flowNam: String? = null
    var flowArgs = ""
    when (args.size) {
        0 -> { usage(); kotlin.system.exitProcess(1) }
        1 -> { cmd = Command.valueOf(args[0]) }
        else -> {cmd = Command.valueOf(args[0]); flowNam=args[1]; flowArgs=args.drop(2).joinToString(separator=" ")}
    }

    val hostPort = "localhost:10002"
    val user = "userN"
    val password = "X"
    try {
        val r = RpcCmdr(hostPort, user, password).cmd(cmd, flowNam, flowArgs)
        Thread.sleep(12) // TODO hide this waiting in dispose(), better yet make it a parameter
        r.dispose()
    } catch (e: net.corda.client.rpc.RPCException) {
        if (e.message.toString().startsWith("Cannot connect to server(s). Tried with all available servers.")) {
            println("Cannot connect to $hostPort")
        } else {
            println("${e.message}")
        }
    } catch (e: Exception) {
        if (e.message.toString().startsWith("AMQ119031: Unable to validate user from ")) {
            println("Cannot login to $hostPort as '$user' with password '$password'")
        } else {
            println("${e.message}")
        }
    }
}

fun usage() { println("Usage: <binary> <cmd>\n" +
        "Example: <binary> exec MyFlow arg1: 10, toParty: PartyB") }
