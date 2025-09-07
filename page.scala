import org.ocpsoft.prettytime.PrettyTime
import scalatags.Text.TypedTag
import scalatags.Text.all.*
import scalatags.Text.svgAttrs
import scalatags.Text.svgTags

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Page:

  private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  private def base(bodyContent: TypedTag[String]) =
    html(
      head(
        meta(charset := "UTF-8"),
        meta(
          name := "viewport",
          content := "width=device-width, initial-scale=1.0"
        ),
        link(
          rel := "stylesheet",
          href := "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css"
        ),
        title := "ydr",
        script(src := "https://cdn.tailwindcss.com"),
        script(src := "https://unpkg.com/htmx.org@1.9.12"),
        script(src := "https://unpkg.com/htmx.org@1.9.12/dist/ext/ws.js")
      ),
      body(`class` := "bg-gray-100 min-h-screen font-comic")(
        div(`class` := "container mx-auto px-4 py-8")(
          div(`class` := "flex justify-end mb-4")(promptEditButton),
          bodyContent
        )
      )
    )

  private def promptEditButton =
    a(
      href := "/prompt",
      `class` := "text-gray-400 hover:text-gray-600 right-5"
    )(
      i(`class` := "fas fa-cog")
    )

  def index(s: State) = base(
    div(
      div(
        id := "content",
        `class` := "bg-white shadow rounded-lg overflow-hidden"
      )(
        newItemEntry,
        renderStateList(s),
        buttonRow
      ),
      logsModal
    )
  )

  def promptPage(currentPrompt: String): TypedTag[String] = base(
    div(`class` := "bg-white shadow rounded-lg overflow-hidden p-6")(
      h1(`class` := "text-2xl font-bold mb-4")("Edit Prompt Template"),
      form(
        attr("hx-post") := "/prompt",
        attr("hx-target") := "#prompt-form",
        id := "prompt-form",
        `class` := "space-y-4"
      )(
        textarea(
          name := "prompt",
          id := "prompt",
          `class` := "w-full h-64 p-2 border border-gray-300 rounded",
          placeholder := "Enter your prompt template here..."
        )(currentPrompt),
        button(
          `type` := "submit",
          `class` := "bg-blue-400 hover:bg-blue-600 text-white font-bold py-2 px-4 rounded"
        )("Save Prompt")
      ),
      div(id := "message")
    )
  )

  def promptSaved(newPrompt: String): TypedTag[String] =
    div(id := "prompt-form")(
      p(cls := "text-green-600 mb-4")("Prompt saved successfully!"),
      textarea(
        name := "prompt",
        id := "prompt",
        cls := "w-full h-64 p-2 border border-gray-300 rounded",
        placeholder := "Enter your prompt template here..."
      )(newPrompt),
      button(
        `type` := "submit",
        cls := "bg-blue-400 hover:bg-blue-600 text-white font-bold py-2 px-4 rounded mt-4"
      )("Save Prompt"),
      script("htmx.trigger('#message', 'showMessage');")
    )

  def promptSaveError(error: String): TypedTag[String] =
    div(id := "message", cls := "text-red-600 mt-4")(
      p(s"Error saving prompt: $error"),
      script("htmx.trigger('#message', 'showMessage');")
    )

  def renderStateList(s: State) = {
    ul(
      id := "state-list",
      `class` := "divide-y divide-gray-200",
      attr("hx-get") := "/state-list",
      attr("hx-trigger") := "every 3s",
      attr("hx-target") := "#state-list"
    )(
      s.dirs.map(toViewEntry) ++
        nextAutosyncEntry(s.lastSynchronization).toList
    )
  }

  private def newItemEntry = {
    val hxPost = attr("hx-post")
    div(`class` := "p-4 bg-white")(
      form(
        hxPost := "/add",
        attr("hx-target") := "#content",
        attr("hx-swap") := "innerHTML",
        `class` := "space-y-4"
      )(
        input(
          `type` := "text",
          name := "name",
          placeholder := "Name of new item",
          `class` := "w-full px-4 py-2 rounded border border-gray-300"
        ),
        input(
          `type` := "text",
          name := "url",
          placeholder := "Url of new item",
          `class` := "w-full px-4 py-2 rounded border border-gray-300"
        ),
        button(
          `type` := "submit",
          `class` := "w-full flex items-center justify-center bg-blue-400 hover:bg-blue-600 text-white font-semibold py-2 px-4 rounded"
        )(i(`class` := "fa-solid fa-plus mr-2"), "Add New Item")
      )
    )
  }

  private def toViewEntry(item: DirState) = {
    val badgeColor = item.synchronizationState match {
      case synced: Synchronized if synced.error => "bg-red-200 text-red-800"
      case synced: Synchronized                 => "bg-green-200 text-green-800"
      case _ => "bg-yellow-200 text-yellow-800"
    }

    val (tooltip, statusText) = item.synchronizationState match {
      case NotSynchronized =>
        None -> "not synchronized"
      case Synchronizing(start) =>
        Some(format(start)) -> s"started ${formatRelative(start)}"
      case Synchronized(syncDate, output, error) =>
        Some(format(syncDate)) -> s"synchronized ${formatRelative(syncDate)}"
    }

    li(
      `class` := "p-4 flex flex-col sm:flex-row justify-between items-start sm:items-center"
    )(
      span(`class` := "text-lg font-medium mb-2 sm:mb-0")(item.path.last),
      div(`class` := "flex items-center gap-2")(
        button(
          `class` := "text-xs bg-gray-600 text-white py-1 px-3 rounded hover:bg-gray-700 transition-colors",
          attr("hx-get") := s"/logs?path=${item.path}",
          attr("hx-target") := "#logs-modal-content",
          attr("hx-trigger") := "click",
          onclick := "document.getElementById('logs-modal').classList.remove('hidden')"
        )("Logs"),
        button(
          `class` := "text-xs bg-blue-600 text-white py-1 px-3 rounded hover:bg-blue-700 transition-colors",
          attr("hx-post") := s"/resync-single?path=${item.path}",
          attr("hx-target") := "#state-list",
          attr("hx-swap") := "outerHTML",
          item.synchronizationState match {
            case Synchronizing(_) => disabled := true
            case _                => ()
          }
        )(
          item.synchronizationState match {
            case Synchronizing(_) => "Syncing..."
            case _                => "Sync"
          }
        ),
        span(
          `class` := s"text-xs font-semibold py-1 px-3 rounded-full $badgeColor",
          tooltip.map(title := _)
        )(statusText)
      )
    )
  }

  private def nextAutosyncEntry(nextAutosync: Option[LocalDateTime]) = {
    nextAutosync.map { definedNextAutosync =>
      li(`class` := "p-4 text-sm text-gray-600")(
        s"Next sync: ${definedNextAutosync.format(dtf)}"
      )
    }
  }

  private def buttonRow = {
    div(`class` := "p-4 bg-gray-50")(
      form(
        attr("hx-post") := "/resync",
        attr("hx-trigger") := "submit",
        attr("hx-target") := "#state-list"
      )(
        button(
          `type` := "submit",
          `class` := "w-full flex items-center justify-center px-4 py-2 bg-blue-400 hover:bg-blue-600 text-white font-semibold rounded-lg shadow"
        )(
          i(`class` := "fa-solid fa-arrows-rotate mr-2"),
          "Resync All"
        )
      )
    )
  }

  def format(date: LocalDateTime): String =
    date.format(dtf)

  def formatRelative(date: LocalDateTime): String =
    val prettyTime = new PrettyTime()
    prettyTime.format(date)

  private def logsModal = div(
    id := "logs-modal",
    `class` := "hidden fixed inset-0 bg-gray-600 bg-opacity-75 overflow-y-auto h-full w-full z-50"
  )(
    div(`class` := "relative top-20 mx-auto p-5 border w-11/12 max-w-4xl shadow-lg rounded-md bg-white")(
      div(`class` := "mt-3")(
        div(`class` := "flex justify-between items-center mb-4")(
          h3(`class` := "text-lg font-medium text-gray-900")("Logs"),
          button(
            `class` := "text-gray-400 hover:text-gray-600",
            onclick := "document.getElementById('logs-modal').classList.add('hidden')"
          )(
            i(`class` := "fas fa-times")
          )
        ),
        div(
          id := "logs-modal-content",
          `class` := "max-h-96 overflow-y-auto"
        )(
          p(`class` := "text-gray-500")("Loading logs...")
        )
      )
    )
  )

  def renderLogs(path: String, logs: List[String]): TypedTag[String] = div(
    id := "logs-modal-content",
    `class` := "max-h-96 overflow-y-auto"
  )(
    if (logs.isEmpty) {
      p(`class` := "text-gray-500")("No logs available for this directory.")
    } else {
      pre(`class` := "bg-gray-100 p-4 rounded text-xs font-mono overflow-x-auto")(
        logs.mkString("\n")
      )
    }
  )
