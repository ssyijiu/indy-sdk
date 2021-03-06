"use strict"

const indy = require('indy-sdk')
const util = require('./util')
const assert = require('assert')

const poolEnv = process.argv[2] ? process.argv[2] : 'default'
if (poolEnv !== 'default' && poolEnv !== 'remote') {
    throw Error(`'${poolEnv}' is not supported, try 'default' or 'remote'.`)
}

const stewardSeed = poolEnv === 'remote' ? '19e2ea3730c3d62f36a095a44d343c7f5d81e0168c7f987b3d70a37c516bb45d' : '000000000000000000000000Steward1'

run(poolEnv)

async function run(poolEnv) {
    let poolName = 'pool1'
    let poolGenesisTxnPath = await util.getPoolGenesisTxnPath(poolName, poolEnv)
    let poolConfig = {
        "genesis_txn": poolGenesisTxnPath
    }

    try {
        await indy.createPoolLedgerConfig(poolName, poolConfig)
    } catch (e) {
        if (e.message !== "PoolLedgerConfigAlreadyExistsError") {
            throw e
        }
    }

    await indy.setProtocolVersion(2)

    let poolHandle = await indy.openPoolLedger(poolName)

    console.log("\n=============================================")
    console.log("=== Steward Setup ===\n")

    console.log('@Steward -> Create Wallet')
    let stewardWalletConfig = {'id': 'stewardWalletName'}
    let stewardWalletCredentials = {'key': 'steward_key'}
    let stewardWallet = await createAndOpenWallet(stewardWalletConfig, stewardWalletCredentials)

    console.log('@Steward -> Create DID')
    let stewardDidInfo = {
        'seed': stewardSeed
    }
    let [stewardDid, stewardVerKey] = await indy.createAndStoreMyDid(stewardWallet, stewardDidInfo)
    console.log({
        'stewardDid': stewardDid,
        'stewardVerKey': stewardVerKey
    })

    console.log("\n=============================================")
    console.log("=== Daniel Setup ===\n")

    console.log('@Daniel -> Create Wallet')
    let danielWalletConfig = {'id': 'danielWallet'}
    let danielWalletCredentials = {'key': 'daniel_key'}
    let danielWallet = await createAndOpenWallet(danielWalletConfig, danielWalletCredentials)

    console.log('@Daniel -> Create Master Scecret')
    let danielMasterSecretId = await indy.proverCreateMasterSecret(danielWallet, null)
    console.log({
        danielMasterSecretId: danielMasterSecretId
    })

    console.log("\n=============================================")
    console.log("=== Steward-Park Onboarding & Park GetVerinym ===\n")
    
    console.log('@Park -> Create Wallet')
    let parkWalletConfig = {'id': 'parkWallet'}
    let parkWalletCredentials = {'key': 'park_key'}
    let parkWallet = await createAndOpenWallet(parkWalletConfig, parkWalletCredentials)

    let [stewardParkDid, stewardParkVerKey, parkStewardDid, parkStewardVerKey] = await onboarding(poolHandle, 'Steward', stewardWallet, stewardDid, 'Park', parkWallet)
    console.log({
        stewardParkDid: stewardParkDid,
        stewardParkVerKey: stewardParkVerKey,
        parkStewardDid: parkStewardDid,
        parkStewardVerKey: parkStewardVerKey
    })

    let parkDid = await getVerinym(poolHandle, 'Steward', stewardWallet, stewardDid, stewardParkVerKey, 'Park', parkWallet, parkStewardDid, parkStewardVerKey, 'TRUST_ANCHOR')
    console.log({
        parkDid: parkDid
    })

    console.log("\n=============================================")
    console.log("=== Steward-Company Onboarding & Company GetVerinym ===\n")

    console.log('@Company -> Create Wallet')
    let companyWalletConfig = {'id': 'companyWallet'}
    let companyWalletCredentials = {'key': 'company_key'}
    let companyWallet = await createAndOpenWallet(companyWalletConfig, companyWalletCredentials)

    let [stewardCompanyDid, stewardCompanyVerKey, companyStewardDid, companyStewardVerkey] = await onboarding(poolHandle, 'Steward', stewardWallet, stewardDid, 'Company', companyWallet)
    console.log({
        stewardCompanyDid: stewardCompanyDid,
        stewardCompanyVerKey: stewardCompanyVerKey,
        companyStewardDid: companyStewardDid,
        companyStewardVerkey: companyStewardVerkey
    })

    let companyDid = await getVerinym(poolHandle, 'Steward', stewardWallet, stewardDid, stewardCompanyVerKey, 'Company', companyWallet, companyStewardDid, companyStewardVerkey, 'TRUST_ANCHOR')
    console.log({
        companyDid: companyDid
    })

    console.log("\n=============================================")
    console.log("=== Credential Schemas Setup ===\n")

    console.log('@Steward -> Create "Job-Certificate" Schema')
    let [jobCertificateSchemaId, jobCertificateSchema] = await indy.issuerCreateSchema(stewardDid, 'Job-Certificate', '0.1', ['first_name', 'last_name', 'salary', 'status', 'experience'])
    console.log({
        jobCertificateSchemaId: jobCertificateSchemaId,
        jobCertificateSchema: jobCertificateSchema
    })

    console.log('@Steward -> Send "Job-Certificate" Schema to Ledger')
    await sendSchema(poolHandle, stewardWallet, stewardDid, jobCertificateSchema)

    console.log('@Steward -> Create "Park-Certificate" Schema')
    let [parkCertificateSchemaId, parkCertificateSchema] = await indy.issuerCreateSchema(stewardDid, 'Park-Certificate', '0.1', ['first_name', 'last_name', 'level'])
    console.log({
        parkCertificateSchemaId: parkCertificateSchemaId,
        parkCertificateSchema, parkCertificateSchema
    })

    console.log('@Steward -> Send "Park-Certificate" Schema to Ledger')
    await sendSchema(poolHandle, stewardWallet, stewardDid, parkCertificateSchema)

    console.log("\n=============================================")
    console.log("=== Company Credential Definition Setup ===\n")

    console.log('@Company -> Get "Job-Certificate" Schema from Ledger')
    let [theJobCertificateSchemaId, theJobCertificateSchema] = await getSchema(poolHandle, companyDid, jobCertificateSchemaId)
    console.log({
        theJobCertificateSchemaId: theJobCertificateSchemaId,
        theJobCertificateSchema: theJobCertificateSchema
    })

    console.log('@Company -> Create and store "Company Job-Certificate" Credential Definition')
    let [companyJobCertificateCredDefId, companyJobCertificateCredDef] = await indy.issuerCreateAndStoreCredentialDef(companyWallet, companyDid, theJobCertificateSchema, 'TAG1', 'CL', '{"support_revocation": false}')
    console.log({
        companyJobCertificateCredDefId: companyJobCertificateCredDefId,
        companyJobCertificateCredDef: companyJobCertificateCredDef
    })

    console.log('@Company -> Send "Company Job-Certificate" Credential Definition to Ledger')
    await sendCredDef(poolHandle, companyWallet, companyDid, companyJobCertificateCredDef)

    console.log("\n=============================================")
    console.log("=== Park Credential Definition Setup ===\n")

    console.log('@Park -> Get "Park-Certificate" Schema from Ledger')
    let [theParkCertificateSchemaId, theParkCertificateSchema] = await getSchema(poolHandle, parkDid, parkCertificateSchemaId)
    console.log({
        theParkCertificateSchemaId, theParkCertificateSchemaId,
        theParkCertificateSchema: theParkCertificateSchema
    })

    console.log('@Park -> Create and store "Park Park-Certificate" Credential Definition')
    let [parkParkCertificateCredDefId, parkParkCertificateCredDef] = await indy.issuerCreateAndStoreCredentialDef(parkWallet, parkDid, theParkCertificateSchema, 'TAG1', 'CL', '{"support_revocation": false}')
    console.log({
        parkParkCertificateCredDefId: parkParkCertificateCredDefId,
        parkParkCertificateCredDef: parkParkCertificateCredDef
    })

    console.log('@Park -> Send "Park Park-Certificate" Credential Definition to Ledger')
    await sendCredDef(poolHandle, parkWallet, parkDid, parkParkCertificateCredDef)

    console.log("\n=============================================")
    console.log("=== Company-Daniel Onboarding ===\n")

    let [companyDanielDid, companyDanielVerKey, danielCompanyDid, danielCompanyVerkey, companyDanielConnectionResponse] = await onboarding(poolHandle, 'Company', companyWallet, companyDid, 'Daniel', danielWallet)
    console.log({
        companyDanielDid: companyDanielDid,
        companyDanielVerKey: companyDanielVerKey,
        danielCompanyDid: danielCompanyDid,
        danielCompanyVerkey: danielCompanyVerkey,
        companyDanielConnectionResponse: companyDanielConnectionResponse
    })

    console.log("\n=============================================")
    console.log("=== Company Sending Job-Certificate Credential Offer ===\n")

    console.log('@Company -> Create \"Job-Certificate\" Credential Offer for Daniel')
    let jobCertificateCredOffer = await indy.issuerCreateCredentialOffer(companyWallet, companyJobCertificateCredDefId)
    console.log({
        jobCertificateCredOffer: jobCertificateCredOffer
    })

    console.log('@Company -> Authcrypt "Job-Certificate" Credential Offer for Daniel')
    let authcryptedJobCertificateCredOfferRaw = await indy.cryptoAuthCrypt(companyWallet, companyDanielVerKey, companyDanielConnectionResponse.verkey, Buffer.from(JSON.stringify(jobCertificateCredOffer), 'utf8'))
    console.log({
        authcryptedJobCertificateCredOfferRaw: authcryptedJobCertificateCredOfferRaw
    })

    console.log('@Company -> Sending authcrypted "Job-Certificate" Credential Offer to Daniel ......')

    console.log('@Daniel -> ...... authcrypted "Job-Certificate" Credential Offer received')

    console.log('@Daniel -> Authdecrypt "Job-Certificate" Credential Offer from Company')
    let [companyDanielVerKey2, authdecryptedJobCertificateCredOfferJson, authdecryptedJobCertificateCredOffer] = await authDecrypt(danielWallet, danielCompanyVerkey, authcryptedJobCertificateCredOfferRaw)
    console.log({
        companyDanielVerKey2: companyDanielVerKey2,
        authdecryptedJobCertificateCredOfferJson: authdecryptedJobCertificateCredOfferJson, 
        authdecryptedJobCertificateCredOffer: authdecryptedJobCertificateCredOffer
    })

    console.log("\n=============================================")
    console.log("=== Daniel Getting Job-Certificate Credential ===\n")

    console.log('@Daniel -> Get "Company Job-Certificate" Credential Definition from Ledger')
    let [theCompanyJobCertificateCredDefId, theCompanyJobCertificateCredDef] = await getCredDef(poolHandle, danielCompanyDid, authdecryptedJobCertificateCredOffer.cred_def_id)
    console.log({
        theCompanyJobCertificateCredDefId: theCompanyJobCertificateCredDefId,
        theCompanyJobCertificateCredDef: theCompanyJobCertificateCredDef
    })

    console.log('@Daniel -> Create "Job-Certificate" Credential Request for Company')
    let [jobCertificateCredRequest, jobCertificateCredRequestMetadata] = await indy.proverCreateCredentialReq(danielWallet, danielCompanyDid, authdecryptedJobCertificateCredOfferJson, theCompanyJobCertificateCredDef, danielMasterSecretId)
    console.log({
        jobCertificateCredRequest: jobCertificateCredRequest,
        jobCertificateCredRequestMetadata: jobCertificateCredRequestMetadata
    })

    console.log('@Daniel -> Authcrypt "Job-Certificate" Credential Request for Company')
    let authcryptedJobCertificateCredRequestRaw = await indy.cryptoAuthCrypt(danielWallet, danielCompanyVerkey, companyDanielVerKey2, Buffer.from(JSON.stringify(jobCertificateCredRequest), 'utf8'))
    console.log({
        authcryptedJobCertificateCredRequestRaw: authcryptedJobCertificateCredRequestRaw
    })

    console.log('@Daniel -> Sending authcrypted "Job-Certificate" Credential Request to Company ......')

    console.log('@Company -> ...... authcrypted "Job-Certificate" Credential Request received')

    console.log('@Company -> Authdecrypt "Job-Certificate" Credential Request from Daniel')
    let [danielCompanyVerkey2, authdecryptedJobCertificateCredRequestJson, authdecryptedJobCertificateCredRequest] = await authDecrypt(companyWallet, companyDanielVerKey, authcryptedJobCertificateCredRequestRaw)
    console.log({
        danielCompanyVerkey2: danielCompanyVerkey2,
        authdecryptedJobCertificateCredRequestJson: authdecryptedJobCertificateCredRequestJson,
        authdecryptedJobCertificateCredRequest: authdecryptedJobCertificateCredRequest
    })

    console.log('@Company -> Create "Job-Certificate" Credential for Daniel')
    let jobCertificateCredValues = {
        first_name: {
            raw: 'Daniel',
            encoded: '245712572474217942457235975012103335'
        },
        last_name: {
            raw: 'Yang',
            encoded: '312643218496194691632153761283356127'
        },
        salary: {
            raw: '2400',
            encoded: '2400'
        },
        status: {
            raw: 'Permanent',
            encoded: '2143135425425143112321314321'
        },
        experience: {
            raw: '10',
            encoded: '10'
        }
    }
    console.log({
        jobCertificateCredValues: jobCertificateCredValues
    })
    let [jobCertificateCred, jobCertificateCredRevocId, jobCertificateCredRevocRegDelta] = await indy.issuerCreateCredential(companyWallet, jobCertificateCredOffer, authdecryptedJobCertificateCredRequestJson, jobCertificateCredValues, null, -1)
    console.log({
        jobCertificateCred: jobCertificateCred,
        jobCertificateCredRevocId: jobCertificateCredRevocId,
        jobCertificateCredRevocRegDelta: jobCertificateCredRevocRegDelta
    })

    console.log('@Company -> Authcrypt "Job-Certificate" Credential for Daniel')
    let authcryptedJobCertificateCredRaw = await indy.cryptoAuthCrypt(companyWallet, companyDanielVerKey, companyDanielConnectionResponse.verkey, Buffer.from(JSON.stringify(jobCertificateCred), 'utf8'))
    console.log({
        authcryptedJobCertificateCredRaw: authcryptedJobCertificateCredRaw
    })

    console.log('@Company -> Sending authcrypted "Job-Certificate" Credential to Daniel ......')

    console.log('@Daniel -> ...... authcrypted "Job-Certificate" Credential received')

    console.log('@Daniel -> Authdecrypt "Job-Certificate" Credential from Company')
    let [companyDanielVerKey3, authdecryptedJobCertificateCredJson, authdecryptedJobCertificateCred] = await authDecrypt(danielWallet, danielCompanyVerkey, authcryptedJobCertificateCredRaw)
    console.log({
        companyDanielVerKey3: companyDanielVerKey3,
        authdecryptedJobCertificateCredJson: authdecryptedJobCertificateCredJson,
        authdecryptedJobCertificateCred: authdecryptedJobCertificateCred
    })

    console.log('@Daniel -> Store "Job-Certificate" Credential from Company')
    let jobCertificateCredId = await indy.proverStoreCredential(danielWallet, null, jobCertificateCredRequestMetadata, authdecryptedJobCertificateCredJson, theCompanyJobCertificateCredDef, null)
    console.log({
        jobCertificateCredId: jobCertificateCredId
    })

    console.log("\n=============================================")
    console.log("=== Park-Daniel Onboarding ===\n")

    let [parkDanielDid, parkDanielVerKey, danielParkDid, danielParkVerKey, parkDanielConnectionResponse] = await onboarding(poolHandle, 'Park', parkWallet, parkDid, 'Daniel', danielWallet)
    console.log({
        parkDanielDid: parkDanielDid,
        parkDanielVerKey: parkDanielVerKey,
        danielParkDid: danielParkDid,
        danielParkVerKey: danielParkVerKey,
        parkDanielConnectionResponse: parkDanielConnectionResponse
    })

    console.log("\n=============================================")
    console.log("=== Job-Certificate Proving ===\n")

    console.log('@Park -> Create "Park-Application" Proof Request')
    let parkApplicationProofRequest = {
        nonce: '1432422343242122312411212',
        name: 'Park-Application',
        version: '0.1',
        requested_attributes: {
            attr1_referent: {
                name: 'first_name',
                restrictions: [{
                    cred_def_id: companyJobCertificateCredDefId // TODO: How to know?
                }]
            },
            attr2_referent: {
                name: 'last_name',
                restrictions: [{
                    cred_def_id: companyJobCertificateCredDefId
                }]
            },
            attr3_referent: {
                name: 'mobile'
            },
        },
        requested_predicates: {
        }
    }
    console.log({
        parkApplicationProofRequest: parkApplicationProofRequest
    })

    console.log('@Park -> Authcrypt "Park-Application" Proof Request for Daniel')
    let authcryptedJobApplicationProofRequestRaw = await indy.cryptoAuthCrypt(parkWallet, parkDanielVerKey, danielParkVerKey, Buffer.from(JSON.stringify(parkApplicationProofRequest), 'utf8'))
    console.log({
        authcryptedJobApplicationProofRequestRaw: authcryptedJobApplicationProofRequestRaw
    })

    console.log('@Park -> Sending authcrypted "Job-Application" Proof Request to Daniel ......')

    console.log('@Daniel -> ...... authcrypted "Job-Application" Proof Request received')

    console.log('@Daniel -> Authdecrypt "Job-Application" Proof Request from Park')
    let [parkDanielVerKey2, authdecryptedJobApplicationProofRequestJson, authdecryptedJobApplicationProofRequest] = await authDecrypt(danielWallet, danielParkVerKey, authcryptedJobApplicationProofRequestRaw)
    console.log({
        parkDanielVerKey2: parkDanielVerKey2,
        authdecryptedJobApplicationProofRequestJson: authdecryptedJobApplicationProofRequestJson,
        authdecryptedJobApplicationProofRequest: authdecryptedJobApplicationProofRequest
    })

    console.log('@Daniel -> Get Credentials for "Job-Application" Proof Request')
    let jobApplicationProofReqSearchHandle = await indy.proverSearchCredentialsForProofReq(danielWallet, authdecryptedJobApplicationProofRequestJson, null)
    console.log({
        jobApplicationProofReqSearchHandle: jobApplicationProofReqSearchHandle
    })

    let credentials = await indy.proverFetchCredentialsForProofReq(jobApplicationProofReqSearchHandle, 'attr1_referent', 100)
    let credForAttr1 = credentials[0].cred_info
    console.log({
        credentials: credentials
    })

    await indy.proverFetchCredentialsForProofReq(jobApplicationProofReqSearchHandle, 'attr2_referent', 100)
    let credForAttr2 = credentials[0].cred_info

    await indy.proverFetchCredentialsForProofReq(jobApplicationProofReqSearchHandle, 'attr3_referent', 100)
    let credForAttr3 = credentials[0].cred_info

    await indy.proverCloseCredentialsSearchForProofReq(jobApplicationProofReqSearchHandle)

    console.log({
        credForAttr1: credForAttr1,
        credForAttr2: credForAttr2,
        credForAttr3: credForAttr3
    })

    let credsForJobApplicationProof = {}
    credsForJobApplicationProof[`${credForAttr1.referent}`] = credForAttr1
    credsForJobApplicationProof[`${credForAttr2.referent}`] = credForAttr2
    credsForJobApplicationProof[`${credForAttr3.referent}`] = credForAttr3
    console.log({
        credsForJobApplicationProof: credsForJobApplicationProof
    })

    console.log('@Daniel -> Prover Get Entities (Schemas and Credential Definitions) from Ledger')
    let [proverSchemas, proverCredDefs, proverRevStates] = await proverGetEntitiesFromLedger(poolHandle, danielParkDid, credsForJobApplicationProof, 'Daniel')
    console.log({
        proverSchemas: proverSchemas,
        proverCredDefs: proverCredDefs,
        proverRevStates: proverRevStates
    })

    console.log('@Daniel -> Create "Park-Application" Proof')
    let parkApplicationRequestedCreds = {
        self_attested_attributes: {
            attr3_referent: '18618386178'
        },
        requested_attributes: {
            attr1_referent: {
                cred_id: credForAttr1.referent,
                revealed: true
            },
            attr2_referent: {
                cred_id: credForAttr2.referent,
                revealed: true
            }
        },
        requested_predicates: {
        }
    }
    console.log({
        parkApplicationRequestedCreds: parkApplicationRequestedCreds
    })
    let parkApplicationProof = await indy.proverCreateProof(danielWallet, authdecryptedJobApplicationProofRequestJson, parkApplicationRequestedCreds, danielMasterSecretId, proverSchemas, proverCredDefs, proverRevStates)
    console.log({
        parkApplicationProof: parkApplicationProof
    })

    console.log('@Daniel -> Authcrypt "Park-Application" Proof for Park')
    let authcryptedParkApplicationProofRaw = await indy.cryptoAuthCrypt(danielWallet, danielParkVerKey, parkDanielVerKey2, Buffer.from(JSON.stringify(parkApplicationProof), 'utf8'))
    console.log({
        authcryptedParkApplicationProofRaw: authcryptedParkApplicationProofRaw
    })

    console.log('@Daniel -> Sending authcrypted "Park-Application" Proof for Park ......')

    console.log('@Park -> ...... authcrypted "Park-Application" Proof received')

    console.log('@Park -> Authdecrypt "Park-Application" Proof from Daniel')
    let [danielParkVerKey2, authdecryptedParkApplicationProofJson, authdecryptedParkApplicationProof] = await authDecrypt(parkWallet, parkDanielVerKey, authcryptedParkApplicationProofRaw)
    console.log({
        danielParkVerKey2: danielParkVerKey2,
        authdecryptedParkApplicationProofJson: authdecryptedParkApplicationProofJson,
        authdecryptedParkApplicationProof: authdecryptedParkApplicationProof,
        'authdecryptedParkApplicationProof.requested_proof.revealed_attrs': authdecryptedParkApplicationProof.requested_proof.revealed_attrs,
        'authdecryptedParkApplicationProof.requested_proof.self_attested_attrs': authdecryptedParkApplicationProof.requested_proof.self_attested_attrs
    })

    console.log('@Park -> Verifier Get Entities (Schemas and Credential Definitions) from Ledger')
    let [verifierSchemas, verifierCredDefs, verifierRevRegs, verifierRevRegDefs] = await verifierGetEntitiesFromLedger(poolHandle, parkDanielDid, authdecryptedParkApplicationProof.identifiers, 'Park')
    console.log({
        verifierSchemas: verifierSchemas,
        verifierCredDefs: verifierCredDefs,
        verifierRevRegs: verifierRevRegs,
        verifierRevRegDefs: verifierRevRegDefs
    })

    console.log('@Park -> Verify "Park-Application" Proof from Daniel')
    assert('Daniel' === authdecryptedParkApplicationProof.requested_proof.revealed_attrs.attr1_referent.raw)
    assert('Yang' === authdecryptedParkApplicationProof.requested_proof.revealed_attrs.attr2_referent.raw)
    assert('18618386178' === authdecryptedParkApplicationProof.requested_proof.self_attested_attrs.attr3_referent)
    assert(await indy.verifierVerifyProof(parkApplicationProofRequest, authdecryptedParkApplicationProof, verifierSchemas, verifierCredDefs, verifierRevRegDefs, verifierRevRegs))






    console.log("\n=============================================")
    console.log("=== Cleanup ===\n")

    console.log('@Steward -> Close and Delete Wallet')
    await indy.closeWallet(stewardWallet)
    await indy.deleteWallet(stewardWalletConfig, stewardWalletCredentials)

    console.log('@Park -> Close and Delete Wallet')
    await indy.closeWallet(parkWallet)
    await indy.deleteWallet(parkWalletConfig, parkWalletCredentials)

    console.log('@Company -> Close and Delete Wallet')
    await indy.closeWallet(companyWallet)
    await indy.deleteWallet(companyWalletConfig, companyWalletCredentials)

    console.log('@Daniel -> Close and Delete Wallet')
    await indy.closeWallet(danielWallet)
    await indy.deleteWallet(danielWalletConfig, danielWalletCredentials)

    console.log('Close and Delete Pool')
    await indy.closePoolLedger(poolHandle)
    await indy.deletePoolLedgerConfig(poolName)
}

