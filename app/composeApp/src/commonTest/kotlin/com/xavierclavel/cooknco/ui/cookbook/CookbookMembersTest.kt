package com.xavierclavel.cooknco.ui.cookbook

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Who a member row is not allowed to offer.
 *
 * The server keeps one row per member of a cookbook, so adding the same account twice
 * silently loses whichever role was set first. The editor therefore never offers an account
 * that is already on the cookbook — this is the rule that decides which those are, and it
 * has two edges worth pinning rather than rediscovering.
 */
class CookbookMembersTest {

    private fun state(vararg members: Pair<Long, String>) = CookbookEditUiState(
        members = members.map { (id, name) -> EditMember(userId = id, username = name, isAdmin = false) },
    )

    @Test
    fun `the accounts already on the cookbook are off the table`() {
        val state = state(7L to "aya", 9L to "sam")
        assertEquals(setOf(7L, 9L), state.memberIdsExcept(-1))
    }

    @Test
    fun `a row does not rule out the account it already holds`() {
        // Otherwise the row being edited would filter away its own member and look empty
        val state = state(7L to "aya", 9L to "sam")
        assertEquals(setOf(9L), state.memberIdsExcept(0))
        assertEquals(setOf(7L), state.memberIdsExcept(1))
    }

    @Test
    fun `an empty row rules out nobody, however many there are`() {
        // A row nobody has picked yet carries userId 0, which is not an account - counting
        // it would block every empty row after the first from offering anyone at all
        val state = state(7L to "aya", 0L to "", 0L to "")
        assertEquals(setOf(7L), state.memberIdsExcept(1))
        assertEquals(setOf(7L), state.memberIdsExcept(2))
    }

    @Test
    fun `a cookbook with no members yet rules out nobody`() {
        assertEquals(emptySet(), state().memberIdsExcept(0))
    }
}
