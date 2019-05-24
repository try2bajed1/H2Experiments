package iot.example.h2experiments.storage

import org.jdbi.v3.core.statement.SqlStatement
import iot.example.h2experiments.storage.domain.CorrectionDB

interface CorrectionSpecification<out T> {
    fun toSqlClauses(): String
    fun bindParams(statement: SqlStatement<*>)
    fun transform(result: Iterable<CorrectionDB>): T

    class ID(private val id: Long) : CorrectionSpecification<CorrectionDB?> {
        override fun transform(result: Iterable<CorrectionDB>): CorrectionDB? =
                result.firstOrNull()

        override fun toSqlClauses(): String = "id = :id"

        override fun bindParams(statement: SqlStatement<*>): Unit =
                let {
                    statement.bind("id", id)
                }
    }

    object First : CorrectionSpecification<CorrectionDB?> {
        override fun toSqlClauses(): String =
                "TRUE LIMIT 1"

        override fun bindParams(statement: SqlStatement<*>) {}

        override fun transform(result: Iterable<CorrectionDB>): CorrectionDB? =
                result.firstOrNull()
    }


    object Success : CorrectionSpecification<Iterable<CorrectionDB>> {
        override fun toSqlClauses(): String =
                "R.RECEIPT_ID IS NOT NULL"

        override fun bindParams(statement: SqlStatement<*>) {}

        override fun transform(result: Iterable<CorrectionDB>): Iterable<CorrectionDB> = result
    }

    object Failed : CorrectionSpecification<Iterable<CorrectionDB>> {
        override fun toSqlClauses(): String =
                "C.ID IN (SELECT RECEIPT_ID FROM ERRORS)"

        override fun bindParams(statement: SqlStatement<*>) {}

        override fun transform(result: Iterable<CorrectionDB>): Iterable<CorrectionDB> = result

    }

    object NotCompleted : CorrectionSpecification<Iterable<CorrectionDB>> {
        override fun toSqlClauses(): String =
                "R.RECEIPT_ID IS NULL AND C.ID NOT IN (SELECT RECEIPT_ID FROM ERRORS)"

        override fun bindParams(statement: SqlStatement<*>) {}

        override fun transform(result: Iterable<CorrectionDB>): Iterable<CorrectionDB> = result
    }

}