async function createAndOpenWallet(config, credentials) {
    try {
        await indy.createWallet(config, credentials)
    } catch (e) {
        if (e.message !== 'WalletAlreadyExistsError') {
            throw e
        }
    }

    return await indy.openWallet(config, credentials)
}

async function onboarding(poolHandle, from, fromWallet, fromDid, to, toWallet) {
    console.log("\n*** onboarding ***\n")

    console.log(`@${from} -> Create DID \"${from} ${to}\"`)
    let [fromToDid, fromToVerKey] = await indy.createAndStoreMyDid(fromWallet, {})
    console.log({
        fromToDid: fromToDid,
        fromToVerKey: fromToVerKey
    })

    console.log(`@${from} -> Send Nym to Ledger for \"${from} ${to}\" DID`)
    await sendNym(poolHandle, fromWallet, fromDid, fromToDid, fromToVerKey, null)

    console.log(`@${from} -> Send connection request to ${to} with \"${from} ${to}\" DID and nonce`)
    let connectionRequest = {
        did: fromToDid,
        nonce: 123456
    }
    console.log({
        connectionRequest: connectionRequest
    })

    console.log(`@${from} -> Sending request ......`)

    console.log(`@${to} -> ...... request received`)

    console.log(`@${to} -> Create DID \"${to} ${from}\"`)
    let [toFromDid, toFromVerKey] = await indy.createAndStoreMyDid(toWallet, {})
    console.log({
        toFromDid: toFromDid,
        toFromVerKey: toFromVerKey
    })

    console.log(`@${to} -> Get VerKey for Did from \"${from}\"'s connection request`)
    let fromToVerKey2 = await indy.keyForDid(poolHandle, toWallet, connectionRequest.did)
    console.log({
        fromToVerKey2: fromToVerKey2
    })

    console.log(`@${to} -> Anoncrypt connection response for \"${from}\" with \"${to} ${from}\" DID, verkey and nonce`)
    let connectionResponse = JSON.stringify({
        did: toFromDid,
        verkey: toFromVerKey,
        nonce: connectionRequest.nonce
    })
    let anoncryptedConnectionResponse = await indy.cryptoAnonCrypt(fromToVerKey2, Buffer.from(connectionResponse, 'utf8'))
    console.log({
        connectionResponse: connectionResponse,
        anoncryptedConnectionResponse: anoncryptedConnectionResponse
    })

    console.log(`@${to} -> Sending anoncrypted connection response to \"${from}\" ......`)

    console.log(`@${from} -> ...... response received`)

    console.log(`@${from} -> Anondecrypt connection response from \"${to}\"`)
    let decryptedConnectionResponse = JSON.parse(Buffer.from(await indy.cryptoAnonDecrypt(fromWallet, fromToVerKey, anoncryptedConnectionResponse)))
    console.log({
        decryptedConnectionResponse: decryptedConnectionResponse
    })

    console.log(`@${from} -> Authenticates \"${to}\" by comparision of none`)
    if (connectionRequest.nonce !== decryptedConnectionResponse.nonce) {
        throw Error('nonces do not match')
    }

    console.log(`@${from} -> Send Nym to Ledger for \"${to} ${from}\" DID`)
    await sendNym(poolHandle, fromWallet, fromDid, decryptedConnectionResponse.did, decryptedConnectionResponse.verkey)

    return [fromToDid, fromToVerKey, toFromDid, toFromVerKey, decryptedConnectionResponse]
}

