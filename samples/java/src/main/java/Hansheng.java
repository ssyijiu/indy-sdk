import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlin.text.Charsets;
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults;
import org.hyperledger.indy.sdk.anoncreds.CredentialsSearchForProofReq;
import org.hyperledger.indy.sdk.crypto.Crypto;
import org.hyperledger.indy.sdk.crypto.CryptoResults;
import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidJSONParameters;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.ledger.LedgerResults;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.AnyExKt;
import utils.Indy;
import utils.PoolUtils;
import utils.console;

import static org.hyperledger.indy.sdk.anoncreds.Anoncreds.issuerCreateAndStoreCredentialDef;
import static org.hyperledger.indy.sdk.anoncreds.Anoncreds.issuerCreateCredential;
import static org.hyperledger.indy.sdk.anoncreds.Anoncreds.issuerCreateCredentialOffer;
import static org.hyperledger.indy.sdk.anoncreds.Anoncreds.issuerCreateSchema;
import static org.hyperledger.indy.sdk.anoncreds.Anoncreds.proverCreateCredentialReq;
import static org.hyperledger.indy.sdk.anoncreds.Anoncreds.proverCreateMasterSecret;
import static org.hyperledger.indy.sdk.anoncreds.Anoncreds.proverCreateProof;
import static org.hyperledger.indy.sdk.anoncreds.Anoncreds.proverStoreCredential;
import static org.junit.Assert.assertEquals;
import static utils.PoolUtils.PROTOCOL_VERSION;

public class Hansheng {

    private static final String POOL_NAME = "pool_hansheng";


    public static void main(String[] args) throws Exception {


        // 1. Init Pool
        // 初始化矿池和链
        // Set protocol version 2 to work with Indy Node 1.4
        Pool.setProtocolVersion(PROTOCOL_VERSION).get();
        // Create ledger config from genesis txn file
        String poolName = PoolUtils.createPoolLedgerConfig(POOL_NAME);
        Pool pool = Pool.openPoolLedger(poolName, "{}").get();

        console.log("\n=============================================");
        console.log("=== Steward Setup ===\n");

        // 2. Create Steward Wallet
        // 创建管理员钱包
        console.log("@Steward -> Create Wallet");
        String stewardWalletConfig = "{\"id\":\"stewardWalletName\"}";
        String stewardWalletCredentials = "{\"key\":\"steward_key\"}";
        Wallet stewardWallet = Indy.createAndOpenWallet(stewardWalletConfig,
            stewardWalletCredentials);

        // 3. Create Steward DID
        // 创建管理员 DID
        console.log("@Steward -> Create DID");
        String stewardSeed = "000000000000000000000000Steward1";
        DidJSONParameters.CreateAndStoreMyDidJSONParameter stewardDidJson =
            new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, stewardSeed, null, null);
        DidResults.CreateAndStoreMyDidResult stewardDidResult = Did.createAndStoreMyDid(
            stewardWallet, stewardDidJson.toJson()).get();
        String stewardDid = stewardDidResult.getDid();
        String stewardVerKey = stewardDidResult.getVerkey();
        console.log("stewardDid:" + stewardDid);
        console.log("stewardVerKey:" + stewardVerKey);

        console.log("\n=============================================");
        console.log("=== Daniel Setup ===\n");

        // 4. Create Daniel Wallet
        // 创建员工的钱包
        console.log("@Daniel -> Create Wallet");
        String danielWalletConfig = "{\"id\":\"danielWallet\"}";
        String danielWalletCredentials = "{\"key\":\"daniel_key\"}";
        Wallet danielWallet = Indy.createAndOpenWallet(danielWalletConfig, danielWalletCredentials);

        // 5. Create Master Scecret
        // 创建员工的主密钥
        console.log("@Daniel -> Create Master Scecret");
        String danielMasterSecretId = proverCreateMasterSecret(danielWallet, null).get();
        console.log("danielMasterSecretId:" + danielMasterSecretId);

        console.log("\n=============================================");
        console.log("=== Steward-Park Onboarding & Park GetVerinym ===\n");

        // 6. Create Pack Wallet
        // 创建园区钱包
        console.log("@Park -> Create Wallet");
        String parkWalletConfig = "{\"id\":\"parkWallet\"}";
        String parkWalletCredentials = "{\"key\":\"park_key\"}";
        Wallet parkWallet = Indy.createAndOpenWallet(parkWalletConfig, parkWalletCredentials);

