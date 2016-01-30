package com.sumologic.elasticsearch.util

import com.amazonaws.auth.AWSCredentials
import com.sumologic.elasticsearch.restlastic.dsl.Dsl._
import org.junit.runner.RunWith
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.junit.JUnitRunner
import spray.http.HttpHeaders.RawHeader
import spray.http.Uri.Query
import spray.http._

@RunWith(classOf[JUnitRunner])
class AwsRequestSignerTest extends WordSpec with Matchers {
  val dummyCredentials = new AWSCredentials {
    override def getAWSAccessKeyId: String = "AKIDEXAMPLE"
    override def getAWSSecretKey: String = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"
  }
  val region = "us-east-1"
  val service = "host"
  val dateStr = "20110909"
  val dateTimeStr = "20110909T233600Z"

  val signer = new TestSigner(dateStr, dateTimeStr, dummyCredentials, region, service)

  "Handle a vanilla example" in {
    // Example from http://docs.aws.amazon.com/general/latest/gr/signature-v4-test-suite.html
    val req = HttpRequest(
      method = HttpMethods.GET,
      uri = Uri.from(
        host = "host.foo.com",
        path = "/",
        query = Query("foo" -> "Zoo", "foo" -> "aha")
      ),
      headers = List(
        RawHeader("Date", "Mon, 09 Sep 2011 23:36:00 GMT"),
        RawHeader("Host", "host.foo.com")
      )
    )

    val expectedCanonical =
      """|GET
         |/
         |foo=Zoo&foo=aha
         |date:Mon, 09 Sep 2011 23:36:00 GMT
         |host:host.foo.com
         |
         |date;host
         |e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855""".stripMargin
    signer.createCanonicalRequest(req) should be(expectedCanonical)

    val expectedStringTosign = """AWS4-HMAC-SHA256
                                 |20110909T233600Z
                                 |20110909/us-east-1/host/aws4_request
                                 |e25f777ba161a0f1baf778a87faf057187cf5987f17953320e3ca399feb5f00d""".stripMargin

    signer.stringToSign(req, dateTimeStr, dateStr) should be (expectedStringTosign)

    val withAuthHeader = signer.withAuthHeader(req)
    val expected = "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20110909/us-east-1/host/aws4_request, SignedHeaders=date;host, Signature=be7148d34ebccdc6423b19085378aa0bee970bdc61d144bd1a8c48c33079ab09"
    val actual = withAuthHeader.headers.filter(_.name == "Authorization").head.value
    actual should be(expected)
  }

  "sign this create index request" in {
    val realDateStr = "20151016"
    val realDateTimeStr = "20151016T025745Z"
    val signer = new TestSigner(realDateStr, realDateTimeStr, dummyCredentials, region, "es")
    val req = HttpRequest(
      method = HttpMethods.POST,
      uri = Uri.from(
        host = "search-kwan-metrics-es-l2fecxdxfit54aod5dgpqchndq.us-east-1.es.amazonaws.com",
        path = "/metrics-catalog-index"
      ),
      entity = HttpEntity(CreateIndex.toJsonStr)
    )

    val expectedCanonical = "POST" +
      "\n/metrics-catalog-index" +
      "\n\nhost:search-kwan-metrics-es-l2fecxdxfit54aod5dgpqchndq.us-east-1.es.amazonaws.com" +
      "\nx-amz-date:20151016T025745Z" +
      "\n\nhost;x-amz-date" +
      "\n44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a"

    signer.createCanonicalRequest(signer.completedRequest(req, realDateTimeStr)) should be(expectedCanonical)

  }

  "sign this put request" in {
    val jsonBody = """{
                     |  "key":"_3",
                     |  "value":"if_octets",
                     |  "value_auto":{
                     |    "input":"if_octets"
                     |  },
                     |  "cust_id_str":"0000000000000138"
                     |}""".stripMargin

    val expectedCanonical =
      "POST\n/metrics-catalog-index/kv/_3%3Aif_octets%3A0000000000000138\n\nhost:" +
        "search-kwan-metrics-es-l2fecxdxfit54aod5dgpqchndq.us-east-1.es.amazonaws.com\nx-amz-date:" +
        "20151016T182449Z\n\nhost;x-amz-date" +
        "\n7acc8e40a1838b09f1db33569f74e1fda7fb919bdc65cb3eee1b4ee166088d19"

    val expectedToSing = "AWS4-HMAC-SHA256\n" +
      "20151016T182449Z\n" +
      "20151016/us-east-1/es/aws4_request\n" +
      "326f6093970bae063fa26fb389fe85d806910df9b2edb1a99ae0cbf23889b8b2"
    val realDateStr = "20151016"
    val realDateTimeStr = "20151016T182449Z"
    val signer = new TestSigner(realDateStr, realDateTimeStr, dummyCredentials, region, "es")
    val req = {
      val preReq = HttpRequest(
        method = HttpMethods.POST,
        uri = Uri.from(
          host = "search-kwan-metrics-es-l2fecxdxfit54aod5dgpqchndq.us-east-1.es.amazonaws.com",
          path = "/metrics-catalog-index/kv/_3:if_octets:0000000000000138"
        ),
        entity = HttpEntity(jsonBody)
      )
      signer.completedRequest(preReq, realDateTimeStr)
    }

    signer.createCanonicalRequest(req) should be(expectedCanonical)
    signer.stringToSign(req, realDateTimeStr, realDateStr) should be(expectedToSing)


  }
}

class TestSigner(dateStr: String, datetimeStr: String, creds: AWSCredentials, region: String, service: String)
  extends AwsRequestSigner(creds, region, service) {
  override def currentDateStrings = (datetimeStr, dateStr)
}

