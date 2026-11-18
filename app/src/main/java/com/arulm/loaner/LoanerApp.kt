@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.arulm.loaner

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val moneyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
private val displayDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
private fun money(value: Double) = moneyFormat.format(value)
private fun date(value: LocalDate) = value.format(displayDate)

@Composable
fun LoanerApp(
    vm: LoanViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCheckUpdates: () -> Unit,
    onExit: () -> Unit,
    biometricAvailable: Boolean,
    onBiometricAuth: (() -> Unit) -> Unit,
) {
    if (!vm.unlocked) {
        PinGate(vm, biometricAvailable, onBiometricAuth, onExit)
        return
    }
    var exitDialog by remember { mutableStateOf(false) }
    val rootScreens = listOf(Screen.Dashboard, Screen.Loans, Screen.Lenders, Screen.Settings)
    val root = vm.screen in rootScreens
    fun navigateBack() {
        vm.screen = when (vm.screen) {
            is Screen.EditLender -> Screen.Lenders
            is Screen.LoanDetails, is Screen.EditLoan -> Screen.Loans
            else -> vm.screen
        }
    }
    BackHandler {
        if (root) exitDialog = true else navigateBack()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle(vm.screen)) },
                navigationIcon = {
                    if (!root) IconButton(onClick = ::navigateBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
            )
        },
        bottomBar = {
            if (root) NavigationBar {
                NavItem("Dashboard", Icons.Default.Home, vm.screen == Screen.Dashboard) { vm.screen = Screen.Dashboard }
                NavItem("Loans", Icons.Default.AccountBalance, vm.screen == Screen.Loans) { vm.screen = Screen.Loans }
                NavItem("Lenders", Icons.Default.Store, vm.screen == Screen.Lenders) { vm.screen = Screen.Lenders }
                NavItem("Settings", Icons.Default.Settings, vm.screen == Screen.Settings) { vm.screen = Screen.Settings }
            }
        },
        snackbarHost = {
            vm.message?.let { msg ->
                Snackbar(modifier = Modifier.padding(12.dp), action = { TextButton(onClick = { vm.message = null }) { Text("OK") } }) { Text(msg) }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val screen = vm.screen) {
                Screen.Dashboard -> DashboardScreen(vm)
                Screen.Loans -> LoanListScreen(vm)
                Screen.Lenders -> LenderListScreen(vm)
                Screen.Settings -> SettingsScreen(vm, onExport, onImport, onCheckUpdates, biometricAvailable, onBiometricAuth)
                is Screen.LoanDetails -> LoanDetailsScreen(vm, screen.id)
                is Screen.EditLoan -> LoanFormScreen(vm, screen.id)
                is Screen.EditLender -> LenderFormScreen(vm, screen.id)
            }
        }
    }
    if (exitDialog) AlertDialog(
        onDismissRequest = { exitDialog = false },
        title = { Text("Exit Loaner?") },
        text = { Text("Are you sure you want to close the app?") },
        confirmButton = { TextButton(onClick = onExit) { Text("Exit") } },
        dismissButton = { TextButton(onClick = { exitDialog = false }) { Text("Cancel") } },
    )
}

private fun screenTitle(screen: Screen) = when (screen) {
    Screen.Dashboard -> "Loaner"
    Screen.Loans -> "Loans"
    Screen.Lenders -> "Banks & pawn shops"
    Screen.Settings -> "Settings"
    is Screen.LoanDetails -> "Loan details"
    is Screen.EditLoan -> if (screen.id == null) "New loan" else "Edit loan"
    is Screen.EditLender -> if (screen.id == null) "New lender" else "Edit lender"
}

@Composable private fun RowScope.NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(selected, onClick, { Icon(icon, label) }, label = { Text(label) })
}

