package iot.example.h2experiments

import com.nhaarman.mockito_kotlin.*
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
import iot.example.h2experiments.domain.CorrectionDB
import iot.example.h2experiments.domain.ReceiptRequestError
import iot.example.h2experiments.domain.Result
import iot.example.h2experiments.storage.CorrectionRepository
import iot.example.h2experiments.storage.domain.CorrectionDB
import kotlin.math.abs
import kotlin.text.Typography.times

object CorrectionRepositoryTest : Spek({
    val dbURL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=0"
    val dbUser = "ekam-box"
    val password = "ekam,rocks!"
    fun createMockJdbi() = mock<Jdbi>()

    lateinit var db: JdbcConnectionPool
    lateinit var realJdbi: Jdbi
    lateinit var jdbi: Jdbi
    lateinit var spy: Jdbi
    lateinit var repo: CorrectionRepository

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

        repo = CorrectionRepository(jdbi)
    }

    afterEachTest {
        db.dispose()
    }

    describe("A iot.example.h2experiments.CorrectionRepository") {

        on("initial DB state") {
            val mock = createMockJdbi()
            whenever(mock.registerRowMapper(any<RowMapperFactory>())).thenReturn(mock)
            CorrectionRepository(mock)

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




        on("batch insert") {
//            val itemsWithNulls = (1..100)
//                    .map { createCorrectionWithNulls(it.toLong()) }
            val itemsWithoutNulls = (101..200)
                    .map { createCorrectionWithoutNulls(it.toLong()) }
//            val items = itemsWithoutNulls + itemsWithNulls
            repo.addAll(itemsWithoutNulls)
            val expected = itemsWithoutNulls.sortedBy { it.id }

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
            val repository = CorrectionRepository(mock)
            repository.addAll(emptyList())

            it("should not call DB") {
                verify(mock, times(2)).registerRowMapper(any<RowMapperFactory>())
                verifyNoMoreInteractions(mock)
            }
        }

        on("item all fields update") {
            val initial = createCorrectionWithoutNulls(10)
            val expected = initial.copy(
                    cashierName = initial.cashierName?.dropLast(2),
                    status = initial.status?.dropLast(2),
                    shouldPrint = !initial.shouldPrint,
                    createdAt = DateTime(abs(initial.createdAt!!.millis + 2)),
                    updatedAt = DateTime(abs(initial.updatedAt!!.millis + 2)),
                    archivedAt = DateTime(abs(initial.archivedAt!!.millis + 2)),
                    lockedPreviously = !initial.lockedPreviously,
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
                    h.createQuery("SELECT * FROM CORRECTION_RESULTS")
                            .mapTo(Result::class.java)
                            .list()
                } shouldEqual listOf(expected.result!!)
            }
        }

        on("removing") {
            val itemsWithNulls = (1..30)
                    .map { createCorrectionWithNulls(it.toLong()) }
            val itemsWithoutNulls = (31..60)
                    .map { createCorrectionWithoutNulls(it.toLong()) }
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
                        it.createQuery("SELECT COUNT(CORRECTION_RESULTS.RECEIPT_ID) FROM CORRECTION_RESULTS WHERE RECEIPT_ID = ?")
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
//        lateinit var itemsWithNulls: List<CorrectionDB>
        lateinit var itemsWithoutNulls: List<CorrectionDB>
        lateinit var allItems: Map<Long, CorrectionDB>
        lateinit var errors: List<ReceiptRequestError>
        lateinit var itemsWithoutError: Map<Long, CorrectionDB>
        lateinit var itemsWithError: Map<Long, CorrectionDB>

        beforeEachTest {
            errorRepo = ReceiptRequestErrorRepository(realJdbi)
//            itemsWithNulls = (1..100)
//                    .map { createCorrectionWithNulls(it.toLong()) }

            itemsWithoutNulls = (101..200)
                    .map { createCorrectionWithoutNulls(it.toLong()) }

//            repo.addAll(itemsWithNulls)
            repo.addAll(itemsWithoutNulls)

            allItems = (/*itemsWithNulls union */itemsWithoutNulls).associateBy { it.id }

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
                    repo.get(CorrectionRequestSpecification.ID(id)) shouldEqual item
                }
            }
            // context Error handling group:describe success specification:it should return only success items on getting
        }
/*
        describe("success specification") {
            it("should return only success items on getting") {
                val success = repo.get(CorrectionRequestSpecification.Success)

                success.associateBy { it.id } shouldEqual itemsWithoutNulls.associateBy { it.id }
            }
        }*/

        describe("Failed specification") {
            it("should return only success items on getting") {
                val failed = repo.get(CorrectionRequestSpecification.Failed)

                failed.associateBy { it.id } shouldEqual itemsWithError
            }
        }

        describe("NotCompleted specification") {
            it("should return only success items on getting") {
                val failed = repo.get(CorrectionRequestSpecification.NotCompleted)

                failed.associateBy { it.id } shouldEqual itemsWithoutError.filterValues { it.result == null }
            }
        }
    }
})