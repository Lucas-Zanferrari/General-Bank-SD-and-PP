package v1.client

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import services.GeneralBankData
import play.api.http.Status
import play.api.libs.concurrent.CustomExecutionContext
import play.api.{Logger, MarkerContext}
import play.api.libs.ws.WSClient

import scala.concurrent.{Await, Future}

final case class ClientData(id: ClientId, name: String, var balance: Float)

class ClientId private (val underlying: Int) extends AnyVal {
  override def toString: String = underlying.toString
}

object ClientId {
  def apply(raw: String): ClientId = {
    require(raw != null)
    new ClientId(Integer.parseInt(raw))
  }
}

class ClientExecutionContext @Inject()(actorSystem: ActorSystem) extends CustomExecutionContext(actorSystem, "repository.dispatcher")

/**
  * A pure non-blocking interface for the ClientRepository.
  */
trait ClientRepository {
  def nextId()(implicit mc: MarkerContext): Int

  def create(data: ClientData)(implicit mc: MarkerContext): Future[List[ClientData]]

  def delete(data: ClientId)(implicit mc: MarkerContext): Unit

  def list()(implicit mc: MarkerContext): Future[Iterable[ClientData]]

  // get() and getOne() are very similar.
  // The 1st returns null if the client if not found.
  // The 2nd throws an IllegalArgumentException
  def get(id: ClientId)(implicit mc: MarkerContext): Future[Option[ClientData]]

  def getOne(id: ClientId)(implicit mc: MarkerContext): Future[Option[ClientData]]

  def withdraw(id: ClientId, amount: Float)(implicit mc: MarkerContext): Future[ClientData]

  def deposit(id: ClientId, amount: Float)(implicit mc: MarkerContext): Future[ClientData]

  def internalTransfer(id: ClientId, receivedId: ClientId, amount: Float)(implicit mc: MarkerContext): Future[Unit]

  def externalTransfer(id: ClientId, targetBankId: String, receiverId: ClientId, amount: Float): Future[Boolean]
}
/**
  * A trivial implementation for the Client Repository.
  *
  * A custom execution context is used here to establish that blocking operations should be
  * executed in a different thread than Play's ExecutionContext, which is used for CPU bound tasks
  * such as rendering.
  */
@Singleton
class ClientRepositoryImpl @Inject()(ws: WSClient, b: GeneralBankData)(implicit ec: ClientExecutionContext) extends ClientRepository {

  private val logger = Logger(this.getClass)
  var idCount = 0
  private var clientList: List[ClientData] = List()

  override def withdraw(id: ClientId, amount: Float)(implicit mc: MarkerContext): Future[ClientData] = {
    Future {
      withdrawHelper(id, amount)
    }
  }

  def withdrawHelper(id: ClientId, amount: Float): ClientData = {
    val clientData = clientList.find(client => client.id == id).getOrElse {
      throw new IllegalArgumentException(s"Client ${id.toString} not registered")
    }
    if (clientData.balance > amount) {
      clientData.balance = clientData.balance - amount
      clientData
    }
    else
      throw new IllegalArgumentException("Not enough balance.")
  }

  override def deposit(id: ClientId, amount: Float)(implicit mc: MarkerContext): Future[ClientData] = {
    Future {
      depositHelper(id, amount)
    }
  }

  def depositHelper(id: ClientId, amount: Float): ClientData = {
    val clientData = clientList.find(client => client.id == id).getOrElse {
      throw new IllegalArgumentException(s"Client ${id.toString} not registered")
    }
    clientData.balance = clientData.balance + amount
    clientData
  }

  override def internalTransfer(id: ClientId, receiverId: ClientId, amount: Float)(implicit mc: MarkerContext): Future[Unit] = {
    Future {
      withdrawHelper(id, amount)
      try {
        depositHelper(receiverId, amount)
      }
      catch {
        case e: Exception =>
          depositHelper(id, amount)
          throw e
      }
    }
  }

