package com.arulm.loaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LoanCalculatorTest {
    @Test fun bankDueDateIsOneYearMinusOneDay() {
        val loan = sample(LenderType.BANK, LocalDate.of(2025, 1, 10))
        assertEquals(LocalDate.of(2026, 1, 9), LoanCalculator.figures(loan, LocalDate.of(2025, 2, 10)).dueDate)
    }

    @Test fun pawnShopHasNoDueDate() {
        assertNull(LoanCalculator.figures(sample(LenderType.PAWN_SHOP, LocalDate.of(2025, 1, 1))).dueDate)
    }

    @Test fun simpleInterestUsesElapsedDays() {
        val figures = LoanCalculator.figures(sample(LenderType.BANK, LocalDate.of(2025, 1, 1)), LocalDate.of(2026, 1, 1))
        assertEquals(1200.0, figures.interestTillDate, 0.01)
        assertEquals(11200.0, figures.totalAmount, 0.01)
        assertEquals(100.0, figures.monthlyInterest, 0.01)
    }

    private fun sample(type: LenderType, date: LocalDate) = Loan(
        lenderId = "lender", lenderType = type, holderName = "Holder", loanNumber = "1",
        goldWeightGrams = 10.0, loanAmount = 10000.0, annualInterestRate = 12.0, loanDate = date,
    )
}