        // 7. Onboarding Steward and Pack
        // 连接园区和管理员
        Indy.OnboardingResult stewardParkOnboarding =
            Indy.onboarding(pool, "Steward", stewardWallet, stewardDid, "Park", parkWallet);
        String stewardParkDid = stewardParkOnboarding.getFromToId();
        String stewardParkVerKey = stewardParkOnboarding.getFromToVerKey();
        String parkStewardDid = stewardParkOnboarding.getToFromDid();
        String parkStewardVerKey = stewardParkOnboarding.getToFromVerKey();
        console.log("stewardParkDid: " + stewardParkDid);
        console.log("stewardParkVerKey: " + stewardParkVerKey);
        console.log("parkStewardDid: " + parkStewardDid);
        console.log("parkStewardVerKey: " + parkStewardVerKey);

        // 8. Get Pack Verinym
        // 获取园区的法定 DID
        String parkDid = Indy.getVerinym(pool, "Steward", stewardWallet, stewardDid,
            stewardParkVerKey,
            "Park", parkWallet, parkStewardDid, parkStewardVerKey, Indy.Role.TRUST_ANCHOR);
        console.log("parkDid: " + parkDid);

        console.log("\n=============================================");
        console.log("=== Steward-Company Onboarding & Company GetVerinym ===\n");

        // 9. Create Company Wallet
        // 创建公司钱包
        console.log("@Company -> Create Wallet");
        String companyWalletConfig = "{\"id\":\"companyWallet\"}";
        String companyWalletCredentials = "{\"key\":\"company_key\"}";
        Wallet companyWallet = Indy.createAndOpenWallet(companyWalletConfig,
            companyWalletCredentials);

        // 10. Onboarding Steward and Company
        // 连接公司和管理员
        Indy.OnboardingResult stewardCompanyOnboarding =
            Indy.onboarding(pool, "Steward", stewardWallet, stewardDid, "Company", companyWallet);
        String stewardCompanyDid = stewardCompanyOnboarding.getFromToId();
        String stewardCompanyVerKey = stewardCompanyOnboarding.getFromToVerKey();
        String companyStewardDid = stewardCompanyOnboarding.getToFromDid();
        String companyStewardVerkey = stewardCompanyOnboarding.getToFromVerKey();
        console.log("stewardCompanyDid: " + stewardCompanyDid);
        console.log("stewardCompanyVerKey: " + stewardCompanyVerKey);
        console.log("companyStewardDid: " + companyStewardDid);
        console.log("parkStewardVerKey: " + companyStewardVerkey);

        // 11. Get Company Verinym
        // 获取公司的法定 DID
        String companyDid = Indy.getVerinym(pool, "Steward", stewardWallet, stewardDid,
            stewardParkVerKey,
            "Company", companyWallet, companyStewardDid, companyStewardVerkey,
            Indy.Role.TRUST_ANCHOR);
        console.log("companyDid: " + companyDid);

        console.log("\n=============================================");
        console.log("=== Credential Schemas Setup ===\n");

        // 12. Create Job-Certificate Schema
        // 创建职位信息的数据格式
        console.log("@Steward -> Create Job-Certificate Schema to Ledger");
        String jobCertificateSchemaAttributes
            = "[\"first_name\", \"last_name\", \"salary\", \"status\", \"experience\"]";
        AnoncredsResults.IssuerCreateSchemaResult jobCertificateSchemaResult =
            issuerCreateSchema(stewardDid, "Job-Certificate", "0.1", jobCertificateSchemaAttributes)
                .get();
        String jobCertificateSchemaId = jobCertificateSchemaResult.getSchemaId();
        String jobCertificateSchema = jobCertificateSchemaResult.getSchemaJson();
        console.log("jobCertificateSchemaId: " + jobCertificateSchemaId);
        console.log("jobCertificateSchema: " + jobCertificateSchema);

        // 13. Send Job-Certificate Schema to Ledger
        // 发送职位信息的数据格式到链上
        console.log("@Steward -> Send Job-Certificate Schema to Ledger");
        Indy.sendSchema(pool, stewardWallet, stewardDid, jobCertificateSchema);

        // 14. Create Park-Certificate Schema
        // 创建园区信息的数据格式
        console.log("@Steward -> Create Park-Certificate Schema to Ledger");
        String parkCertificateSchemaAttributes = "[\"first_name\", \"last_name\", \"level\"]";
        AnoncredsResults.IssuerCreateSchemaResult parkCertificateSchemaResult =
            issuerCreateSchema(stewardDid, "Park-Certificate", "0.1",
                parkCertificateSchemaAttributes).get();
        String parkCertificateSchemaId = parkCertificateSchemaResult.getSchemaId();
        String parkCertificateSchema = parkCertificateSchemaResult.getSchemaJson();
        console.log("parkCertificateSchemaId: " + parkCertificateSchemaId);
        console.log("parkCertificateSchema: " + parkCertificateSchema);

