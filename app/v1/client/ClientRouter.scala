package v1.client

import javax.inject.Inject
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

/**
  * Routes and URLs to the ClientResource controller.
  */
class ClientRouter @Inject()(controller: ClientController) extends SimpleRouter {
  val prefix = "/v1/clientes"

  def link(id: ClientId): String = {
    import com.netaporter.uri.dsl._
    val url = prefix / id.toString
    url.toString()
  }

  override def routes: Routes = {
    case GET(p"/") =>
      controller.index

    case POST(p"/") =>
      controller.process

    case GET(p"/$id") =>
      controller.show(id)

    case DELETE(p"/$id") =>
      controller.remove(id)

    case POST(p"/$id/saque/$amount") =>
      controller.withdraw(id, amount.toFloat)

    case POST(p"/$id/deposito/$amount") =>
      controller.deposit(id, amount.toFloat)

    case POST(p"/$id/transferencia/$receiverId/$amount") =>
      controller.internalTransfer(id, receiverId, amount.toFloat)

    case POST(p"/$id/TED/$targetBankId/$receiverId/$amount") =>
      controller.externalTransfer(id, targetBankId, receiverId, amount.toFloat)
  }

}
