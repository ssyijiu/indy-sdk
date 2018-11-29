package utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.apache.commons.io.FileUtils;
import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.pool.PoolJSONParameters;

public class PoolUtils {

    private static final String DEFAULT_POOL_NAME = "default_pool";
    public static final int PROTOCOL_VERSION = 2;


    private static File createGenesisTxnFile(String filename) throws IOException {
        String path = EnvironmentUtils.getTmpPath(filename);
        String testPoolIp = EnvironmentUtils.getTestPoolIP();

        String[] defaultTxns = new String[] {
            String.format(
                "{\"reqSignature\":{},\"txn\":{\"data\":{\"data\":{\"alias\":\"N1\",\"blskey\":\"uh9BMFSbd8tfpeym91dtqsea9P8RiknQmhw5UAsym3BJpEjBuYMRynH8YVPbXAM9uLZRo2FLokr7z7f28TBHYkj45YZaJKH5YG4ECE3PsMtXAJ4pjCByF8e2Pm2zTnkm691pa88xghLZbFSKsfSvHD1jHr8aJAPMDWiS63DXzTf5SV\",\"blskey_pop\":\"R7S9eX2P5X9KXqKo2FUZRStcZKRwDmA6vPyYtmqsuqSchx3aYjrVWzZdmVkagF3G79Qmr2H5NJdQPsr6YuifzrzuvcUKbBZwLctyDkXB8uv6Ss7Gj9N3mbvpqyYerZSAM3rBjr7DsVwXMmZ4zwAry5g3wmSxQcTXsWiMTyJMqK5jDZ\",\"client_ip\":\"116.196.114.151\",\"client_port\":9701,\"node_ip\":\"116.196.114.151\",\"node_port\":9700,\"services\":[\"VALIDATOR\"]},\"dest\":\"2L17FEjouZZzQ8h1iqxU4MNZYpWZvdhSQSfFSsGiYsxS\"},\"metadata\":{\"from\":\"KnQM8SWskdXEbPZLk239aE\"},\"type\":\"0\"},\"txnMetadata\":{\"seqNo\":1,\"txnId\":\"b79f477f6d435116155b1121455748240bb5e7c81f7043519980df21a8167ca1\"},\"ver\":\"1\"}\n",
                testPoolIp, testPoolIp),
            String.format(
                "{\"reqSignature\":{},\"txn\":{\"data\":{\"data\":{\"alias\":\"N2\",\"blskey\":\"2C4rTh34VphPRdsJYbxbTGhxQ44aC5L9VYTzfVdx7TmeU2RBQj9eypDt91Eu757xVbvFesJmhDUNLtVvXLDVcZm4bctUTuGmj5Gnm4AEvjWuy2DfdazBXqEdAFux8meUejLVNGz9cNiPYKdXY6x6qWcaQKwXmRmh8cv71eiDw8oSajv\",\"blskey_pop\":\"QvsuKBQJLSteNyX2WrudbZBR3dA5Tjd2ijuCXUAX6MB49Lc4of2YRFtpSiGEbp2aVLKcV3LVV9DRKonxqxgACeUEk5NJLs3F8fESHKn86X6dP7Lv5uztLVEqt8TWxMQ4v8XMXpcQ16PYdVzeXHZr72p4StTN9ffHSCvXGKz9BNdNFq\",\"client_ip\":\"116.196.114.151\",\"client_port\":9703,\"node_ip\":\"116.196.114.151\",\"node_port\":9702,\"services\":[\"VALIDATOR\"]},\"dest\":\"GUyscLYHbmtUy4bWo5tGmSB8q7YPYbkc1W5rdzAQiUE2\"},\"metadata\":{\"from\":\"XimcCU72wNNDTS5enepDpr\"},\"type\":\"0\"},\"txnMetadata\":{\"seqNo\":2,\"txnId\":\"2a5f06422d35ac8977cbf311a3178de243428e2c1ca836ed16477cac024360ec\"},\"ver\":\"1\"}\n",
                testPoolIp, testPoolIp),
            String.format(
                "{\"reqSignature\":{},\"txn\":{\"data\":{\"data\":{\"alias\":\"N3\",\"blskey\":\"4G6TJnwCpLvmCtzrvYnNZ6BCu545jrmbpMhJhX89SKxpM6eTvkHeA3KqBKe92KYUWJgRqCpqJenaKSpuqysrLfBrGbutDcy3UZvd7NZe8QqGvR1nmbaurmUzj64rzv2CR1oV3xEkKwWAddZT1UrCCpSbHiHBDoo5Au6PX8XEQXwSHdW\",\"blskey_pop\":\"R29cMVohasdZapXQo81q9QWjTBqebCqP5UJCpc4uqUvLif1VWAn4xoEpt7NNuwALTda8Ey1DhD4W4yHhiypNNCeR6fsaYW1H8pxauGn3rBnrmQSutQF5j6nrHosBJR2ANG6LWzvbnRtqJXYoWQv4zRtxrS5wJfc1gSDmU4KNdUWAWs\",\"client_ip\":\"116.196.114.151\",\"client_port\":9705,\"node_ip\":\"116.196.114.151\",\"node_port\":9704,\"services\":[\"VALIDATOR\"]},\"dest\":\"BRLp1Rqho2v8ap3df5QoWQ8FP7gGg3tQZXYA6bQGa8XQ\"},\"metadata\":{\"from\":\"2sxZreEizBphCXpYXruEWV\"},\"type\":\"0\"},\"txnMetadata\":{\"seqNo\":3,\"txnId\":\"2da09e1fac71f1257e0efcb158f2d71e7d11ccb7a7db83461ce7f4cade83e770\"},\"ver\":\"1\"}\n",
                testPoolIp, testPoolIp),
            String.format(
                "{\"reqSignature\":{},\"txn\":{\"data\":{\"data\":{\"alias\":\"N4\",\"blskey\":\"4GgWGZeLciYT4kzFZ3xxWb8KUKGKY2VJbKk1f6rPmuA68DH6K86vYKMgvoVNzmKbJRvkK8gH1GpNoKUU22XTheiRbZge2M8DNu5aYEnkAuzJVQxPUFACTP9yTN4GtC7wTE1HoSsK17yFX5wniExfKn3yG9JURm5p46Hi4waswtWp5KP\",\"blskey_pop\":\"QkXWbLFXpjxeY4SV3M1XNotFBQit37SKeNFra7pPvFe8HFcAvcfRfhFpNx3wiR7My2ZuP41V5688sHRvpkBTj4ua2mzG8RgSAJbB4EWSzySG3dgMUbTq1WQ3hbEscHdH85YUfqWvjEuTHWZ4aUUJ2giyrrw7vGJfxApsV1TYcX1bju\",\"client_ip\":\"116.196.114.151\",\"client_port\":9707,\"node_ip\":\"116.196.114.151\",\"node_port\":9706,\"services\":[\"VALIDATOR\"]},\"dest\":\"5d5q1WyrK4t5TCcXowhT2eG2W26b1xbPhiec6rUVhyBo\"},\"metadata\":{\"from\":\"2sqZUrBqGMARkgWgrVyzGR\"},\"type\":\"0\"},\"txnMetadata\":{\"seqNo\":4,\"txnId\":\"e4872d0e91c6dd72820b7a39f029620940e5d801ebee22dfbb8ffdca6b328fed\"},\"ver\":\"1\"}\n",
                testPoolIp, testPoolIp)
        };

        File file = new File(path);

        FileUtils.forceMkdirParent(file);

        FileWriter fw = new FileWriter(file);
        for (String defaultTxn : defaultTxns) {
            fw.write(defaultTxn);
            fw.write("\n");
        }

        fw.close();

        return file;
    }


    public static String createPoolLedgerConfig()
        throws IOException, InterruptedException, ExecutionException, IndyException {
        return createPoolLedgerConfig(DEFAULT_POOL_NAME);
    }


    public static String createPoolLedgerConfig(String poolName)
        throws IOException, InterruptedException, ExecutionException, IndyException {
        File genesisTxnFile = createGenesisTxnFile(poolName + ".txn");
        PoolJSONParameters.CreatePoolLedgerConfigJSONParameter createPoolLedgerConfigJSONParameter
            = new PoolJSONParameters.CreatePoolLedgerConfigJSONParameter(
            genesisTxnFile.getAbsolutePath());
        Pool.createPoolLedgerConfig(poolName, createPoolLedgerConfigJSONParameter.toJson()).get();
        return poolName;
    }

}