        // 15. Send Park-Certificate Schema to Ledger
        // 发送园区信息的数据格式到链上
        console.log("@Steward -> Send Park-Certificate Schema to Ledger");
        Indy.sendSchema(pool, stewardWallet, stewardDid, parkCertificateSchema);

        console.log("\n=============================================");
        console.log("=== Company Credential Definition Setup ===\n");

        // 16. Get Job-Certificate Schema from Ledger
        // 从链上获取职位信息的数据格式
        console.log("@Company -> Get Job-Certificate Schema from Ledger");
        LedgerResults.ParseResponseResult theJobCertificateSchemaResult = Indy.getSchema(pool,
            companyDid, jobCertificateSchemaId);
        String theJobCertificateSchemaId = theJobCertificateSchemaResult.getId();
        String theJobCertificateSchema = theJobCertificateSchemaResult.getObjectJson();
        console.log("theJobCertificateSchemaId: " + theJobCertificateSchemaId);
        console.log("theJobCertificateSchema: " + theJobCertificateSchema);

        // 17. Create Company Job-Certificate Credential Definition
        // 创建职位数据 Definition
        console.log("@Company -> Create and store Company Job-Certificate Credential Definition");
        AnoncredsResults.IssuerCreateAndStoreCredentialDefResult
            companyJobCertificateCredDefResult = issuerCreateAndStoreCredentialDef(
            companyWallet, companyDid, theJobCertificateSchema, "TAG1", "CL",
            "{\"support_revocation\": false}").get();
        String companyJobCertificateCredDefId = companyJobCertificateCredDefResult.getCredDefId();
        String companyJobCertificateCredDef = companyJobCertificateCredDefResult.getCredDefJson();
        console.log("companyJobCertificateCredDefId: " + companyJobCertificateCredDefId);
        console.log("companyJobCertificateCredDef: " + companyJobCertificateCredDef);

        // 18. Send Company Job-Certificate Credential Definition to Ledger
        // 发送职位数据 Definition 到链上
        console.log("@Company -> Send Company Job-Certificate Credential Definition to Ledger");
        Indy.sendCredDef(pool, companyWallet, companyDid, companyJobCertificateCredDef);

        console.log("\n=============================================");
        console.log("=== Park Credential Definition Setup ===\n");

        // 19. Get Park-Certificate Schema from Ledger
        // 从链上获取园区信息的数据格式
        console.log("@Park -> Get Park-Certificate Schema from Ledger");
        LedgerResults.ParseResponseResult thePackCertificateSchemaResult = Indy.getSchema(pool,
            parkDid, parkCertificateSchemaId);
        String theParkCertificateSchemaId = thePackCertificateSchemaResult.getId();
        String theParkCertificateSchema = thePackCertificateSchemaResult.getObjectJson();
        console.log("theParkCertificateSchemaId: " + theParkCertificateSchemaId);
        console.log("theParkCertificateSchema: " + theParkCertificateSchema);

        // 20. Create Park Park-Certificate Credential Definition
        // 创建园区数据 Definition
        console.log("@Park -> Create and store Park Park-Certificate Credential Definition");
        AnoncredsResults.IssuerCreateAndStoreCredentialDefResult
            parkParkCertificateCredDefResult = issuerCreateAndStoreCredentialDef(
            parkWallet, parkDid, theParkCertificateSchema, "TAG1", "CL",
            "{\"support_revocation\": false}").get();
        String parkParkCertificateCredDefId = parkParkCertificateCredDefResult.getCredDefId();
        String parkParkCertificateCredDef = parkParkCertificateCredDefResult.getCredDefJson();
        console.log("parkParkCertificateCredDefId: " + parkParkCertificateCredDefId);
        console.log("parkParkCertificateCredDef: " + parkParkCertificateCredDef);

        // 21. Send Park Park-Certificate Credential Definition to Ledge
        // 发送园区数据 Definition 到链上
        console.log("@Park -> Send Park Park-Certificate Credential Definition to Ledge");
        Indy.sendCredDef(pool, parkWallet, parkDid, parkParkCertificateCredDef);

