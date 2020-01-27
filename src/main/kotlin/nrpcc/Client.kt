package nrpcc

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort.Companion.parse
import net.corda.tools.shell.Helper.triggerAndTrackFlowByNameFragment
import rx.observers.Observers
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
        return ops.nodeInfo().legalIdentities.first()
    }

    private fun exec(flowNam: String, flowArgs: String) {
        println("${System.currentTimeMillis()} exec start")
        val fh = triggerAndTrackFlowByNameFragment(flowNam, flowArgs, ops)
        fh.progress.subscribe(Observers.create(
            { evt: String -> println("${System.currentTimeMillis()} $evt") },   // onNext
            { t -> println(t.message)},                                         // onError
            { println("${System.currentTimeMillis()} onComplete fired"); this.busy = false } // onComplete
        )) // make an Observer, and subscribe it to the FlowProgressHandle updates

        this.busy = true; println("${System.currentTimeMillis()} ${fh.returnValue.get()}") // start flow & get result
        println("${System.currentTimeMillis()}  exec(,) finish")
    }

    fun dispose() {
        println("${System.currentTimeMillis()} closing RPC connection")
        conn.notifyServerAndClose()
    }
}
