package com.arulm.loaner

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.security.MessageDigest
import java.time.LocalDate

class LoanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LoanRepository(application)
    private val prefs = application.getSharedPreferences("security", Context.MODE_PRIVATE)

    var data by mutableStateOf(repository.load()); private set
    var unlocked by mutableStateOf(!hasPin()); private set
    var screen by mutableStateOf<Screen>(Screen.Dashboard)
    var message by mutableStateOf<String?>(null)
    var darkMode by mutableStateOf(prefs.getBoolean("darkMode", false)); private set
    var biometricEnabled by mutableStateOf(hasPin() && prefs.getBoolean("biometricEnabled", false)); private set
    var updateDownloadProgress by mutableStateOf<Int?>(null)

    fun saveLender(lender: Lender) {
        val list = data.lenders.toMutableList()
        val index = list.indexOfFirst { it.id == lender.id }
        if (index >= 0) list[index] = lender else list.add(lender)
        val loans = data.loans.map { if (it.lenderId == lender.id) it.copy(interestCalculation = lender.interestCalculation) else it }
        update(data.copy(lenders = list.sortedBy { it.name.lowercase() }, loans = loans))
    }

    fun saveLoan(loan: Loan) {
        val list = data.loans.toMutableList()
        val index = list.indexOfFirst { it.id == loan.id }
        if (index >= 0) list[index] = loan else list.add(loan)
        update(data.copy(loans = list))
    }

    fun closeLoan(id: String, amount: Double, date: LocalDate) {
        data.loans.firstOrNull { it.id == id }?.let { saveLoan(it.copy(status = LoanStatus.CLOSED, closedOn = date, totalRepaid = amount)) }
    }

    fun renewLoan(id: String, newAmount: Double, newDate: LocalDate, newRate: Double, paid: Double) {
        data.loans.firstOrNull { it.id == id }?.let { old ->
            val cycle = LoanCycle(old.loanAmount, old.loanDate, old.annualInterestRate, paid, newDate)
            saveLoan(old.copy(loanAmount = newAmount, loanDate = newDate, annualInterestRate = newRate, status = LoanStatus.ACTIVE, closedOn = null, totalRepaid = null, history = old.history + cycle))
        }
    }

    fun exportJson(): String = data.toJson().toString(2)
    fun importJson(text: String) { update(appDataFromJson(text)); message = "Backup imported" }

    fun hasPin(): Boolean = prefs.contains("pinHash")
    fun verifyPin(pin: String): Boolean = hash(pin) == prefs.getString("pinHash", null).also { if (hash(pin) == it) unlocked = true }
    fun setPin(pin: String?) {
        if (pin == null) {
            prefs.edit().remove("pinHash").putBoolean("biometricEnabled", false).apply()
            biometricEnabled = false
        } else prefs.edit().putString("pinHash", hash(pin)).apply()
        unlocked = true
    }
    fun lock() { if (hasPin()) unlocked = false }
    fun updateDarkMode(enabled: Boolean) { darkMode = enabled; prefs.edit().putBoolean("darkMode", enabled).apply() }
    fun updateBiometricEnabled(enabled: Boolean) {
        biometricEnabled = enabled && hasPin()
        prefs.edit().putBoolean("biometricEnabled", biometricEnabled).apply()
    }
    fun unlockWithBiometric() { if (biometricEnabled) unlocked = true }

    private fun update(newData: AppData) { data = newData; repository.save(newData) }
    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

sealed interface Screen {
    data object Dashboard : Screen
    data object Loans : Screen
    data object Lenders : Screen
    data object Settings : Screen
    data class LoanDetails(val id: String) : Screen
    data class EditLoan(val id: String? = null) : Screen
    data class EditLender(val id: String? = null) : Screen
}
