
class Bank{
  def findAccount(id: Int, vetor: Array[BankAccount]): BankAccount = {
    val pos = buscaTR[Int](vetor, id, _ == _)
    if (pos != -1) vetor(pos)
    else null
  }

  def buscaTR[A](vetor: Array[BankAccount], x: A, f: (Int, A) => Boolean): Int = {
    @annotation.tailrec
    def go(i: Int): Int = {
      if (i < 0) -1
      else if (f(vetor(i).id, x)) i
      else go(i - 1)
    }
    go(vetor.length - 1)
  }
  /*
    val conta1 = new BankAccount(123, 1000)
    val conta2 = new BankAccount(456)
    val contas = Array[BankAccount](conta1, conta2)

    conta1.withdraw(200)
    conta1.internalTransfer(200, findAccount(456, contas))
    conta2.deposit(100)

    contas.foreach(f => println("id: " + f.id + "; saldo: " + f.balance))

  */
}