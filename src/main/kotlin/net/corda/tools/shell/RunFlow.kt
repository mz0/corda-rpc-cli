package net.corda.tools.shell

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.client.jackson.JacksonSupport
import net.corda.client.jackson.StringToMethodCallParser
import net.corda.core.CordaException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.packageName_
import net.corda.core.internal.uncheckedCast
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowProgressHandle
import java.io.InputStream
import java.lang.reflect.Type
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType

// SerializationSupport imports for UniqueIdentifierDeserializer, InputStreamDeserializer
import net.corda.core.internal.inputStream
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.io.BufferedInputStream
import java.nio.file.Paths
import java.util.Collections.synchronizedSet
import java.util.UUID

object Helper {

    fun createYamlInputMapper(rpcOps: CordaRPCOps): ObjectMapper {
        // Return a standard Corda Jackson object mapper, configured to use YAML by default and with extra
        // serializers.
        return JacksonSupport.createDefaultMapper(rpcOps, YAMLFactory(), true).apply {
            val rpcModule = SimpleModule().apply {
                addDeserializer(InputStream::class.java, InputStreamDeserializer)
                addDeserializer(UniqueIdentifier::class.java, UniqueIdentifierDeserializer)
            }
            registerModule(rpcModule)
        }
    }

    class RpcFlowNotFound(val error: String, val inList: List<String>) : CordaException(this.toString()) {
        override fun toString() = (listOf("$error") + inList).joinToString(System.lineSeparator())
    }

    @Throws(RpcFlowNotFound::class)
    fun triggerFlowByNameFragment(nameFragment: String, inputData: String, rpcOps: CordaRPCOps) {
        runFlowByNameFragment(nameFragment, inputData, rpcOps, false)
    }

    @Throws(RpcFlowNotFound::class)
    fun triggerAndTrackFlowByNameFragment(nameFragment: String, inputData: String, rpcOps: CordaRPCOps): FlowProgressHandle<Any?> {
        return runFlowByNameFragment(nameFragment, inputData, rpcOps, true) as FlowProgressHandle<Any?>
    }

    /**
     * Called from the 'run' and 'exec' rpc-client commands.
     *
     * Take a name fragment and find a matching flow name, or throws an exception
     * with all the registered flows listed in the message if the name fragment is ambiguous or not found.
     *
     * Then parse [inputData] as constructor arguments using the [flowFromString] method
     * and return a FlowProgressHandle for the requested flow with proper arguments.
     *
     * Made from
     * github.com/corda/corda/blob//c18c3ae/tools/shell/src/main/kotlin/net/corda/tools/shell/InteractiveShell.kt#L266
     */
    @JvmStatic
    @Throws(RpcFlowNotFound::class)
    fun runFlowByNameFragment(nameFragment: String,
                              inputData: String,
                              rpcOps: CordaRPCOps,
                              track: Boolean = true,
                              inputObjectMapper: ObjectMapper = createYamlInputMapper(rpcOps)): FlowHandle<Any?> {
        val allFlows = rpcOps.registeredFlows()
        val matches = allFlows.filter { nameFragment in it }
        if (matches.isEmpty()) {
            throw RpcFlowNotFound("No flow named like '$nameFragment' in:", allFlows)
        } else if (matches.size > 1) {
            throw RpcFlowNotFound("Ambiguous name $nameFragment provided; registered flows are:", allFlows)
        }

        val flowName = matches.single()
        val flowClazz: Class<FlowLogic<*>> = uncheckedCast(Class.forName(flowName))

        if (track) {
            return flowFromString({ clazz, args -> rpcOps.startTrackedFlowDynamic(clazz, *args) }, inputData, flowClazz, inputObjectMapper)
        } else {
            return flowFromString({ clazz, args -> rpcOps.startFlowDynamic(clazz, *args) }, inputData, flowClazz, inputObjectMapper)
        }

    }

    class NoApplicableConstructor(val errors: List<String>) : CordaException(this.toString()) {
        override fun toString() = (listOf("No applicable constructor for flow. Problems were:") + errors).joinToString(System.lineSeparator())
    }

