package com.arulm.loaner

import java.time.LocalDate

data class GoldSummary(
    val bankGrams: Double,
    val pawnShopGrams: Double,
    val borrowedPerGram: Double?,
    val repaidPerGram: Double?,
    val repaymentIsEstimate: Boolean,
) {
    val totalGrams: Double get() = bankGrams + pawnShopGrams

    companion object {
        fun from(loans: List<Loan>, today: LocalDate = LocalDate.now()): GoldSummary {
            val weighed = loans.filter { it.goldWeightGrams?.let { g -> g.isFinite() && g > 0 } == true }
            val active = weighed.filter { it.status == LoanStatus.ACTIVE }
            val repaid = weighed.filter { it.status == LoanStatus.CLOSED && it.totalRepaid?.isFinite() == true }
            val activeGrams = active.sumOf { it.goldWeightGrams!! }
            val repaidGrams = repaid.sumOf { it.goldWeightGrams!! }
            val useEstimate = loans.none { it.status == LoanStatus.CLOSED }
            return GoldSummary(
                bankGrams = active.filter { it.lenderType == LenderType.BANK }.sumOf { it.goldWeightGrams!! },
                pawnShopGrams = active.filter { it.lenderType == LenderType.PAWN_SHOP }.sumOf { it.goldWeightGrams!! },
                borrowedPerGram = if (activeGrams > 0) active.sumOf { it.loanAmount } / activeGrams else null,
                repaidPerGram = when {
                    useEstimate && activeGrams > 0 -> active.sumOf { LoanCalculator.figures(it, today).totalAmount } / activeGrams
                    repaidGrams > 0 -> repaid.sumOf { it.totalRepaid!! } / repaidGrams
                    else -> null
                },
                repaymentIsEstimate = useEstimate,
            )
        }
    }
}
