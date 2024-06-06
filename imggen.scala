import sttp.openai.OpenAISyncClient
import sttp.openai.requests.images.creation.ImageCreationRequestBody
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

class ImgGen(token: String) {
    private val client = OpenAISyncClient(token)

    def generateImage(directory: os.Path, forceRegeneration: Boolean = false): Unit = {
        val coverPath = directory / "cover.jpg"
        
        if (forceRegeneration || !os.exists(coverPath)) {
            println("will generate cover image")
            val titles = os.list(directory)
                .filter(_.ext == "mp3")
                .map(_.baseName)
                .toList

            val prompt = generateImagePrompt(titles)
            
            println("prompt:")
            println(prompt)
            val response = client.createImage(
                ImageCreationRequestBody.ImageCreationBody(
                    prompt = prompt,
                    model = "dall-e-3",
                    n = Some(1),
                    size = Some(Size.Large),
                    responseFormat = Some(ResponseFormat.URL)
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
                        println(s"Cover image ${if (forceRegeneration) "regenerated" else "generated"} and saved to: $coverPath")
                    case Left(error) =>
                        println(s"Failed to download image: $error")
                }
            }
        } else {
            println(s"Cover image already exists at: $coverPath. Use forceRegeneration flag to regenerate.")
        }
    }

    private def generateImagePrompt(titles: List[String]): String = {
        val client = OpenAISyncClient(token)
        val contentPrompt = s"""Based on the following podcast episode titles, propose a general image content that would summarize the podcast's theme:
                               |
                               |${titles.mkString("\n")}
                               |
                               |Focus on symbolic or abstract representations rather than literal interpretations.
                               |Please provide a description of an image that can be used as prompt for DALL-E 3.
                               |Concrete style of an image is up to you, but it should be symbolic enough to be distinguishable on a thumbnail level from other podcasts.
                               |Please respond only with DALL-E prompt.
                               |""".stripMargin
        
        val response = client.createChatCompletion(
            ChatRequestBody.ChatBody(
                model = ChatCompletionModel.GPT4o,
                messages = List(UserMessage(TextContent(contentPrompt)))
            )
        )
        
        response.choices.headOption.map(_.message.content).getOrElse("A symbolic representation of diverse topics")
    }
}