        // 22. Onboarding Company and Daniel
        // 连接公司和员工
        console.log("\n=============================================");
        console.log("=== Company-Daniel Onboarding ===\n");
        Indy.OnboardingResult companyDanielOnboarding =
            Indy.onboarding(pool, "Company", companyWallet, companyDid, "Daniel", danielWallet);
        String companyDanielDid = companyDanielOnboarding.getFromToId();
        String companyDanielVerKey = companyDanielOnboarding.getFromToVerKey();
        String danielCompanyDid = companyDanielOnboarding.getToFromDid();
        String danielCompanyVerkey = companyDanielOnboarding.getToFromVerKey();
        Indy.ConnectionResponse companyDanielConnectionResponse = companyDanielOnboarding.getConnectionResponse();
        console.log("companyDanielDid: " + companyDanielDid);
        console.log("companyDanieVerKey: " + companyDanielVerKey);
        console.log("danielCompanyDid: " + danielCompanyDid);
        console.log("companyDanielConnectionResponse: " + companyDanielConnectionResponse);

        console.log("\n=============================================");
        console.log("=== Company Sending Job-Certificate Credential Offer ===\n");

        // 23. Company create Job-Certificate Credential Offer to Daniel
        // 公司给员工创建一个职位信息钥匙
        console.log("@Company -> Create Job-Certificate Credential Offer for Daniel");
        String jobCertificateCredOffer = issuerCreateCredentialOffer(companyWallet, companyJobCertificateCredDefId).get();
        console.log("jobCertificateCredOffer: " + jobCertificateCredOffer);

        // 24. Company Authcrypt Job-Certificate Credential Offer for Daniel
        // 公司加密职位信息钥匙发送给员工
        console.log("@Company -> Authcrypt Job-Certificate Credential Offer for Daniel");
        byte[] authcryptedJobCertificateCredOfferRaw = Crypto.authCrypt(companyWallet, companyDanielVerKey, companyDanielOnboarding.getConnectionResponse().getVerkey(), jobCertificateCredOffer.getBytes(Charsets.UTF_8)).get();
        console.log("authcryptedJobCertificateCredOfferRaw: " + AnyExKt.toUTF8(authcryptedJobCertificateCredOfferRaw));

        console.log("@Company -> Sending authcrypted Job-Certificate Credential Offer to Daniel ......");

        console.log("@Daniel -> ...... authcrypted Job-Certificate Credential Offer received");

        console.log("@Daniel -> Authdecrypt Job-Certificate Credential Offer from Company");

        // 25. Daniel Authdecrypt Job-Certificate Credential Offer from Company and Get cred_def_id
        // 员工解密职位信息钥匙，获取 schema_id、cred_def_id 等数据
        CryptoResults.AuthDecryptResult authdecryptedJobCertificateCredOfferResult = Crypto.authDecrypt(danielWallet,danielCompanyVerkey,authcryptedJobCertificateCredOfferRaw).get();
        String companyDanielVerKey2 = authdecryptedJobCertificateCredOfferResult.getVerkey();
        String authdecryptedJobCertificateCredOfferJson = AnyExKt.toUTF8(authdecryptedJobCertificateCredOfferResult.getDecryptedMessage());
        Indy.CertificateCredOffer authdecryptedJobCertificateCredOffer = AnyExKt.toObject(authdecryptedJobCertificateCredOfferResult.getDecryptedMessage(), Indy.CertificateCredOffer.class);
        console.log("companyDanielVerKey2: " + companyDanielVerKey2);
        console.log("authdecryptedJobCertificateCredOfferJson: " + authdecryptedJobCertificateCredOfferJson);
        console.log("authdecryptedJobCertificateCredOffer: " + authdecryptedJobCertificateCredOffer);

        console.log("\n=============================================");
        console.log("=== Daniel Getting Job-Certificate Credential ===\n");

        // 26. Daniel use cred_def_id Get Company Job-Certificate Credential Definition from Ledger
        // 员工使用 cred_def_id 在链上获取职位信息的 Definition
        console.log("@Daniel -> Get Company Job-Certificate Credential Definition from Ledger");
        LedgerResults.ParseResponseResult theCompanyJobCertificateCredDefResult = Indy.getCredDef(pool, danielCompanyDid, authdecryptedJobCertificateCredOffer.getCred_def_id());
        String theCompanyJobCertificateCredDefId = theCompanyJobCertificateCredDefResult.getId();
        String theCompanyJobCertificateCredDef = theCompanyJobCertificateCredDefResult.getObjectJson();
        console.log("theCompanyJobCertificateCredDefId: " + theCompanyJobCertificateCredDefId);
        console.log("theCompanyJobCertificateCredDef: " + theCompanyJobCertificateCredDef);

