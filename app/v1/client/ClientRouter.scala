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

    case GET(p"/$id/saldo") =>
      controller.balance(id)

    case POST(p"/$id/saque/$amount") =>
      controller.withdraw(id,amount)

    case POST(p"/$id/deposito/$amount") =>
      controller.deposit(id,amount)

    case POST(p"/$id/transferencia/$receiver/$amount") =>
      controller.internalTransfer(id,receiver,amount)

    case POST(p"/$id/TED/$bank/$receiver/$amount") =>
      controller.externalTransfer(id,bank,receiver,amount)
  }

}
