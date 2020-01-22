package nrpcc

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.identity.Party
import net.corda.core.utilities.NetworkHostAndPort.Companion.parse

enum class Commands {
    exec,
    execw,
    execm,
    peers,
    whoami,
    notars,
    time,
    flows,
}

class RpcCmdr(val addr: String, val user: String, val password: String) {
    val nodeAddr by lazy { parse(addr) }
    val client by lazy { CordaRPCClient(nodeAddr) }
    val conn by lazy { client.start(user, password) }
    val ops by lazy { conn.proxy }
    val me by lazy { getSelfID() }

    fun cmd(i: Commands) {
        when (i) {
            Commands.exec -> { println( "exec unimplemented")}
            Commands.execw -> { println( "e-w unimplemented")}
            Commands.execm -> { println( "e-m unimplemented")}
            Commands.peers -> { println( "Peers (excluding self): ${getPeerOrgs()}")}
            Commands.whoami -> { println( "${me.name}")}
            Commands.notars -> { println( "notars unimplemented")}
            Commands.time -> { println( "Node time is ${ops.currentNodeTime()}")}
            Commands.flows -> { println( "Registered flows: ${ops.currentNodeTime()}")}
        }
        conn.notifyServerAndClose()
    }

    fun getPeers(): List<Party> {
        return ops.networkMapSnapshot().map { ni -> ni.legalIdentities.first() }.filter { it != me }
    }

    fun getPeerOrgs(): List<String> {
        return getPeers().map { it.name.organisation }
    }

    fun getSelfID(): Party {
        return ops.nodeInfo().legalIdentities.first()
    }
}
