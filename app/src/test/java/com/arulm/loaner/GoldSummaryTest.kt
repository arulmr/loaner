package com.arulm.loaner

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class GoldSummaryTest {
    @Test fun noClosedLoansUsesWeightedPrincipalPlusInterest() {
        val summary = GoldSummary.from(listOf(loan(10.0, 50000.0), loan(30.0, 90000.0), loan(null, 999999.0)), LocalDate.of(2026, 7, 1))
        assertEquals(3710.0, summary.repaidPerGram!!, 0.001)
        assertTrue(summary.repaymentIsEstimate)
    }

    @Test fun closedLoanWithoutWeightDoesNotTriggerEstimate() {
        val summary = GoldSummary.from(listOf(loan(10.0, 50000.0), loan(null, 10000.0).copy(status = LoanStatus.CLOSED, totalRepaid = 11000.0)))
        assertNull(summary.repaidPerGram)
        assertFalse(summary.repaymentIsEstimate)
    }
    private fun loan(weight: Double?, amount: Double, type: LenderType = LenderType.BANK) = Loan(
        lenderId = type.name, lenderType = type, holderName = "Test", loanNumber = "1",
        goldWeightGrams = weight, loanAmount = amount, annualInterestRate = 12.0, loanDate = LocalDate.of(2026, 1, 1),
    )

    @Test fun weightsAndAverageUseOnlyActiveWeighedLoans() {
        val summary = GoldSummary.from(listOf(loan(10.0, 50000.0), loan(30.0, 90000.0, LenderType.PAWN_SHOP), loan(null, 999999.0)))
        assertEquals(10.0, summary.bankGrams, 0.001)
        assertEquals(30.0, summary.pawnShopGrams, 0.001)
        assertEquals(40.0, summary.totalGrams, 0.001)
        assertEquals(3500.0, summary.borrowedPerGram!!, 0.001)
    }

    @Test fun closureAverageExcludesMissingRepaymentAndActiveLoans() {
        val closed = loan(20.0, 80000.0).copy(status = LoanStatus.CLOSED, totalRepaid = 90000.0)
        val summary = GoldSummary.from(listOf(closed, loan(10.0, 50000.0), closed.copy(totalRepaid = null)))
        assertEquals(4500.0, summary.repaidPerGram!!, 0.001)
        assertEquals(10.0, summary.totalGrams, 0.001)
    }

    @Test fun filteredInputLimitsTotalsAndEmptyInputHasNoAverage() {
        val loans = listOf(loan(10.0, 50000.0), loan(30.0, 90000.0, LenderType.PAWN_SHOP))
        val summary = GoldSummary.from(loans.filter { it.lenderType == LenderType.PAWN_SHOP })
        assertEquals(30.0, summary.totalGrams, 0.001)
        assertEquals(3000.0, summary.borrowedPerGram!!, 0.001)
        assertNull(GoldSummary.from(emptyList()).borrowedPerGram)
        assertNull(GoldSummary.from(listOf(loan(0.0, 100.0))).repaidPerGram)
    }
}
