package iot.example.h2experiments

import com.nhaarman.mockito_kotlin.*
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeIn
import org.amshove.kluent.shouldEqual
import org.flywaydb.core.Flyway
import org.h2.jdbcx.JdbcConnectionPool
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.h2.H2DatabasePlugin
import org.jdbi.v3.core.mapper.RowMapperFactory
import org.jdbi.v3.jodatime2.JodaTimePlugin
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.joda.time.DateTime
import iot.example.h2experiments.domain.Line
import iot.example.h2experiments.domain.ReceiptRequestError
import iot.example.h2experiments.domain.ReceiptType
import iot.example.h2experiments.domain.SaleReceiptRequest
import iot.example.h2experiments.storage.SaleRepository
import iot.example.h2experiments.storage.domain.Line
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.text.Typography.times

object SaleReceiptRequestsRepositoryTest : Spek({
    val dbURL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=0"
    val dbUser = "ekam-box"
    val password = "ekam,rocks!"
    fun createMockJdbi() = mock<Jdbi>()

    lateinit var db: JdbcConnectionPool
    lateinit var realJdbi: Jdbi
    lateinit var jdbi: Jdbi
    lateinit var spy: Jdbi
    lateinit var repo: SaleRepository

    beforeEachTest {
        db = JdbcConnectionPool.create(dbURL, dbUser, password)

        Flyway().apply { dataSource = db }
                .migrate()

        jdbi = Jdbi.create(db)
                .installPlugin(JodaTimePlugin())
                .installPlugin(H2DatabasePlugin())
                .let {
                    realJdbi = it
                    spy(it)
                }
                .also {
                    spy = it
                }

        repo = SaleRepository(jdbi)
    }

    afterEachTest {
        db.dispose()
    }

    describe("A iot.example.h2experiments.storage.SaleRepository") {

        on("initial DB state") {
            val mock = createMockJdbi()
            whenever(mock.registerRowMapper(any<RowMapperFactory>())).thenReturn(mock)
            SaleRepository(mock)

            it("should only register mappers") {
                verify(mock, times(3)).registerRowMapper(any<RowMapperFactory>())
                verifyNoMoreInteractions(mock)
            }

            it("should be empty every time") {
                for (i in 0 until 10) {
                    repo.getAll().shouldBeEmpty()
                }
            }
        }

        listOf(0, 1, 10).forEach {
            on("insert without nulls and $it lines") {
                val instance: SaleReceiptRequest = createReceiptRequestWithoutNullFields(randomLong(), it)
                repo.add(instance)
                val expected = setOf(instance)
                it("should has only one inserted item") {
                    val result = repo.getAll()
                    result shouldEqual expected
                }
                it("should has only one item every time") {
                    for (i in 0 until 10) {
                        repo.getAll() shouldEqual expected
                    }
                }
            }
        }

        listOf(0, 1, 10).forEach {
            on("insert with nulls and $it lines") {
                val instance: SaleReceiptRequest = createReceiptRequestWithNullFields(randomLong(), it)
                val expected: Set<SaleReceiptRequest> = setOf(instance)

                repo.add(instance)

                it("should has only one inserted items") {
                    val result = repo.getAll()
                    result shouldEqual expected
                }
                it("should has only one item every time") {
                    for (i in 0 until 10) {
                        val result = repo.getAll()
                        result shouldEqual expected
                    }
                }
            }
        }

        on("batch insert") {
            val itemsWithNulls = (1..100)
                    .map { createReceiptRequestWithNullFields(it.toLong(), it / 10, it * 11L) }
            val itemsWithoutNulls = (101..200)
                    .map { createReceiptRequestWithoutNullFields(it.toLong(), (it - 100) / 10, it * 11L) }
            val items = itemsWithoutNulls + itemsWithNulls
            repo.addAll(items)
            val expected = items.sortedBy { it.id }

            it("should do only one request to DB") {
                verify(spy, times(1)).useTransaction<Exception>(any())
            }

            it("should has same items") {
                val result = repo.getAll().sortedBy { it.id }
                expected shouldEqual result
            }

        }

        on("empty batch inserting") {
            val mock: Jdbi = mock()
            whenever(mock.registerRowMapper(any<RowMapperFactory>())).thenReturn(mock)
            val repository = SaleRepository(mock)
            repository.addAll(emptyList())

            it("should not call DB") {
                verify(mock, times(3)).registerRowMapper(any<RowMapperFactory>())
                verifyNoMoreInteractions(mock)
            }
        }

        on("item every field update") {
            val initial = createReceiptRequestWithoutNullFields(10, 10)
            val expected = initial.copy(
                    accountId = initial.accountId + 1,
                    webCashboxId = initial.webCashboxId!! + 1,
                    type = if (initial.type == ReceiptType.CHECK_SALE) ReceiptType.CHECK_RETURN else ReceiptType.CHECK_SALE,
                    status = initial.status.dropLast(2),
                    kktReceiptId = initial.kktReceiptId!! + 1,
                    amount = initial.amount + BigDecimal.ONE,
                    cashAmount = initial.cashAmount + BigDecimal.ONE,
                    electronAmount = initial.electronAmount + BigDecimal.ONE,
                    lines = initial.lines.map { createLinesWithNulls(it.id + 1, it.receiptId) },
                    cashierName = initial.cashierName?.dropLast(2),
                    email = initial.email?.dropLast(2),
                    phoneNumber = initial.phoneNumber?.dropLast(2),
                    shouldPrint = !initial.shouldPrint,
                    orderId = initial.orderId!! + 1,
                    orderNumber = initial.orderNumber!! + 1,
                    createdAt = DateTime(abs(initial.createdAt!!.millis + 2)),
                    updatedAt = DateTime(abs(initial.updatedAt!!.millis + 2)),
                    archivedAt = DateTime(abs(initial.archivedAt!!.millis + 2)),
                    lockedPreviously = !initial.lockedPreviously)
            repo.add(initial)
            val result = repo.updateRequest(expected)
            it("update should be success") {
                result shouldEqual true
            }

            it("should be still only one item") {
                repo.getAll().size shouldEqual 1
            }

            it("every field should be updated") {
                repo.getAll() shouldEqual setOf(expected)
            }

            it("old lines should be deleted") {
                realJdbi.inTransaction<List<Line>, Exception> { h ->
                    h.createQuery("SELECT * FROM LINES")
                            .mapTo(Line::class.java)
                            .list()
                }.forEach { it shouldBeIn expected.lines }
            }
        }

        on("line updating") {
            val initial = createReceiptRequestWithoutNullFields(10, 10)
            val expected = initial.copy(
                    lines = initial.lines.map { createLinesWithNulls(it.id, it.receiptId) })

            repo.add(initial)
            val result = repo.updateRequest(expected)
            it("update should be success") {
                result shouldEqual true
            }

            it("should be still only one item") {
                repo.getAll().size shouldEqual 1
            }

            it("only lines should be updated") {
                repo.getAll() shouldEqual setOf(expected)
            }

            it("old lines should be deleted") {
                realJdbi.inTransaction<List<Line>, Exception> { h ->
                    h.createQuery("SELECT * FROM LINES")
                            .mapTo(Line::class.java)
                            .list()
                }.forEach { it shouldBeIn expected.lines }
            }
        }

        on("removing") {
            val itemsWithNulls = (1..30)
                    .map { createReceiptRequestWithNullFields(it.toLong(), it / 10, it * 11L) }
            val itemsWithoutNulls = (31..60)
                    .map { createReceiptRequestWithoutNullFields(it.toLong(), (it - 30) / 10, it * 11L) }
            val expected = (itemsWithNulls + itemsWithoutNulls).toSortedSet(compareBy { it.id })
            repo.addAll(itemsWithNulls)
            repo.addAll(itemsWithoutNulls)

            it("should not delete items while using not exist ID") {
                repo.remove(201) shouldEqual false
                repo.remove(0) shouldEqual false
                repo.getAll().toSortedSet(compareBy { it.id }) shouldEqual expected
            }

            it("should delete only needed items") {
                expected.toList().forEach { item ->
                    val updatedRows = repo.remove(item.id)
                    val result = repo.getAll().toSortedSet(compareBy { it.id })
                    updatedRows shouldEqual true
                    result shouldEqual expected.also { it.remove(item) }

                    jdbi.useTransaction<Exception> {
                        it.createQuery("SELECT COUNT(LINES.ID) FROM LINES WHERE RECEIPT_ID = ?")
                                .bind(0, item.id)
                                .mapTo(Int::class.java).first() shouldEqual 0

                        it.createQuery("SELECT COUNT(SALE_RESULTS.RECEIPT_ID) FROM SALE_RESULTS WHERE RECEIPT_ID = ?")
                                .bind(0, item.id)
                                .mapTo(Int::class.java).first() shouldEqual 0
                    }
                }
            }

            it("should delete nothing if empty") {
                expected.toList().forEach { item ->
                    val updatedRows = repo.remove(item.id)
                    val result = repo.getAll()
                    updatedRows shouldEqual false
                    result.shouldBeEmpty()
                }
            }
        }
    }

    context("Error handling group") {
        lateinit var errorRepo: ReceiptRequestErrorRepository
        lateinit var itemsWithNulls: List<SaleReceiptRequest>
        lateinit var itemsWithoutNulls: List<SaleReceiptRequest>
        lateinit var allItems: Map<Long, SaleReceiptRequest>
        lateinit var errors: List<ReceiptRequestError>
        lateinit var itemsWithoutError: Map<Long, SaleReceiptRequest>
        lateinit var itemsWithError: Map<Long, SaleReceiptRequest>

        beforeEachTest {
            errorRepo = ReceiptRequestErrorRepository(realJdbi)
            itemsWithNulls = (1..100)
                    .map { createReceiptRequestWithNullFields(it.toLong(), it / 10, it * 11L) }

            itemsWithoutNulls = (101..200)
                    .map { createReceiptRequestWithoutNullFields(it.toLong(), (it - 100) / 10, it * 11L) }

            repo.addAll(itemsWithNulls)
            repo.addAll(itemsWithoutNulls)

            allItems = (itemsWithNulls union itemsWithoutNulls).associateBy { it.id }

            errors = allItems.values
                    .shuffled(random)
                    .take(50)
                    .map {
                        createRandomError(it.id)
                    }

            errorRepo.addAll(errors)

            itemsWithoutError = allItems.minus(errors.map { it.receiptRequestId })
            itemsWithError = allItems.filterKeys { !itemsWithoutError.containsKey(it) }
        }


    }


})