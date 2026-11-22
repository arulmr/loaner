package com.arulm.loaner

object LoanListOrdering {
    fun sort(loans: List<Loan>, sort: LoanSort? = null): List<Loan> {
        val comparator = when (sort) {
            null -> compareBy<Loan> {
                if (it.lenderType == LenderType.BANK) it.loanDate.plusYears(1).minusDays(1) else it.loanDate
            }
            LoanSort.DUE_DATE -> compareBy<Loan> { LoanCalculator.figures(it).dueDate ?: java.time.LocalDate.MAX }
            LoanSort.LOAN_DATE -> compareBy<Loan> { it.loanDate }
            LoanSort.LOAN_AMOUNT -> compareByDescending<Loan> { it.loanAmount }
            LoanSort.TOTAL_AMOUNT -> compareByDescending<Loan> { LoanCalculator.figures(it).totalAmount }
            LoanSort.INTEREST -> compareByDescending<Loan> { LoanCalculator.figures(it).interestTillDate }
        }
        return loans.sortedWith(comparator.thenBy { it.id })
    }
}
