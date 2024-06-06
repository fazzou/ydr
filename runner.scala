import java.time.Duration
import ox.channels.ActorRef
import ox.*
import scala.util.Try
import os.SubprocessException
import os.CommandResult
import java.time.LocalDateTime
import os.SubProcess.OutputStream
import os.ProcessOutput
import ox.channels.Channel
import scala.collection.mutable

sealed trait RunnerState
case object Idle extends RunnerState
case object Processing extends RunnerState

case class Runner(
    stateActor: ActorRef[StateActor],
    imgGenActor: Option[ActorRef[ImgGen]],
    commonFlags: List[String]
) {
  private var state = Idle
  val timeout = Duration.ofHours(1).toMillis()

  def processAll: Unit = supervised {
    println("will proces all")
    processMany(stateActor.ask(_.getState).dirs)
  }

  def processMany(dirs: List[DirState], isHeadAllowed: Boolean = true): Unit =
    supervised {
      dirs match
        case head :: next =>
          process(head, isHead = isHeadAllowed && head == dirs.head)
          processMany(next, false)
        case Nil =>
          ()
    }

  def process(dirState: DirState, isHead: Boolean) = supervised {
    val output: mutable.Buffer[String] = mutable.Buffer()
    Try {
      val logFile = dirState.path / "log.txt"

      println("updating state actor about sync start")
      stateActor.tell(
        _.update(
          dirState.copy(synchronizationState =
            Synchronizing(LocalDateTime.now())
          )
        )
      )

      val write = ProcessOutput.Readlines { line =>
        println(line)
        os.write.append(logFile, line)
        output.append(line)
      }

      println("spawning proc")
      val spawned = os
        .proc("yt-dlp", commonFlags)
        .spawn(
          cwd = dirState.path,
          stdout = write,
          stderr = write
        )
      val success = spawned.join(timeout)
      if (success) println("yt-dlp command succeeded")
      success
    }.recover {
      case SubprocessException(cr: CommandResult) if cr.exitCode == 101 =>
        true
      case e =>
        println(s"failed downloading for ${dirState.path} because of:")
        e.printStackTrace()
        false
    }.map { success =>
      println("finished")
      imgGenActor.foreach(
        _.tell(_.generateImage(dirState.path, forceRegeneration = false))
      )
      stateActor.tell(
        _.update(
          dirState.copy(synchronizationState =
            Synchronized(LocalDateTime.now(), output = output.toList, !success)
          )
        )
      )
    }
  }
}
