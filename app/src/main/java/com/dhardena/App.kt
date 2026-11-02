package app.dhardena

import android.Manifest
import android.app.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.work.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class App : Application() {
    val db by lazy { Room.databaseBuilder(this, Db::class.java, "dhardena.db").build() }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("due", "Repayment reminders", NotificationManager.IMPORTANCE_DEFAULT))
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily-reminders", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
        )
    }
}

class VM(app: Application) : AndroidViewModel(app) {
    private val dao = (app as App).db.dao()
    val rows = dao.rows().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun payments(id: Long) = dao.payments(id)
    fun save(l: Loan) = viewModelScope.launch { if (l.id == 0L) dao.add(l) else dao.update(l) }
    fun delete(l: Loan) = viewModelScope.launch { dao.delete(l) }
    fun pay(r: LoanRow, amount: Long, date: Long) = viewModelScope.launch {
        if (amount in 1..r.remaining) dao.addPayment(Payment(loanId = r.loan.id, amount = amount, date = date))
    }
    fun unpay(p: Payment) = viewModelScope.launch { dao.deletePayment(p) }
}

/** Runs daily; notifies on (due date - reminder days). Nothing leaves the device. */
class ReminderWorker(private val c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return Result.success()
        val today = LocalDate.now().toEpochDay()
        (c as? App ?: c.applicationContext as App).db.dao().rows().first()
            .filter { it.remaining > 0 && it.loan.due != null && it.loan.remindDays != null && it.loan.due - it.loan.remindDays == today }
            .forEach { r ->
                val verb = if (r.loan.direction == Direction.LENT) "is due to repay you" else "is waiting for your repayment"
                val n = NotificationCompat.Builder(c, "due")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Dhar-Dena reminder")
                    .setContentText("${r.loan.friend} $verb: ${r.remaining.money()}")
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setAutoCancel(true).build()
                NotificationManagerCompat.from(c).notify(r.loan.id.toInt(), n)
            }
        return Result.success()
    }
}
