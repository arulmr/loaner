package com.arulm.loaner

data class GoldSummary(
    val bankGrams: Double,
    val pawnShopGrams: Double,
    val borrowedPerGram: Double?,
    val repaidPerGram: Double?,
) {
    val totalGrams: Double get() = bankGrams + pawnShopGrams

    companion object {
        fun from(loans: List<Loan>): GoldSummary {
            val weighed = loans.filter { it.goldWeightGrams?.let { g -> g.isFinite() && g > 0 } == true }
            val active = weighed.filter { it.status == LoanStatus.ACTIVE }
            val repaid = weighed.filter { it.status == LoanStatus.CLOSED && it.totalRepaid?.isFinite() == true }
            val activeGrams = active.sumOf { it.goldWeightGrams!! }
            val repaidGrams = repaid.sumOf { it.goldWeightGrams!! }
            return GoldSummary(
                bankGrams = active.filter { it.lenderType == LenderType.BANK }.sumOf { it.goldWeightGrams!! },
                pawnShopGrams = active.filter { it.lenderType == LenderType.PAWN_SHOP }.sumOf { it.goldWeightGrams!! },
                borrowedPerGram = if (activeGrams > 0) active.sumOf { it.loanAmount } / activeGrams else null,
                repaidPerGram = if (repaidGrams > 0) repaid.sumOf { it.totalRepaid!! } / repaidGrams else null,
            )
        }
    }
}
