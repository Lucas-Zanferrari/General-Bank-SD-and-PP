package services

import javax.inject._
import play.api.Logger
import play.api.http.Status
import play.api.libs.ws.WSClient
import scala.concurrent.{Await, ExecutionContext, TimeoutException}
import scala.concurrent.duration._

case class TransferParameterInterface(targetBankId: String, receiverId: String, amount: String)

@Singleton
class TransferRequest @Inject()(b: GeneralBankData, ws: WSClient)(implicit ec: ExecutionContext) {

  private val REQUEST_TIMEOUT: FiniteDuration = 30.seconds
  private val PROTOCOL = "http://"

  // This code is called when the application starts.
  def run(params: TransferParameterInterface) {
    val getTargetHost = ws.url(s"${b.CENTRAL_BANK_HOST}${b.CENTRAL_BANK_ROOT_ENDPOINT}/${params.targetBankId}")
      .withRequestTimeout(REQUEST_TIMEOUT)
      .get()
      .map { getHostResponse =>
        if (getHostResponse.status == Status.OK) {
          val targetBankHost = (getHostResponse.json \ "host").as[String]
          val targetBankName = (getHostResponse.json \ "name").as[String]
          Logger.info(s"$targetBankName@$targetBankHost was checked successfully with CentralBank@${b.CENTRAL_BANK_HOST}! Host is #$targetBankHost")
          ws.url(s"$PROTOCOL$targetBankHost/v1/clientes/${params.receiverId}/deposito/${params.amount}")
            .withRequestTimeout(REQUEST_TIMEOUT)
            .post("")
            .map { sendDespositResponse =>
              if (sendDespositResponse.status == Status.OK)
                Logger.info(s"${b.BANK_NAME}@${b.BANK_HOST} transferred successfully with CentralBank@${b.CENTRAL_BANK_HOST}! Target host is #$targetBankHost")
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
