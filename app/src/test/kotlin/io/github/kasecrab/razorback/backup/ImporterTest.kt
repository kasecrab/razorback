package io.github.kasecrab.razorback.backup

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The check that stands between a truncated archive and a replace.
 *
 * A zip that was cut short still unpacks: whatever was written before the cut
 * is whole, and the rest is simply not there. Until the manifest's counts were
 * held against what the archive actually holds, such a file wiped every chat
 * and every attachment on its way past the first table and then stopped.
 */
class ImporterTest {

    @Test
    fun anArchiveHoldingFewerRowsThanItClaimsIsNotOpened() {
        assertNull(Importer.miscount(mapOf("conversations" to 3, "messages" to 40), mapOf("conversations" to 3, "messages" to 40)))
        assertNotNull(Importer.miscount(mapOf("conversations" to 3), mapOf("conversations" to 2)))
        assertNotNull(Importer.miscount(mapOf("messages" to 40), emptyMap()))
        // More than it claims is no better: the file is not the one its own
        // manifest describes either way.
        assertNotNull(Importer.miscount(mapOf("prompts" to 1), mapOf("prompts" to 2)))
    }

    @Test
    fun theOneCountThatSaysTwoThingsIsNotHeldAgainstEither() {
        // The exporter writes the number of attachment files under the name of
        // the attachments table, over the count of the table itself.
        assertNull(Importer.miscount(mapOf("attachments" to 12), mapOf("attachments" to 3)))
        // And a table name this build has never heard of is not this build's to
        // count: it skips the entry, so finding none of it proves nothing.
        assertNull(Importer.miscount(mapOf("gramophones" to 5), emptyMap()))
    }
}
