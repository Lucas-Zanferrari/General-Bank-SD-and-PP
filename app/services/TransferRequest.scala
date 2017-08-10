package services

import javax.inject._
import play.api.Logger
import play.api.http.Status
import play.api.libs.ws.WSClient
import v1.client.ClientData
import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
import scala.concurrent.duration._

@Singleton
case class TransferParameterInterface(clientFuture: Future[Option[ClientData]], bank: String, receiver: String, amount: String)

@Singleton
class TransferRequest @Inject()(b: GeneralBankData, ws: WSClient)(implicit ec: ExecutionContext) {

  private val REQUEST_TIMEOUT: FiniteDuration = 30.seconds
  private val PROTOCOL = "http://"
  private var targetHost = ""

  // This code is called when the application starts.
  def run(params: TransferParameterInterface) {
    val getTargetHost = ws.url(s"${b.CENTRAL_BANK_HOST}${b.CENTRAL_BANK_POST_BANK_ENDPOINT}/${params.bank}").withRequestTimeout(REQUEST_TIMEOUT).get().map {
      getHostResponse =>
        if (getHostResponse.status == Status.OK) {
          targetHost = (getHostResponse.json \ "host").as[String]
          Logger.info(s"${b.BANK_NAME}@${b.BANK_HOST} bank checked successfully with CentralBank@${b.CENTRAL_BANK_HOST}! Host is #$targetHost")
          ws.url(s"$PROTOCOL$targetHost/v1/${params.receiver}/deposito/${params.amount}").withRequestTimeout(REQUEST_TIMEOUT).post().map{
            sendDespositResponse =>
              if (sendDespositResponse.status == Status.OK)
                Logger.info(s"${b.BANK_NAME}@${b.BANK_HOST} transferred successfully with CentralBank@${b.CENTRAL_BANK_HOST}! Target host is #$targetHost")
          }
        }
    }
    .recover {
      case e: TimeoutException => Logger.info(s"CentralBank@${b.CENTRAL_BANK_HOST} did not respond before timeout.")
      case e: Exception => Logger.info(s"${b.BANK_NAME}@${b.BANK_HOST}: [Error] - ${e.getMessage}")
    }

    Await.result(getTargetHost, Duration.Inf)
  }

}
