package utils

import org.hyperledger.indy.sdk.crypto.Crypto
import org.hyperledger.indy.sdk.did.Did
import org.hyperledger.indy.sdk.ledger.Ledger.*
import org.hyperledger.indy.sdk.ledger.LedgerResults
import org.hyperledger.indy.sdk.pool.Pool
import org.hyperledger.indy.sdk.wallet.Wallet
import org.json.JSONObject

object Indy {

    object Role {
        const val TRUST_ANCHOR = "TRUST_ANCHOR"
    }

    @JvmStatic
    fun createAndOpenWallet(config: String, credentials: String): Wallet {
        Wallet.createWallet(config, credentials).get()
        return Wallet.openWallet(config, credentials).get()
    }

    @JvmStatic
    fun onboarding(pool: Pool, from: String, fromWallet: Wallet, fromDid: String, to: String, toWallet: Wallet)
        : OnboardingResult {
        console.log("\n*** onboarding ***\n")

        console.log("@$from -> Create DID \"$from $to\"")
        val fromToResult = createAndStoreDid(fromWallet)
        val fromToDid = fromToResult.did
        val fromToVerkey = fromToResult.verkey
        console.log("\"$from $to\" DID: $fromToDid")
        console.log("\"$from $to\" Verkey: $fromToVerkey")

        console.log("@$from -> Send Nym to Ledger for \"$from $to\" DID")
        sendNym(pool, fromWallet, fromDid, fromToDid, fromToVerkey, null)

        console.log("@$from -> Send connection request to $to with \"$from $to\" DID and nonce")
        val connectionRequest = ConnectionRequest(fromToDid, 123456)
        console.log("connectionRequest: ${connectionRequest.toJson()}")

        console.log("@$from -> Sending request ......")

        console.log("@$to -> ...... request received")

        console.log("@$to -> Create DID \"$to $from\"")
        val toFromResult = createAndStoreDid(toWallet)
        val toFromDid = toFromResult.did
        val toFromVerKey = toFromResult.verkey
        console.log("\"$to $from\" DID: $toFromDid")
        console.log("\"$to $from\" VerKey: $toFromVerKey")

        console.log("@$to -> Get VerKey for Did from \"$from\"'s connection request")
        val fromToVerkey2 = Did.keyForDid(pool, toWallet, connectionRequest.did).get()
        console.log("\"$to $from\" VerKey from connection request: $fromToVerkey2")

        console.log("@$to -> Anoncrypt connection response for \"$from\" with \"$to $from\" DID, verkey and nonce")
        val connectionResponse = ConnectionResponse(toFromDid, toFromVerKey, connectionRequest.nonce)
        val anoncryptedConnectionResponse = Crypto.anonCrypt(fromToVerkey2, connectionResponse.toJson().toByteArray()).get()
        console.log("connectionResponse: ${connectionResponse.toJson()}")
        console.log("anoncryptedConnectionResponse: ${String(anoncryptedConnectionResponse)}")

        console.log("@$to -> Sending anoncrypted connection response to \"$from\" ......")

        console.log("@$from -> ...... response received")

        console.log("@$from -> Anondecrypt connection response from \"$to\"")
        val decryptedConnectionResponse = Crypto.anonDecrypt(fromWallet, fromToVerkey, anoncryptedConnectionResponse).get().toObject(ConnectionResponse::class.java)
        console.log("connectionResponse: $decryptedConnectionResponse")

        console.log("@$from -> Authenticates \"$to\" by comparision of none")
        if (connectionRequest.nonce != decryptedConnectionResponse.nonce) {
            throw NonceNotMatchException()
        }

        console.log("@$from -> Send Nym to Ledger for \"$to $from\" DID")
        sendNym(pool, fromWallet, fromDid, decryptedConnectionResponse.did, decryptedConnectionResponse.verkey, null)

        return OnboardingResult(fromToDid, fromToVerkey, toFromDid, toFromVerKey, decryptedConnectionResponse)
    }