async function getVerinym(poolHandle, from, fromWallet, fromDid, fromToVerKey, to, toWallet, toFromDid, toFromVerKey, role) {
    console.log("\n*** getVerinym ***\n")
    
    console.log(`@${to} -> Create DID`)
    let [toDid, toVerKey] = await indy.createAndStoreMyDid(toWallet, {})
    console.log({
        toDid: toDid,
        toVerKey: toVerKey
    })

    console.log(`@${to} -> Authcrypt \"${to}\" DID info for \"${from}\"`)
    let didInfo = JSON.stringify({
        did: toDid,
        verkey: toVerKey
    })
    let authcryptedDidInfoRaw = await indy.cryptoAuthCrypt(toWallet, toFromVerKey, fromToVerKey, Buffer.from(didInfo, 'utf8'))
    console.log({
        didInfo: didInfo,
        authcryptedDidInfoRaw: authcryptedDidInfoRaw
    })

    console.log(`@${to} -> Sending authcrypted \"${to}\" DID info to \"${from}\" ......`)

    console.log(`@${from} -> ...... DID info received`)

    console.log(`@${from} -> Authdecrypt \"${to}\" DID info from \"${to}\"`)
    let [senderVerKey, authdecryptedDidInfoRaw] = await indy.cryptoAuthDecrypt(fromWallet, fromToVerKey, Buffer.from(authcryptedDidInfoRaw))
    let authdecryptedDidInfo = JSON.parse(Buffer.from(authdecryptedDidInfoRaw))
    console.log({
        senderVerKey: senderVerKey,
        authdecryptedDidInfoRaw: authdecryptedDidInfoRaw,
        authdecryptedDidInfo: authdecryptedDidInfo
    })

    console.log(`@${from} -> Authenticates \"${to}\" by comparison of Verkeys`)
    let retrievedVerKey = await indy.keyForDid(poolHandle, fromWallet, toFromDid)
    console.log({
        retrievedVerKey: retrievedVerKey
    })
    if (senderVerKey !== retrievedVerKey) {
        throw Error('Verkey is not the same')
    }

    console.log(`@${from} -> Send Nym to Ledger for \"${to}\" DID with ${role} Role`)
    await sendNym(poolHandle, fromWallet, fromDid, authdecryptedDidInfo.did, authdecryptedDidInfo.verkey, role)

    return toDid
}