        // 27. Daniel use MasterSecret Create Job-certificate Credential Request for Company
        // 员工使用主密钥创建请求，以获取完整的职位信息
        console.log("@Daniel -> Create Job-Certificate Credential Request for Company");
        AnoncredsResults.ProverCreateCredentialRequestResult jobCertificateCredRequestResult
            = proverCreateCredentialReq(danielWallet, danielCompanyDid, authdecryptedJobCertificateCredOfferJson, theCompanyJobCertificateCredDef, danielMasterSecretId).get();
        String jobCertificateCredRequest = jobCertificateCredRequestResult.getCredentialRequestJson();
        String jobCertificateCredRequestMetadata = jobCertificateCredRequestResult.getCredentialRequestMetadataJson();
        console.log("jobCertificateCredRequest: " + jobCertificateCredRequest);
        console.log("jobCertificateCredRequestMetadata: " + jobCertificateCredRequestMetadata);

        // 28. Daniel Authcrypt Job-Certificate Credential Request for Company
        // 员工加密请求并发送给公司
        console.log("@Daniel -> Authcrypt Job-Certificate Credential Request for Company");
        byte[] authcryptedJobCertificateCredRequestRaw = Crypto.authCrypt(danielWallet, danielCompanyVerkey, companyDanielVerKey2, jobCertificateCredRequest.getBytes(Charsets.UTF_8)).get();
        console.log("authcryptedJobCertificateCredRequestRaw: " + AnyExKt.toUTF8(authcryptedJobCertificateCredRequestRaw));

        console.log("@Daniel -> Sending authcrypted Job-Certificate Credential Request to Company ......");

        // 29. Company Authdecrypt Job-Certificate Credential Request from Daniel
        // 公司解密请求
        console.log("@Company -> Authdecrypt Job-Certificate Credential Request from Daniel");
        CryptoResults.AuthDecryptResult authdecryptedJobCertificateCredRequestResult = Crypto.authDecrypt(companyWallet, companyDanielVerKey, authcryptedJobCertificateCredRequestRaw).get();
        String danielCompanyVerkey2 = authdecryptedJobCertificateCredRequestResult.getVerkey();
        String authdecryptedJobCertificateCredRequestJson = AnyExKt.toUTF8(authdecryptedJobCertificateCredRequestResult.getDecryptedMessage());
        console.log("danielCompanyVerkey2: " + danielCompanyVerkey2);
        console.log("authdecryptedJobCertificateCredRequestJson: " + authdecryptedJobCertificateCredRequestJson);

        console.log("@Company -> Create Job-Certificate Credential for Daniel");
        // 30. Company Create Job-Certificate Credential for Daniel
        // 公司根据职位信息的数据格式 为员工创建完整的职位信息
        Map<String,Indy.CertificateValue> jobCertificateCredMap = new HashMap<>();
        Indy.CertificateValue first_name = new Indy.CertificateValue("Daniel","245712572474217942457235975012103335");
        Indy.CertificateValue last_name = new Indy.CertificateValue("Yang","312643218496194691632153761283356127");
        Indy.CertificateValue salary = new Indy.CertificateValue("2400","2400");
        Indy.CertificateValue status = new Indy.CertificateValue("Permanent","2143135425425143112321314321");
        Indy.CertificateValue experience = new Indy.CertificateValue("10","10");
        jobCertificateCredMap.put("first_name",first_name);
        jobCertificateCredMap.put("last_name", last_name);
        jobCertificateCredMap.put("salary", salary);
        jobCertificateCredMap.put("status", status);
        jobCertificateCredMap.put("experience", experience);
        String jobCertificateCredValues = AnyExKt.toJson(jobCertificateCredMap);
        console.log("jobCertificateCredValues: " + jobCertificateCredValues);
        AnoncredsResults.IssuerCreateCredentialResult jobCertificateCredResult =
            issuerCreateCredential(companyWallet, jobCertificateCredOffer, authdecryptedJobCertificateCredRequestJson, jobCertificateCredValues, null, -1).get();
        String jobCertificateCred = jobCertificateCredResult.getCredentialJson();
        String jobCertificateCredRevocId = jobCertificateCredResult.getRevocId();
        String jobCertificateCredRevocRegDelta = jobCertificateCredResult.getRevocRegDeltaJson();
        console.log("jobCertificateCred: " + jobCertificateCred);
        console.log("jobCertificateCredRevocId: " + jobCertificateCredRevocId);
        console.log("jobCertificateCredRevocRegDelta: " + jobCertificateCredRevocRegDelta);