@Composable
private fun DashboardScreen(vm: LoanViewModel) {
    var lenderType by remember { mutableStateOf<LenderType?>(null) }
    var lenderId by remember { mutableStateOf<String?>(null) }
    val matching = vm.data.loans.filter { (lenderType == null || it.lenderType == lenderType) && (lenderId == null || it.lenderId == lenderId) }
    val active = matching.filter { it.status == LoanStatus.ACTIVE }
    val closed = matching.filter { it.status == LoanStatus.CLOSED }
    val borrowed = active.sumOf { it.loanAmount }
    val interest = active.sumOf { LoanCalculator.figures(it).interestTillDate }
    val extraPaid = closed.sumOf { (it.totalRepaid ?: 0.0) - it.loanAmount }.coerceAtLeast(0.0)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Your gold loans", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("A current snapshot across all lenders", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { NullableDropdown("Lender type", lenderType, LenderType.entries, { it.label }) { lenderType = it; lenderId = null } }
                Box(Modifier.weight(1f)) {
                    NullableDropdown(
                        "Lender", lenderId,
                        vm.data.lenders.filter { lenderType == null || it.type == lenderType }.map { it.id },
                        { id -> vm.data.lenders.firstOrNull { it.id == id }?.name ?: "" },
                    ) { lenderId = it }
                }
            }
        }
        item { SummaryCard("Active borrowed", money(borrowed), "${active.size} active loan${if (active.size == 1) "" else "s"}", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) }
        item { SummaryCard("Interest till today", money(interest), "Estimated using annual simple interest", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
        item { SummaryCard("Current total", money(borrowed + interest), "Principal + accrued interest", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) }
        item { SummaryCard("Extra paid on closed loans", money(extraPaid), "${closed.size} closed loan${if (closed.size == 1) "" else "s"}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) }
        val urgent = active.filter { it.lenderType == LenderType.BANK && (LoanCalculator.figures(it).daysToDue ?: 999) <= 30 }
        if (urgent.isNotEmpty()) {
            item { Text("Needs attention", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(urgent) { loan -> CompactLoanCard(vm, loan) }
        }
    }
}

@Composable private fun SummaryCard(title: String, value: String, subtitle: String, color: Color, contentColor: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = color, contentColor = contentColor), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LoanListScreen(vm: LoanViewModel) {
    var sort by remember { mutableStateOf(LoanSort.LOAN_DATE) }
    var type by remember { mutableStateOf<LenderType?>(null) }
    var lenderId by remember { mutableStateOf<String?>(null) }
    var holder by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(LoanStatus.ACTIVE) }
    val filtered = vm.data.loans.filter { loan ->
        loan.status == status && (type == null || loan.lenderType == type) && (lenderId == null || loan.lenderId == lenderId) && (holder == null || loan.holderName == holder)
    }.sortedWith(compareByDescending { loan ->
        when (sort) {
            LoanSort.LOAN_DATE -> loan.loanDate.toEpochDay().toDouble()
            LoanSort.LOAN_AMOUNT -> loan.loanAmount
            LoanSort.TOTAL_AMOUNT -> LoanCalculator.figures(loan).totalAmount
            LoanSort.INTEREST -> LoanCalculator.figures(loan).interestTillDate
            LoanSort.DUE_DATE -> LoanCalculator.figures(loan).dueDate?.toEpochDay()?.toDouble() ?: Double.NEGATIVE_INFINITY
        }
    })
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(status == LoanStatus.ACTIVE, { status = LoanStatus.ACTIVE }, { Text("Active") })
                    FilterChip(status == LoanStatus.CLOSED, { status = LoanStatus.CLOSED }, { Text("Closed") })
                }
                DropdownField("Sort by", sort, LoanSort.entries, { it.label }) { sort = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { NullableDropdown("Lender type", type, LenderType.entries, { it.label }) { type = it; lenderId = null } }
                    Box(Modifier.weight(1f)) { NullableDropdown("Lender", lenderId, vm.data.lenders.filter { type == null || it.type == type }.map { it.id }, { id -> vm.data.lenders.firstOrNull { it.id == id }?.name ?: "" }) { lenderId = it } }
                }
                NullableDropdown("Loan holder", holder, vm.data.loans.map { it.holderName }.distinct().sorted(), { it }) { holder = it }
            }
            if (filtered.isEmpty()) item { EmptyState("No loans match these filters", "Create a loan or adjust the filters above.") }
            items(filtered, key = { it.id }) { CompactLoanCard(vm, it) }
        }
        FloatingActionButton(onClick = { vm.screen = Screen.EditLoan() }, modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)) {
            Icon(Icons.Default.Add, "Add loan")
        }
    }
}