async function sendNym(poolHandle, walletHandle, submitterDid, targetDid, targetVerKey, role) {
    let nymRequest = await indy.buildNymRequest(submitterDid, targetDid, targetVerKey, null, role)
    let requestResult = await indy.signAndSubmitRequest(poolHandle, walletHandle, submitterDid, nymRequest)
    console.log({
        requestResult: requestResult
    })

    return requestResult
}

async function sendSchema(poolHandle, walletHandle, submitterDid, schema) {
    let schemaRequest = await indy.buildSchemaRequest(submitterDid, schema)
    let requestResult = await indy.signAndSubmitRequest(poolHandle, walletHandle, submitterDid, schemaRequest)
    console.log({
        requestResult: requestResult
    })

    return requestResult
}

async function getSchema(poolHandle, submitterDid, schemaId) {
    let request = await indy.buildGetSchemaRequest(submitterDid, schemaId)
    let requestResult = await indy.submitRequest(poolHandle, request)
    console.log({
        requestResult: requestResult
    })

    return await indy.parseGetSchemaResponse(requestResult)
}

async function getCredDef(poolHandle, submitterDid, credDefId) {
    let request = await indy.buildGetCredDefRequest(submitterDid, credDefId)
    let requestResult = await indy.submitRequest(poolHandle, request)
    console.log({
        requestResult: requestResult
    })

    return await indy.parseGetCredDefResponse(requestResult)
}

