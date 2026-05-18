import java.time.LocalDateTime

class ModelActorTests extends munit.FunSuite:

  private val pathA = os.Path("/tmp/ydr-test/a")
  private val pathB = os.Path("/tmp/ydr-test/b")
  private val pathC = os.Path("/tmp/ydr-test/c")

  private def notSynced(p: os.Path): DirState =
    DirState(p, NotSynchronized)

  test("empty model has no dirs and no next sync"):
    val a = new ModelActor
    val m = a.getModel
    assertEquals(m.dirs.size, 0)
    assertEquals(m.nextScheduledSync, None)

  test("upsertDir adds a new dir"):
    val a = new ModelActor
    val dir = notSynced(pathA)
    a.upsertDir(dir)
    assertEquals(a.findDir(pathA), Some(dir))
    assertEquals(a.getModel.dirs.size, 1)

  test("upsertDir replaces existing dir by path"):
    val a = new ModelActor
    a.upsertDir(notSynced(pathA))
    val syncing = DirState(pathA, Synchronizing(LocalDateTime.now()))
    a.upsertDir(syncing)
    assertEquals(a.findDir(pathA), Some(syncing))
    assertEquals(a.getModel.dirs.size, 1)

  test("upsertDir preserves insertion order"):
    val a = new ModelActor
    a.upsertDir(notSynced(pathA))
    a.upsertDir(notSynced(pathB))
    a.upsertDir(notSynced(pathC))
    assertEquals(
      a.getModel.dirs.keys.toList,
      List(pathA, pathB, pathC)
    )

  test("upsertDir of existing key preserves position in iteration order"):
    val a = new ModelActor
    a.upsertDir(notSynced(pathA))
    a.upsertDir(notSynced(pathB))
    a.upsertDir(notSynced(pathC))
    val updatedB = DirState(pathB, Synchronizing(LocalDateTime.now()))
    a.upsertDir(updatedB)
    assertEquals(
      a.getModel.dirs.values.toList,
      List(notSynced(pathA), updatedB, notSynced(pathC))
    )

  test("findDir returns None for unknown path"):
    val a = new ModelActor
    a.upsertDir(notSynced(pathA))
    assertEquals(a.findDir(pathB), None)

  test("setNextSync sets and clears the value"):
    val a = new ModelActor
    val t = LocalDateTime.of(2026, 5, 18, 12, 0)
    a.setNextSync(Some(t))
    assertEquals(a.getModel.nextScheduledSync, Some(t))
    a.setNextSync(None)
    assertEquals(a.getModel.nextScheduledSync, None)

  test("setNextSync does not touch dirs"):
    val a = new ModelActor
    a.upsertDir(notSynced(pathA))
    a.setNextSync(Some(LocalDateTime.now()))
    assertEquals(a.findDir(pathA), Some(notSynced(pathA)))
