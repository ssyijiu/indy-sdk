import com.evernym.sdk.vcx.connection.ConnectionApi
import com.evernym.sdk.vcx.credential.CredentialApi
import com.evernym.sdk.vcx.proof.DisclosedProofApi
import com.evernym.sdk.vcx.utils.UtilsApi
import com.evernym.sdk.vcx.vcx.VcxApi
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import sample.utils.VcxHelper
import sample.utils.VcxState
import sample.utils.toJson
import sample.utils.toObject
import java.util.*


/**
 * Created by lxm on 2018/11/30.
 */
suspend fun main(args: Array<String>) {

    println("#7 Provision an agent and wallet, get back configuration details")
    val config = await { UtilsApi.vcxProvisionAgent(VcxHelper.provisionConfig()) }
    val type = object : TypeToken<Map<String, String>>() {}.type
    val configMap: MutableMap<String, String> = config.toObject(type)
    // Set some additional configuration options specific to alice
    configMap["institution_name"] = "alice"
    configMap["institution_logo_url"] = "http://robohash.org/456"
    configMap["genesis_path"] = "./docker-jd.txn"
    configMap["pool_name"] = "gytest"
    print("config: $configMap")

    println("#8 Initialize libvcx with new configuration")
    await { VcxApi.vcxInitWithConfig(configMap.toJson()).get() }

    print("#9 Input faber.py invitation details: ")
    val details = Scanner(System.`in`).nextLine()

    println("#10 Convert to valid json and string and create a connection to faber")
    val connectionToFaber = await { ConnectionApi.vcxCreateConnectionWithInvite("faber", details).get() }
    val connectionType = "{ \"connection_type\": \"\" }"
    val connectionHandle = await { ConnectionApi.vcxConnectionConnect(connectionToFaber, connectionType).get() }
    await { ConnectionApi.vcxConnectionUpdateState(connectionToFaber).get() }

    println("#11 Wait for faber.py to issue a credential offer")
    val offers = await(10) { CredentialApi.credentialGetOffers(connectionToFaber).get() }
    println("offers: $offers")

    // Create a credential object from the credential offer
    val offerArray: MutableList<String> = offers.toObject(object : TypeToken<MutableList<String>>() {}.type)
    await { CredentialApi.credentialCreateWithOffer("credential", offerArray[0]).get() }

    println("#15 After receiving credential offer, send credential request")
    await { CredentialApi.credentialSendRequest(connectionToFaber, connectionHandle.toInt(), 0).get() }

    println("#16 Poll agency and accept credential offer from faber")
    var credentialState = await { CredentialApi.credentialGetState(connectionToFaber).get() }
    while (credentialState != VcxState.Accepted) {
        await(2) { CredentialApi.credentialUpdateState(connectionToFaber).get() }
        credentialState = await { CredentialApi.credentialGetState(connectionToFaber).get() }
    }

    println("#22 Poll agency for a proof request")
    val requests = await { DisclosedProofApi.proofGetRequests(connectionToFaber) }
    println(requests)

    println("#23 Create a Disclosed proof object from proof request")
    // val proof = await { DisclosedProofApi.proofCreate() }

    println("#24 Query for credentials in the wallet that satisfy the proof request")

}

suspend fun <T> await(timeMillis: Long = 0, await: () -> T): T {
    return GlobalScope.async {
        delay(timeMillis)
        await()
    }.await()
}

