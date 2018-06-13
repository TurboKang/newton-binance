import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.httpGet
import java.util.*
import kotlin.concurrent.fixedRateTimer

fun main(args : Array<String>) {
    val prop = Properties()
    prop.load(ClassLoader.getSystemResourceAsStream("application.properties"))
    val cl = KucoinClient(
            domain = prop.getProperty("kucoin.domain"),
            apiKey = prop.getProperty("kucoin.key"),
            apiSecret = prop.getProperty("kucoin.secret")
    )
    System.out.println(cl.getUserInfo())
    /*
    FuelManager.instance.basePath = mainUrl
    val fixedRateTimer = fixedRateTimer(name = "hello-timer", period = 1000) {
        "/v1/open/lang-list".httpGet().responseString { request, response, result ->
            //make a GET to http://httpbin.org/get and do something with response
            val (data, error) = result
            if (error == null) {
                System.out.println(data)
            } else {
                System.out.println("error")
            }
        }
    }
    */
}