package typeclasses

import org.apache.spark.streaming.dstream._
import org.apache.spark.streaming._
import org.apache.spark.rdd.RDD
import scala.reflect.ClassTag


trait PartialState[D] {
  type S
  type K
  import PartialState._

  def recoverState(previousState: S, diff: D): RecoverResult[S]

  def newState(diff: D): RecoverResult[S]

  def stateToKey(state: S): K

  def diffToKey(diff: D): K

}

object PartialState {
  type Aux[D, S0, K0] = PartialState[D] {
    type S = S0
    type K = K0
  }
  sealed trait RecoverResult[S]
  case object Delete extends RecoverResult[Nothing]
  case class Update[S](newState: S) extends RecoverResult[S]

  def applyNewDiff[D, S, K](diff: D, state: State[S])(implicit ev: PartialState.Aux[D, S, K]) = {
    val recoverResult = state.getOption match {
      case None => ev.newState(diff)
      case Some(s) => ev.recoverState(s, diff)
    }
    applyRecoverResult(recoverResult, state)
  }

  def applyRecoverResult[S](res: RecoverResult[S], state: State[S]): Option[S] = res match {
    case Delete =>
      state.remove
      None
    case Update(s) =>
      state.update(s)
      Some(s)
  }

  def getStateSpec[D, S, K](implicit ev: PartialState.Aux[D, S, K]): StateSpec[K, D, S, S] =
    StateSpec.function { (t: Time, k: K, v: Option[D], s: State[S]) =>
      v.flatMap { applyNewDiff(_, s) }
    }

  def recoverStateFromPartial[D: ClassTag, S: ClassTag, K: ClassTag](partialStates: DStream[D],
                                       loadedStates: Option[RDD[S]] = None)(implicit ev: PartialState.Aux[D, S, K])
      : StreamingTask[DStream[S]] = StreamingTask { ssc =>
    partialStates
      .map { x => (ev.diffToKey(x), x) }
      .mapWithState(getStateSpec)

  }
}

case class StreamingTask[R](run: StreamingContext => R)
