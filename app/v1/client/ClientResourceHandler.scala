package v1.client

import javax.inject.{Inject, Provider}
import play.api.MarkerContext
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import services.{TransferParameterInterface, TransferRequest}

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
        "balance" -> BigDecimal(client.balance).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
      )
    }
  }
}

/**
  * Controls access to the backend data, returning [[ClientResource]]
  */
class ClientResourceHandler @Inject()(
    routerProvider: Provider[ClientRouter],
    clientRepository: ClientRepository, requester: TransferRequest)(implicit ec: ExecutionContext) {

  def create(clientInput: ClientFormInput)(implicit mc: MarkerContext): Future[ClientResource] = {
    val nextId = clientRepository.nextId()
    val data = ClientData(ClientId(nextId.toString), clientInput.name, clientInput.balance.toFloat)
    clientRepository.create(data).map { id =>
      createClientResource(data)
    }
  }

  def lookup(id: String)(implicit mc: MarkerContext): Future[Option[ClientResource]] = {
    val clientFuture = clientRepository.getOne(ClientId(id))
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

  def remove(id: String)(implicit mc: MarkerContext): Unit = clientRepository.delete(ClientId(id))

  private def createClientResource(p: ClientData): ClientResource = {
    ClientResource(p.id.toString, routerProvider.get.link(p.id), p.name, p.balance.toString)
  }

  def makeWithdraw(id: String, amount: Float)(implicit mc: MarkerContext): Future[ClientResource] = {
    val clientFuture = clientRepository.withdraw(ClientId(id), amount)
    clientFuture.map(clientData => createClientResource(clientData))
  }

  def makeDeposit(id: String, amount: Float)(implicit mc: MarkerContext): Future[ClientResource] = {
    val clientFuture = clientRepository.deposit(ClientId(id), amount)
    clientFuture.map(clientData => createClientResource(clientData))
  }


  def makeInternalTransfer(id: String, receiverId: String, amount: Float)(implicit mc: MarkerContext): Future[Unit] = {
    clientRepository.internalTransfer(ClientId(id), ClientId(receiverId), amount)
  }

//  def makeExternalTransfer(id: String, targetBankId: String, receiverId: String, amount: String)(implicit mc: MarkerContext): Future[Option[Boolean]] = {
//    val clientFuture = clientRepository.get(ClientId(id))
//    val params = TransferParameterInterface(targetBankId, receiverId, amount)
//    requester.run(params)
//    clientFuture.map { _.map(clientData => clientData.withdraw(amount.toFloat))}
//  }
}