@Composable
private fun CompactLoanCard(vm: LoanViewModel, loan: Loan) {
    val figures = LoanCalculator.figures(loan)
    val lender = vm.data.lenders.firstOrNull { it.id == loan.lenderId }
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val (bg, content) = when {
        loan.status == LoanStatus.CLOSED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        loan.lenderType == LenderType.BANK && (figures.daysToDue ?: 999) <= 7 -> if (darkTheme) Color(0xFF5C1F1F) to Color(0xFFFFDAD6) else Color(0xFFFFCDD2) to Color(0xFF410002)
        loan.lenderType == LenderType.BANK && (figures.daysToDue ?: 999) <= 30 -> if (darkTheme) Color(0xFF513A00) to Color(0xFFFFDEA5) else Color(0xFFFFE0B2) to Color(0xFF261900)
        else -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { vm.screen = Screen.LoanDetails(loan.id) },
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = content), elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(loan.holderName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${lender?.name ?: "Unknown lender"} • ${loan.loanNumber}", style = MaterialTheme.typography.bodySmall)
                }
                Text(money(loan.loanAmount), fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Interest", money(figures.interestTillDate))
                Metric("Total", money(figures.totalAmount))
                Metric("Period", figures.period)
            }
            figures.dueDate?.let { due -> Text("Due ${date(due)}${figures.daysToDue?.let { " • ${if (it >= 0) "$it days left" else "${-it} days overdue"}" } ?: ""}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable private fun Metric(label: String, value: String) = Column { Text(label, style = MaterialTheme.typography.labelSmall); Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }

@Composable
private fun LenderListScreen(vm: LoanViewModel) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.data.lenders.isEmpty()) item { EmptyState("No lenders yet", "Add your bank or pawn shop to begin.") }
            items(vm.data.lenders, key = { it.id }) { lender ->
                Card(Modifier.fillMaxWidth().clickable { vm.screen = Screen.EditLender(lender.id) }) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (lender.type == LenderType.BANK) Icons.Default.AccountBalance else Icons.Default.Store, null)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) { Text(lender.name, fontWeight = FontWeight.Bold); Text("${lender.type.label} • ${lender.interestCalculation.label}") }
                        AssistChip(onClick = {}, label = { Text(if (lender.active) "Active" else "Inactive") })
                    }
                }
            }
        }
        FloatingActionButton(onClick = { vm.screen = Screen.EditLender() }, modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)) { Icon(Icons.Default.Add, "Add lender") }
    }
}