async function sendCredDef(poolHandle, walletHandle, submitterDid, credDef) {
    let request = await indy.buildCredDefRequest(submitterDid, credDef)
    let requestResult = await indy.signAndSubmitRequest(poolHandle, walletHandle, submitterDid, request)
    console.log({
        requestResult: requestResult
    })

    return requestResult
}

async function authDecrypt(walletHandle, recipientVerKey, encryptedMessageRaw) {
    let [senderVerKey, decryptedMessageRaw] = await indy.cryptoAuthDecrypt(walletHandle, recipientVerKey, encryptedMessageRaw)
    let decryptedMessage = JSON.parse(decryptedMessageRaw)
    let decryptedMessageJson = JSON.stringify(decryptedMessage)

    return [senderVerKey, decryptedMessageJson, decryptedMessage]
}

async function proverGetEntitiesFromLedger(poolHandle, submitterDid, credentials, actor) {
    console.log("\n*** proverGetEntitiesFromLedger ***\n")

    let schemas = {}
    let credDefs = {}
    let revStates = {}

    for (let referent of Object.keys(credentials)) {
        let credential = credentials[referent]
        console.log({
            credential: credential
        })

        console.log(`@${actor} -> Get Schema from Ledger`)
        let [schemaId, schema] = await getSchema(poolHandle, submitterDid, credential.schema_id)
        schemas[schemaId] = schema

        console.log(`@${actor} -> Get Credential Definition from Ledger`)
        let [credDefId, credDef] = await getCredDef(poolHandle, submitterDid, credential.cred_def_id)
        credDefs[credDefId] = credDef

        if (credential.rev_reg_seq_no) {
            // TODO: Create Revocation States
        }
    }

    return [schemas, credDefs, revStates]
}

async function verifierGetEntitiesFromLedger(poolHandle, submitterDid, credentials, actor) {
    console.log("\n*** verifierGetEntitiesFromLedger ***\n")

    let schemas = {}
    let credDefs = {}
    let revRegs = {}
    let revRegDefs = {}

    for (let referent of Object.keys(credentials)) {
        let credential = credentials[referent]
        console.log({
            credential: credential
        })

        console.log(`@${actor} -> Get Schema from Ledger`)
        let [schemaId, schema] = await getSchema(poolHandle, submitterDid, credential.schema_id)
        schemas[schemaId] = schema

        console.log(`@${actor} -> Get Credential Definition from Ldger`)
        let [credDefId, credDef] = await getCredDef(poolHandle, submitterDid, credential.cred_def_id)
        credDefs[credDefId] = credDef

        if (credential.rev_reg_seq_no) {
            // TODO: Get Revocation Definitions and Revocation Registries
        }
    }

    return [schemas, credDefs, revRegs, revRegDefs]
}
