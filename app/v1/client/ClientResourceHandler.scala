package v1.client

import javax.inject.{Inject, Provider}

import play.api.MarkerContext

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._

/**
  * DTO for displaying client information.
  */
case class ClientResource(id: String, link: String, name: String, balance: String)

object ClientResource {

  /**
    * Mapping to write a ClientResource out as a JSON value.
    */
  implicit val implicitWrites = new Writes[ClientResource] {
    def writes(client: ClientResource): JsValue = {
      Json.obj(
        "id" -> client.id,
        "link" -> client.link,
        "name" -> client.name,
        "balance" -> client.balance
      )
    }
  }
}

/**
  * Controls access to the backend data, returning [[ClientResource]]
  */
class ClientResourceHandler @Inject()(
    routerProvider: Provider[ClientRouter],
    clientRepository: ClientRepository)(implicit ec: ExecutionContext) {

  def create(clientInput: ClientFormInput)(implicit mc: MarkerContext): Future[ClientResource] = {
    val nextId = clientRepository.nextId()
    val data = ClientData(ClientId(nextId.toString), clientInput.name, clientInput.balance)
    // We don't actually create the client, so return what we have
    clientRepository.create(data).map { id =>
      createClientResource(data)
    }
  }

  def lookup(id: String)(implicit mc: MarkerContext): Future[Option[ClientResource]] = {
    val clientFuture = clientRepository.get(ClientId(id))
    clientFuture.map { maybeClientData =>
      maybeClientData.map { clientData =>
        createClientResource(clientData)
      }
    }
  }

  def find(implicit mc: MarkerContext): Future[Iterable[ClientResource]] = {
    clientRepository.list().map { clientDataList =>
      clientDataList.map(clientData => createClientResource(clientData))
    }
  }

  private def createClientResource(p: ClientData): ClientResource = {
    ClientResource(p.id.toString, routerProvider.get.link(p.id), p.name, p.balance)
  }

}