        // 31. Company Authcrypt Job-Certificate Credential for Daniel
        // 公司加密完整的身份信息并发送给员工
        console.log("@Company -> Authcrypt Job-Certificate Credential for Daniel");
        byte[] authcryptedJobCertificateCredRaw = Crypto.authCrypt(companyWallet, companyDanielVerKey,
            companyDanielConnectionResponse.getVerkey() , jobCertificateCred.getBytes(Charsets.UTF_8)).get();
        console.log("authcryptedJobCertificateCredRaw: " + AnyExKt.toUTF8(authcryptedJobCertificateCredRaw));

        console.log("@Company -> Sending authcrypted Job-Certificate Credential to Daniel ......");

        console.log("@Daniel -> ...... authcrypted Job-Certificate Credential received");

        // 32. Daniel Authdecrypt Job-Certificate Credential from Company
        // 员工解密完整的身份信息
        console.log("@Daniel -> Authdecrypt Job-Certificate Credential from Company");
        CryptoResults.AuthDecryptResult authdecryptedJobCertificateCredResult = Crypto.authDecrypt(danielWallet, danielCompanyVerkey, authcryptedJobCertificateCredRaw).get();
        String companyDanielVerKey3 = authdecryptedJobCertificateCredResult.getVerkey();
        String authdecryptedJobCertificateCredJson = AnyExKt.toUTF8(authdecryptedJobCertificateCredResult.getDecryptedMessage());
        console.log("companyDanielVerKey3: " + companyDanielVerKey3);
        console.log("authdecryptedJobCertificateCredJson: " + authdecryptedJobCertificateCredJson);

        // 33. @Daniel Store Job-Certificate Credential from Company
        // 员工将保存身份信息保存在钱包
        console.log("@Daniel -> Store Job-Certificate Credential from Company");
        String jobCertificateCredId = proverStoreCredential(danielWallet, null, jobCertificateCredRequestMetadata, authdecryptedJobCertificateCredJson, theCompanyJobCertificateCredDef, null).get();
        console.log("jobCertificateCredId: " + jobCertificateCredId);

        console.log("\n=============================================");
        console.log("=== Park-Daniel Onboarding ===\n");
        // 34. Onboarding Park and Daniel
        // 绑定园区和员工
        Indy.OnboardingResult parkDanielOnboarding = Indy.onboarding(pool, "Park", parkWallet, parkDid, "Daniel", danielWallet);
        String parkDanielDid = parkDanielOnboarding.getFromToId();
        String parkDanielVerKey = parkDanielOnboarding.getFromToVerKey();
        String danielParkDid = parkDanielOnboarding.getToFromDid();
        String danielParkVerKey = parkDanielOnboarding.getToFromVerKey();
        Indy.ConnectionResponse parkDanielConnectionResponse = parkDanielOnboarding.getConnectionResponse();
        console.log("parkDanielDid: " + parkDanielDid);
        console.log("parkDanielVerKey: " + parkDanielVerKey);
        console.log("danielParkDid: " + danielParkDid);
        console.log("danielParkVerKey: " + danielParkVerKey);
        console.log("parkDanielConnectionResponse: " + parkDanielConnectionResponse);

        console.log("\n=============================================");
        console.log("=== Job-Certificate Proving ===\n");

        // 35. Park Create Park-Application Proof Request
        // 园区为员工创建入园申请表
        console.log("@Park -> Create Park-Application Proof Request");
        String parkApplicationProofRequest = Indy.getParkApplicationProofRequest(companyJobCertificateCredDefId);
        console.log("parkApplicationProofRequest: " + parkApplicationProofRequest);

        // 36. Park Authcrypt "Park-Application" Proof Request for Daniel
        // 园区加密入园申请表并发送个员工
        console.log("@Park -> Authcrypt Park-Application Proof Request for Daniel");
        byte[] authcryptedJobApplicationProofRequestRaw = Crypto.authCrypt(parkWallet, parkDanielVerKey, danielParkVerKey , parkApplicationProofRequest.getBytes(Charsets.UTF_8)).get();
        console.log("authcryptedJobApplicationProofRequestRaw: " + AnyExKt.toUTF8(authcryptedJobApplicationProofRequestRaw));

        console.log("@Park -> Sending authcrypted Job-Application Proof Request to Daniel ......");

        console.log("@Daniel -> ...... authcrypted Job-Application Proof Request received");

