package v1.client

import javax.inject.{Inject, Provider}

import play.api.MarkerContext

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import services.{TransferParameterInterface, TransferRequest}

/**
  * DTO for displaying client information.
  */
case class ClientResource(id: String, link: String, name: String, initial: String)

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
        "initial" -> client.initial
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
    val data = ClientData(ClientId(nextId.toString), clientInput.name, clientInput.initial)
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

  def remove(id: String)(implicit mc: MarkerContext): Unit = clientRepository.delete(ClientId(id))

  private def createClientResource(p: ClientData): ClientResource = {
    ClientResource(p.id.toString, routerProvider.get.link(p.id), p.name, p.initial)
  }

  def getBalance(id: String)(implicit mc: MarkerContext): Future[Option[String]] = {
    val clientFuture = clientRepository.get(ClientId(id))
    clientFuture.map { _.map(clientData => clientData.balance)
    }
  }

  def makeWithdraw(id: String, amount: String)(implicit mc: MarkerContext): Future[Option[Boolean]] = {
    val clientFuture = clientRepository.get(ClientId(id))
    clientFuture.map { _.map(clientData => clientData.withdraw(amount.toFloat))
    }
  }

  def makeDeposit(id: String, amount: String)(implicit mc: MarkerContext): Future[Option[Boolean]] = {
    val clientFuture = clientRepository.get(ClientId(id))
    clientFuture.map { _.map(clientData => clientData.deposit(amount.toFloat))
    }
  }

  def makeInternalTransfer(id:String, receiver: String, amount: String)(implicit mc: MarkerContext): Future[Option[Boolean]] = {
    val clientFuture = clientRepository.get(ClientId(id))
    val receiverClient = clientRepository.getOne(ClientId(receiver))
    clientFuture.map { _.map(_.transfer(receiverClient, amount.toFloat))}
  }

  def makeExternalTransfer(id:String, bank: String, receiver: String, amount: String)(implicit mc: MarkerContext): Future[Option[Boolean]] = {
    val clientFuture = clientRepository.get(ClientId(id))
    val params = TransferParameterInterface(clientFuture, bank, receiver, amount)
    requester.run(params)
    clientFuture.map { _.map(clientData => clientData.withdraw(amount.toFloat))}
  }
}
