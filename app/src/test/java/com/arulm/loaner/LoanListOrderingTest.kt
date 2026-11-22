package com.arulm.loaner

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LoanListOrderingTest {
    private fun loan(id: String, type: LenderType, date: String) = Loan(
        id = id, lenderId = "lender", lenderType = type, holderName = "Test",
        loanNumber = id, goldWeightGrams = null, loanAmount = 10000.0,
        annualInterestRate = 12.0, loanDate = LocalDate.parse(date),
    )

    @Test fun bankDefaultOrdersOverdueBeforeUpcoming() {
        val overdue = loan("old", LenderType.BANK, "2024-01-01")
        val upcoming = loan("new", LenderType.BANK, "2026-11-01")
        assertEquals(listOf(overdue, upcoming), LoanListOrdering.sort(listOf(upcoming, overdue)))
    }

    @Test fun pawnShopDefaultOrdersOldestFirst() {
        val oldest = loan("old", LenderType.PAWN_SHOP, "2023-08-01")
        val newest = loan("new", LenderType.PAWN_SHOP, "2026-08-01")
        assertEquals(listOf(oldest, newest), LoanListOrdering.sort(listOf(newest, oldest)))
    }

    @Test fun manualAmountSortOverridesDateOrder() {
        val old = loan("old", LenderType.BANK, "2024-01-01")
        val new = loan("new", LenderType.BANK, "2026-01-01").copy(loanAmount = 20000.0)
        assertEquals(listOf(new, old), LoanListOrdering.sort(listOf(old, new), LoanSort.LOAN_AMOUNT))
    }
}
