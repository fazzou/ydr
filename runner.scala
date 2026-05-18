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
    modelActor: ActorRef[ModelActor],
    imgGenActor: Option[ActorRef[ImgGen]],
    commonFlags: List[String]
) {
  private var state = Idle
  val timeout = Duration.ofHours(1).toMillis()

  def processAll: Unit = supervised {
    scribe.info("Will process all")
    processMany(modelActor.ask(_.getModel).dirs.values.toList)
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
    Try {
      val logFile = dirState.path / "log.txt"
      os.write.over(logFile, "")

      scribe.debug("Updating model actor about sync start")
      modelActor.tell(
        _.upsertDir(
          dirState.copy(synchronizationState =
            Synchronizing(LocalDateTime.now())
          )
        )
      )

      val write = ProcessOutput.Readlines { line =>
        scribe.info(line)
        os.write.append(logFile, line + "\n")
      }

      scribe.info("Spawning proc")
      val spawned = os
        .proc("yt-dlp", commonFlags)
        .spawn(
          cwd = dirState.path,
          stdout = write,
          stderr = write
        )
      val success = spawned.join(timeout)
      if (success) scribe.info("yt-dlp command succeeded")
      success
    }.recover {
      case SubprocessException(cr: CommandResult) if cr.exitCode == 101 =>
        true
      case e =>
        scribe.warn(s"failed downloading for ${dirState.path} because of:")
        e.printStackTrace()
        false
    }.map { success =>
      scribe.info("finished")
      imgGenActor.foreach(
        _.tell(_.generateImage(dirState.path, forceRegeneration = false))
      )
      modelActor.tell(
        _.upsertDir(
          dirState.copy(synchronizationState =
            Synchronized(LocalDateTime.now(), error = !success)
          )
        )
      )
    }
  }
}
