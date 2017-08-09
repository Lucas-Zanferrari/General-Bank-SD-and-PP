package services

import java.net
import javax.inject._
import java.net._

import play.api.Logger
import play.api.http.Status
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.WSClient
import play.api.libs.json.Json
import play.Application
import v1.client.{ClientData, ClientResourceHandler}

import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
import scala.concurrent.duration._

@Singleton
class TransferRequest @Inject()(ws: WSClient, clientFuture: Future[Option[ClientData]], bank: String, receiver:String, amount:String)(implicit ec: ExecutionContext) {

  // These properties will need to be changed for every different bank that gets deployed
  private val CENTRAL_BANK_HOST: String = "http://localhost:9000"
  private val CENTRAL_BANK_POST_BANK_ENDPOINT = "/v1/bancos"
  private val BANK_NAME: String = "Santander"
  private val BANK_PORT = "9001"
  private val PROTOCOL = "http://"

  private val BANK_HOST: String = s"${InetAddress.getLocalHost.getHostAddress}"
  private var bankHost = ""
  private val REQUEST_TIMEOUT: FiniteDuration = 30.seconds

  // This code is called when the application starts.
  def run() {
    val receivedResponse = ws.url(CENTRAL_BANK_HOST+CENTRAL_BANK_POST_BANK_ENDPOINT+"/"+bank).withRequestTimeout(REQUEST_TIMEOUT).get().map {
      response =>
        if (response.status == Status.OK) {
          bankHost = (response.json \ "host").as[String]
          Logger.info(s"$BANK_NAME@$BANK_HOST bank checked successfully with CentralBank@$CENTRAL_BANK_HOST! Host is #$bankHost")
          val receivedResponse2 = ws.url(s"$PROTOCOL${bankHost}/v1/$receiver/deposito/$amount").withRequestTimeout(REQUEST_TIMEOUT).post().map{
            response2 =>
              if (response2.status == Status.OK) {
                Logger.info(s"$BANK_NAME@$BANK_HOST transfered successfully with CentralBank@$CENTRAL_BANK_HOST! ID is #$bankHost")
                clientFuture.map { _.map(clientData => clientData.withdraw(amount.toFloat))}
              }
          }
        }
    }
      .recover {
        case e: TimeoutException => Logger.info(s"CentralBank@$CENTRAL_BANK_HOST did not respond before timeout.")
        case e: Exception => Logger.info(s"$BANK_NAME@$BANK_HOST: [Error] - ${e.getMessage}")
      }

    Await.result(receivedResponse, Duration.Inf)
  }

  run()
}
