package nrpcc

import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.Dispatchers

fun main(args: Array<String>) {
    val cmd: Command
    var flowNam: String? = null
    var flowArgs = ""
    when (args.size) {
        0 -> { usage(); kotlin.system.exitProcess(1) }
        1 -> { cmd = Command.valueOf(args[0]) }
        else -> {cmd = Command.valueOf(args[0]); flowNam=args[1]; flowArgs=args.drop(2).joinToString(separator=" ")}
    }

    val host = "p.x302.net"
    val n1 = "$host:10002"
    val n2 = "$host:10012"
    val n3 = "$host:10022"
    val n4 = "$host:10032"
    val n5 = "$host:10042"

    val nodes = listOf(n1, n2, n3, n4, n5)
    val user = "userN"
    val password = "X"
    val cmdrs = mutableListOf<RpcCmdr>()

    runBlocking {
        nodes.forEach {
            launch(Dispatchers.Default) {
                cmdrs.add(RpcCmdr(it, user, password).cmd(cmd, flowNam, flowArgs))
            }
        }
    }

    Thread.sleep(12) // TODO hide this waiting in dispose(), better yet make it a parameter
    runBlocking {
        cmdrs.forEach {
            launch(Dispatchers.Default) { it.dispose() }
        }
    }
}

fun usage() { println("Usage: <binary> <cmd>\n" +
        "Example: <binary> exec MyFlow arg1: 10, toParty: PartyB") }
