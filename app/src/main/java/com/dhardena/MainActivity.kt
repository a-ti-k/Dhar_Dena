package com.dhardena

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Entity(tableName = "loans")
data class Loan(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val contact: String = "", val direction: String,
    val amount: Long, val date: String, val due: String, val notes: String = "")
@Entity(tableName = "payments", foreignKeys = [ForeignKey(entity = Loan::class, parentColumns = ["id"], childColumns = ["loanId"], onDelete = ForeignKey.CASCADE)], indices = [Index("loanId")])
data class Payment(@PrimaryKey(autoGenerate = true) val id: Long = 0, val loanId: Long, val amount: Long, val date: String)
data class LoanWithPayments(@Embedded val loan: Loan, @Relation(parentColumn = "id", entityColumn = "loanId") val payments: List<Payment>) {
    val paid get() = payments.sumOf { it.amount }; val remaining get() = (loan.amount - paid).coerceAtLeast(0)
    val status get() = if (remaining == 0L) "Paid" else if (paid > 0) "Partially repaid" else "Unpaid"
}
@Dao interface LoanDao {
    @Transaction @Query("SELECT * FROM loans ORDER BY due ASC") fun watch(): Flow<List<LoanWithPayments>>
    @Insert suspend fun add(loan: Loan): Long
    @Update suspend fun update(loan: Loan)
    @Delete suspend fun delete(loan: Loan)
    @Insert suspend fun addPayment(payment: Payment)
    @Query("DELETE FROM payments WHERE loanId=:loanId") suspend fun clearPayments(loanId: Long)
}
@Database(entities = [Loan::class, Payment::class], version = 1, exportSchema = false)
abstract class LoanDb : RoomDatabase() { abstract fun dao(): LoanDao
    companion object { @Volatile private var instance: LoanDb? = null
        fun get(context: android.content.Context) = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context.applicationContext, LoanDb::class.java, "dhardena.db").build().also { instance = it } }
    }
}

private val Ink = Color(0xFF1E342B); private val Green = Color(0xFF28654B); private val Canvas = Color(0xFFF5F7F3); private val Muted = Color(0xFF68766D); private val Line = Color(0xFFE6EBE5)
private fun money(cents: Long) = "৳" + NumberFormat.getNumberInstance(Locale("en", "BD")).format(cents / 100.0)
private val dayFmt = DateTimeFormatter.ISO_LOCAL_DATE
class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { installSplashScreen(); super.onCreate(savedInstanceState); val dao = LoanDb.get(this).dao(); setContent { App(dao) } } }

@Composable fun App(dao: LoanDao) {
    val loans by dao.watch().collectAsStateWithLifecycle(emptyList()); val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }; var filter by remember { mutableStateOf("All") }; var editing by remember { mutableStateOf<LoanWithPayments?>(null) }
    var form by remember { mutableStateOf(false) }; var paymentFor by remember { mutableStateOf<LoanWithPayments?>(null) }; var detail by remember { mutableStateOf<LoanWithPayments?>(null) }
    val filtered = loans.filter { it.loan.name.contains(query, true) && (filter == "All" || it.loan.direction == filter) }
    val lent = loans.filter { it.loan.direction == "Lent" }.sumOf { it.remaining }; val borrowed = loans.filter { it.loan.direction == "Borrowed" }.sumOf { it.remaining }
    val today = LocalDate.now().format(dayFmt); val upcoming = loans.filter { it.remaining > 0 && it.loan.due >= today }.sortedBy { it.loan.due }.take(3)
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Canvas, surface = Color.White, onBackground = Ink, onSurface = Ink)) {
        Scaffold(containerColor = Canvas, floatingActionButton = { ExtendedFloatingActionButton(onClick = { editing = null; form = true }, containerColor = Green, contentColor = Color.White, text = { Text("Add a loan") }, icon = { Text("+") }) }) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 22.dp, bottom = 100.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(painter = painterResource(R.drawable.ic_startup_logo_mark), contentDescription = "Dhar-Dena Logo", tint = Color.Unspecified, modifier = Modifier.size(52.dp))
                        Column {
                            Text("Dhar-Dena", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text("Your money, clearly accounted for.", color = Muted, fontSize = 14.sp)
                        }
                    }
                }
                item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { SummaryCard("You are owed", lent, Modifier.weight(1f)); SummaryCard("You owe", borrowed, Modifier.weight(1f)) } }
                if (upcoming.isNotEmpty()) item { Column(Modifier.fillMaxWidth().background(Color(0xFFEAF2EB), RoundedCornerShape(18.dp)).padding(16.dp)) { Text("COMING UP", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); upcoming.forEach { Text("${it.loan.name} · ${it.loan.due} · ${money(it.remaining)}", Modifier.padding(top = 8.dp), color = Ink, fontSize = 14.sp) } } }
                item { Text("Your loans", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
                item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), placeholder = { Text("Search by friend") }, label = { Text("Search") }) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Lent", "Borrowed").forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) }) } } }
                if (filtered.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(if (loans.isEmpty()) "A clear start." else "No matching loans", fontWeight = FontWeight.SemiBold, fontSize = 18.sp); Text(if (loans.isEmpty()) "Add a loan to keep the details in one place." else "Try another name or filter.", color = Muted, modifier = Modifier.padding(top = 4.dp)) } }
                items(filtered, key = { it.loan.id }) { item -> LoanCard(item, onClick = { detail = item }, onPay = { paymentFor = item }) }
                item { Text("Records stay on this device. Add contact details only when useful.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 10.dp)) }
            }
        }
    }
    if (form) LoanForm(editing, onDismiss = { form = false }, onSave = { loan -> scope.launch { if (loan.id == 0L) dao.add(loan) else dao.update(loan); form = false } })
    paymentFor?.let { item -> PaymentForm(item, onDismiss = { paymentFor = null }, onSave = { amount, date -> scope.launch { dao.addPayment(Payment(loanId = item.loan.id, amount = amount, date = date)); paymentFor = null } }) }
    detail?.let { selected -> LoanDetail(selected, onDismiss = { detail = null }, onEdit = { editing = selected; detail = null; form = true }, onDelete = { scope.launch { dao.delete(selected.loan); detail = null } }) }
}

