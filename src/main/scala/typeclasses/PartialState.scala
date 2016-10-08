package typeclasses

trait PartialState[D] {
  import PartialState._
  type S
  type K

  def recoverState(previousState: S, diff: D): S

  def newState(diff: D): RecoverResult[S]

  def toKey(state: S): K

  def toKey(diff: D): K

}

object PartialState {
  sealed trait RecoverResult[S]
  case object Delete extends RecoverResult[Nothing]
  case class Update[S](newState: S) extends RecoverResult[S]
}
