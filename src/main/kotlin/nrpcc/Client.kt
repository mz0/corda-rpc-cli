package nrpcc

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.utilities.NetworkHostAndPort.Companion.parse

enum class Commands {
    exec,
    execw,
    execm,
    neigh,
    whoami,
    notars,
    time,
}

class RpcCmdr(val addr: String, val user: String, val password: String) {
    var nodeAddr = parse(addr)
    var client = CordaRPCClient(nodeAddr)
    var conn = client.start(user, password)
    var ops = conn.proxy
    fun cmd(i: Commands) {
        when (i) {
            Commands.exec -> { println( "exec unimplemented")}
            Commands.execw -> { println( "e-w unimplemented")}
            Commands.execm -> { println( "e-m unimplemented")}
            Commands.neigh -> { println( "neigh unimplemented")}
            Commands.whoami -> { println( "whoami unimplemented")}
            Commands.notars -> { println( "notars unimplemented")}
            Commands.time -> { println( "Node time is ${ops.currentNodeTime()}")}
        }
        conn.notifyServerAndClose()
    }
}
