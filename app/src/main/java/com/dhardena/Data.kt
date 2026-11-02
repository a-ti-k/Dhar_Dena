package app.dhardena

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

enum class Direction { LENT, BORROWED }
enum class Status { UNPAID, PARTIAL, PAID }

/** Money is stored in minor units (e.g. paisa/cents) to avoid floating-point errors. Dates are epoch days. */
@Entity
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val friend: String,
    val phone: String? = null,
    val email: String? = null,
    val direction: Direction,
    val amount: Long,
    val given: Long,
    val due: Long? = null,
    val note: String? = null,
    val remindDays: Int? = null, // null = no reminder, 0 = on the day
)

@Entity(
    foreignKeys = [ForeignKey(Loan::class, ["id"], ["loanId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("loanId")]
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long, val amount: Long, val date: Long,
)

data class LoanRow(@Embedded val loan: Loan, val paid: Long) {
    val remaining get() = loan.amount - paid
    val status get() = when { remaining <= 0 -> Status.PAID; paid > 0 -> Status.PARTIAL; else -> Status.UNPAID }
    val overdue get() = remaining > 0 && loan.due != null && loan.due < LocalDate.now().toEpochDay()
}

class Conv {
    @TypeConverter fun dirToStr(d: Direction) = d.name
    @TypeConverter fun strToDir(s: String) = Direction.valueOf(s)
}

@Dao
interface LoanDao {
    @Query("SELECT l.*, COALESCE((SELECT SUM(amount) FROM Payment WHERE loanId = l.id), 0) AS paid FROM Loan l ORDER BY due IS NULL, due")
    fun rows(): Flow<List<LoanRow>>
    @Query("SELECT * FROM Payment WHERE loanId = :id ORDER BY date DESC, id DESC")
    fun payments(id: Long): Flow<List<Payment>>
    @Insert suspend fun add(l: Loan): Long
    @Update suspend fun update(l: Loan)
    @Delete suspend fun delete(l: Loan)
    @Insert suspend fun addPayment(p: Payment)
    @Delete suspend fun deletePayment(p: Payment)
}

@Database(entities = [Loan::class, Payment::class], version = 1, exportSchema = false)
@TypeConverters(Conv::class)
abstract class Db : RoomDatabase() { abstract fun dao(): LoanDao }