        // 37. "Daniel Authdecrypt Job-Application Proof Request from Park"
        // 员工解密入园申请表
        console.log("@Daniel -> Authdecrypt Job-Application Proof Request from Park");
        CryptoResults.AuthDecryptResult authdecryptedJobApplicationProofRequestResult = Crypto.authDecrypt(danielWallet, danielParkVerKey, authcryptedJobApplicationProofRequestRaw).get();
        String parkDanielVerKey2 = authdecryptedJobApplicationProofRequestResult.getVerkey();
        String authdecryptedJobApplicationProofRequestJson = AnyExKt.toUTF8(authdecryptedJobApplicationProofRequestResult.getDecryptedMessage());
        console.log("parkDanielVerKey2: " + parkDanielVerKey2);
        console.log("authdecryptedJobApplicationProofRequestJson: " + authdecryptedJobApplicationProofRequestJson);

        // 38. Daniel Get Credentials for Job-Application Proof Request
        // 员工填写入园申请表之前必须申请一个凭证
        console.log("@Daniel -> Get Credentials for Job-Application Proof Request");
        CredentialsSearchForProofReq jobApplicationProofReqSearchHandle = CredentialsSearchForProofReq.open(danielWallet, authdecryptedJobApplicationProofRequestJson,null).get();
        console.log("jobApplicationProofReqSearchHandle: " + jobApplicationProofReqSearchHandle);

        JSONArray credentialsForAttribute1 = new JSONArray(jobApplicationProofReqSearchHandle.fetchNextCredentials("attr1_referent", 100).get());
        Indy.CredAttrInfo credForAttr1 = AnyExKt.toObject(credentialsForAttribute1.getJSONObject(0).getJSONObject("cred_info").toString(), Indy.CredAttrInfo.class);
        console.log("credForAttr1: " + credForAttr1);

        JSONArray credentialsForAttribute2 = new JSONArray(jobApplicationProofReqSearchHandle.fetchNextCredentials("attr2_referent", 100).get());
        Indy.CredAttrInfo credForAttr2 = AnyExKt.toObject(credentialsForAttribute2.getJSONObject(0).getJSONObject("cred_info").toString(), Indy.CredAttrInfo.class);
        console.log("credForAttr2: " + credForAttr2);

        JSONArray credentialsForAttribute3 = new JSONArray(jobApplicationProofReqSearchHandle.fetchNextCredentials("attr3_referent", 100).get());
        assertEquals(0, credentialsForAttribute3.length());
        // Indy.CredAttrInfo credForAttr3 = AnyExKt.toObject(credentialsForAttribute3.getJSONObject(0).getJSONObject("cred_info").toString(), Indy.CredAttrInfo.class);
        // console.log("credForAttr3: " + credForAttr3);

        jobApplicationProofReqSearchHandle.close();

        List<Indy.CredAttrInfo> credsForJobApplicationProof = new ArrayList<>();
        credsForJobApplicationProof.add(credForAttr1);
        credsForJobApplicationProof.add(credForAttr2);
        console.log("credsForJobApplicationProof: " + credsForJobApplicationProof);

        // 39. Daniel Prover Get Entities (Schemas and Credential Definitions) from Ledger
        // 员工使用刚刚申请的凭证从链上获取 Schemas、Definitions 等信息
        console.log("@Daniel -> Prover Get Entities (Schemas and Credential Definitions) from Ledger");
        Indy.ProverGetEntitiesFromLedgerResult proverGetEntitiesFromLedgerResult = Indy.proverGetEntitiesFromLedger(pool, danielParkDid, credsForJobApplicationProof, "daniel");
        String proverSchemas = proverGetEntitiesFromLedgerResult.getSchemas();
        String proverCredDefs = proverGetEntitiesFromLedgerResult.getCredDefs();
        String proverRevStates = proverGetEntitiesFromLedgerResult.getRevStates();
        console.log("proverSchemas: " + proverSchemas);
        console.log("proverCredDefs: " + proverCredDefs);
        console.log("proverRevStates: " + proverRevStates);

        // 40. Daniel Create Park-Application Proof
        // 员工填写入园申请表
        console.log("@Daniel -> Create Park-Application Proof");
        String parkApplicationRequestedCreds = Indy.getParkApplicationRequestedCreds(credForAttr1.getReferent(), credForAttr2.getReferent());
        console.log("parkApplicationRequestedCreds: " + parkApplicationRequestedCreds);
        String parkApplicationProof = proverCreateProof(danielWallet, authdecryptedJobApplicationProofRequestJson, parkApplicationRequestedCreds, danielMasterSecretId, proverSchemas, proverCredDefs, proverRevStates).get();
        console.log("parkApplicationProof: " + parkApplicationProof);

