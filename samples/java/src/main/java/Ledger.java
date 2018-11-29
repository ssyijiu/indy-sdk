import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidJSONParameters;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.did.DidResults.CreateAndStoreMyDidResult;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONObject;
import utils.PoolUtils;

import static org.hyperledger.indy.sdk.ledger.Ledger.*;
import static org.junit.Assert.assertEquals;
import static utils.PoolUtils.PROTOCOL_VERSION;


class Ledger {

    public static void main(String[] args) throws Exception {
        demo();
    }

    private static void demo() throws Exception {
        System.out.println("Ledger sample -> started");

        String trusteeSeed = "19e2ea3730c3d62f36a095a44d343c7f5d81e0168c7f987b3d70a37c516bb45d";

        // Set protocol version 2 to work with Indy Node 1.4
        Pool.setProtocolVersion(PROTOCOL_VERSION).get();

        // 1. Create ledger config from genesis txn file
        String poolName = PoolUtils.createPoolLedgerConfig();
        Pool pool = Pool.openPoolLedger(poolName, "{}").get();
        System.out.println("\n1. Create ledger config from genesis txn file");
        System.out.println("\npoolName: " + poolName);

        // 2. Create and Open My Wallet
        String myWalletConfig = "{\"id\":\"myWallet\"}";
        String myWalletCredentials = "{\"key\":\"my_wallet_key\"}";
        Wallet.createWallet(myWalletConfig, myWalletCredentials).get();
        Wallet myWallet = Wallet.openWallet(myWalletConfig, myWalletCredentials).get();
        System.out.println("\n2. Create and Open My Identity Wallet");

        // 3. Create and Open Trustee Wallet
        String trusteeWalletConfig = "{\"id\":\"theirWallet\"}";
        String trusteeWalletCredentials = "{\"key\":\"trustee_wallet_key\"}";
        Wallet.createWallet(trusteeWalletConfig, trusteeWalletCredentials).get();
        Wallet trusteeWallet = Wallet.openWallet(trusteeWalletConfig, trusteeWalletCredentials).get();
        System.out.println("\n3. Create and Open Trustee Identity Wallet");

        // 4. Create My Did from Wallet
        CreateAndStoreMyDidResult createMyDidResult = Did.createAndStoreMyDid(myWallet, "{}").get();
        String myDid = createMyDidResult.getDid();
        String myVerkey = createMyDidResult.getVerkey();
        System.out.println("\n4. Create My Did");
        System.out.println("\nmyDid: " + myDid);
        System.out.println("myVerkey: " + myVerkey);

        // 5. Create Did from Trustee seed
        DidJSONParameters.CreateAndStoreMyDidJSONParameter theirDidJson =
                new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, trusteeSeed, null, null);

        CreateAndStoreMyDidResult trusteeDidResult = Did.createAndStoreMyDid(trusteeWallet, theirDidJson.toJson()).get();
        String trusteeDid = trusteeDidResult.getDid();
        System.out.println("\n5. Create Did from Trustee seed");
        System.out.println("trusteeDid: " + trusteeDid);
        System.out.println("trusteeVerkey: " + trusteeDidResult.getVerkey());

        // 6. Build NYM request to add my identity to the ledger
        String nymRequest = buildNymRequest(trusteeDid, myDid, myVerkey, null, null).get();
        System.out.println("\n6. Build NYM request to add my identity to the ledger\\n");
        System.out.println("NYM Request: " + nymRequest);

        // 7. Trustee Sign NYM Request and Sending the nym request to ledger
        String nymResponseJson = signAndSubmitRequest(pool, trusteeWallet, trusteeDid, nymRequest).get();
        System.out.println("\n7. Trustee Sign NYM Request and Sending the nym request to ledger");
        System.out.println("NYM ResponseJson: " + nymResponseJson);
        System.out.println("\nAt this point, we have successfully written a new identity to the ledger. Our next step will be to query it.");


        JSONObject nymResponse = new JSONObject(nymResponseJson);
        assertEquals(myDid, nymResponse.getJSONObject("result").getJSONObject("txn").getJSONObject("data").getString("dest"));
        assertEquals(myVerkey, nymResponse.getJSONObject("result").getJSONObject("txn").getJSONObject("data").getString("verkey"));

        // 8. Generating and storing DID and Verkey to query the ledger
        DidResults.CreateAndStoreMyDidResult clientDid = Did.createAndStoreMyDid(myWallet, "{}").get();
        String clientDID = clientDid.getDid();
        String clientVerkey = clientDid.getVerkey();
        System.out.println("\n8. Generating and storing DID and Verkey to query the ledger with\n");
        System.out.println("Client DID: " + clientDID);
        System.out.println("Client Verkey: " + clientVerkey);

        // 9. Building the GET_NYM request to query my identity Verkey
        String getNymRequest = buildGetNymRequest(clientDID, myDid).get();
        System.out.println("\n9. Building the GET_NYM request to query my identity Verkey as the Client\n");
        System.out.println("GET_NYM request json:\n" + getNymRequest);

        // 10. Sending the GET_NYM request to the ledger
        String getNymResponse = submitRequest(pool, getNymRequest).get();
        System.out.println("\n10. Sending the GET_NYM request to the ledger\n");
        System.out.println("GET_NYM response json:\n" + getNymResponse);

        // 11. See whether we received the same info that we wrote the ledger
        String responseData = new JSONObject(getNymResponse).getJSONObject("result").getString("data");
        String myAnchorVerkeyFromLedger = new JSONObject(responseData).getString("verkey");
        System.out.println("\n11. Comparing the Verkey as written by Trustee and as retrieved in Client's query\n");
        System.out.println("Written by Trustee: " + myVerkey);
        System.out.println("Queried from Ledger: " + myAnchorVerkeyFromLedger);
        System.out.println("Matching: " + myVerkey.equals(myAnchorVerkeyFromLedger));

        // 12. Close and delete My Wallet
        myWallet.closeWallet().get();
        Wallet.deleteWallet(myWalletConfig, myWalletCredentials).get();
        System.out.println("\n12. Close and delete My Wallet");

        // 13. Close and delete Their Wallet
        trusteeWallet.closeWallet().get();
        Wallet.deleteWallet(trusteeWalletConfig, trusteeWalletCredentials).get();
        System.out.println("\n13. Close and delete Trustee Wallet");

        // 14. Close Pool
        pool.closePoolLedger().get();
        System.out.println("\n14. Close Pool");

        // 15. Delete Pool ledger config
        Pool.deletePoolLedgerConfig(poolName).get();
        System.out.println("\n15. Delete Pool ledger config");

        System.out.println("\nLedger sample -> completed");
    }
}
