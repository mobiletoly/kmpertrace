package dev.goquick.kmpertrace.analysis

import dev.goquick.kmpertrace.parse.parseLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidMultilineGrouperTest {
    @Test
    fun `collapses multi-line adb entry into single structured log record`() {
        val lines = sequenceOf(
            "         1765253114.749 17097 17144 D AppRootView: AppRootView: SafeSQLiteConnection.execSQL: CREATE TABLE app_settings_record",
            "         1765253114.749 17097 17144 D AppRootView: AppRootView: (",
            "         1765253114.749 17097 17144 D AppRootView: AppRootView: id TEXT NOT NULL PRIMARY KEY,",
            "         1765253114.749 17097 17144 D AppRootView: AppRootView: ); |{ ts=2025-12-09T22:19:52.747879Z lvl=debug trace=495667e4535b7beb span=72d7309dc852583a parent=8567601fae276cf1 head=\"SafeSQLiteConne\" src=AppRootView/openDatabase log=AppRootView svc=sample-app thread=\"DefaultDispatcher-worker-3\" }|"
        )

        val grouper = AndroidMultilineGrouper()
        val out = buildList {
            lines.forEach { addAll(grouper.feed(it)) }
            addAll(grouper.flush())
        }

        assertEquals(1, out.size, "expected fragments to collapse into one line")

        val parsed = parseLine(out.first())
        assertNotNull(parsed, "collapsed structured line should parse")
        val msg = parsed.message ?: ""
        assertTrue(msg.contains("CREATE TABLE app_settings_record"), "message should include full SQL body")
        assertTrue(msg.lines().size >= 3, "message should preserve embedded newlines, got: $msg")
    }

    @Test
    fun `flushes buffered entry when header changes`() {
        val lines = sequenceOf(
            " 1765253114.749 17097 17144 D TagOne: TagOne: first part",
            " 1765253114.749 17097 17144 D TagOne: TagOne: second part",
            " 1765253114.750 17097 17144 D TagTwo: other message"
        )
        val grouper = AndroidMultilineGrouper()
        val out = buildList {
            lines.forEach { addAll(grouper.feed(it)) }
            addAll(grouper.flush())
        }
        assertEquals(2, out.size)
        assertTrue(out[0].contains("TagOne: first part\nTagOne: second part"))
        assertTrue(out[1].contains("TagTwo"))
    }

    @Test
    fun `does not merge distinct same-ms entries without continuation markers`() {
        val lines = sequenceOf(
            " 1765666872.209 3886 3886 W ziparchive: Unable to open '/a': No such file",
            " 1765666872.209 3886 3886 W ziparchive: Unable to open '/b': No such file"
        )
        val grouper = AndroidMultilineGrouper()
        val out = buildList {
            lines.forEach { addAll(grouper.feed(it)) }
            addAll(grouper.flush())
        }
        assertEquals(2, out.size)
        assertTrue(out[0].contains("Unable to open '/a'"))
        assertTrue(out[1].contains("Unable to open '/b'"))
    }

    @Test
    fun `structured lines are not coalesced`() {
        val lines = sequenceOf(
            " 1765253114.749 17097 17144 D AppRootView: message one |{ ts=2025-12-09T22:19:52.747Z lvl=debug head=\"one\" log=AppRootView }|",
            " 1765253114.749 17097 17144 D AppRootView: message two |{ ts=2025-12-09T22:19:52.748Z lvl=debug head=\"two\" log=AppRootView }|"
        )
        val grouper = AndroidMultilineGrouper()
        val out = buildList {
            lines.forEach { addAll(grouper.feed(it)) }
            addAll(grouper.flush())
        }
        assertEquals(2, out.size, "structured entries must remain separate")
        assertEquals("two", parseLine(out[1])?.rawFields?.get("head"))
    }
}
