package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.parse.parseLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidMultilineGrouperTest {
    @Test
    fun `collapses multi-line adb entry into single structured event`() {
        val lines = sequenceOf(
            "         1765253114.749 17097 17144 D AppRootView: AppRootView: SafeSQLiteConnection.execSQL: CREATE TABLE app_settings_record",
            "         1765253114.749 17097 17144 D AppRootView: AppRootView: (",
            "         1765253114.749 17097 17144 D AppRootView: AppRootView: id TEXT NOT NULL PRIMARY KEY,",
            "         1765253114.749 17097 17144 D AppRootView: AppRootView: ); |{ ts=2025-12-09T22:19:52.747879Z lvl=debug trace=495667e4535b7beb span=72d7309dc852583a parent=8567601fae276cf1 head=\"SafeSQLiteConne\" src=AppRootView/openDatabase log=AppRootView svc=sample-app thread=\"DefaultDispatcher-worker-3\" }|"
        )

        val collapsed = collapseAndroidMultiline(lines).toList()
        assertEquals(1, collapsed.size, "expected fragments to collapse into one line")

        val parsed = parseLine(collapsed.first())
        assertNotNull(parsed, "collapsed structured line should parse")
        val msg = parsed.message ?: ""
        assertTrue(msg.contains("CREATE TABLE app_settings_record"), "message should include full SQL body")
        assertTrue(msg.lines().size >= 3, "message should preserve embedded newlines, got: $msg")
    }

    @Test
    fun `flushes buffered entry when header changes`() {
        val lines = sequenceOf(
            " 1765253114.749 17097 17144 D TagOne: first part",
            " 1765253114.749 17097 17144 D TagOne: second part",
            " 1765253114.750 17097 17144 D TagTwo: other message"
        )
        val collapsed = collapseAndroidMultiline(lines).toList()
        assertEquals(2, collapsed.size)
        assertTrue(collapsed[0].contains("first part\nsecond part"))
        assertTrue(collapsed[1].contains("TagTwo"))
    }

    @Test
    fun `structured lines are not coalesced`() {
        val lines = sequenceOf(
            " 1765253114.749 17097 17144 D AppRootView: message one |{ ts=2025-12-09T22:19:52.747Z lvl=debug head=\"one\" log=AppRootView }|",
            " 1765253114.749 17097 17144 D AppRootView: message two |{ ts=2025-12-09T22:19:52.748Z lvl=debug head=\"two\" log=AppRootView }|"
        )
        val collapsed = collapseAndroidMultiline(lines).toList()
        assertEquals(2, collapsed.size, "structured entries must remain separate")
        assertEquals("two", parseLine(collapsed[1])?.rawFields?.get("head"))
    }
}
