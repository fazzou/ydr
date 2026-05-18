class PageRenderLogsTests extends munit.FunSuite:

  test("renderLogs with empty list shows 'no logs' message and no <pre>"):
    val html = Page.renderLogs("/tmp/x", List.empty).render
    assert(html.contains("No logs available"))
    assert(!html.contains("<pre"))

  test("renderLogs with non-empty list renders lines inside <pre>"):
    val html = Page.renderLogs("/tmp/x", List("first", "second")).render
    assert(html.contains("<pre"))
    assert(html.contains("first"))
    assert(html.contains("second"))

  test("renderLogs joins lines with newlines"):
    val html = Page.renderLogs("/tmp/x", List("a", "b", "c")).render
    assert(html.contains("a\nb\nc"))

  test("renderLogs root div keeps id=logs-modal-content so button hx-target hits it"):
    val html = Page.renderLogs("/tmp/x", List.empty).render
    assert(html.contains("""id="logs-modal-content""""))

  test("renderLogs polls every 1s so logs refresh live while modal is open"):
    val html = Page.renderLogs("/tmp/x", List.empty).render
    assert(html.contains("""hx-trigger="every 1s""""))

  test("renderLogs uses hx-swap=outerHTML so polling chain keeps firing"):
    // Without outerHTML, each refresh would strip the trigger from the parent,
    // and live updates would stop after the first tick.
    val html = Page.renderLogs("/tmp/x", List.empty).render
    assert(html.contains("""hx-swap="outerHTML""""))

  test("renderLogs URL-encodes the path in hx-get"):
    val html = Page.renderLogs("/data/my podcast", List.empty).render
    assert(
      html.contains("/logs?path=%2Fdata%2Fmy+podcast"),
      s"expected URL-encoded path in hx-get, got: $html"
    )
    assert(!html.contains("/data/my podcast"))

  test("renderLogs escapes HTML special chars in log content"):
    val html = Page.renderLogs("/tmp/x", List("<script>alert(1)</script>")).render
    assert(!html.contains("<script>alert"))
    assert(html.contains("&lt;script&gt;"))

  test("renderLogs scroll container is separate from polling target so scroll survives ticks"):
    // The outer container (overflow-y-auto, id=logs-modal-content) must NOT carry
    // the hx-get/hx-trigger attributes — otherwise it would be outerHTML-swapped
    // every tick and scrollTop would reset to 0.
    val html = Page.renderLogs("/tmp/x", List("a")).render
    val outerStart = html.indexOf("""id="logs-modal-content"""")
    val outerEnd = html.indexOf(">", outerStart)
    val outerTag = html.substring(outerStart, outerEnd)
    assert(!outerTag.contains("hx-get"), s"outer tag must not poll: $outerTag")
    assert(!outerTag.contains("hx-trigger"), s"outer tag must not poll: $outerTag")

  test("renderLogs outer container has tail-mode hx-on handlers"):
    // Without these, scroll position is preserved but the view never auto-follows
    // new lines once they're below the visible area.
    val html = Page.renderLogs("/tmp/x", List.empty).render
    assert(html.contains("hx-on::before-swap"))
    assert(html.contains("hx-on::after-swap"))
