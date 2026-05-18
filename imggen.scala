import sttp.openai.OpenAISyncClient
import os._
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4._
import sttp.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.openai.requests.completions.chat.ChatRequestBody
import sttp.openai.requests.completions.chat.message.Message.UserMessage
import sttp.openai.requests.completions.chat.message.Content.TextContent
import yamusca.imports.*
import yamusca.implicits.*
import scala.util.Try
import java.util.Base64
import ox.resilience.retry
import ox.scheduling.Schedule
import scala.concurrent.duration.DurationInt

class ImgGen(token: String, basePath: os.Path) {
  private val client = OpenAISyncClient(token)
  private val retrySchedule = Schedule.exponentialBackoff(10.seconds).maxRetries(3)

  private val promptPath = basePath / "prompt.txt"
  private val defaultPrompt = mustache"""
Based on the following podcast episode titles, propose a general image content that would summarize the podcast's theme:

{{ titles }}

Focus on symbolic or abstract representations rather than literal interpretations.
Please provide a description of an image that can be used as prompt for an image generation model.
Concrete style of an image is up to you, but it should be symbolic enough to be distinguishable on a thumbnail level from other podcasts.
Please respond only with the image prompt.
"""

  def getPrompt(): Template = {
    for {
      fileContent <- Try(os.read(promptPath)).toEither
      template <- toPromptTemplate(fileContent)
    } yield template
  }.left.map { err =>
    scribe.warn(s"Error loading prompt: $err")
    scribe.info("Falling back to default prompt")
    defaultPrompt
  }.merge

  def savePrompt(content: String): Either[String, Unit] = {
    toPromptTemplate(content).map { _ =>
      os.write.over(promptPath, content)
    }
  }

  private def toPromptTemplate(content: String): Either[String, Template] = {
    mustache
      .parse(content)
      .left
      .map(err => s"Failed to parse prompt template: $err")
      .flatMap { parsedTemplate =>
        val keys = parsedTemplate.els.collect { case Variable(key, _) => key }
        keys.toList match {
          case "titles" :: Nil => Right(parsedTemplate)
          case other =>
            Left(
              s"Expected template to contain only one key - titles, got: ${other.mkString(", ")}"
            )
        }
      }
  }

  def regenerateImages(dirs: Seq[os.Path]): Unit = {
    dirs.foreach(generateImage(_, forceRegeneration = true))
  }

  def generateImage(
      directory: os.Path,
      forceRegeneration: Boolean = false
  ): Unit = {
    val coverPath = directory / "cover.jpg"

    if (forceRegeneration || !os.exists(coverPath)) {
      scribe.info("Will generate cover image")
      val titles = os
        .list(directory)
        .filter(_.ext == "mp3")
        .map(_.baseName)
        .toList

      val result = for {
        prompt <- generateImagePrompt(titles)
        _ = scribe.info("Generated image prompt:")
        _ = scribe.info(prompt)
        bytes <- generateGptImage(prompt)
      } yield bytes

      result match {
        case Right(bytes) =>
          os.write.over(coverPath, bytes)
          scribe.info(s"Cover image ${
              if (forceRegeneration) "regenerated" else "generated"
            } and saved to: $coverPath")
        case Left(error) =>
          scribe.warn(s"Failed to generate image: $error")
      }
    } else {
      scribe.info(
        s"Cover image already exists at: $coverPath. Use forceRegeneration flag to regenerate."
      )
    }
  }

  private def generateGptImage(prompt: String): Either[String, Array[Byte]] = {
    // gpt-image-1 always returns base64 (no URL option), so we call the API directly
    // and decode the b64_json field manually — sttp-openai's response model only models `url`.
    val backend = HttpClientSyncBackend()
    val body = ujson.Obj(
      "model" -> "gpt-image-1",
      "prompt" -> prompt,
      "n" -> 1,
      "size" -> "1024x1024"
    )
    def sendOnce(): Response[Either[String, String]] =
      basicRequest
        .post(uri"https://api.openai.com/v1/images/generations")
        .header("Authorization", s"Bearer $token")
        .header("Content-Type", "application/json")
        .body(body.render())
        .readTimeout(2.minutes)
        .response(asString)
        .send(backend)

    retryWithLogging("OpenAI image request")(sendOnce()).flatMap { response =>
      response.body.left.map(err => s"OpenAI returned error body: $err").flatMap { raw =>
        Try {
          val json = ujson.read(raw)
          val b64 = json("data").arr.head("b64_json").str
          Base64.getDecoder.decode(b64)
        }.toEither.left
          .map(err => s"Failed to parse OpenAI response: ${err.getMessage}; raw=$raw")
      }
    }
  }

  private def generateImagePrompt(titles: List[String]): Either[String, String] = {
    val contentPrompt = mustache.render(getPrompt())(
      Context("titles" -> Value.of(titles.mkString("\n")))
    )
    val request = ChatRequestBody.ChatBody(
      model = ChatCompletionModel.GPT5,
      messages = List(UserMessage(TextContent(contentPrompt)))
    )

    retryWithLogging("OpenAI chat request")(client.createChatCompletion(request))
      .map(response =>
        response.choices.headOption
          .map(_.message.content)
          .getOrElse("A symbolic representation of diverse topics")
      )
  }

  private def retryWithLogging[T](label: String)(action: => T): Either[String, T] =
    Try(retry(retrySchedule) {
      try action
      catch
        case t: Throwable =>
          scribe.warn(s"$label failed, will retry: ${t.getMessage}")
          throw t
    }).toEither.left.map(err => s"$label failed after retries: ${err.getMessage}")
}
