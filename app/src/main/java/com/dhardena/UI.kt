package app.dhardena

import android.Manifest
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun Long.money(): String = NumberFormat.getCurrencyInstance().format(this / 100.0)
fun Long.day(): String = LocalDate.ofEpochDay(this).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
fun parseMoney(s: String): Long? = runCatching { BigDecimal(s.trim()).movePointRight(2).toLong() }.getOrNull()?.takeIf { it > 0 }
fun Loan.who() = if (direction == Direction.LENT) "You lent" else "You borrowed"
fun LoanRow.label() = if (overdue) "Overdue" else when (status) {
    Status.UNPAID -> "Unpaid"; Status.PARTIAL -> "Partly repaid"; Status.PAID -> "Repaid" }

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF6FD6BC)) else lightColorScheme(primary = Color(0xFF0F6E5A))) {
                Surface(Modifier.fillMaxSize()) { Nav() }
            }
        }
    }
}

@Composable fun Nav(vm: VM = viewModel()) {
    val nav = rememberNavController()
    val idArg = listOf(navArgument("id") { type = NavType.LongType })
    NavHost(nav, "home") {
        composable("home") { Shell(nav, "home") { Home(vm, nav, it) } }
        composable("loans") { Shell(nav, "loans") { Loans(vm, nav, it) } }
        composable("loan/{id}", idArg) { Detail(vm, nav, it.arguments!!.getLong("id")) }
        composable("form/{id}", idArg) { Form(vm, nav, it.arguments!!.getLong("id")) }
    }
}

@Composable fun Centered(content: @Composable () -> Unit) =
    Box(Modifier.fillMaxSize(), Alignment.TopCenter) { Box(Modifier.widthIn(max = 640.dp)) { content() } }

