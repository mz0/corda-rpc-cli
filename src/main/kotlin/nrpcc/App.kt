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

    val hostPort1 = "localhost:10002"
    val user = "userN"
    val password = "X"
    val r = RpcCmdr(hostPort1, user, password).cmd(cmd, flowNam, flowArgs)
    Thread.sleep(12) // TODO hide this waiting in dispose(), better yet make it a parameter
    r.dispose()
}

fun usage() { println("Usage: <binary> <cmd>\n" +
        "Example: <binary> exec MyFlow arg1: 10, toParty: PartyB") }
