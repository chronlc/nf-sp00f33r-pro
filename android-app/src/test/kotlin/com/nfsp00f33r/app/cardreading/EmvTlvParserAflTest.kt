package com.nfsp00f33r.app.cardreading

import org.junit.Test
import kotlin.test.assertEquals

class EmvTlvParserAflTest {

    @Test
    fun `parse simple AFL single entry`() {
        val afl = "08100102" // example: SFI=0x08 >> 3 = 1, start=0x10=16,end=0x01=1 -> invalid range
        val entries = EmvTlvParser.parseAfl(afl)
        // The parser should still return entries but validate ranges elsewhere
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals((0x08 shr 3), e.sfi)
        assertEquals(0x10, e.startRecord)
        assertEquals(0x01, e.endRecord)
    }

    @Test
    fun `parse multiple AFL entries`() {
        val afl = "081001020C010304" // two entries
        val entries = EmvTlvParser.parseAfl(afl)
        assertEquals(2, entries.size)
        assertEquals((0x08 shr 3), entries[0].sfi)
        assertEquals(0x10, entries[0].startRecord)
        assertEquals(0x01, entries[0].endRecord)
        assertEquals((0x0C shr 3), entries[1].sfi)
    }
}
