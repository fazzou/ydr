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
