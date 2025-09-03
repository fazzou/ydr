import sttp.openai.OpenAISyncClient
import sttp.openai.requests.images.creation.ImageCreationRequestBody
import sttp.openai.requests.images.creation.ImageCreationRequestBody.ImageCreationModel
import sttp.openai.requests.images.ResponseFormat
import sttp.openai.requests.images.Size
import os._
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4._

import sttp.openai.OpenAISyncClient
import sttp.openai.requests.images.creation.ImageCreationRequestBody
import sttp.openai.requests.images.ResponseFormat
import sttp.openai.requests.images.Size
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4._
import sttp.openai.requests.completions.chat.ChatRequestBody.ChatCompletionModel
import sttp.openai.requests.completions.chat.ChatRequestBody
import sttp.openai.requests.completions.chat.ChatRequestBody
import sttp.openai.requests.completions.chat.message.Message.UserMessage
import sttp.openai.requests.completions.chat.message.Content.TextContent
import yamusca.imports.*
import yamusca.implicits.*
import scala.util.Try

class ImgGen(token: String, basePath: os.Path) {
  private val client = OpenAISyncClient(token)

  private val promptPath = basePath / "prompt.txt"
  private val defaultPrompt = mustache"""
Based on the following podcast episode titles, propose a general image content that would summarize the podcast's theme:

{{ titles }}

Focus on symbolic or abstract representations rather than literal interpretations.
Please provide a description of an image that can be used as prompt for DALL-E 3.
Concrete style of an image is up to you, but it should be symbolic enough to be distinguishable on a thumbnail level from other podcasts.
Please respond only with DALL-E prompt.
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

      val prompt = generateImagePrompt(titles)

      scribe.info("Generated image prompt:")
      scribe.info(prompt)
      val response = client.createImage(
        ImageCreationRequestBody.ImageCreationBody(
          prompt = prompt,
          model = ImageCreationModel.DALLE3,
          n = Some(1),
          size = Some(Size.Large) // 1024x1024 for DALL-E 3
          // responseFormat removed - not supported
        )
      )

      response.data.headOption.foreach { imageData =>
        val imageUrl = imageData.url

        // Download and save the image using sttp
        val backend = HttpClientSyncBackend()
        val request = basicRequest.get(uri"$imageUrl").response(asByteArray)
        val imageResponse = request.send(backend)

        imageResponse.body match {
          case Right(bytes) =>
            os.write.over(coverPath, bytes)
            scribe.info(s"Cover image ${
                if (forceRegeneration) "regenerated" else "generated"
              } and saved to: $coverPath")
          case Left(error) =>
            scribe.warn(s"Failed to download image: $error")
        }
      }
    } else {
      scribe.info(
        s"Cover image already exists at: $coverPath. Use forceRegeneration flag to regenerate."
      )
    }
  }

  private def generateImagePrompt(titles: List[String]): String = {
    val client = OpenAISyncClient(token)

    val contentPrompt = mustache.render(getPrompt())(
      Context("titles" -> Value.of(titles.mkString("\n")))
    )

    val response = client.createChatCompletion(
      ChatRequestBody.ChatBody(
        model = ChatCompletionModel.GPT5,
        messages = List(UserMessage(TextContent(contentPrompt)))
      )
    )

    response.choices.headOption
      .map(_.message.content)
      .getOrElse("A symbolic representation of diverse topics")
  }
}