    /**
     * Tidies up a possibly generic type name by chopping off the package names of classes in a hard-coded set of
     * hierarchies that are known to be widely used and recognised, and also not have (m)any ambiguous names in them.
     *
     * This is used for printing error messages when something doesn't match.
     */
    private fun maybeAbbreviateGenericType(type: Type, extraRecognisedPackage: String): String {
        val packagesToAbbreviate = listOf("java.", "net.corda.core.", "kotlin.", extraRecognisedPackage)

        fun shouldAbbreviate(typeName: String) = packagesToAbbreviate.any { typeName.startsWith(it) }
        fun abbreviated(typeName: String) = if (shouldAbbreviate(typeName)) typeName.split('.').last() else typeName

        fun innerLoop(type: Type): String = when (type) {
            is ParameterizedType -> {
                val args: List<String> = type.actualTypeArguments.map(::innerLoop)
                abbreviated(type.rawType.typeName) + '<' + args.joinToString(", ") + '>'
            }
            is GenericArrayType -> {
                innerLoop(type.genericComponentType) + "[]"
            }
            is Class<*> -> {
                if (type.isArray)
                    abbreviated(type.simpleName)
                else
                    abbreviated(type.name).replace('$', '.')
            }
            else -> type.toString()
        }

        return innerLoop(type)
    }
    // github.com/corda/corda/blob/c18c3ae/tools/shell/src/main/kotlin/net/corda/tools/shell/InteractiveShell.kt#L388
    // TODO: This utility is generally useful and might be better moved to the node class, or an RPC, if we can commit to making it stable API.
    /**
     * Given a [FlowLogic] class and a string in one-line Yaml form, finds an applicable constructor and starts
     * the flow, returning the created flow logic. Useful for lightweight invocation where text is preferable
     * to statically typed, compiled code.
     *
     * See the [StringToMethodCallParser] class to learn more about limitations and acceptable syntax.
     *
     * @throws NoApplicableConstructor if no constructor could be found for the given set of types.
     */
    @Throws(NoApplicableConstructor::class)
    fun <T> flowFromString(invoke: (Class<out FlowLogic<T>>, Array<out Any?>) -> FlowHandle<T>,
                           inputData: String,
                           clazz: Class<out FlowLogic<T>>,
                           om: ObjectMapper): FlowHandle<T> {
        // For each constructor, attempt to parse the input data as a method call. Use the first that succeeds,
        // and keep track of the reasons we failed so we can print them out if no constructors are usable.
        val parser = StringToMethodCallParser(clazz, om)
        val errors = ArrayList<String>()

        val classPackage = clazz.packageName_
        for (ctor in clazz.constructors) {
            var paramNamesFromConstructor: List<String>? = null

            fun getPrototype(): List<String> {
                val argTypes = ctor.genericParameterTypes.map { it: Type ->
                    // If the type name is in the net.corda.core or java namespaces, chop off the package name
                    // because these hierarchies don't have (m)any ambiguous names and the extra detail is just noise.
                    maybeAbbreviateGenericType(it, classPackage)
                }
                return paramNamesFromConstructor!!.zip(argTypes).map { (name, type) -> "$name: $type" }
            }

            try {
                // Attempt construction with the given arguments.
                paramNamesFromConstructor = parser.paramNamesFromConstructor(ctor)
                val args = parser.parseArguments(clazz.name, paramNamesFromConstructor.zip(ctor.genericParameterTypes), inputData)
                if (args.size != ctor.genericParameterTypes.size) {
                    errors.add("${getPrototype()}: Wrong number of arguments (${args.size} provided, ${ctor.genericParameterTypes.size} needed)")
                    continue
                }
                return invoke(clazz, args)
            } catch (e: StringToMethodCallParser.UnparseableCallException.MissingParameter) {
                errors.add("${getPrototype()}: missing parameter ${e.paramName}")
            } catch (e: StringToMethodCallParser.UnparseableCallException.TooManyParameters) {
                errors.add("${getPrototype()}: too many parameters")
            } catch (e: StringToMethodCallParser.UnparseableCallException.ReflectionDataMissing) {
                val argTypes = ctor.genericParameterTypes.map { it.typeName }
                errors.add("$argTypes: <constructor missing parameter reflection data>")
            } catch (e: StringToMethodCallParser.UnparseableCallException) {
                val argTypes = ctor.genericParameterTypes.map { it.typeName }
                errors.add("$argTypes: ${e.message}")
            }
        }
        throw NoApplicableConstructor(errors)
    }

}

// net/corda/tools/shell/SerializationSupport.kt
/**
 * String value deserialized to [UniqueIdentifier].
 * Any string value used as [UniqueIdentifier.externalId].
 * If string contains underscore(i.e. externalId_uuid) then split with it.
 *      Index 0 as [UniqueIdentifier.externalId]
 *      Index 1 as [UniqueIdentifier.id]
 * */
object UniqueIdentifierDeserializer : JsonDeserializer<UniqueIdentifier>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): UniqueIdentifier {
        //Check if externalId and UUID may be separated by underscore.
        if (p.text.contains("_")) {
            val ids = p.text.split("_")
            //Create UUID object from string.
            val uuid: UUID = UUID.fromString(ids[1])
            //Create UniqueIdentifier object using externalId and UUID.
            return UniqueIdentifier(ids[0], uuid)
        }
        //Any other string used as externalId.
        return UniqueIdentifier.fromString(p.text)
    }
}

// A file name is deserialized to an InputStream if found.
object InputStreamDeserializer : JsonDeserializer<InputStream>() {
    // Keep track of them so we can close them later.
    private val streams = synchronizedSet(HashSet<InputStream>())

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): InputStream {
        val stream = object : BufferedInputStream(Paths.get(p.text).inputStream()) {
            override fun close() {
                super.close()
                streams.remove(this)
            }
        }
        streams += stream
        return stream
    }
}
