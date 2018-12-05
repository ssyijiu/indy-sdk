import com.evernym.sdk.vcx.connection.ConnectionApi
import com.evernym.sdk.vcx.credential.CredentialApi
import com.evernym.sdk.vcx.proof.DisclosedProofApi
import com.evernym.sdk.vcx.schema.SchemaApi
import com.evernym.sdk.vcx.utils.UtilsApi
import com.evernym.sdk.vcx.vcx.VcxApi
import com.google.gson.reflect.TypeToken
import com.jayway.jsonpath.JsonPath
import sample.utils.VcxHelper
import sample.utils.VcxState
import sample.utils.toJson
import sample.utils.toObject
import java.util.*


/**
 * Created by lxm on 2018/11/30.
 */
fun main(args: Array<String>) {

    println("#7 Provision an agent and wallet, get back configuration details")
    val config = UtilsApi.vcxProvisionAgent(VcxHelper.provisionConfig())
    val type = object : TypeToken<Map<String, String>>() {}.type
    val configMap: MutableMap<String, String> = config.toObject(type)
    // Set some additional configuration options specific to alice
    configMap["institution_name"] = "alice"
    configMap["institution_logo_url"] = "http://robohash.org/456"
    configMap["genesis_path"] = "./docker-jd.txn"
    configMap["pool_name"] = "gytest"
    println("config: ${configMap.toJson()}")

    println("#8 Initialize libvcx with new configuration")
    VcxApi.vcxInitWithConfig(configMap.toJson()).get()

    print("#9 Input faber.py invitation details: ")
    val details = Scanner(System.`in`).nextLine()
    println("details: $details")

    println("#10 Convert to valid json and string and create a connection to faber")
    val connectionToFaber = ConnectionApi.vcxCreateConnectionWithInvite("faber", details).get()
    println("connectionToFaber: $connectionToFaber")
    val connectionType = "{ \"connection_type\": \"\" }"
    ConnectionApi.vcxConnectionConnect(connectionToFaber, connectionType).get()
    ConnectionApi.vcxConnectionUpdateState(connectionToFaber).get()

    println("#11 Wait for faber.py to issue a credential offer")
    sleep(10)
    val offers = CredentialApi.credentialGetOffers(connectionToFaber).get()
    println("offers: $offers")

    // Create a credential object from the credential offer
    val credentialOffer = JsonPath.read<List<String>>(offers, "$.[0]").toJson()
    println("credentialOffer: $credentialOffer")
    val credential = CredentialApi.credentialCreateWithOffer("credential", credentialOffer).get()

    println("#15 After receiving credential offer, send credential request")
    CredentialApi.credentialSendRequest(credential, connectionToFaber.toInt(), 0).get()

    println("#16 Poll agency and accept credential offer from faber")
    var credentialState = CredentialApi.credentialGetState(credential).get()
    while (credentialState != VcxState.Accepted) {
        sleep(2)
        CredentialApi.credentialUpdateState(credential).get()
        credentialState = CredentialApi.credentialGetState(credential).get()
    }

    println("#22 Poll agency for a proof request")
    val requests = DisclosedProofApi.proofGetRequests(connectionToFaber).get()
    val requests0 = JsonPath.read<Map<String, String>>(requests, "$.[0]").toJson()


    println("#23 Create a Disclosed proof object from proof request")
    val proof = DisclosedProofApi.proofCreateWithRequest("proof", requests0).get()

    println("#24 Query for credentials in the wallet that satisfy the proof request")
    var credentials = DisclosedProofApi.proofRetrieveCredentials(proof).get()
    println("credentials: $credentials")

    // TODO: Use the first available credentials to satisfy the proof request
    // for attr in credentials['attrs']:
    // credentials['attrs'][attr] = credentials['attrs'][attr][0]
    credentials = credentials.replace("[", "").replace("]", "")

    println("#25 Generate the proof")
    DisclosedProofApi.proofGenerate(proof, credentials, "{}")

    println("#26 Send the proof to faber")
    DisclosedProofApi.proofSend(proof, connectionToFaber)

}

fun sleep(timeSeconds: Long) {
    try {
        Thread.sleep(timeSeconds * 1000)
    } catch (e: InterruptedException) {
        e.printStackTrace()
    }
}