    @JvmStatic
    fun getVerinym(pool: Pool, from: String, fromWallet: Wallet, fromDid: String, fromToVerKey: String,
                   to: String, toWallet: Wallet, toFromDid: String, toFromVerKey: String, role: String?): String {
        console.log("\n*** getVerinym ***\n")

        console.log("@$to -> Create DID")
        val toResult = createAndStoreDid(toWallet)
        val toDid = toResult.did
        val toVerkey = toResult.verkey
        console.log("toDid: $toDid")
        console.log("toVerkey: $toVerkey")

        console.log("@$to -> Authcrypt \"$to\" DID info for \"$from\"")
        val didInfo = DidInfo(toDid, toVerkey)
        val authcryptedDidInfoRaw = Crypto.authCrypt(toWallet, toFromVerKey, fromToVerKey, didInfo.toJson().toByteArray()).get()
        console.log("didInfo: ${didInfo.toJson()}")
        console.log("authcryptedDidInfoRaw: ${authcryptedDidInfoRaw.toUTF8()}")

        console.log("@$to -> Sending authcrypted \"$to\" DID info to \"$from\" ......")

        console.log("@$from -> ...... DID info received")

        console.log("@$from -> Authdecrypt \"$to\" DID info from \"$to\"")
        val authDecryptResult = Crypto.authDecrypt(fromWallet, fromToVerKey, authcryptedDidInfoRaw).get()
        val senderVerKey = authDecryptResult.verkey
        val authdecryptedDidInfo = authDecryptResult.decryptedMessage.toObject(DidInfo::class.java)
        console.log("senderVerKey: $senderVerKey")
        console.log("authdecryptedDidInfo: ${authdecryptedDidInfo.toJson()}")

        console.log("@$from -> Authenticates \"$to\" by comparison of Verkeys")
        val retrievedVerKey = Did.keyForDid(pool, fromWallet, toFromDid).get()
        console.log("retrievedVerKey: $retrievedVerKey")
        if (senderVerKey != retrievedVerKey) {
            throw VerkeyNotMatchException()
        }

        console.log("@$from -> Send Nym to Ledger for \"$to\" DID with $role Role")
        sendNym(pool, fromWallet, fromDid, authdecryptedDidInfo.did, authdecryptedDidInfo.verkey, role)

        return toDid
    }

    @JvmStatic
    fun sendNym(pool: Pool, submitterWallet: Wallet, submitterDid: String, targetDid: String, targetVerKey: String, role: String?): Pair<String, String> {
        val nymRequest = buildNymRequest(submitterDid, targetDid, targetVerKey, null, role).get()
        val nymResponse = signAndSubmitRequest(pool, submitterWallet, submitterDid, nymRequest).get()
        console.log("NYM request:\n$nymRequest")
        console.log("NYM response:\n$nymResponse")
        // At this point, we have successfully written a new identity to the ledger

        return Pair(nymRequest, nymResponse)
    }

    @JvmStatic
    fun sendSchema(pool: Pool, submitterWallet: Wallet, submitterDid: String, schema: String): Pair<String, String> {
        val schemaRequest = buildSchemaRequest(submitterDid, schema).get()
        val schemaResponse = signAndSubmitRequest(pool, submitterWallet, submitterDid, schemaRequest).get()
        console.log("=== Send Schema ===")
        console.log("Schema:$schema")
        console.log("Schema request:\n$schemaRequest")
        console.log("Schema response:\n$schemaResponse")

        return Pair(schemaRequest, schemaResponse)
    }

    @JvmStatic
    fun getSchema(pool: Pool, submitterDid: String, schemaId: String): LedgerResults.ParseResponseResult {
        val schemaRequest = buildGetSchemaRequest(submitterDid, schemaId).get()
        val schemaResponse = submitRequest(pool, schemaRequest).get()
        console.log("=== Get Schema ===")
        console.log("Schema request:\n$schemaRequest")
        console.log("Schema response:\n$schemaResponse")

        return parseGetSchemaResponse(schemaResponse).get()
    }

    @JvmStatic
    fun sendCredDef(pool: Pool, submitterWallet: Wallet, submitterDid: String, credDef: String): Pair<String, String> {
        val credDefRequest = buildCredDefRequest(submitterDid, credDef).get()
        val credDefResponse = signAndSubmitRequest(pool, submitterWallet, submitterDid, credDefRequest).get()
        console.log("=== Send CredDef ===")
        console.log("credDef request:\n$credDefRequest")
        console.log("credDef response:\n$credDefResponse")

        return Pair(credDefRequest, credDefResponse)
    }

    @JvmStatic
    fun getCredDef(pool: Pool, submitterDid: String, schemaId: String): LedgerResults.ParseResponseResult {
        val credDefRequest = buildGetCredDefRequest(submitterDid, schemaId).get()
        val credDefResponse = submitRequest(pool, credDefRequest).get()
        console.log("=== Get CredDef ===")
        console.log("credDef request:\n$credDefRequest")
        console.log("credDef response:\n$credDefResponse")

        return parseGetCredDefResponse(credDefResponse).get()
    }

    @JvmStatic
    fun proverGetEntitiesFromLedger(pool: Pool, submitterDid: String, credentials: List<CredAttrInfo>, actor: String)
        : ProverGetEntitiesFromLedgerResult {
        console.log("\n*** proverGetEntitiesFromLedger ***\n")

        val schemas = mutableMapOf<String, String>()
        val credDefs = mutableMapOf<String, String>()
        val revStates = JSONObject("{}").toString()

        credentials.forEach {
            val credential = it
            console.log("credential: $credential")

            console.log("@$actor -> Get Schema from Ledger")
            val schemaResult = getSchema(pool, submitterDid, credential.schema_id)
            schemas[schemaResult.id] = schemaResult.objectJson

            console.log("@$actor -> Get Credential Definition from Ledger")
            val credDefResult = getCredDef(pool, submitterDid, credential.cred_def_id)
            credDefs[credDefResult.id] = credDefResult.objectJson

            //if (credential.rev_reg_seq_no) {
            // TODO: Create Revocation States
            //}
        }

        return ProverGetEntitiesFromLedgerResult(schemas.toJson().formatJson(), credDefs.toJson().formatJson(), revStates)
    }

