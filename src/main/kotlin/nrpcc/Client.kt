package nrpcc

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.identity.Party
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.utilities.NetworkHostAndPort.Companion.parse
import net.corda.tools.shell.Helper.triggerAndTrackFlowByNameFragment
import rx.observers.Observers

enum class Command {
    exec,
    execw,
    execm,
    peers,
    whoami,
    notars,
    time,
    flows,
    down
}

class RpcCmdr(val addr: String, val user: String, private val password: String) {
    val nodeAddr by lazy { parse(addr) }
    private val client by lazy { CordaRPCClient(nodeAddr) }
    private val conn by lazy { client.start(user, password) }
    private val ops by lazy { conn.proxy }
    val me by lazy { getSelfID() }
    private var busy = false

    fun cmd(i: Command, flowNam: String? = null, flowArgs: String = ""): RpcCmdr {
        when (i) {
            Command.exec -> { exec(flowNam ?: throw IllegalArgumentException("need flow name"), flowArgs)}
            Command.execw -> { println( "e-w unimplemented")}
            Command.execm -> { println( "e-m unimplemented")}
            Command.peers -> { println( "Peers (excluding self): ${getPeerOrgs()}")}
            Command.whoami -> { println( "${me.name}")}
            Command.notars -> { println( "notars unimplemented")}
            Command.time -> { println( "Node time is ${ops.currentNodeTime()}")}
            Command.flows -> { println( "Registered flows: ${ops.registeredFlows()}");}
            Command.down -> { println( "Shutting node ${me.name} down."); ops.shutdown() }
        }
        return this
    }

    private fun getPeers(): List<Party> {
        return ops.networkMapSnapshot().map { ni -> ni.legalIdentities.first() }.filter { it != me }
    }

    private fun getPeerOrgs(): List<String> {
        return getPeers().map { it.name.organisation }
    }

    private fun getSelfID(): Party {
        try {
            return ops.nodeInfo().legalIdentities.first()
        } catch (e: net.corda.client.rpc.RPCException) {
            if (e.message.toString().startsWith("Cannot connect to server(s). Tried with all available servers.")) {
                throw IllegalArgumentException("Cannot connect to $addr")
            } else {
                throw e
            }
        } catch (e: Exception) {
            if (e.message.toString().startsWith("AMQ119031: Unable to validate user from ")) {
                throw IllegalArgumentException("Cannot login to $addr as '$user' with password '$password'")
            } else {
                throw e
            }
        }
    }

    private fun exec(flowNam: String, flowArgs: String) { // TODO how to signal error?
        println("${System.currentTimeMillis()} exec start on ${me.name.organisation} using ${Thread.currentThread().name}")
        val fh: FlowProgressHandle<Any?>
        try {
            fh = triggerAndTrackFlowByNameFragment(flowNam, flowArgs, ops)
        } catch (e: net.corda.tools.shell.Helper.RpcFlowNotFound) {
            println("Error: $e")
            return
        }
        fh.progress.subscribe(Observers.create(
                { evt: String -> println("${System.currentTimeMillis()} $evt") },   // onNext
                { t -> println("Error in exec $flowNam: ${t.message}") },            // onError
                { println("${System.currentTimeMillis()} onComplete fired"); this.busy = false } // onComplete
        )) // make an Observer, and subscribe it to the FlowProgressHandle updates

        this.busy = true; println("${System.currentTimeMillis()} ${fh.returnValue.get()}") // start flow & get result
        println("${System.currentTimeMillis()} exec $flowNam finish")
    }

    fun dispose() {
        if (this.busy) {
            println("${System.currentTimeMillis()} Busy! Not closing RPC connection!")
        } else {
            println("${System.currentTimeMillis()} Closing RPC connection")
            conn.notifyServerAndClose()
        }
    }
}
