package nrpcc

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: <binary> [exec|execw|execm] <flow-name> <flow-args>\n" +
            "Example: <binary> exec MyFlow arg1: 10, toParty: PartyB" }
    val cmd = Commands.valueOf(args[0])
    RpcCmdr("localhost:10002", "userN", "X").cmd(cmd)
}