  override def externalTransfer(id: ClientId, targetBankId: String, receiverId: ClientId, amount: Float): Future[Boolean] = {
    Future {
      var result = false
      try {
        withdrawHelper(id, amount)
        val firstRequest = ws.url(s"${b.CENTRAL_BANK_HOST}${b.CENTRAL_BANK_ROOT_ENDPOINT}/$targetBankId")
          .withRequestTimeout(b.REQUEST_TIMEOUT)
          .get()
          .map { getHostResponse =>
            if (getHostResponse.status == Status.OK) {
              val targetBankHost = (getHostResponse.json \ "host").as[String]
              val targetBankName = (getHostResponse.json \ "name").as[String]
              println(s"$targetBankName@$targetBankHost was checked successfully with CentralBank@${b.CENTRAL_BANK_HOST}! Host is #$targetBankHost")
              val secondRequest = ws.url(s"${b.COMMUNICATION_PROTOCOL}$targetBankHost/v1/clientes/$receiverId/deposito/$amount")
                .withRequestTimeout(b.REQUEST_TIMEOUT)
                .post("")
                .map { sendDepositResponse =>
                  if (sendDepositResponse.status == Status.OK) {
                    result = true
                    println(s"${b.BANK_NAME}@${b.BANK_HOST}: transferred successfully with CentralBank@${b.CENTRAL_BANK_HOST}! Target host is #$targetBankHost")
                  }
                  else {
                    depositHelper(id, amount)
                    println(s"${b.BANK_NAME}@${b.BANK_HOST}: client #$receiverId does not exist on host $targetBankHost")
                  }
                }
              Await.result(secondRequest, b.REQUEST_TIMEOUT)
            }
            else {
              depositHelper(id, amount)
              println(s"${b.BANK_NAME}@${b.BANK_HOST}: could not validate existence of bank #$targetBankId")
            }
          }.recover {
            case e: Exception =>
              depositHelper(id, amount)
              println(s"${b.BANK_NAME}@${b.BANK_HOST}: bank #$targetBankId is not in Central Bank list, therefore it's considered nonexistent")
          }
        Await.result(firstRequest, b.REQUEST_TIMEOUT)
      }
      catch {
        case e: Exception =>
          depositHelper(id, amount)
          println(s"${b.BANK_NAME}@${b.BANK_HOST}: [Error] - ${e.getMessage}")
          throw e
      }
      result
    }
  }

  override def nextId()(implicit mc: MarkerContext): Int = {
    this.synchronized {
      idCount += 1
      idCount
    }
  }

  override def list()(implicit mc: MarkerContext): Future[Iterable[ClientData]] = {
    Future {
      logger.trace(s"list: ")
      clientList
    }
  }

  override def get(id: ClientId)(implicit mc: MarkerContext): Future[Option[ClientData]] = {
    Future {
      val client = clientList.find(client => client.id == id)
      logger.trace(s"get: client = $client")
      client
    }
  }

  override def create(data: ClientData)(implicit mc: MarkerContext): Future[List[ClientData]] = {
    Future {
      logger.trace(s"create: data = $data")
      clientList = clientList.::(data)
      clientList
    }
  }

  override def delete(id: ClientId)(implicit mc: MarkerContext) {
    Future {
      logger.trace(s"delete: id = $id")
      clientList = clientList.filter(client => client.id != id)
    }
  }

  private def findAccount(id: ClientId, vetor: List[ClientData]): Option[ClientData] = {
    val pos = buscaTR[ClientId](vetor, id, _ == _)
    if (pos != -1) Option {
      vetor(pos)
    }
    else throw new IllegalArgumentException(s"Client ${id.toString} not registered")
  }

  private def buscaTR[A](vetor: List[ClientData], x: A, f: (ClientId, A) => Boolean): Int = {
    @annotation.tailrec
    def go(i: Int): Int = {
      if (i < 0) -1
      else if (f(vetor(i).id, x)) i
      else go(i - 1)
    }

    go(vetor.length - 1)
  }

  override def getOne(id: ClientId)(implicit mc: MarkerContext): Future[Option[ClientData]] = {
    Future {
      val client = findAccount(id, clientList).map {
        client => client
      }
      logger.trace(s"getOne: client = $client")
      client
    }
  }
}
