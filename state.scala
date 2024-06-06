import org.ocpsoft.prettytime.PrettyTime
import java.time.ZonedDateTime
import scala.collection.mutable
import ox.channels.Channel
import java.time.LocalDateTime
import ox.channels.Source
import java.util.UUID

case class State(
    dirs: List[DirState],
    lastSynchronization: Option[LocalDateTime]
)

case class DirState(path: os.Path, synchronizationState: SynchronizationState)

sealed trait SynchronizationState
case object NotSynchronized extends SynchronizationState
case class Synchronizing(start: LocalDateTime) extends SynchronizationState
case class Synchronized(
    syncDate: LocalDateTime,
    output: List[String],
    error: Boolean
) extends SynchronizationState

class StateActor:
  private var state: State =
    State(dirs = List.empty, lastSynchronization = None)

  def update(dirState: DirState): Unit =
    state = state.copy(
      dirs = (state.dirs
        .map(dir => dir.path -> dir)
        .toMap + (dirState.path -> dirState)).values.toList
    )

  def getState: State =
    state
