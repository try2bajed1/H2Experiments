package iot.example.h2experiments

import com.nhaarman.mockito_kotlin.*
import iot.example.h2experiments.storage.CloseRepository
import iot.example.h2experiments.storage.CloseSpecification
import org.amshove.kluent.shouldBeEmpty
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
import kotlin.math.abs
import kotlin.text.Typography.times

object CloseRepositoryTest : Spek({
    val dbURL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=0"
    val dbUser = "ekam-box"
    val password = "ekam,rocks!"
    fun createMockJdbi() = mock<Jdbi>()

    lateinit var db: JdbcConnectionPool
    lateinit var realJdbi: Jdbi
    lateinit var jdbi: Jdbi
    lateinit var spy: Jdbi
    lateinit var repo: CloseRepository

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

        repo = CloseRepository(jdbi)
    }

    afterEachTest {
        db.dispose()
    }

    describe("A CloseRepository") {

        on("initial DB state") {
            val mock = createMockJdbi()
            whenever(mock.registerRowMapper(any<RowMapperFactory>())).thenReturn(mock)
            CloseRepository(mock)

            it("should only register mappers") {
                verify(mock, times(2)).registerRowMapper(any<RowMapperFactory>())
                verifyNoMoreInteractions(mock)
            }

            it("should be empty every time") {
                for (i in 0 until 10) {
                    repo.getAll().shouldBeEmpty()
                }
            }
        }

        
        on("insert without nulls") {
            val instance: CloseRetailShiftRequest = createRandomCloseRetailShiftRequestWithoutNulls(randomLong())
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



        on("insert with nulls") {
            val instance: CloseRetailShiftRequest = createRandomCloseRetailShiftRequestWithNulls(randomLong())
            val expected: Set<CloseRetailShiftRequest> = setOf(instance)

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


        on("batch insert") {
            val itemsWithNulls = (1..100)
                    .map { createRandomCloseRetailShiftRequestWithNulls(it.toLong()) }
            val itemsWithoutNulls = (101..200)
                    .map { createRandomCloseRetailShiftRequestWithoutNulls(it.toLong()) }
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
            val repository = CloseRepository(mock)
            repository.addAll(emptyList())

            it("should not call DB") {
                verify(mock, times(2)).registerRowMapper(any<RowMapperFactory>())
                verifyNoMoreInteractions(mock)
            }
        }

        on("item all fields update") {
            val initial = createRandomCloseRetailShiftRequestWithoutNulls(10)
            val expected = initial.copy(
                    accountId = initial.accountId + 1,
                    webCashboxId = initial.webCashboxId!! + 1,
                    status = initial.status?.dropLast(2),
                    shouldPrint = !initial.shouldPrint,
                    createdAt = DateTime(abs(initial.createdAt!!.millis + 2)),
                    updatedAt = DateTime(abs(initial.updatedAt!!.millis + 2)),
                    archivedAt = DateTime(abs(initial.archivedAt!!.millis + 2)),
                    lockedPreviously = !initial.lockedPreviously,
                    serverNum = initial.serverNum,
                    result = createRandomResult(initial.id))
            repo.add(initial)
            val result = repo.update(expected)
            it("update should be success") {
                result shouldEqual true
            }

            it("should has still only one item") {
                repo.getAll().size shouldEqual 1
            }

            it("all fields should be updated") {
                repo.getAll() shouldEqual setOf(expected)
            }

            it("old result should be deleted") {
                realJdbi.inTransaction<List<Result>, Exception> { h ->
                    h.createQuery("SELECT * FROM CLOSE_RESULTS")
                            .mapTo(Result::class.java)
                            .list()
                } shouldEqual listOf(expected.result!!)
            }
        }

        on("removing") {
            val itemsWithNulls = (1..30)
                    .map { createRandomCloseRetailShiftRequestWithNulls(it.toLong()) }
            val itemsWithoutNulls = (31..60)
                    .map { createRandomCloseRetailShiftRequestWithoutNulls(it.toLong()) }
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
                        it.createQuery("SELECT COUNT(CLOSE_RESULTS.RECEIPT_ID) FROM CLOSE_RESULTS WHERE RECEIPT_ID = ?")
                                .bind(0, item.id)
                                .mapTo(Int::class.java).findOnly() shouldEqual 0
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
        lateinit var itemsWithNulls: List<CloseRetailShiftRequest>
        lateinit var itemsWithoutNulls: List<CloseRetailShiftRequest>
        lateinit var allItems: Map<Long, CloseRetailShiftRequest>
        lateinit var errors: List<ReceiptRequestError>
        lateinit var itemsWithoutError: Map<Long, CloseRetailShiftRequest>
        lateinit var itemsWithError: Map<Long, CloseRetailShiftRequest>

        beforeEachTest {
            errorRepo = ReceiptRequestErrorRepository(realJdbi)
            itemsWithNulls = (1..100)
                    .map { createRandomCloseRetailShiftRequestWithNulls(it.toLong()) }

            itemsWithoutNulls = (101..200)
                    .map { createRandomCloseRetailShiftRequestWithoutNulls(it.toLong()) }

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

        describe("ID Specification") {

            it("should select correct item on getting") {
                allItems.forEach { id, item ->
                    repo.get(CloseSpecification.ID(id)) shouldEqual item
                }
            }

        }

        describe("success specification") {
            it("should return only success items on getting") {
                val success = repo.get(CloseSpecification.Success)

                success.associateBy { it.id } shouldEqual itemsWithoutNulls.associateBy { it.id }
            }
        }

        describe("Failed specification") {
            it("should return only success items on getting") {
                val failed = repo.get(CloseSpecification.Failed)

                failed.associateBy { it.id } shouldEqual itemsWithError
            }
        }

        describe("NotCompleted specification") {
            it("should return only success items on getting") {
                val failed = repo.get(CloseSpecification.NotCompleted)

                failed.associateBy { it.id } shouldEqual itemsWithoutError.filterValues { it.result == null }
            }
        }
    }
})