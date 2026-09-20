package com.kilkari.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kilkari.data.db.KilkariDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migrations run against real data, on real SQLite.
 *
 * A migration is the one piece of this app that cannot be corrected afterwards: it runs once,
 * on a phone holding the only copy of a family's records, and a mistake in it loses them. Each
 * test builds a database in an older shape from that version's exported schema, fills it with
 * rows, then opens it through Room — which runs the migrations and refuses to open at all if
 * the result does not match what the entities describe.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: these tests want SQLite, not the app's WorkManager and notification
// channels, which its own Application sets up on start.
@Config(application = android.app.Application::class, sdk = [34])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file = File(context.cacheDir, "migration-test.db")
    private var room: KilkariDatabase? = null

    @After
    fun tearDown() {
        room?.close()
        file.delete()
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
    }

    @Test
    fun `the fund keeps its money when accounts arrive`() {
        buildVersion(11) { db ->
            db.execSQL("INSERT INTO baby (id, name, dob) VALUES (1, 'Meera', '2026-08-20')")
            db.execSQL(
                "INSERT INTO fund_txn (id, babyId, kind, amountInr, date, note) " +
                    "VALUES (1, 1, 'deposit', 25000, '2026-08-21', 'Opening')"
            )
            db.execSQL(
                "INSERT INTO expense (id, babyId, title, vendor, category, amountInr, date, icon, paidFromFund) " +
                    "VALUES (1, 1, 'Nappies', 'Pharmacy', 'gen', 1899, '2026-09-14', 'child_care', 1)"
            )
            db.execSQL(
                "INSERT INTO expense (id, babyId, title, vendor, category, amountInr, date, icon, paidFromFund) " +
                    "VALUES (2, 1, 'Cot', 'Shop', 'gen', 3600, '2026-08-24', 'bedtime', 0)"
            )
            db.execSQL(
                "INSERT INTO investment (id, babyId, name, kind, startDate, active) " +
                    "VALUES (1, 1, 'FD', 'fd', '2026-08-25', 1)"
            )
            db.execSQL(
                "INSERT INTO investment_contribution (id, investmentId, amountInr, date, paidFromFund) " +
                    "VALUES (1, 1, 10000, '2026-08-25', 1)"
            )
        }

        val db = openThroughRoom()
        val accountId = runBlocking { db.fundAccountDao().first(1)!! }.let {
            assertEquals("Baby fund", it.name)
            it.id
        }

        val movement = runBlocking { db.fundDao().allForExport(1) }.single()
        assertEquals(accountId, movement.accountId)
        assertNull("a migrated movement is not part of a transfer", movement.transferGroup)
        assertNull("and has not been reconciled", movement.reconciledOn)

        val expenses = runBlocking { db.expenseDao().allForExport(1) }.associateBy { it.title }
        assertEquals(accountId, expenses.getValue("Nappies").fundAccountId)
        assertNull("an expense paid from elsewhere keeps no account", expenses.getValue("Cot").fundAccountId)
        // The rebuilt table keeps everything else about the row.
        assertEquals("Pharmacy", expenses.getValue("Nappies").vendor)
        assertEquals(1899, expenses.getValue("Nappies").amountInr)
        assertEquals("child_care", expenses.getValue("Nappies").icon)

        val contribution = runBlocking { db.investmentDao().contributionsForExport(1) }.single()
        assertEquals(accountId, contribution.fundAccountId)
        assertEquals(10000, contribution.amountInr)
    }

    @Test
    fun `a database with no child migrates without inventing an account`() {
        buildVersion(11) { }
        val db = openThroughRoom()
        assertEquals(0, runBlocking { db.fundAccountDao().count(1) })
    }

    @Test
    fun `a database from the first release migrates the whole way`() {
        buildVersion(1) { db ->
            db.execSQL("INSERT INTO baby (id, name, dob) VALUES (1, 'Meera', '2026-08-20')")
        }
        val db = openThroughRoom()
        // Opening at all means every migration ran and Room validated the final shape; the
        // child put in at version 1 is still there at the end of it.
        assertEquals("Meera", runBlocking { db.babyDao().get() }?.name)
    }

    /**
     * Creates the database in an older shape, straight from that version's exported schema,
     * and stamps it so Room knows which version it is looking at.
     */
    private fun buildVersion(version: Int, fill: (SupportSQLiteDatabase) -> Unit) {
        val schema = JSONObject(
            File("schemas/com.kilkari.data.db.KilkariDatabase/$version.json").readText()
        ).getJSONObject("database")

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(file.path)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
        )

        helper.writableDatabase.use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(
                    entity.getString("createSql")
                        .replace("\${TABLE_NAME}", entity.getString("tableName"))
                )
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql")
                        .replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
            }
            // Room refuses to open a database it cannot identify, so it is left the same
            // bookkeeping its own creation would have written.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(schema.getString("identityHash")),
            )
            db.version = version
            fill(db)
        }
    }

    /** Opens the file through Room, which runs the migrations and validates what they left. */
    private fun openThroughRoom(): KilkariDatabase =
        Room.databaseBuilder(context, KilkariDatabase::class.java, file.path)
            .addMigrations(*KilkariDatabase.ALL_MIGRATIONS)
            .build()
            .also {
                room = it
                // Room is lazy: touch it so the migration actually runs inside the test.
                assertTrue(it.openHelper.writableDatabase.isOpen)
            }
}