@Composable private fun SummaryCard(title: String, value: Long, modifier: Modifier = Modifier) { Column(modifier.background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp)) { Text(title, color = Muted, fontSize = 13.sp); Text(money(value), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) } }
@Composable private fun LoanCard(item: LoanWithPayments, onClick: () -> Unit, onPay: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(item.loan.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text("${item.loan.direction} · due ${item.loan.due}", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 3.dp)); Text(item.status, fontSize = 12.sp, color = if (item.remaining == 0L) Green else Muted, modifier = Modifier.padding(top = 7.dp)); Text("${money(item.remaining)} remaining", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp)) }
        if (item.remaining > 0) TextButton(onClick = onPay) { Text("Record payment") }
    }
}
@Composable private fun LoanForm(existing: LoanWithPayments?, onDismiss: () -> Unit, onSave: (Loan) -> Unit) {
    val old = existing?.loan; var name by remember { mutableStateOf(old?.name ?: "") }; var contact by remember { mutableStateOf(old?.contact ?: "") }; var amount by remember { mutableStateOf(old?.amount?.let { (it / 100.0).toString() } ?: "") }
    var direction by remember { mutableStateOf(old?.direction ?: "Lent") }; var date by remember { mutableStateOf(old?.date ?: LocalDate.now().format(dayFmt)) }; var due by remember { mutableStateOf(old?.due ?: LocalDate.now().plusDays(30).format(dayFmt)) }; var notes by remember { mutableStateOf(old?.notes ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (old == null) "Add a loan" else "Edit loan") }, text = { Column(Modifier.heightIn(max = 530.dp).padding(end = 4.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Friend's name *") }, singleLine = true)
        OutlinedTextField(contact, { contact = it }, label = { Text("Contact (optional)") }, singleLine = true)
        Row { listOf("Lent", "Borrowed").forEach { FilterChip(selected = direction == it, onClick = { direction = it }, label = { Text(it) }, modifier = Modifier.padding(end = 8.dp)) } }
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount in ৳ *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        OutlinedTextField(date, { date = it }, label = { Text("Date given / received (YYYY-MM-DD)") }, singleLine = true)
        OutlinedTextField(due, { due = it }, label = { Text("Target repayment (YYYY-MM-DD)") }, singleLine = true)
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, minLines = 2)
        Text("Dates use YYYY-MM-DD. Amounts support up to 2 decimal places.", color = Muted, fontSize = 11.sp)
    } }, confirmButton = { TextButton(onClick = { val cents = ((amount.toDoubleOrNull() ?: 0.0) * 100).toLong(); if (name.isNotBlank() && cents > 0) onSave(Loan(id = old?.id ?: 0, name = name.trim(), contact = contact.trim(), direction = direction, amount = cents, date = date, due = due, notes = notes.trim())) }, enabled = name.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
@Composable private fun PaymentForm(item: LoanWithPayments, onDismiss: () -> Unit, onSave: (Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now().format(dayFmt)) }; val cents = ((amount.toDoubleOrNull() ?: 0.0) * 100).toLong()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Record a payment") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("${item.loan.name} · ${money(item.remaining)} remaining", color = Muted); OutlinedTextField(amount, { amount = it }, label = { Text("Payment amount in ৳") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)); OutlinedTextField(date, { date = it }, label = { Text("Payment date (YYYY-MM-DD)") }) } }, confirmButton = { TextButton(onClick = { if (cents > 0 && cents <= item.remaining) onSave(cents, date) }, enabled = cents > 0 && cents <= item.remaining) { Text("Save payment") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
@Composable private fun LoanDetail(item: LoanWithPayments, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(item.loan.name) }, text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("${item.loan.direction} · ${item.status}", color = Muted); Text("Original amount  ${money(item.loan.amount)}"); Text("Remaining  ${money(item.remaining)}", fontWeight = FontWeight.Bold); Text("Given / received  ${item.loan.date}"); Text("Target date  ${item.loan.due}")
        if (item.loan.contact.isNotBlank()) Text("Contact  ${item.loan.contact}"); if (item.loan.notes.isNotBlank()) Text("Note  ${item.loan.notes}")
        HorizontalDivider(Modifier.padding(vertical = 4.dp)); Text("Repayment history", fontWeight = FontWeight.SemiBold)
        if (item.payments.isEmpty()) Text("No payments recorded yet.", color = Muted) else item.payments.sortedByDescending { it.date }.forEach { Text("${it.date}  ·  ${money(it.amount)}") }
    } }, confirmButton = { TextButton(onClick = onEdit) { Text("Edit") } }, dismissButton = { Row { TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFF9E3D39)) }; TextButton(onClick = onDismiss) { Text("Close") } } })
}
