# Indy Getting-Started

> Indy 是以 hyperledger 下的去中心话身份证明 sdk：https://github.com/hyperledger/indy-sdk
>
> Getting-Started：https://github.com/hyperledger/indy-sdk/blob/master/doc/getting-started/getting-started.md

## 关于 Alice

Alice 的母校 Faber College 正在颁发数字成绩单，她登录 Faber College 的网站去 **Get Transcript**。

但是下载成绩单需要需要一个 **self-sovereign identity**（自我主权身份，Indy 系统的核心)。

下面的故事中 Alice 使用 Indy app 来获取 **self-sovereign identity** 然后 **Get Transcript**。

## 基础设施准备

### Step1：获取 Faber College 、Acme Corp 和 Thrift Bank 提供的信任证书

一些概念

- Faber：学校名称
- Acme：公司名称
- Thrift：银行名称

- Ledger：账本 / 链，用来存储 Identity Records ( 身份记录 )。

- DID：Decentralized Identifier，可以直接理解为 Identity Records 的 ID，每个 Identity Record 都与一个唯一的 DID 关联， Ledger 就是用来存储 DID 的。

  DID 分为两种类型：

  - Verinym：法定身份
  - Pseudonym：昵称 ( 网络ID )

- Identity Owner：身份所有者，每个 Identity Owner 都可以拥有多个 DID。

- Verkey：DID 对应的公钥，用来证明该 DID 的 Identity Owner 是谁。

