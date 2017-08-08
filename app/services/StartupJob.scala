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
class StartupJob @Inject()(ws: WSClient, appLifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext) {

  // These properties will need to be changed for every different bank that gets deployed
  private val CENTRAL_BANK_HOST: String = "http://localhost:9000"
  private val CENTRAL_BANK_POST_BANK_ENDPOINT = "/v1/bancos"
  private val BANK_NAME: String = "Santander"
  private val BANK_PORT = "9001"

  private val BANK_HOST: String = s"${InetAddress.getLocalHost.getHostAddress}"
  var bankId = 0
  private val REQUEST_TIMEOUT: FiniteDuration = 30.seconds
  private val BANK_JSON = Json.obj(
    "name" -> BANK_NAME,
    "host" -> s"$BANK_HOST:$BANK_PORT"
  )

  // This code is called when the application starts.
  def run() {
    val receivedResponse = ws.url(CENTRAL_BANK_HOST+CENTRAL_BANK_POST_BANK_ENDPOINT).withRequestTimeout(REQUEST_TIMEOUT).post(BANK_JSON).map {
      response =>
        if (response.status == Status.CREATED) {
          bankId = Integer.parseInt((response.json \ "id").as[String])
          Logger.info(s"$BANK_NAME@$BANK_HOST registered successfully with CentralBank@$CENTRAL_BANK_HOST! ID is #$bankId")
        }
    }
      .recover {
        case e: TimeoutException => Logger.info(s"CentralBank@$CENTRAL_BANK_HOST did not respond before timeout.")
        case e: Exception => Logger.info(s"$BANK_NAME@$BANK_HOST: [Error] - ${e.getMessage}")
      }

    Await.result(receivedResponse, Duration.Inf)
  }

  run()

  // When the application starts, register a stop hook with the
  // ApplicationLifecycle object. The code inside the stop hook will
  // be run when the application stops.
  appLifecycle.addStopHook { () =>
    Logger.info(s"Bank is going offline. A notification will be sent to CentralBank@$CENTRAL_BANK_HOST.")
    val receivedResponse = ws.url(s"$CENTRAL_BANK_HOST$CENTRAL_BANK_POST_BANK_ENDPOINT/$bankId").withRequestTimeout(REQUEST_TIMEOUT).delete().map {
      response =>
        if (response.status == Status.OK)
          Logger.info(s"$BANK_NAME@$BANK_HOST removed successfully from CentralBank@$CENTRAL_BANK_HOST!")
      }
      .recover {
        case e: TimeoutException => Logger.info(s"CentralBank@$CENTRAL_BANK_HOST did not respond before timeout.")
        case e: Exception => Logger.info(s"$BANK_NAME@$BANK_HOST: [Error] - ${e.getMessage}")
      }

    Await.result(receivedResponse, Duration.Inf)
    Future.successful(())
  }
}