@Composable fun Shell(nav: NavController, cur: String, body: @Composable (PaddingValues) -> Unit) = Scaffold(
    bottomBar = { NavigationBar {
        NavigationBarItem(cur == "home", { nav.navigate("home") }, { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
        NavigationBarItem(cur == "loans", { nav.navigate("loans") }, { Icon(Icons.Default.List, null) }, label = { Text("Loans") })
    } },
    floatingActionButton = { ExtendedFloatingActionButton(text = { Text("Add loan") }, icon = { Icon(Icons.Default.Add, null) }, onClick = { nav.navigate("form/0") }) },
) { Centered { body(it) } }

@Composable fun Total(title: String, v: Long, m: Modifier) = Card(m) {
    Column(Modifier.padding(16.dp)) { Text(title, style = MaterialTheme.typography.labelLarge); Text(v.money(), style = MaterialTheme.typography.headlineSmall) }
}

@Composable fun LoanItem(r: LoanRow, onClick: () -> Unit) = Card(onClick, Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp)) {
        Text(r.loan.friend, style = MaterialTheme.typography.titleMedium)
        Text("${r.loan.who()} ${r.loan.amount.money()} · ${r.remaining.money()} left")
        Text(listOfNotNull(r.label(), r.loan.due?.let { "due ${it.day()}" }).joinToString(" · "),
            color = if (r.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun LazyListScope.section(title: String, list: List<LoanRow>, nav: NavController) {
    if (list.isEmpty()) return
    item { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
    items(list, key = { "$title${it.loan.id}" }) { r -> LoanItem(r) { nav.navigate("loan/${r.loan.id}") } }
}

@Composable fun Home(vm: VM, nav: NavController, pad: PaddingValues) {
    val rows by vm.rows.collectAsState()
    val open = rows.filter { it.remaining > 0 }
    val today = LocalDate.now().toEpochDay()
    LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
            Total("Owed to you", open.filter { it.loan.direction == Direction.LENT }.sumOf { it.remaining }, Modifier.weight(1f))
            Total("You owe", open.filter { it.loan.direction == Direction.BORROWED }.sumOf { it.remaining }, Modifier.weight(1f))
        } }
        section("Overdue", open.filter { it.overdue }, nav)
        section("Due in the next 14 days", open.filter { val d = it.loan.due; d != null && d in today..today + 14 }, nav)
        if (rows.isEmpty()) item { Text("No loans yet. Tap “Add loan” to start.") }
    }
}

@Composable fun Loans(vm: VM, nav: NavController, pad: PaddingValues) {
    val rows by vm.rows.collectAsState()
    var q by remember { mutableStateOf("") }
    var dir by remember { mutableStateOf<Direction?>(null) }
    var repaid by remember { mutableStateOf(false) }
    val shown = rows.filter {
        (dir == null || it.loan.direction == dir) && (repaid || it.remaining > 0) &&
            (q.isBlank() || it.loan.friend.contains(q, true) || (it.loan.note ?: "").contains(q, true))
    }
    Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Search name or note") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Row(Modifier.padding(vertical = 8.dp), Arrangement.spacedBy(8.dp)) {
            FilterChip(dir == null, { dir = null }, { Text("All") })
            FilterChip(dir == Direction.LENT, { dir = Direction.LENT }, { Text("Lent") })
            FilterChip(dir == Direction.BORROWED, { dir = Direction.BORROWED }, { Text("Borrowed") })
            FilterChip(repaid, { repaid = !repaid }, { Text("Show repaid") })
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(shown, key = { it.loan.id }) { r -> LoanItem(r) { nav.navigate("loan/${r.loan.id}") } }
            if (shown.isEmpty()) item { Text("No loans match.") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateField(label: String, v: Long?, clearable: Boolean = false, onChange: (Long?) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton({ show = true }, Modifier.weight(1f)) { Text("$label: ${v?.day() ?: "Not set"}") }
        if (clearable && v != null) TextButton({ onChange(null) }) { Text("Clear") }
    }
    if (show) {
        val st = rememberDatePickerState(initialSelectedDateMillis = v?.times(86_400_000))
        DatePickerDialog({ show = false },
            confirmButton = { TextButton({ st.selectedDateMillis?.let { onChange(it / 86_400_000) }; show = false }) { Text("OK") } },
            dismissButton = { TextButton({ show = false }) { Text("Cancel") } }) { DatePicker(st) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Form(vm: VM, nav: NavController, id: Long) {
    val rows by vm.rows.collectAsState()
    val old = rows.firstOrNull { it.loan.id == id }?.loan
    var loaded by remember { mutableStateOf(id == 0L) }
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }; var dir by remember { mutableStateOf(Direction.LENT) }
    var given by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    var due by remember { mutableStateOf<Long?>(null) }; var remind by remember { mutableStateOf<Int?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    if (!loaded && old != null) { loaded = true
        name = old.friend; phone = old.phone ?: ""; email = old.email ?: ""; amount = (old.amount / 100.0).toString()
        note = old.note ?: ""; dir = old.direction; given = old.given; due = old.due; remind = old.remindDays }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Scaffold(topBar = { TopAppBar(title = { Text(if (id == 0L) "New loan" else "Edit loan") },
        navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { pad ->
        Centered { Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(dir == Direction.LENT, { dir = Direction.LENT }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("I lent") }
                SegmentedButton(dir == Direction.BORROWED, { dir = Direction.BORROWED }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("I borrowed") }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Friend's name") }, singleLine = true)
            OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Amount") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
            DateField("Date ${if (dir == Direction.LENT) "given" else "received"}", given) { given = it ?: given }
            DateField("Repay by", due, clearable = true) { due = it }
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone (optional)") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email (optional)") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note (optional)") })
            Text("Reminder", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Row(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).let { it }, Arrangement.spacedBy(8.dp)) {
                listOf<Int?>(null, 0, 1, 3, 7).forEach { d ->
                    FilterChip(remind == d, { remind = d
                        if (d != null && Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        { Text(when (d) { null -> "Off"; 0 -> "On the day"; 1 -> "1 day before"; else -> "$d days before" }) })
                }
            }
            Text("Reminders are shown as notifications on this device only. Android will ask for notification permission when you turn one on; contact details never leave your phone.",
                style = MaterialTheme.typography.bodySmall)
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button({
                val cents = parseMoney(amount)
                err = when {
                    name.isBlank() -> "Please enter a name."
                    cents == null -> "Enter an amount greater than zero."
                    due != null && due!! < given -> "Repay-by date can't be before the loan date."
                    else -> null
                }
                if (err == null) {
                    vm.save(Loan(id, name.trim(), phone.ifBlank { null }, email.ifBlank { null }, dir, cents!!, given, due, note.ifBlank { null }, if (due == null) null else remind))
                    nav.popBackStack()
                }
            }, Modifier.fillMaxWidth()) { Text("Save") }
        } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Detail(vm: VM, nav: NavController, id: Long) {
    val row = vm.rows.collectAsState().value.firstOrNull { it.loan.id == id }
    val pays by vm.payments(id).collectAsState(emptyList())
    var payDlg by remember { mutableStateOf(false) }; var delDlg by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(row?.loan?.friend ?: "") },
        navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = {
            IconButton({ nav.navigate("form/$id") }) { Icon(Icons.Default.Edit, "Edit loan") }
            IconButton({ delDlg = true }) { Icon(Icons.Default.Delete, "Delete loan") }
        }) }) { pad ->
        if (row == null) return@Scaffold
        val l = row.loan
        Centered { LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("${row.label()} · ${l.who()} ${l.amount.money()}", style = MaterialTheme.typography.titleMedium)
                Text("Remaining ${row.remaining.money()}", style = MaterialTheme.typography.headlineMedium)
                LinearProgressIndicator(progress = { (row.paid.toFloat() / l.amount).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Text("Paid ${row.paid.money()} of ${l.amount.money()}")
                Text("Date ${if (l.direction == Direction.LENT) "given" else "received"}: ${l.given.day()}")
                l.due?.let { Text("Repay by: ${it.day()}") }
                l.phone?.let { Text("Phone: $it") }; l.email?.let { Text("Email: $it") }; l.note?.let { Text("Note: $it") }
                if (row.remaining > 0) Button({ payDlg = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Record payment") }
                Text("Payment history", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
                if (pays.isEmpty()) Text("No payments yet.")
            }
            items(pays, key = { it.id }) { p ->
                ListItem(headlineContent = { Text(p.amount.money()) }, supportingContent = { Text(p.date.day()) },
                    trailingContent = { IconButton({ vm.unpay(p) }) { Icon(Icons.Default.Delete, "Delete payment of ${p.amount.money()}") } })
            }
        } }
        if (payDlg) {
            var amt by remember { mutableStateOf((row.remaining / 100.0).toString()) }
            var date by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
            val cents = parseMoney(amt); val ok = cents != null && cents <= row.remaining
            AlertDialog({ payDlg = false }, title = { Text("Record payment") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(amt, { amt = it }, label = { Text("Amount") }, singleLine = true, isError = !ok,
                        supportingText = { if (!ok) Text("Up to ${row.remaining.money()}") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    DateField("Date", date) { date = it ?: date } } },
                confirmButton = { TextButton({ vm.pay(row, cents!!, date); payDlg = false }, enabled = ok) { Text("Save") } },
                dismissButton = { TextButton({ payDlg = false }) { Text("Cancel") } })
        }
        if (delDlg) AlertDialog({ delDlg = false }, title = { Text("Delete this loan?" ) },
            text = { Text("The loan and its payment history will be permanently removed from this phone.") },
            confirmButton = { TextButton({ vm.delete(l); delDlg = false; nav.popBackStack() }) { Text("Delete") } },
            dismissButton = { TextButton({ delDlg = false }) { Text("Cancel") } })
    }
}
