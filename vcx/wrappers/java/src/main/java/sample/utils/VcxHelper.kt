package sample.utils

/**
 * Created by lxm on 2018/11/30.
 */

object VcxHelper {

    fun provisionConfig() =
        mutableMapOf<String, String>().apply {
            put(key = "agency_url", value = "http://116.196.91.197:8080")
            put(key = "agency_did", value = "VsKV7grR1BUE29mG2Fm2kX")
            put(key = "agency_verkey", value = "Hezce2UWMZ3wUhVkh2LfKSs8nDzWwzs2Win7EzNN3YaR")
            put(key = "wallet_key", value = "123")
            put(key = "payment_method", value = "null")
            put(key = "enterprise_seed", value = "19e2ea3730c3d62f36a095a44d343c7f5d81e0168c7f987b3d70a37c516bb45d")
        }.toJson()
}