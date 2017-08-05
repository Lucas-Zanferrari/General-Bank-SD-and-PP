package v1.client

import javax.inject.Inject

import play.api.Logger
import play.api.data.Form
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}

case class ClientFormInput(name: String, balance: String)

/**
  * Takes HTTP requests and produces JSON.
  */
class ClientController @Inject()(cc: ClientControllerComponents)(implicit ec: ExecutionContext)
    extends ClientBaseController(cc) {

  private val logger = Logger(getClass)

  private val form: Form[ClientFormInput] = {
    import play.api.data.Forms._

    Form(
      mapping(
        "name" -> nonEmptyText,
        "balance" -> text
      )(ClientFormInput.apply)(ClientFormInput.unapply)
    )
  }

  def index: Action[AnyContent] = ClientAction.async { implicit request =>
    logger.trace("index: ")
    clientResourceHandler.find.map { clients =>
      Ok(Json.toJson(clients))
    }
  }

  def process: Action[AnyContent] = ClientAction.async { implicit request =>
    logger.trace("process: ")
    processJsonClient()
  }

  def show(id: String): Action[AnyContent] = ClientAction.async { implicit request =>
    logger.trace(s"show: id = $id")
    clientResourceHandler.lookup(id).map { client =>
      Ok(Json.toJson(client))
    }
  }

  private def processJsonClient[A]()(implicit request: ClientRequest[A]): Future[Result] = {
    def failure(badForm: Form[ClientFormInput]) = {
      Future.successful(BadRequest(badForm.errorsAsJson))
    }

    def success(input: ClientFormInput) = {
      clientResourceHandler.create(input).map { client =>
        Created(Json.toJson(client)).withHeaders(LOCATION -> client.link)
      }
    }

    form.bindFromRequest().fold(failure, success)
  }
}