        // 41. Daniel Authcrypt "Park-Application" Proof for Park
        // 员工加密入园申请表并发送给园区
        console.log("@Daniel -> Authcrypt Park-Application Proof for Park");
        byte[] authcryptedParkApplicationProofRaw = Crypto.authCrypt(danielWallet, danielParkVerKey, parkDanielVerKey2, parkApplicationProof.getBytes(Charsets.UTF_8)).get();
        console.log("authcryptedParkApplicationProofRaw: " + AnyExKt.toUTF8(authcryptedParkApplicationProofRaw));

        console.log("@Daniel -> Sending authcrypted Park-Application Proof for Park ......");

        console.log("@Park -> ...... authcrypted Park-Application Proof received");

        // 42. Park Authdecrypt Park-Application Proof from Daniel
        // 园区解密员工发过来的入园申请表
        console.log("@Park -> Authdecrypt Park-Application Proof from Daniel");
        CryptoResults.AuthDecryptResult authdecryptedParkApplicationProofResult = Crypto.authDecrypt(parkWallet, parkDanielVerKey, authcryptedParkApplicationProofRaw).get();
        String danielParkVerKey2 = authdecryptedParkApplicationProofResult.getVerkey();
        String authdecryptedParkApplicationProofJson = AnyExKt.toUTF8(authdecryptedParkApplicationProofResult.getDecryptedMessage());
        console.log("danielParkVerKey2: " + danielParkVerKey2);
        console.log("authdecryptedParkApplicationProofJson: " + authdecryptedParkApplicationProofJson);

        // 43. Park Verifier Get Entities (Schemas and Credential Definitions) from Ledger
        // 园区使用入园申请表上查询员工的具体信息
        console.log("@Park -> Verifier Get Entities (Schemas and Credential Definitions) from Ledger");
        JSONObject proof = new JSONObject(authdecryptedParkApplicationProofJson);
        List<Indy.CredAttrInfo> authdecryptedParkApplicationProofJsonList = AnyExKt.toObject(
            proof.getJSONArray("identifiers").toString(), new TypeToken<List<Indy.CredAttrInfo>>() {}.getType());
        Indy.VerifierGetEntitiesFromLedgerResult verifierGetEntitiesFromLedgerResult = Indy.verifierGetEntitiesFromLedger(pool, parkDanielDid, authdecryptedParkApplicationProofJsonList,"Park");
        String verifierSchemas = verifierGetEntitiesFromLedgerResult.getSchemas();
        String verifierCredDefs = verifierGetEntitiesFromLedgerResult.getCredDefs();
        String verifierRevRegs = verifierGetEntitiesFromLedgerResult.getRevRegs();
        String verifierRevRegDefs = verifierGetEntitiesFromLedgerResult.getRevRegDefs();
        console.log("verifierSchemas: " + verifierSchemas);
        console.log("verifierCredDefs: " + verifierCredDefs);
        console.log("verifierRevRegs: " + verifierRevRegs);
        console.log("verifierRevRegDefs: " + verifierRevRegDefs);

        // 44. Park Verify Park-Application Proof from Daniel
        // 园区验证员工信息
        console.log("@Park -> Verify Park-Application Proof from Daniel");
        assertEquals("Daniel", proof.getJSONObject("requested_proof").getJSONObject("revealed_attrs").getJSONObject("attr1_referent").getString("raw"));
        assertEquals("Yang", proof.getJSONObject("requested_proof").getJSONObject("revealed_attrs").getJSONObject("attr2_referent").getString("raw"));
        assertEquals("18618386178",proof.getJSONObject("requested_proof").getJSONObject("self_attested_attrs").getString("attr3_referent"));
        // assertTrue(verifierVerifyProof(parkApplicationProofRequest, authdecryptedParkApplicationProofJson, verifierSchemas, verifierCredDefs, verifierCredDefs, verifierRevRegs).get());


        // 45. Clean up
        console.log("\n=============================================");
        console.log("=== Cleanup ===\n");

        console.log("@Steward -> Close and Delete Wallet");
        stewardWallet.close();
        Wallet.deleteWallet(stewardWalletConfig, stewardWalletCredentials);

        console.log("@Park -> Close and Delete Wallet");
        parkWallet.close();
        Wallet.deleteWallet(parkWalletConfig, parkWalletCredentials);

        console.log("@Company -> Close and Delete Wallet");
        companyWallet.close();
        Wallet.deleteWallet(companyWalletConfig, companyWalletCredentials);

        console.log("@Daniel -> Close and Delete Wallet");
        danielWallet.close();
        Wallet.deleteWallet(danielWalletConfig, danielWalletCredentials);

        console.log("Close and Delete Pool");
        pool.close();
        Pool.deletePoolLedgerConfig(poolName).get();
    }


}
