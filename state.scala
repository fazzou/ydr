import scala.collection.immutable.VectorMap
import java.time.LocalDateTime

case class YdrModel(
    dirs: VectorMap[os.Path, DirState],
    nextScheduledSync: Option[LocalDateTime]
)

object YdrModel:
  val empty: YdrModel = YdrModel(VectorMap.empty, None)

case class DirState(path: os.Path, synchronizationState: SynchronizationState)

sealed trait SynchronizationState
case object NotSynchronized extends SynchronizationState
case class Synchronizing(start: LocalDateTime) extends SynchronizationState
case class Synchronized(syncDate: LocalDateTime, error: Boolean)
    extends SynchronizationState

class ModelActor:
  private var model: YdrModel = YdrModel.empty

  def upsertDir(dirState: DirState): Unit =
    model = model.copy(dirs = model.dirs.updated(dirState.path, dirState))

  def findDir(path: os.Path): Option[DirState] =
    model.dirs.get(path)

  def setNextSync(at: Option[LocalDateTime]): Unit =
    model = model.copy(nextScheduledSync = at)

  def getModel: YdrModel = model
