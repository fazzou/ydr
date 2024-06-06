import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import scalatags.Text.svgAttrs
import scalatags.Text.svgTags
import scalatags.Text.all.*
import org.ocpsoft.prettytime.PrettyTime

object Page:

  private val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  def index(s: State) =
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
      body(`class` := "bg-gray-100 min-h-screen")(
        div(
          id := "content",
          `class` := "container mx-auto px-4 py-8"
        )(
          div(`class` := "bg-white shadow rounded-lg overflow-hidden")(
            newItemEntry,
            renderStateList(s),
            buttonRow
          )
        )
      )
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
          `class` := "w-full px-4 py-2 rounded border-gray-300"
        ),
        input(
          `type` := "text",
          name := "url",
          placeholder := "Url of new item",
          `class` := "w-full px-4 py-2 rounded border-gray-300"
        ),
        button(
          `type` := "submit",
          `class` := "w-full flex items-center justify-center bg-blue-500 hover:bg-blue-600 text-white font-semibold py-2 px-4 rounded"
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
      span(
        `class` := s"text-xs font-semibold py-1 px-3 rounded-full $badgeColor",
        tooltip.map(title := _)
      )(statusText)
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
          `class` := "w-full flex items-center justify-center px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white font-semibold rounded-lg shadow"
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
