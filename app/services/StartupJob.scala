package services

import javax.inject._
import java.net._
import play.api.Logger
import play.api.http.Status
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.WSClient
import play.api.libs.json.{JsObject, Json}
import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
import scala.concurrent.duration._

/**
  * This class demonstrates how to run code when the
  * application starts and stops. It starts a timer when the
  * application starts. When the application stops it prints out how
  * long the application was running for.
  *
  * This class is registered for Guice dependency injection in the
  * [[Module]] class. We want the class to start when the application
  * starts, so it is registered as an "eager singleton". See the code
  * in the [[Module]] class to see how this happens.
  *
  * This class needs to run code when the server stops. It uses the
  * application's [[ApplicationLifecycle]] to register a stop hook.
  */

@Singleton
class GeneralBankData {
  // These properties will need to be changed for every different bank that gets deployed
  val CENTRAL_BANK_HOST: String = "http://localhost:9000"
  val CENTRAL_BANK_ROOT_ENDPOINT = "/v1/bancos"
  val BANK_NAME: String = "Santander"
  val BANK_PORT = "9001"
  val BANK_HOST: String = s"${InetAddress.getLocalHost.getHostAddress}"
  val BANK_JSON: JsObject = Json.obj(
    "name" -> BANK_NAME,
    "host" -> s"$BANK_HOST:$BANK_PORT"
  )
  var bankId = 0
}

@Singleton
class StartupJob @Inject()(b: GeneralBankData, ws: WSClient, appLifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext) {
  // This code is called when the application starts.

  val REQUEST_TIMEOUT: FiniteDuration = 30.seconds

  def run() {
    val receivedResponse = ws.url(b.CENTRAL_BANK_HOST+b.CENTRAL_BANK_ROOT_ENDPOINT)
      .withRequestTimeout(REQUEST_TIMEOUT)
      .post(b.BANK_JSON)
      .map { response =>
        if (response.status == Status.CREATED) {
          b.bankId = Integer.parseInt((response.json \ "id").as[String])
          Logger.info(s"${b.BANK_NAME}@${b.BANK_HOST} registered successfully with CentralBank@${b.CENTRAL_BANK_HOST}! ID is #${b.bankId}")
        }
      }
      .recover {
        case e: TimeoutException => Logger.info(s"CentralBank@${b.CENTRAL_BANK_HOST} did not respond before timeout.")
        case e: Exception => Logger.info(s"${b.BANK_NAME}@${b.BANK_HOST}: [Error] - ${e.getMessage}")
      }
    Await.result(receivedResponse, Duration.Inf)
  }

  run()

  // When the application starts, register a stop hook with the
  // ApplicationLifecycle object. The code inside the stop hook will
  // be run when the application stops.
  appLifecycle.addStopHook { () =>
    Logger.info(s"Bank is going offline. A notification will be sent to CentralBank@${b.CENTRAL_BANK_HOST}.")
    val receivedResponse = ws.url(s"${b.CENTRAL_BANK_HOST}${b.CENTRAL_BANK_ROOT_ENDPOINT}/${b.bankId}")
      .withRequestTimeout(REQUEST_TIMEOUT)
      .delete()
      .map { response =>
        if (response.status == Status.OK)
          Logger.info(s"${b.BANK_NAME}@${b.BANK_HOST} removed successfully from CentralBank@${b.CENTRAL_BANK_HOST}!")
      }
      .recover {
        case e: TimeoutException => Logger.info(s"CentralBank@${b.CENTRAL_BANK_HOST} did not respond before timeout.")
        case e: Exception => Logger.info(s"${b.BANK_NAME}@${b.BANK_HOST}: [Error] - ${e.getMessage}")
      }

    Await.result(receivedResponse, Duration.Inf)
    Future.successful(())
  }
}
