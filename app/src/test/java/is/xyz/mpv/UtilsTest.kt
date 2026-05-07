package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Test

class UtilsTest {
    @Test
    fun prettyTimeFormatsShortDurations() {
        assertEquals("00:00", Utils.prettyTime(0))
        assertEquals("00:09", Utils.prettyTime(9))
        assertEquals("01:05", Utils.prettyTime(65))
    }

    @Test
    fun prettyTimeFormatsHourDurations() {
        assertEquals("1:00:00", Utils.prettyTime(3600))
        assertEquals("2:03:04", Utils.prettyTime(7384))
    }

    @Test
    fun prettyTimeFormatsSignedDurations() {
        assertEquals("+01:05", Utils.prettyTime(65, sign = true))
        assertEquals("-01:05", Utils.prettyTime(-65, sign = true))
    }
}