@Composable private fun EmptyState(title: String, subtitle: String) = Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(Icons.Default.Info, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LenderFormScreen(vm: LoanViewModel, id: String?) {
    val existing = vm.data.lenders.firstOrNull { it.id == id }
    var name by remember(id) { mutableStateOf(existing?.name ?: "") }
    var type by remember(id) { mutableStateOf(existing?.type ?: LenderType.BANK) }
    var active by remember(id) { mutableStateOf(existing?.active ?: true) }
    var interestCalculation by remember(id) { mutableStateOf(existing?.interestCalculation ?: InterestCalculation.DAY_BASED) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        DropdownField("Type", type, LenderType.entries, { it.label }) { type = it }
        DropdownField("Interest calculation", interestCalculation, InterestCalculation.entries, { it.label }) { interestCalculation = it }
        Text(interestCalculation.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(active, { active = it }); Text("Active lender")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            when {
                name.isBlank() -> error = "Name is required"
                vm.data.lenders.any { it.id != id && it.name.equals(name.trim(), true) && it.type == type } -> error = "This lender already exists"
                else -> {
                    vm.saveLender(Lender(existing?.id ?: java.util.UUID.randomUUID().toString(), name.trim(), type, active, interestCalculation))
                    vm.screen = Screen.Lenders
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save lender") }
    }
}

@Composable
private fun LoanFormScreen(vm: LoanViewModel, id: String?) {
    val existing = vm.data.loans.firstOrNull { it.id == id }
    var type by remember(id) { mutableStateOf(existing?.lenderType ?: LenderType.BANK) }
    var lenderId by remember(id) { mutableStateOf(existing?.lenderId) }
    var holder by remember(id) { mutableStateOf(existing?.holderName ?: "") }
    var loanNumber by remember(id) { mutableStateOf(existing?.loanNumber ?: "") }
    var weight by remember(id) { mutableStateOf(existing?.goldWeightGrams?.toString() ?: "") }
    var amount by remember(id) { mutableStateOf(existing?.loanAmount?.toString() ?: "") }
    var rate by remember(id) { mutableStateOf(existing?.annualInterestRate?.toString() ?: "") }
    var loanDate by remember(id) { mutableStateOf((existing?.loanDate ?: LocalDate.now()).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val lenders = vm.data.lenders.filter { (it.active && it.type == type) || it.id == existing?.lenderId }
    val holders = vm.data.loans.map { it.holderName }.distinct().filter { it.contains(holder, true) && !it.equals(holder, true) }.take(5)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("1. Choose lender type", fontWeight = FontWeight.Bold); DropdownField("Lender type", type, LenderType.entries, { it.label }) { type = it; lenderId = null } }
        item { RequiredNullableDropdown("Lender", lenderId, lenders.map { it.id }, { key -> lenders.firstOrNull { it.id == key }?.name ?: "Select" }) { lenderId = it } }
        item {
            OutlinedTextField(holder, { holder = it }, label = { Text("Loan holder name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            holders.forEach { suggestion -> TextButton(onClick = { holder = suggestion }) { Text(suggestion) } }
        }
        item { OutlinedTextField(loanNumber, { loanNumber = it }, label = { Text("Loan number") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { NumberField("Gold weight (g) — optional", weight) { weight = it } }
        item { NumberField("Loan amount (₹)", amount) { amount = it } }
        item { NumberField("Annual interest rate (%)", rate) { rate = it } }
        item { DateField("Loan date", loanDate) { loanDate = it } }
        item {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val parsedDate = loanDate.toDateOrNull()
                val parsedWeight = weight.toDoubleOrNull()
                val parsedAmount = amount.toDoubleOrNull()
                val parsedRate = rate.toDoubleOrNull()
                error = when {
                    lenderId == null -> "Select a lender"
                    holder.isBlank() -> "Loan holder name is required"
                    loanNumber.isBlank() -> "Loan number is required"
                    weight.isNotBlank() && (parsedWeight == null || parsedWeight <= 0) -> "Enter a valid gold weight or leave it blank"
                    parsedAmount == null || parsedAmount <= 0 -> "Enter a valid loan amount"
                    parsedRate == null || parsedRate < 0 -> "Enter a valid interest rate"
                    parsedDate == null -> "Use date format YYYY-MM-DD"
                    else -> null
                }
                if (error == null) {
                    vm.saveLoan(Loan(
                        id = existing?.id ?: java.util.UUID.randomUUID().toString(), lenderId = lenderId!!, lenderType = type,
                        holderName = holder.trim(), loanNumber = loanNumber.trim(), goldWeightGrams = parsedWeight,
                        loanAmount = parsedAmount!!, annualInterestRate = parsedRate!!, loanDate = parsedDate!!,
                        status = existing?.status ?: LoanStatus.ACTIVE, closedOn = existing?.closedOn, totalRepaid = existing?.totalRepaid,
                        history = existing?.history ?: emptyList(),
                        interestCalculation = vm.data.lenders.first { it.id == lenderId }.interestCalculation,
                    ))
                    vm.screen = Screen.Loans
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save loan") }
        }
    }
}

@Composable
private fun LoanDetailsScreen(vm: LoanViewModel, id: String) {
    val loan = vm.data.loans.firstOrNull { it.id == id } ?: return
    val lender = vm.data.lenders.firstOrNull { it.id == loan.lenderId }
    val figures = LoanCalculator.figures(loan)
    var closeDialog by remember { mutableStateOf(false) }
    var renewDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(loan.holderName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${lender?.name ?: "Unknown lender"} • ${loan.lenderType.label}")
                    Text("Loan #${loan.loanNumber}")
                }
            }
        }
        item { DetailSection("Loan", listOf("Principal" to money(loan.loanAmount), "Gold weight" to (loan.goldWeightGrams?.let { "$it g" } ?: "Not entered"), "Loan date" to date(loan.loanDate), "Period" to figures.period, "Annual rate" to "${loan.annualInterestRate}%", "Interest method" to loan.interestCalculation.label)) }
        item {
            val rows = mutableListOf("Monthly interest" to money(figures.monthlyInterest), "Yearly interest" to money(figures.yearlyInterest), "Interest till date" to money(figures.interestTillDate), "Total amount" to money(figures.totalAmount))
            figures.dueDate?.let { rows.add("Due date" to date(it)) }
            DetailSection("Calculated amounts", rows)
        }
        if (loan.status == LoanStatus.CLOSED) item {
            val paid = loan.totalRepaid ?: 0.0
            DetailSection("Closure", listOf("Closed on" to (loan.closedOn?.let(::date) ?: "—"), "Amount borrowed" to money(loan.loanAmount), "Total repaid" to money(paid), "Extra amount paid" to money(paid - loan.loanAmount)))
        }
        if (loan.history.isNotEmpty()) item {
            Text("Renewal history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            loan.history.reversed().forEachIndexed { index, cycle ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Previous cycle ${loan.history.size - index}", fontWeight = FontWeight.Bold)
                        Text("${date(cycle.loanDate)} • ${money(cycle.loanAmount)} at ${cycle.annualInterestRate}%")
                        Text("Paid at renewal: ${money(cycle.amountPaid)} • renewed ${date(cycle.renewedOn)}")
                    }
                }
            }
        }
        if (loan.status == LoanStatus.ACTIVE) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { renewDialog = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Renew") }
                Button(onClick = { closeDialog = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("Close") }
            }
            TextButton(onClick = { vm.screen = Screen.EditLoan(loan.id) }, modifier = Modifier.fillMaxWidth()) { Text("Edit loan details") }
        }
        item {
            TextButton(
                onClick = { deleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.DeleteForever, null)
                Spacer(Modifier.width(6.dp))
                Text("Delete loan")
            }
        }
    }
    if (closeDialog) CloseLoanDialog(loan, { closeDialog = false }) { amount, closedOn -> vm.closeLoan(loan.id, amount, closedOn); closeDialog = false }
    if (renewDialog) RenewLoanDialog(loan, { renewDialog = false }) { amount, loanDate, rate, paid -> vm.renewLoan(loan.id, amount, loanDate, rate, paid); renewDialog = false }
    if (deleteDialog) AlertDialog(
        onDismissRequest = { deleteDialog = false },
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete this loan?") },
        text = { Text("${loan.holderName} • Loan #${loan.loanNumber}\n\nThis permanently deletes the loan and its complete renewal and repayment history. This cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = { vm.deleteLoan(loan.id); vm.screen = Screen.Loans },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete permanently") }
        },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancel") } },
    )
}

@Composable private fun DetailSection(title: String, rows: List<Pair<String, String>>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            rows.forEach { (label, value) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.SemiBold) } }
        }
    }
}

