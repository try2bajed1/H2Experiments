package iot.example.h2experiments.storage

import org.jdbi.v3.core.statement.SqlStatement
import iot.example.h2experiments.storage.domain.CloseRequest

interface CloseSpecification<out T> {
    fun toSqlClauses(): String
    fun bindParams(statement: SqlStatement<*>)
    fun transform(result: Iterable<CloseRequest>): T

    class ID(private val id: Long) : CloseSpecification<CloseRequest?> {
        override fun transform(result: Iterable<CloseRequest>): CloseRequest? =
                result.firstOrNull()

        override fun toSqlClauses(): String = "id = :id"

        override fun bindParams(statement: SqlStatement<*>): Unit =
                let {
                    statement.bind("id", id)
                }
    }

    object First : CloseSpecification<CloseRequest?> {
        override fun toSqlClauses(): String =
                "TRUE LIMIT 1"

        override fun bindParams(statement: SqlStatement<*>) {}

        override fun transform(result: Iterable<CloseRequest>): CloseRequest? =
                result.firstOrNull()
    }


    object Success : CloseSpecification<Iterable<CloseRequest>> {
        override fun toSqlClauses(): String =
                "R.RECEIPT_ID IS NOT NULL"

        override fun bindParams(statement: SqlStatement<*>) {}

        override fun transform(result: Iterable<CloseRequest>): Iterable<CloseRequest> = result
    }

    object Failed : CloseSpecification<Iterable<CloseRequest>> {
        override fun toSqlClauses(): String =
                "C.ID IN (SELECT RECEIPT_ID FROM ERRORS)"

        override fun bindParams(statement: SqlStatement<*>) {}

        override fun transform(result: Iterable<CloseRequest>): Iterable<CloseRequest> = result

    }

    object NotCompleted : CloseSpecification<Iterable<CloseRequest>> {
        override fun toSqlClauses(): String =
                "R.RECEIPT_ID IS NULL AND C.ID NOT IN (SELECT RECEIPT_ID FROM ERRORS)"

        override fun bindParams(statement: SqlStatement<*>) {}

        override fun transform(result: Iterable<CloseRequest>): Iterable<CloseRequest> = result
    }

}