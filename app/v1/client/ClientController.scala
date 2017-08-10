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
    clientResourceHandler.find.map { clientes =>
      Ok(Json.toJson(clientes))
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

  def remove(id: String): Action[AnyContent] = ClientAction.async { implicit request =>
    logger.trace(s"remove: id = $id")
    clientResourceHandler.remove(id)
    Future {
      Ok(JsObject(Seq(
        "message" -> JsString(s"client #$id removed successfully")
      )))
    }
  }

  def withdraw(id: String, amount: Float): Action[AnyContent] = ClientAction.async { implicit request =>
    logger.trace(s"withdraw: id = $id; amount = $amount")
    clientResourceHandler.makeWithdraw(id, amount).map { client =>
      Ok(Json.toJson(client))
    }
  }

  def deposit(id: String, amount: Float): Action[AnyContent] = ClientAction.async { implicit request =>
    logger.trace(s"deposit: id = $id; amount = $amount")
    clientResourceHandler.makeDeposit(id, amount).map { client =>
      Ok(Json.toJson(client))
    }
  }

  def internalTransfer(id: String, receiverId: String, amount: Float): Action[AnyContent] = ClientAction.async { implicit request =>
    logger.trace(s"internalTransfer: id = $id; receiverId = $receiverId; amount = $amount")
    clientResourceHandler.makeInternalTransfer(id, receiverId, amount).map { result =>
      Ok(JsObject(Seq(
        "message" -> JsString(s"sucessfully transferred $$$amount from client #$id to #$receiverId")
      )))
    }
  }

  def externalTransfer(id:String, targetBankId: String, receiverId: String, amount: String): Action[AnyContent] = ClientAction.async { implicit request =>
    logger.trace(s"externalTransfer: id = $id; target bank = $targetBankId; receiverId = $receiverId; amount = $amount")
    clientResourceHandler.makeExternalTransfer(id, targetBankId, receiverId, amount).map { client =>
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