@Composable private fun CloseLoanDialog(loan: Loan, dismiss: () -> Unit, confirm: (Double, LocalDate) -> Unit) {
    var amount by remember { mutableStateOf(LoanCalculator.figures(loan).totalAmount.toString()) }
    var closedOn by remember { mutableStateOf(LocalDate.now().toString()) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Close loan") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("Total amount repaid", amount) { amount = it }
            DateField("Closed date", closedOn) { closedOn = it }
            if (error) Text("Enter a valid amount and date", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = { val a = amount.toDoubleOrNull(); val d = closedOn.toDateOrNull(); if (a != null && a >= 0 && d != null) confirm(a, d) else error = true }) { Text("Close loan") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable private fun RenewLoanDialog(loan: Loan, dismiss: () -> Unit, confirm: (Double, LocalDate, Double, Double) -> Unit) {
    var amount by remember { mutableStateOf(loan.loanAmount.toString()) }
    var loanDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var rate by remember { mutableStateOf(loan.annualInterestRate.toString()) }
    var paid by remember { mutableStateOf(LoanCalculator.figures(loan).totalAmount.toString()) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Renew loan") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { NumberField("New loan amount", amount) { amount = it } }
            item { DateField("New loan date", loanDate) { loanDate = it } }
            item { NumberField("New annual interest rate (%)", rate) { rate = it } }
            item { NumberField("Amount paid for previous cycle", paid) { paid = it } }
            if (error) item { Text("Check all values and the date", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        val a = amount.toDoubleOrNull(); val d = loanDate.toDateOrNull(); val r = rate.toDoubleOrNull(); val p = paid.toDoubleOrNull()
        if (a != null && a > 0 && d != null && r != null && r >= 0 && p != null && p >= 0) confirm(a, d, r, p) else error = true
    }) { Text("Renew") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun SettingsScreen(
    vm: LoanViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCheckUpdates: () -> Unit,
    biometricAvailable: Boolean,
    onBiometricAuth: (() -> Unit) -> Unit,
) {
    var pinDialog by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { SettingsCard(if (vm.darkMode) Icons.Default.DarkMode else Icons.Default.LightMode, if (vm.darkMode) "Dark mode" else "Light mode", "Tap to switch the app theme") { vm.updateDarkMode(!vm.darkMode) } }
        item { Text("Security", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            SettingsCard(Icons.Default.Lock, if (vm.hasPin()) "Change or remove PIN" else "Set a 4-digit PIN", "PIN is stored only on this device and never exported") { pinDialog = true }
        }
        if (vm.hasPin()) item { SettingsCard(Icons.Default.LockOpen, "Lock now", "Require the PIN to return to your loans") { vm.lock() } }
        if (vm.hasPin()) item {
            val enabled = vm.biometricEnabled
            SettingsCard(Icons.Default.Fingerprint, if (enabled) "Biometric unlock enabled" else "Enable biometric unlock", if (biometricAvailable) "Optional; your PIN remains available as fallback" else "No enrolled biometric is available") {
                when {
                    enabled -> vm.updateBiometricEnabled(false)
                    !biometricAvailable -> vm.message = "Set up fingerprint or face unlock in Android settings first"
                    else -> onBiometricAuth { vm.updateBiometricEnabled(true); vm.message = "Biometric unlock enabled" }
                }
            }
        }
        item { Text("Data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { SettingsCard(Icons.Default.Upload, "Export backup", "Save lenders, loans, closures, and renewals as JSON") { onExport() } }
        item { SettingsCard(Icons.Default.Download, "Import backup", "Replace current data from a Loaner JSON backup") { onImport() } }
        item { Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { SettingsCard(Icons.Default.SystemUpdate, "Check for updates", "Download and install newer APKs from GitHub Releases") { onCheckUpdates() } }
        vm.updateDownloadProgress?.let { progress ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Downloading update", fontWeight = FontWeight.Bold)
                            Text("$progress%")
                        }
                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        item { Text("Loaner version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (pinDialog) PinSettingsDialog(vm) { pinDialog = false }
}

@Composable private fun SettingsCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable private fun PinSettingsDialog(vm: LoanViewModel, dismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (vm.hasPin()) "PIN settings" else "Set PIN") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PinField("New 4-digit PIN", pin) { if (it.length <= 4) pin = it.filter(Char::isDigit) }
            PinField("Confirm PIN", confirmation) { if (it.length <= 4) confirmation = it.filter(Char::isDigit) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (vm.hasPin()) OutlinedButton(onClick = { vm.setPin(null); dismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Remove PIN") }
        }
    }, confirmButton = { TextButton(onClick = {
        error = when { pin.length != 4 -> "PIN must contain 4 digits"; pin != confirmation -> "PINs do not match"; else -> null }
        if (error == null) { vm.setPin(pin); dismiss() }
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable private fun PinGate(vm: LoanViewModel, biometricAvailable: Boolean, onBiometricAuth: (() -> Unit) -> Unit, onExit: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var exitDialog by remember { mutableStateOf(false) }
    BackHandler { exitDialog = true }
    fun press(value: String) {
        if (pin.length < 4) pin += value
        if (pin.length == 4) {
            if (!vm.verifyPin(pin)) { error = true; pin = "" }
        }
    }
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Lock, null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Loaner is locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(if (error) "Incorrect PIN. Try again." else "Enter your 4-digit PIN", color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if (vm.biometricEnabled && biometricAvailable) {
            TextButton(onClick = { onBiometricAuth { vm.unlockWithBiometric() } }) { Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(6.dp)); Text("Unlock with biometric") }
        }
        Spacer(Modifier.height(18.dp))
        Text("●".repeat(pin.length) + "○".repeat(4 - pin.length), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(18.dp))
        (listOf("1", "2", "3", "4", "5", "6", "7", "8", "9") + "" + "0" + "⌫").chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    FilledTonalButton(onClick = { when (key) { "" -> {}; "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1); else -> press(key) } }, enabled = key.isNotEmpty(), modifier = Modifier.size(72.dp)) { Text(key, style = MaterialTheme.typography.titleLarge) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (exitDialog) AlertDialog(
        onDismissRequest = { exitDialog = false },
        title = { Text("Exit Loaner?") },
        text = { Text("Are you sure you want to close the app?") },
        confirmButton = { TextButton(onClick = onExit) { Text("Exit") } },
        dismissButton = { TextButton(onClick = { exitDialog = false }) { Text("Cancel") } },
    )
}

@Composable private fun PinField(label: String, value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value, onChange, label = { Text(label) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth(),
)

@Composable private fun NumberField(label: String, value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value, { onChange(it.filter { c -> c.isDigit() || c == '.' }) }, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
)

@Composable private fun DateField(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val selected = value.toDateOrNull() ?: LocalDate.now()
    fun showCalendar() {
        DatePickerDialog(
            context,
            { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day).toString()) },
            selected.year,
            selected.monthValue - 1,
            selected.dayOfMonth,
        ).show()
    }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = { IconButton(onClick = ::showCalendar) { Icon(Icons.Default.CalendarMonth, "Choose date") } },
        supportingText = { Text(value.toDateOrNull()?.let(::date) ?: "Choose a date") },
    )
}

private fun String.toDateOrNull(): LocalDate? = try { LocalDate.parse(this) } catch (_: DateTimeParseException) { null }

@Composable
private fun <T> DropdownField(label: String, selected: T, values: List<T>, text: (T) -> String, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(text(selected), {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded, { expanded = false }) { values.forEach { item -> DropdownMenuItem({ Text(text(item)) }, onClick = { onSelected(item); expanded = false }) } }
    }
}

@Composable
private fun <T> RequiredNullableDropdown(label: String, selected: T?, values: List<T>, text: (T) -> String, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(selected?.let(text) ?: "", {}, readOnly = true, label = { Text(label) }, placeholder = { Text("Select") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded, { expanded = false }) { values.forEach { item -> DropdownMenuItem({ Text(text(item)) }, onClick = { onSelected(item); expanded = false }) } }
    }
}

@Composable
private fun <T> NullableDropdown(label: String, selected: T?, values: List<T>, text: (T) -> String, onSelected: (T?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(selected?.let(text) ?: "All", {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem({ Text("All") }, onClick = { onSelected(null); expanded = false })
            values.forEach { item -> DropdownMenuItem({ Text(text(item)) }, onClick = { onSelected(item); expanded = false }) }
        }
    }
}
