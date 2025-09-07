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
  private val logsMap: mutable.Map[os.Path, List[String]] = mutable.Map.empty

  def update(dirState: DirState): Unit =
    state = state.copy(
      dirs = (state.dirs
        .map(dir => dir.path -> dir)
        .toMap + (dirState.path -> dirState)).values.toList
    )

  def updateLogs(path: os.Path, logs: List[String]): Unit =
    logsMap.put(path, logs)

  def getLogs(path: os.Path): List[String] =
    logsMap.getOrElse(path, List.empty)

  def getState: State =
    state
