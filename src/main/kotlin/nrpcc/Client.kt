package nrpcc

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.FlowHandle
import net.corda.core.utilities.NetworkHostAndPort.Companion.parse
import net.corda.tools.shell.Helper.triggerAndTrackFlowByNameFragment
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
    val client by lazy { CordaRPCClient(nodeAddr) }
    val conn by lazy { client.start(user, password) }
    val ops by lazy { conn.proxy }
    val me by lazy { getSelfID() }
    var done = false

    fun cmd(i: Command, flowNam: String? = null, flowArgs: String = "") {
        when (i) {
            Command.exec -> { exec(flowNam ?: throw IllegalArgumentException("need flow name"), flowArgs)}
            Command.execw -> { println( "e-w unimplemented")}
            Command.execm -> { println( "e-m unimplemented")}
            Command.peers -> { println( "Peers (excluding self): ${getPeerOrgs()}"); done = true}
            Command.whoami -> { println( "${me.name}"); done = true}
            Command.notars -> { println( "notars unimplemented")}
            Command.time -> { println( "Node time is ${ops.currentNodeTime()}"); done = true}
            Command.flows -> { println( "Registered flows: ${ops.registeredFlows()}"); done = true}
            Command.down -> { println( "Shutting node ${me.name} down."); ops.shutdown() }
        }
        if (done) conn.notifyServerAndClose()
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
        val cf = CompletableFuture.runAsync(FlowByNameExec(flowNam, flowArgs, ops), Executors.newSingleThreadExecutor())
        cf.get(300L, TimeUnit.SECONDS)
        done = true
        println("${System.currentTimeMillis()}  exec(,) finish")
    }
}

class FlowByNameExec(val flowNam: String, val flowArgs: String, val ops: CordaRPCOps): Runnable {
    override fun run() {
        val fh = triggerAndTrackFlowByNameFragment(flowNam, flowArgs, ops)
        fh.progress.subscribe { evt ->
            println("${System.currentTimeMillis()} $evt")
            if (evt.toString() == "Done") println("Yay! Done.")
        }
        println("${System.currentTimeMillis()} ${fh.returnValue.get()}")
    }
}
