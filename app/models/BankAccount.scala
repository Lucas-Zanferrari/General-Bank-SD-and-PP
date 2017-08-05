class BankAccount(val id: Int, initial: Float) {
  private var bal: Float = initial

  def this(id: Int) = this(id: Int, 0)

  def balance: Float = bal

  def deposit(amount: Float) {
    require(amount > 0)
    bal += amount
  }

  def withdraw(amount: Float): Boolean =
    if (amount > bal) false
    else {
      bal -= amount
      true
    }

  def internalTransfer(amount: Float, receiver: BankAccount): Boolean = {
    if (receiver != null && withdraw(amount)) {
      receiver.deposit(amount)
      true
    } else false
  }
}