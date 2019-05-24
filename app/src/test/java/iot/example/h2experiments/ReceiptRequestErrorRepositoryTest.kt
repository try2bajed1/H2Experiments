package iot.example.h2experiments

import com.nhaarman.mockito_kotlin.*
import iot.example.h2experiments.storage.ErrorRepository
import org.amshove.kluent.mock
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldEqual
import org.flywaydb.core.Flyway
import org.h2.jdbcx.JdbcConnectionPool
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.h2.H2DatabasePlugin
import org.jdbi.v3.core.mapper.RowMapperFactory
import org.jdbi.v3.jodatime2.JodaTimePlugin
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import kotlin.text.Typography.times

object ErrorRepositoryTest : Spek({
    val dbURL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=0"
    val dbUser = "ekam-box"
    val password = "ekam,rocks!"

    describe("A iot.example.h2experiments.storage.ErrorRepository") {
        lateinit var repo: ErrorRepository
        lateinit var db: JdbcConnectionPool
        lateinit var jdbi: Jdbi

        fun createMockJdbi() = mock<Jdbi>()

        beforeEachTest {
            db = JdbcConnectionPool.create(dbURL, dbUser, password)

            Flyway().apply { dataSource = db }
                    .migrate()

            jdbi = Jdbi.create(db)
                    .installPlugin(JodaTimePlugin())
                    .installPlugin(H2DatabasePlugin())
                    .let { spy(it) }

            repo = ErrorRepository(jdbi)
        }

        afterEachTest {
            db.dispose()
        }

        on("initial state") {
            val mock = createMockJdbi()
            ErrorRepository(mock)

            it("should only register mapper") {
                verify(mock, times(1)).registerRowMapper(any<RowMapperFactory>())
                verifyNoMoreInteractions(mock)
            }

            it("should be empty") {
                repo.getAll().shouldBeEmpty()
            }
        }

        on("insert") {
            val expected = createRandomError(1)
            repo.add(expected)
            it("should contains only it") {
                repo.getAll().first() shouldEqual expected
            }
        }

        on("empty batch insert") {
            val mock = mock<Jdbi>()
            repo = ErrorRepository(mock)
            repo.addAll(emptyList())

            it("should not do any requests") {
                verify(mock).registerRowMapper(any<RowMapperFactory>())
                verifyNoMoreInteractions(mock)
            }
        }

        on("batch insert") {
            val errors = (0L..100L).map { createRandomError(it) }
            repo.addAll(errors)

            it("should do only one request") {
                verify(jdbi, times(1)).useTransaction<Exception>(any())
            }

            it("should contains only inserted items") {
                repo.getAll().toSortedSet(compareBy { it.receiptRequestId }) shouldEqual
                        errors.toSortedSet(compareBy { it.receiptRequestId })
            }
        }

        on("remove") {
            val initial = (0L..100L).mapTo(ArrayList()) { createRandomError(it) }
            repo.addAll(initial)

            it("should remove only one item") {
                initial.toList().forEach { item ->
                    repo.remove(item.receiptRequestId) shouldEqual true
                    repo.getAll().sortedBy { it.receiptRequestId } shouldEqual initial.also { it.remove(item) }
                }
            }
        }
    }
})