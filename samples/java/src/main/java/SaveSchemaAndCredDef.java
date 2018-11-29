import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults;
import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import utils.Indy;
import utils.PoolUtils;

import static org.hyperledger.indy.sdk.anoncreds.Anoncreds.issuerCreateAndStoreCredentialDef;
import static org.hyperledger.indy.sdk.anoncreds.Anoncreds.issuerCreateSchema;
import static utils.PoolUtils.PROTOCOL_VERSION;

class SaveSchemaAndCredDef {

    private static void demo() throws Exception {
        // Step 1
        String stewardSeed = "000000000000000000000000Steward1";
        Pool.setProtocolVersion(PROTOCOL_VERSION).get();

        System.out.println(
            "\n1. Creating a new local pool ledger configuration that can be used later to connect pool nodes.");
        String poolName = PoolUtils.createPoolLedgerConfig();

        System.out.println("\n2. Open pool ledger and get the pool handle from libindy.");
        Pool pool = Pool.openPoolLedger(poolName, "{}").get();
        System.out.println("\npoolName: " + poolName);

        // Step2
        System.out.println("\n3. Creates and open a new secure wallet");
        String walletConfig = "{\"id\":\"myWallet\"}";
        String walletCredentials = "{\"key\":\"my_wallet_key\"}";
        Wallet wallet = Indy.createAndOpenWallet(walletConfig, walletCredentials);

        System.out.println("\n4. Generating and storing steward DID and Verkey");
        String did_json = "{\"seed\": \"" + stewardSeed + "\"}";
        DidResults.CreateAndStoreMyDidResult stewardResult = Did.createAndStoreMyDid(wallet,
            did_json).get();
        String defaultStewardDid = stewardResult.getDid();
        System.out.println("\nSteward DID: " + defaultStewardDid);
        System.out.println("\nSteward Verkey: " + stewardResult.getVerkey());

        System.out.println("\n5. Generating and storing Trust Anchor DID and Verkey");
        DidResults.CreateAndStoreMyDidResult trustAnchorResult = Did.createAndStoreMyDid(wallet,
            "{}").get();
        String trustAnchorDID = trustAnchorResult.getDid();
        String trustAnchorVerkey = trustAnchorResult.getVerkey();
        System.out.println("\nTrust anchor DID: " + trustAnchorDID);
        System.out.println("\nTrust anchor Verkey: " + trustAnchorVerkey);

        System.out.println("\n6. Build and Send the nym request to ledger\n");
        Indy.sendNym(pool, wallet, defaultStewardDid, trustAnchorDID, trustAnchorVerkey,
            "TRUST_ANCHOR");

        // Step3
        System.out.println(
            "\n7. Build the SCHEMA request to add new schema to the ledger as a Steward");
        String schemaName = "gvt";
        String schemaVersion = "1.0";
        String schemaAttributes = "[\"name\", \"age\", \"sex\", \"height\"]";
        AnoncredsResults.IssuerCreateSchemaResult createSchemaResult =
            issuerCreateSchema(trustAnchorDID, schemaName, schemaVersion, schemaAttributes).get();

        String schemaId = createSchemaResult.getSchemaId();
        String schemaJson = createSchemaResult.getSchemaJson();
        System.out.println("\nschemaId: " + schemaId);
        System.out.println();
        Indy.sendSchema(pool, wallet, defaultStewardDid, schemaJson);

        // Step4
        System.out.println("\n8. Creating and storing CRED DEF using anoncreds as Trust Anchor, for the given Schema\n");
        String credDefTag = "Tag1";
        String credDefJSON = "{\"seqNo\": 1, \"dest\": \"" + defaultStewardDid + "\", \"data\": " + schemaJson + "}";
        System.out.println("Cred Def JSON:\n" + credDefJSON);
        AnoncredsResults.IssuerCreateAndStoreCredentialDefResult createCredDefResult
            = issuerCreateAndStoreCredentialDef(wallet, trustAnchorDID, schemaJson, credDefTag, "CL", credDefJSON).get();
        String credDefId = createCredDefResult.getCredDefId();
        String credDefJson = createCredDefResult.getCredDefJson();
        System.out.println("\nReturned Cred Id:\n" + credDefId);
        System.out.println("\nReturned Cred Definition:\n" + credDefJson);

        // Some cleanup code.
        System.out.println("\n9. Close and delete wallet\n");
        wallet.closeWallet().get();
        Wallet.deleteWallet(walletConfig, walletCredentials).get();

        System.out.println("\n10. Close pool\n");
        pool.closePoolLedger().get();

        System.out.println("\n11. Delete pool ledger config\n");
        Pool.deletePoolLedgerConfig(poolName).get();

    }


    public static void main(String[] args) throws Exception {
        demo();
    }
}
