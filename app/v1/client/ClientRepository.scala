package v1.client

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import play.api.libs.concurrent.CustomExecutionContext
import play.api.{Logger, MarkerContext}

import scala.concurrent.Future

final case class ClientData(id: ClientId, name: String, initial: String)
{
  def this(id: ClientId, name:String) = this(id: ClientId, name, "0")

  private var bal: Float = initial.toFloat

  def balance: String = bal.toString

  def deposit(amount: Float): Boolean ={
    require(amount > 0)
    bal += amount
    true
  }

  def withdraw(amount: Float): Boolean =
    if (amount > bal) false
    else {
      bal -= amount
      true
    }

  def internalTransfer(receiver: ClientData, amount: Float): Boolean = {
    if (receiver != null && withdraw(amount)) receiver.deposit(amount)
    else false
  }
}

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

  def delete(data: ClientId)(implicit mc: MarkerContext)

  def list()(implicit mc: MarkerContext): Future[Iterable[ClientData]]

  def get(id: ClientId)(implicit mc: MarkerContext): Future[Option[ClientData]]

  def getOne(id: ClientId)(implicit mc: MarkerContext): Option[ClientData]
}

/**
  * A trivial implementation for the Client Repository.
  *
  * A custom execution context is used here to establish that blocking operations should be
  * executed in a different thread than Play's ExecutionContext, which is used for CPU bound tasks
  * such as rendering.
  */
@Singleton
class ClientRepositoryImpl @Inject()()(implicit ec: ClientExecutionContext) extends ClientRepository {

  private val logger = Logger(this.getClass)

  var idCount = 0

  private var clientList: List[ClientData] = List()

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
    if (pos != -1) Option{vetor(pos)}
    else null
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

  override def getOne(id: ClientId)(implicit mc: MarkerContext): Option[ClientData] = {
    val client = findAccount(id,clientList)
    logger.trace(s"getOne: client = $client")
    client
  }
}