- NYM transaction：交易，通过 NYM transaction 可以添加一个已知的 DID 到 Ledger、设置或者修改 DID 的 Verkey、设置或者修改 Role 等，[总之对 Ledger 的 Request 都需要通过 NYM transaction 来完成](https://github.com/hyperledger/indy-node/blob/master/docs/requests.md)。
- NYM transaction 中的一些概念
  - Dest：target DID，Request 的目标 DID
  - Verkey：target DID 的公钥
  - Role：是谁在进行 Request，[不同的 Role 拥有的权限不同](https://docs.google.com/spreadsheets/d/1TWXF7NtBjSOaUIBeIH77SyZnawfo91cJ_ns4TR-wsq4/edit#gid=0)。
- **Trust Anchor**：Role 的一种，可信任用户，通常是已知的组织或者个人，能够帮助或者引导他人，具有在 Ledger 上面添加 DID 的权限，前提是 **Trust Anchor** 的 DID 要先添加到 Ledger。

**Faber College, Acme Corp and Thrift Bank** 都属于 Trust Anchor，他们将创建 Verinym 和 Pseudonym 来为 Alice 提供服务。

### Step2：连接 Indy 的矿池节点

```java
Pool.setProtocolVersion(PROTOCOL_VERSION).get();
String poolName = PoolUtils.createPoolLedgerConfig();
Pool pool = Pool.openPoolLedger(poolName, "{}").get();
```

### Step3：获取 Steward's Verinym 的所有权

Wallet：钱包，用来在客户端存储 DID。

在最开始启动 Ledger 后，上面是没有任何 DID 的，我们需要使用一个 seed (种子) 来创建第一个 DID，这个 DID 被称为 **Steward**。

### Step4：建立 Steward 与 Faber, Acme, Thrift and Government 的连接

#### Steward 与 **Faber College** 的连接过程 (**Onboarding**)

1. 事前 Steward 与 Faber College 双方同意建立连接。

2. Steward 创建一个 DID 用来和 Faber 交互

   ```js
   # Steward Agent
   (steward_faber_did, steward_faber_key) = 
       await did.create_and_store_my_did(steward_wallet, "{}")
   ```

3. Steward 通过 NYM Request 将 **它创建的用来和 Faber 交互的 DID** 保存在 Ledger

   ```js
   # Steward Agent
   nym_request = await ledger.build_nym_request(steward_did, steward_faber_did, steward_faber_key, None, role)
   
   await ledger.sign_and_submit_request(pool_handle, steward_wallet, steward_did, nym_request)
   
   // At this point, we have successfully written a new identity to the ledger
   ```

4. Steward 创建一个 connection request， connection reques 包括用来和 Faber 交互 的 DID 和一个 nonce

   ```javascript
   # Steward Agent
   connection_request = {
       'did': steward_faber_did, # Steward 创建，用来和 Faber 交互的 DID
       'nonce': 123456789        # 随机数
   }
   ```

5. Steward 发送 connection request 给 Faber ( 没有代码 )

6. Faber 接受 Steward 的 connection request ( 没有代码 )

7. Faber 创建一个 Wallet

   ```js
   # Faber Agent
   await wallet.create_wallet(config, credentials)
   faber_wallet = await wallet.open_wallet(config, credentials)
   ```

8. Faber 创建一个 DID 用来和 Steward 交互

   ```js
   # Faber Agent
   (faber_steward_did, faber_steward_key) = 
       await did.create_and_store_my_did(faber_wallet, "{}")
   ```

9. Faber 使用这个的 DID 创建一个 connection response  

   ```js
   # Faber Agent
   connection_response = json.dumps({
       'did': faber_steward_did,    # Faber 创建，用来和 Steward 交互的 DID
       'verkey': faber_steward_key, # Faber DID 的公钥
       'nonce': connection_request['nonce'] # Steward 发送过来的随机数
   })
   ```

10. Faber 使用 connection request 中的 DID 获取的 Verkey

    ```js
    # Faber Agent
    steward_faber_verkey = await did.key_for_did(pool_handle, faber_wallet, connection_request['did'])
    ```

11. Faber 使用获取到的 Verkey 来加密 connection response  

    ```js
    # Faber Agent
    anoncrypted_connection_response = await crypto.anon_crypt(steward_faber_verkey, connection_response.encode('utf-8'))
    ```

12. Faber 将加密后的 connection response  发送给 Steward

13. Steward 解密收到的 connection response

    ```js
    # Steward Agent
    decrypted_connection_response = await crypto.anon_decrypt(steward_wallet, steward_faber_key, anoncrypted_connection_response)).decode("utf-8")
    ```

14. Steward 通过比较 Nonce 来验证收到的 connection response

    ```js
    # Steward Agent
    assert connection_request['nonce'] == decrypted_connection_response['nonce']
    ```

15. 验证通过后，Steward 将 Faber 的 DID 添加到 Ledger

    ```js
    # Steward Agent
    nym_request = await ledger.build_nym_request(steward_did, decrypted_connection_response['did'], decrypted_connection_response['verkey'], None, role)
    
    await ledger.sign_and_submit_request(pool_handle, steward_wallet, steward_did, nym_request)
    ```

现在，Faber 和 Steward 已经建立了一个安全的点对点连接。

注意：在建立连接时，不要使用同一个 DID 与不同身份的人或者组织建立连接。

Onboarding 这个过程得到了 4 个重要的数据：**steward_park_did、steward_park_verKey、park_steward_did、park_steward_verKey**

#### Getting Verinym (获取法定身份)

在与Steward 建立连接后，Faber 需要创建一个新的的 DID 作为自己的 Verinym 存储在 Ledger

1. Faber 创建一个新的 DID

   ```js
   # Faber Agent
   (faber_did, faber_key) = await did.create_and_store_my_did(faber_wallet, "{}")
   ```

2. Faber 将 DID 和 verkey 组合成消息

   ```js
   # Faber Agent
   faber_did_info_json = json.dumps({
       'did': faber_did,
       'verkey': faber_key
   })
   ```

3. Faber 使用在 Onboarding 过程中的 faber_steward_key 和 steward_faber_key 对消息进行加密

   ```js
   # Faber Agent
   authcrypted_faber_did_info_json = await crypto.auth_crypt(faber_wallet, faber_steward_key, steward_faber_key,faber_did_info_json.encode('utf-8'))
   ```

4. Faber 将加密后的消息发送给 Steward

5. Steward 解密消息

   ```js
   # Steward Agent    
   sender_verkey, authdecrypted_faber_did_info_json = await crypto.auth_decrypt(
       steward_handle, steward_faber_key, authcrypted_faber_did_info_json)
   
   faber_did_info = json.loads(authdecrypted_faber_did_info_json)
   ```

6. Steward 请求 Ledger 获取 Faber 的公钥

   ```js
   # Steward Agent    
   faber_steward_verkey = await did.key_for_did(pool_handle, from_wallet, faber_steward_did)
   ```

7. Steward 验证从 Ledger 获取的 DID 和 解密后得到的 DID 是否匹配

   ```js
   # Steward Agent    
   assert sender_verkey == faber_steward_verkey
   ```

8. Steward 将这个 DID 作为 Faber 的 Verinym 存储在 Ledger

   ```js
   # Steward Agent
   nym_request = await ledger.build_nym_request(steward_did, decrypted_faber_did_info_json['did'], decrypted_faber_did_info_json['verkey'], None, 'TRUST_ANCHOR')
   
   await ledger.sign_and_submit_request(pool_handle, steward_wallet, steward_did, nym_request)
   ```

现在，Faber 在 Ledger 上有了一个与其身份相关的 DID。

### Step5: Credential Schemas Setup (设置证明的数据结构)

**Credential Schema**  描述了一个 Credential 可以包含哪些属性，也就是 Credential 数据结构是怎样的。

注意：Schema 没办法更新，只能创建一个新版本的 Schema。

Credential Schema 可以被任何的 **Trust Anchor** 创建并保存在 Ledger。

下面是 **Government** 创建并发布 **Transcript**  ( 一种 Credential Schema ) 的方式。

1. 首先 Government **(Trust Anchor)** 身份创建一个凭证协议

   ```js
   # Government Agent
   (transcript_schema_id, transcript_schema) = 
       await anoncreds.issuer_create_schema(government_did, # 发布协议的 DID
                                            'Transcript',   # 协议名称
                                            '1.2',          # 协议版本
                                            # 协议的属性，就是规定下协议中要有什么内容
                                            json.dumps(['first_name', 'last_name', 'degree', 'status', 'year', 'average', 'ssn']))
   ```

2. Trust Anchor 将协议发送到 Ledger。

   ```js
   # Government Agent
   schema_request = await ledger.build_schema_request(government_did, transcript_schema)
   await ledger.sign_and_submit_request(pool_handle, government_wallet, government_did, schema_request)
   ```

这两步后，Government 将 Transcript Schema 的格式发布到了 Ledger，以后所有 Government 开具的 Transcript 证明必须符合这个数据结构。

同样的 Acme 公司将创建 **Job-Certificate Schema**  并保存到 Ledger 上面。

### Step6: Credential Definition Setup 

Credential Schema 仅仅定义了一份证明的数据结构，Credential Definition 相比 Credential Schema 包含了一些其他需要的信息。

Credential Definition 包括：Credential Schema、发布 Credential Schema 的发布者的 DID、用来签名和销毁 Credential 的秘钥。

Credential Definition 可以被任何 Trust Anchor 创建被添加到 Ledger 上。下面展示了 **Faber** 如何为 **Transcript** 协议 创建并发布一个 Credential Definition。

1. Faber 从 Ledger 获取 Transcript 协议

   ```js
   # Faber Agent
   get_schema_request = await ledger.build_get_schema_request(faber_did, transcript_schema_id)
   
   get_schema_response = await ledger.submit_request(pool_handle, get_schema_request) 
   (transcript_schema_id, transcript_schema) = 
       	await ledger.parse_get_schema_response(get_schema_response)
   ```

2. Faber 通过 Transcript 协议创建对应的 Credential Definition

   ```js
   # Faber Agent
   (faber_transcript_cred_def_id, faber_transcript_cred_def_json) = 
       await anoncreds.issuer_create_and_store_credential_def(faber_wallet, faber_did, transcript_schema, 'TAG1', 'CL', '{"support_revocation": false}')
   ```

3. Faber 将 Credential Definition 发送到 Ledger 上面

   ```js
   # Faber Agent     
   cred_def_request = await ledger.build_cred_def_request(faber_did, faber_transcript_cred_def_json)
   
   await ledger.sign_and_submit_request(pool_handle, faber_wallet, faber_did, cred_def_request)
   ```

同样的，Acme 公司将会创建一份  **Job-Certificate Definition** 并保存到 Ledger 上面。

到现在为止，Acme 公司发布了 Job-Certificate Definition (工作证明)，Faber 学校发布了 Transcript Definition (成绩单证明) 到 Ledger 上面。

## Alice Gets a Transcript 获取身份信息

Transcript 狭义上可以理解为成绩单，广义上市指 Faber 学校发布给的一份身份证明，包括姓名、年龄、成绩等等。

任何 Ledger 上的 DID 都可以发行一个证明，但是这个证明的价值在于发行的人。

Alice 在 Faber 学校毕业后，学校为 Alice 创建了一个具体的 Transcript Offer。

```js
 # Faber Agent
transcript_cred_offer_json = await anoncreds.issuer_create_credential_offer(faber_wallet, faber_transcript_cred_def_id)
```

相比 Schema 纯粹的数据结构定义，Definition 包含 Schema 及一些其他的信息，Offer 相当于一个钥匙，包装了 schema_id、cred_def_id 等信息，通过这些 id 可以在 Ledger 获取到相应的 Schema、Definition 信息。

Alice 查看这个 Offer 中的信息，这些信息在 Ledger 是公开的。

```js
  # Alice Agent
  get_schema_request = await ledger.build_get_schema_request(alice_faber_did, transcript_cred_offer['schema_id'])
  get_schema_response = await ledger.submit_request(pool_handle, get_schema_request)
  transcript_schema = await ledger.parse_get_schema_response(get_schema_response)

  print(transcript_schema['data'])
  # Transcript Schema:
  {
      'name': 'Transcript',
      'version': '1.2',
      'attr_names': ['first_name', 'last_name', 'degree', 'status', 'year', 'average', 'ssn']
  }
```

现在，这个 Offer 还不是可用状态，Alice 必须先创建一个主秘钥来获取它。

```js
# Alice Agent
alice_master_secret_id = await anoncreds.prover_create_master_secret(alice_wallet, None)
```

为了获取这个 Offer，Alice 还要知道和这个 Offer 对应的 Definition

```js
# Alice Agent
get_cred_def_request = await ledger.build_get_cred_def_request(alice_faber_did, transcript_cred_offer['cred_def_id'])

get_cred_def_response = await ledger.submit_request(pool_handle, get_cred_def_request)
faber_transcript_cred_def = 
    await ledger.parse_get_cred_def_response(get_cred_def_response)
```

现在 Alice 可以通过 transcript_cred_offer_json、alice_master_secret_id、faber_transcript_cred_def 创建一个请求来真正的使用这个 Offer

```js
# Alice Agent
(transcript_cred_request_json, transcript_cred_request_metadata_json) = 
        await anoncreds.prover_create_credential_req(alice_wallet, alice_faber_did, transcript_cred_offer_json, faber_transcript_cred_def, alice_master_secret_id)
```

Faber 收到请求后根据 Schema 定义的数据格式，将 Alice 的信息填写完整

```js
# Faber Agent
# note that encoding is not standardized by Indy except that 32-bit integers are encoded as themselves. IS-786
transcript_cred_values = json.dumps({
      "first_name": {"raw": "Alice", "encoded": "1139481716457488690172217916278103335"},
      "last_name": {"raw": "Garcia", "encoded": "5321642780241790123587902456789123452"},
      "degree": {"raw": "Bachelor of Science, Marketing", "encoded": "12434523576212321"},
      "status": {"raw": "graduated", "encoded": "2213454313412354"},
      "ssn": {"raw": "123-45-6789", "encoded": "3124141231422543541"},
      "year": {"raw": "2015", "encoded": "2015"},
      "average": {"raw": "5", "encoded": "5"}
})

transcript_cred_json, _, _ = 
      await anoncreds.issuer_create_credential(faber_wallet, transcript_cred_offer_json, transcript_cred_request_json, transcript_cred_values, None, None)
```

Alice 将 Faber 为他创建的身份信息保存在钱包里面

```js
# Alice Agent
await anoncreds.prover_store_credential(alice_wallet, None, transcript_cred_request_json, transcript_cred_request_metadata_json, transcript_cred_json,    faber_transcript_cred_def, None)

```

### Apply for a Job 申请工作

Alice 已经从 Faber 学校获取了身份证明，接下来他要使用这个身份证明去 Acme 公司申请一份工作。

在 Alice 与 Acme 公司 onboarding 之后，Acme 公司发送了一份工作申请表 (Park-Application Proof) 给  Alice。

```js
  # Acme Agent
  job_application_proof_request_json = json.dumps({
      'nonce': '1432422343242122312411212',
      'name': 'Job-Application',
      'version': '0.1',
      'requested_attributes': {
          'attr1_referent': {
              'name': 'first_name'
          },
          'attr2_referent': {
              'name': 'last_name'
          },
          'attr3_referent': {
              'name': 'degree',
              'restrictions': [{'cred_def_id': faber_transcript_cred_def_id}]
          },
          'attr4_referent': {
              'name': 'status',
              'restrictions': [{'cred_def_id': faber_transcript_cred_def_id}]
          },
          'attr5_referent': {
              'name': 'ssn',
              'restrictions': [{'cred_def_id': faber_transcript_cred_def_id}]
          },
          'attr6_referent': {
              'name': 'phone_number'
          }
      },
      'requested_predicates': {
          'predicate1_referent': {
              'name': 'average',
              'p_type': '>=',
              'p_value': 4,
              'restrictions': [{'cred_def_id': faber_transcript_cred_def_id}]
          }
      }
  })
```

Alice 收到工作申请表后，还不能使用，想使用的话还需要申请一个凭证。

```js
# Alice Agent
creds_for_job_application_proof_request = json.loads(
    await anoncreds.prover_get_credentials_for_proof_req(alice_wallet, job_application_proof_request_json))
```

未完待续



## Docker 命令：

```shell
docker ps
docker ps -a
docker start indy_pool
```

