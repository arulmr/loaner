package com.arulm.loaner

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.max

enum class LenderType(val label: String) { BANK("Bank"), PAWN_SHOP("Pawn shop") }
enum class LoanStatus { ACTIVE, CLOSED }
enum class LoanSort(val label: String) {
    LOAN_DATE("Loan date"), LOAN_AMOUNT("Loan amount"), TOTAL_AMOUNT("Total amount"),
    INTEREST("Interest till date"), DUE_DATE("Due date")
}

data class Lender(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: LenderType,
    val active: Boolean = true,
)

data class LoanCycle(
    val loanAmount: Double,
    val loanDate: LocalDate,
    val annualInterestRate: Double,
    val amountPaid: Double,
    val renewedOn: LocalDate,
)

data class Loan(
    val id: String = UUID.randomUUID().toString(),
    val lenderId: String,
    val lenderType: LenderType,
    val holderName: String,
    val loanNumber: String,
    val goldWeightGrams: Double,
    val loanAmount: Double,
    val annualInterestRate: Double,
    val loanDate: LocalDate,
    val status: LoanStatus = LoanStatus.ACTIVE,
    val closedOn: LocalDate? = null,
    val totalRepaid: Double? = null,
    val history: List<LoanCycle> = emptyList(),
)

data class AppData(val lenders: List<Lender> = emptyList(), val loans: List<Loan> = emptyList())

data class LoanFigures(
    val monthlyInterest: Double,
    val yearlyInterest: Double,
    val interestTillDate: Double,
    val totalAmount: Double,
    val elapsedDays: Long,
    val period: String,
    val dueDate: LocalDate?,
    val daysToDue: Long?,
)

object LoanCalculator {
    fun figures(loan: Loan, today: LocalDate = LocalDate.now()): LoanFigures {
        val endDate = loan.closedOn ?: today
        val elapsed = max(0, ChronoUnit.DAYS.between(loan.loanDate, endDate))
        val yearly = loan.loanAmount * loan.annualInterestRate / 100.0
        val interest = yearly * elapsed / 365.0
        val due = if (loan.lenderType == LenderType.BANK) loan.loanDate.plusYears(1).minusDays(1) else null
        return LoanFigures(
            monthlyInterest = yearly / 12.0,
            yearlyInterest = yearly,
            interestTillDate = interest,
            totalAmount = loan.loanAmount + interest,
            elapsedDays = elapsed,
            period = formatPeriod(loan.loanDate, endDate),
            dueDate = due,
            daysToDue = due?.let { ChronoUnit.DAYS.between(today, it) },
        )
    }

    private fun formatPeriod(start: LocalDate, end: LocalDate): String {
        val p = Period.between(start, if (end.isBefore(start)) start else end)
        val parts = buildList {
            if (p.years > 0) add("${p.years}y")
            if (p.months > 0) add("${p.months}m")
            if (p.days > 0 || isEmpty()) add("${p.days}d")
        }
        return parts.joinToString(" ")
    }
}

fun AppData.toJson(): JSONObject = JSONObject().apply {
    put("formatVersion", 1)
    put("exportedAt", java.time.Instant.now().toString())
    put("lenders", JSONArray().apply { lenders.forEach { put(it.toJson()) } })
    put("loans", JSONArray().apply { loans.forEach { put(it.toJson()) } })
}

private fun Lender.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("type", type.name); put("active", active)
}

private fun LoanCycle.toJson() = JSONObject().apply {
    put("loanAmount", loanAmount); put("loanDate", loanDate.toString())
    put("annualInterestRate", annualInterestRate); put("amountPaid", amountPaid)
    put("renewedOn", renewedOn.toString())
}

private fun Loan.toJson() = JSONObject().apply {
    put("id", id); put("lenderId", lenderId); put("lenderType", lenderType.name)
    put("holderName", holderName); put("loanNumber", loanNumber)
    put("goldWeightGrams", goldWeightGrams); put("loanAmount", loanAmount)
    put("annualInterestRate", annualInterestRate); put("loanDate", loanDate.toString())
    put("status", status.name); put("closedOn", closedOn?.toString() ?: JSONObject.NULL)
    put("totalRepaid", totalRepaid ?: JSONObject.NULL)
    put("history", JSONArray().apply { history.forEach { put(it.toJson()) } })
}

fun appDataFromJson(text: String): AppData {
    val root = JSONObject(text)
    require(root.optInt("formatVersion", 1) == 1) { "Unsupported backup format" }
    val lenders = root.getJSONArray("lenders").mapObjects { o ->
        Lender(o.getString("id"), o.getString("name"), LenderType.valueOf(o.getString("type")), o.getBoolean("active"))
    }
    val loans = root.getJSONArray("loans").mapObjects { o ->
        val history = o.optJSONArray("history")?.mapObjects { h ->
            LoanCycle(h.getDouble("loanAmount"), LocalDate.parse(h.getString("loanDate")), h.getDouble("annualInterestRate"), h.getDouble("amountPaid"), LocalDate.parse(h.getString("renewedOn")))
        } ?: emptyList()
        Loan(
            id = o.getString("id"), lenderId = o.getString("lenderId"), lenderType = LenderType.valueOf(o.getString("lenderType")),
            holderName = o.getString("holderName"), loanNumber = o.getString("loanNumber"), goldWeightGrams = o.getDouble("goldWeightGrams"),
            loanAmount = o.getDouble("loanAmount"), annualInterestRate = o.getDouble("annualInterestRate"), loanDate = LocalDate.parse(o.getString("loanDate")),
            status = LoanStatus.valueOf(o.getString("status")), closedOn = o.optString("closedOn").takeIf { it.isNotBlank() && it != "null" }?.let(LocalDate::parse),
            totalRepaid = if (o.isNull("totalRepaid")) null else o.getDouble("totalRepaid"), history = history,
        )
    }
    require(loans.all { loan -> lenders.any { it.id == loan.lenderId } }) { "Backup contains loans with missing lenders" }
    return AppData(lenders, loans)
}

private fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> = (0 until length()).map { block(getJSONObject(it)) }