    @JvmStatic
    fun verifierGetEntitiesFromLedger(pool: Pool, submitterDid: String, credentials: List<CredAttrInfo>, actor: String): VerifierGetEntitiesFromLedgerResult {
        console.log("\n*** verifierGetEntitiesFromLedger ***\n")

        val schemas = mutableMapOf<String, String>()
        val credDefs = mutableMapOf<String, String>()
        val revRegs = JSONObject("{}").toString()
        val revRegDefs = JSONObject("{}").toString()

        credentials.forEach {
            val credential = it
            console.log("credential: $credential")

            console.log("@$actor -> Get Schema from Ledger")
            val schemaResult = getSchema(pool, submitterDid, credential.schema_id)
            schemas[schemaResult.id] = schemaResult.objectJson
            console.log("@$actor -> Get Credential Definition from Ledger")
            val credDefResult = getCredDef(pool, submitterDid, credential.cred_def_id)
            credDefs[credDefResult.id] = credDefResult.objectJson

            //if (credential.rev_reg_seq_no) {
            // TODO: Create Revocation States
            //}
        }

        return VerifierGetEntitiesFromLedgerResult(schemas.toJson().formatJson(), credDefs.toJson().formatJson(), revRegs, revRegDefs)
    }

    @JvmStatic
    private fun createAndStoreDid(wallet: Wallet) = Did.createAndStoreMyDid(wallet, "{}").get()

    @JvmStatic
    private fun String.formatJson() = this.replace("\\", "").replace("\"{", "{").replace("}\"", "}")

    data class ConnectionRequest(val did: String, val nonce: Int)
    data class ConnectionResponse(val did: String, val verkey: String, val nonce: Int)
    data class DidInfo(val did: String, val verkey: String)
    data class OnboardingResult(val fromToId: String, val fromToVerKey: String, val toFromDid: String, val toFromVerKey: String, val connectionResponse: ConnectionResponse)
    data class CertificateCredOffer(val schema_id: String, val cred_def_id: String, val key_correctness_proof: Any, val nonce: String)
    data class CertificateValue(val raw: String, val encoded: String)
    // CredAttrInfo
    data class CredAttrInfo(val cred_rev_id: String, val rev_reg_id: String, val referent: String, val schema_id: String, val cred_def_id: String, val timestamp: String, val attrs: Map<String, String>)
    // proverGetEntitiesFromLedger
    data class ProverGetEntitiesFromLedgerResult(val schemas: String, val credDefs: String, val revStates: String)
    // verifierGetEntitiesFromLedger
    data class VerifierGetEntitiesFromLedgerResult(val schemas: String, val credDefs: String, val revRegs: String, val revRegDefs: String)

    @JvmStatic
    fun getParkApplicationProofRequest(companyJobCertificateCredDefId: String): String {
        return JSONObject("{" +
                    "\"nonce\":\"1432422343242122312411212\"," +
                    "\"name\":\"Park-Application\"," +
                    "\"version\":\"0.1\"," +
                    "\"requested_attributes\":{" +
                    "   \"attr2_referent\":{" +
                    "       \"name\":\"last_name\"," +
                    "       \"restrictions\":[{" +
                    "           \"cred_def_id\":\"$companyJobCertificateCredDefId\"" +
                    "        }]" +
                    "   }," +
                    "   \"attr1_referent\":{" +
                    "       \"name\":\"first_name\"," +
                    "       \"restrictions\":[{" +
                    "           \"cred_def_id\":\"$companyJobCertificateCredDefId\"" +
                    "       }]" +
                    "   }," +
                    "   \"attr3_referent\":{" +
                    "       \"name\":\"mobile\"" +
                    "   }}," +
                    "\"requested_predicates\":{" +
                    "}" +
               "}\n").toString()
    }

    @JvmStatic
    fun getParkApplicationRequestedCreds(credForAttr1Referent: String, credForAttr2Referent: String): String {

        return   JSONObject(String.format("{\n" +
            "                                   \"self_attested_attributes\":{\"attr3_referent\":\"%s\"},\n" +
            "                                   \"requested_attributes\":{      " +
            "                                                               \"attr1_referent\":{\"cred_id\":\"%s\", \"revealed\":true},\n" +
            "                                                               \"attr2_referent\":{\"cred_id\":\"%s\", \"revealed\":true}},\n" +
            "                                   \"requested_predicates\":{}\n" +
            "                              }", "18618386178", credForAttr1Referent, credForAttr2Referent)).toString()
    }
}

fun main(args: Array<String>) {
    println(Indy.getParkApplicationRequestedCreds("xx","yy"))
}