package iot.example.h2experiments.storage

import org.jdbi.v3.core.statement.SqlStatement
import iot.example.h2experiments.storage.domain.OperationRequest

interface SaleSpecification<out T> {
    fun toSqlClauses(): String
    fun bindParams(statement: SqlStatement<*>)
    fun transform(result: Iterable<OperationRequest>): T


    
    class Id(private val id: Long) : SaleSpecification<OperationRequest?> {
        override fun transform(result: Iterable<OperationRequest>): OperationRequest? =
                result.firstOrNull()

        override fun toSqlClauses(): String = "R.id = :id"

        override fun bindParams(statement: SqlStatement<*>): Unit =
                let {
                    statement.bind("id", id)
                }
    }

    object First : SaleSpecification<OperationRequest?> {
        override fun toSqlClauses(): String =
                "TRUE LIMIT 1"

        override fun bindParams(statement: SqlStatement<*>) {}

        override fun transform(result: Iterable<OperationRequest>): OperationRequest? =
                result.firstOrNull()
    }

    object Success : SaleSpecification<Iterable<OperationRequest>> {
        override fun toSqlClauses(): String = "R.STATUS='printed'" /*"RE.RECEIPT_ID IS NOT NULL"*/

        override fun bindParams(statement: SqlStatement<*>) {}

        override fun transform(result: Iterable<OperationRequest>): Iterable<OperationRequest> = result
    }

    object Failed : SaleSpecification<Iterable<OperationRequest>> {
        override fun toSqlClauses(): String =
                "R.ID IN (SELECT RECEIPT_ID FROM ERRORS)"

        override fun bindParams(statement: SqlStatement<*>) {}

        override fun transform(result: Iterable<OperationRequest>): Iterable<OperationRequest> = result

    }

    object NotCompleted : SaleSpecification<Iterable<OperationRequest>> {
        // был добавлен для обработки копии OR (R.STATUS = 'pending' AND R.COPY ) в хвост
       // рассмотреть кейс при ошибке при печати копии r.status ='error' recoverable = true

        override fun toSqlClauses(): String =
//                "RE.RECEIPT_ID IS NULL AND R.ID NOT IN (SELECT RECEIPT_ID FROM ERRORS) "
                "(RE.RECEIPT_ID IS NULL AND R.ID NOT IN (SELECT RECEIPT_ID FROM ERRORS)) OR (R.STATUS = 'pending' AND R.FISCALCOPY = 'true' )"


        override fun bindParams(statement: SqlStatement<*>) {}

        override fun transform(result: Iterable<OperationRequest>): Iterable<OperationRequest> = result
    }

}

                                        