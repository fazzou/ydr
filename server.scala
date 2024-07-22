import ox.*
import ox.channels.*
import sttp.tapir.*
import sttp.tapir.server.netty.sync.NettySyncServer
import scalatags.Text.svgAttrs
import scalatags.Text.svgTags
import scalatags.Text.all.*
import scala.collection.mutable
import java.time.ZonedDateTime
import scala.concurrent.Future
import org.ocpsoft.prettytime.PrettyTime;
import sttp.tapir.server.netty.NettyConfig
import scala.util.Try
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import javax.net.ssl.KeyManagerFactory
import sttp.tapir.server.ServerEndpoint
import scala.concurrent.ExecutionContext
import sttp.tapir.server.netty.sync.OxStreams
import sttp.ws.WebSocketFrame
import scala.concurrent.duration.DurationInt
import yamusca.data.Show
import yamusca.data.Template

object YdrServer:
  val dataDir = os.Path(sys.env.get("DATA_DIR").getOrElse("/data"))
  val port = sys.env.get("PORT").map(_.toInt).getOrElse(80)
  val commonFlags =
    sys.env
      .get("COMMON_FLAGS")
      .map(_.replaceAll("\n", " ").split(" ").toList)
      .getOrElse(List.empty)
  val interval = sys.env
    .get("INTERVAL")
    .map(java.time.Duration.parse)
    .map(_.toMillis)
    .map(Duration(_, TimeUnit.MILLISECONDS))
  val token = sys.env.get("OPENAI_TOKEN")

  def handleWithErrorHandling[INPUT, OUTPUT](
      logic: INPUT => OUTPUT
  ): INPUT => Either[String, OUTPUT] =
    input => Try(logic(input)).toEither.left.map(_.toString)

  def index(stateActor: ActorRef[StateActor]) = endpoint.get
    .out(htmlBodyUtf8)
    .errorOut(stringBody)
    .handle(handleWithErrorHandling { _ =>
      Page.index(stateActor.ask(_.getState)).render
    })

  case class NewItem(name: String, url: String) derives Schema

  def add(stateActor: ActorRef[StateActor], runnerActor: ActorRef[Runner]) =
    endpoint.post
      .in("add")
      .in(formBody[NewItem])
      .out(htmlBodyUtf8)
      .errorOut(stringBody)
      .handle(handleWithErrorHandling { newItem =>
        val newDir = dataDir / newItem.name
        os.makeDir.all(newDir)
        os.write(newDir / "yt-dlp.conf", newItem.url)
        val newDirState = DirState(newDir, NotSynchronized)
        stateActor.tell(_.update(newDirState))

        runnerActor.tell(_.process(newDirState, isHead = false))

        val currentState = stateActor.ask(_.getState)
        Page.index(currentState).render
      })

  def resync(stateActor: ActorRef[StateActor], runnerActor: ActorRef[Runner]) =
    endpoint.post
      .in("resync")
      .out(htmlBodyUtf8)
      .errorOut(stringBody)
      .handle(handleWithErrorHandling { _ =>
        runnerActor.tell(_.processAll)
        Page.renderStateList(stateActor.ask(_.getState)).render
      })

  def stateList(stateActor: ActorRef[StateActor]) = endpoint
    .in("state-list")
    .out(htmlBodyUtf8)
    .handleSuccess(_ => Page.renderStateList(stateActor.ask(_.getState)).render)

  def getPrompt(imgGenActor: ActorRef[ImgGen]) = endpoint.get
    .in("prompt")
    .out(htmlBodyUtf8)
    .errorOut(stringBody)
    .handle(handleWithErrorHandling { _ =>
      val currentPrompt = Template.asString(imgGenActor.ask(_.getPrompt()))
      Page.promptPage(currentPrompt).render
    })

  case class SavePrompt(prompt: String) derives Schema

  def savePrompt(
      imgGenActor: ActorRef[ImgGen],
      stateActor: ActorRef[StateActor]
  ) = endpoint.post
    .in("prompt")
    .in(formBody[SavePrompt])
    .out(htmlBodyUtf8)
    .errorOut(stringBody)
    .handle(handleWithErrorHandling { case SavePrompt(prompt) =>
      val decodedPrompt = java.net.URLDecoder.decode(prompt, "UTF-8")
      scribe.info("Received prompt:")
      scribe.info(decodedPrompt)
      imgGenActor.ask(_.savePrompt(decodedPrompt)) match {
        case Right(_) =>
          // Regenerate images for all directories
          val state = stateActor.ask(_.getState)
          imgGenActor.tell(_.regenerateImages(state.dirs.map(_.path)))
          Page.promptSaved(decodedPrompt).render
        case Left(error) =>
          Page.promptSaveError(error).render
      }
    })

  def main(args: Array[String]): Unit =
    supervised {
      val imgGenActor =
        token.map(existingToken =>
          Actor.create(new ImgGen(existingToken, dataDir))
        )
      val stateActor = Actor.create(new StateActor)
      initState(stateActor)
      val runnerActor =
        Actor.create(new Runner(stateActor, imgGenActor, commonFlags))
      fork {
        runnerActor.tell(_.processAll)
        interval.fold(()) { definedInterval =>
          forever {
            sleep(definedInterval)
            runnerActor.tell(_.processAll)
          }
        }
      }

      val serverBinding =
        useInScope(
          NettySyncServer(
            NettyConfig.default.copy(
              host = "0.0.0.0",
              port = port
            )
          ).addEndpoints(
            imgGenActor.toList.flatMap(definedActor =>
              List(
                getPrompt(definedActor),
                savePrompt(definedActor, stateActor)
              )
            ) ++ List(
              stateList(stateActor),
              index(stateActor),
              add(stateActor, runnerActor),
              resync(stateActor, runnerActor)
            )
          ).start()
        )(_.stop())
      scribe.info(s"Tapir is running on port ${serverBinding.port}")
      never
    }

  private def initState(stateActor: ActorRef[StateActor]) = supervised {
    os
      .walk(dataDir)
      .filter { path =>
        os.exists(path / "yt-dlp.conf")
      }
      .map(DirState(_, NotSynchronized))
      .foreach { dirState =>
        stateActor.tell(_.update(dirState))
      }
  }
