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

  test("renderLogs does NOT render the scroll container (it lives in the modal)"):
    // Returning the scroll container here would, on polling tick, get nested
    // inside the existing one — duplicating id and breaking the layout.
    val html = Page.renderLogs("/tmp/x", List("a")).render
    assert(
      !html.contains("logs-modal-content"),
      s"renderLogs must not include the scroll container id: $html"
    )

  test("Page.index renders the stable scroll container with tail-mode hx-on handlers"):
    // The scroll container has to live in the static modal markup so its
    // scrollTop survives polling ticks. Without the hx-on handlers, the view
    // never auto-follows new lines once they're below the visible area.
    val html = Page.index(YdrModel.empty).render
    assert(html.contains("""id="logs-modal-content""""))
    assert(html.contains("overflow-y-auto"))
    assert(html.contains("hx-on::before-swap"))
    assert(html.contains("hx-on::after-swap"))
