package iot.example.h2experiments.storage

import android.app.DownloadManager
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper
import org.jdbi.v3.core.statement.Query
import iot.example.h2experiments.storage.domain.Line
import iot.example.h2experiments.storage.domain.Result
import iot.example.h2experiments.storage.domain.OperationRequest
import java.beans.ConstructorProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

open class SaleRepository(private val jdbi: Jdbi) {

    init {
        jdbi.registerRowMapper(ConstructorMapper.factory(Line::class.java))
            .registerRowMapper(ConstructorMapper.factory(OperationRequest::class.java))
            .registerRowMapper(ConstructorMapper.factory(Result::class.java))
    }

    fun addAll(requests: Iterable<OperationRequest>) {
        if (requests.firstOrNull() == null) {
            return
        }

        jdbi.useTransaction<Exception> { h ->
            h.prepareBatch(insertSQL)
                    .also { batch ->
                        requests.forEach { v -> batch.bindBean(v).add() }
                    }.execute()

            insertLines(requests.flatMap { it.lines }, h)
            insertResults(requests.mapNotNull { it.result }, h)
        }
    }

    
    private val insertSQL = "INSERT INTO RECEIPT_REQUESTS ($receiptRequestStatementPositions) VALUES ($receiptRequestValuesBindings)"
    fun add(request: OperationRequest) {
        jdbi.useTransaction<Exception> { h ->
            h.createUpdate(insertSQL)
                    .bindBean(request)
                    .execute()
            insertLines(request.lines, h)
            if (request.result != null) {
                insertResult(request.result, h)
            }
        }
    }
    private val insertLinesSQL = "INSERT INTO LINES ($lineStatementPositions) VALUES ($lineValuesBindings)"

    private fun insertLines(lines: List<Line>, handle: Handle) {
        if (lines.isEmpty()) {
            return
        }
        handle.prepareBatch(insertLinesSQL)
                .also { b ->
                    lines.forEach { v -> b.bindBean(v).add() }
                }.execute()
    }
    private val insertResultSQL = "INSERT INTO SALE_RESULTS ($resultStatementPosition) VALUES ($resultValuesBinding)"

    private fun insertResult(result: Result, handle: Handle) {
        handle.createUpdate(insertResultSQL)
                .bindBean(result)
                .execute()
    }



    private fun insertResults(results: List<Result>, handle: Handle) {
        if (results.isEmpty()) {
            return
        }
        handle.prepareBatch(insertResultSQL)
                .also { b ->
                    results.forEach { v -> b.bindBean(v).add() }
                }
                .execute()
    }


    //TODO Не самое лучшее решение, было бы неплохо доработать
    private val deleteSQL = "DELETE FROM RECEIPT_REQUESTS R WHERE id = ?"

    fun remove(id: Long): Boolean =
            jdbi.inTransaction<Boolean, Exception> { h ->
                h.createUpdate(deleteSQL)
                        .bind(0, id)
                        .execute() == 1
            }

    private val updateSQL = "UPDATE RECEIPT_REQUESTS SET $updateValuesBindings WHERE ID = :id"
    //UPDATE RECEIPT_REQUESTS SET
    // id = :id, account_id = :accountId,
    // fiscalcopygg = :fiscalcopygg, web_cashbox_id = :webCashboxId,
    // type = :type, status = :status, kkt_receipt_id = :kktReceiptId,
    // amount = :amount, cash_amount = :cashAmount, electron_amount = :electronAmount,
    // cashier_name = :cashierName, email = :email, phone_number = :phoneNumber,
    // should_print = :shouldPrint, order_id = :orderId, order_number = :orderNumber, createdAt = :createdAt, updatedAt = :updatedAt, archivedAt = :archivedAt, lockedPreviously = :lockedPreviously, cashier_role = :cashierRole, cashier_inn = :cashierInn, transaction_address = :transactionAddress WHERE ID = :id


    private val deleteLineSQL = "DELETE FROM LINES WHERE RECEIPT_ID = ?"
    private val deleteResultSQL = "DELETE FROM SALE_RESULTS WHERE RECEIPT_ID = ?"
    fun updateRequest(request: OperationRequest) =
            jdbi.inTransaction<Boolean, Exception> { h ->
                val updated = h.createUpdate(updateSQL)
                        .bindBean(request)
                        .execute() == 1
                if (updated) {
                    h.createUpdate(deleteLineSQL)
                            .bind(0, request.id)
                            .execute()
                    insertLines(request.lines, h)
                    h.createUpdate(deleteResultSQL)
                            .bind(0, request.id)
                            .execute()
                    if (request.result != null) {
                        insertResult(request.result, h)
                    }
                }
                return@inTransaction updated
            }!!

    /*
    
    SELECT *
    FROM Authors LEFT JOIN Books
    ON Authors.AuthorID = Books.BookID

     */

    private val selectAllSQL =
            "SELECT $preparedJoinAliases FROM RECEIPT_REQUESTS R " +
                    "LEFT JOIN LINES L ON R.ID = L.RECEIPT_ID " +
                    "LEFT JOIN SALE_RESULTS RE ON R.ID = RE.RECEIPT_ID "
    

    fun getAll(): Set<OperationRequest> {
        return jdbi.inTransaction<Set<OperationRequest>, Exception> { h ->
            h.createQuery(selectAllSQL).handleJoinQuery()
        }!!
    }

    private fun DownloadManager.Query.handleJoinQuery(): Set<OperationRequest> =
        this.registerRowMapper(ConstructorMapper.factory(Line::class.java, "L"))
            .registerRowMapper(ConstructorMapper.factory(SaleReceiptRequest::class.java, "R"))
            .registerRowMapper(ConstructorMapper.factory(Result::class.java, "RE"))
            .reduceRows(LinkedHashMap<OperationRequest, MutableList<Line>>()) { acc, row ->
                val rr = row.getRow(SaleReceiptRequest::class.java)
                        .let {
                            if (row.getNullableColumn<Long?>("re_receiptId") != null) {
                                it.copy(result = row.getRow(Result::class.java))
                            } else {
                                it
                            }
                        }
                val list = acc.getOrPut(rr) { ArrayList() }
                if (row.getNullableColumn<Long?>("l_id") != null) {
                    row.getRow(Line::class.java).let { list.add(it) }
                }
                return@reduceRows acc
            }
            .mapTo(HashSet()) { entry ->
                entry.key.copy(lines = entry.value)
            }

    private val selectSQLPrefix =
            "SELECT $preparedJoinAliases FROM RECEIPT_REQUESTS R LEFT JOIN LINES L ON R.ID = L.RECEIPT_ID " +
                    "LEFT JOIN SALE_RESULTS RE ON R.ID = RE.RECEIPT_ID " +
                    "WHERE "

    fun <T> get(specification: SaleSpecification<T>): T =
            jdbi.inTransaction<T, Exception> { h ->
                h.createQuery(selectSQLPrefix + specification.toSqlClauses())
                        .also(specification::bindParams)
                        .handleJoinQuery()
                        .let(specification::transform)
            }


    private companion object {

        //========= Ниже - подготовленные строки для Receipt Request  ==========
        //Название всех полей класса SaleReceiptRequest ассоциированные с соответствующими SQL названиями
        val receiptRequestFieldNamesToSqlNames: Map<String, String> = OperationRequest::class.constructors
                .first { it.findAnnotation<ConstructorProperties>() != null }
                .let {
                    it.parameters
                            .map { it.name!! }
                            .zip(it.findAnnotation<ConstructorProperties>()!!.value) { fieldName, sqlName ->
                                fieldName to sqlName
                            }
                }
                .toMap()

        //Названия полей класса SaleReceiptRequest преобразованные в биндящую строку для Insert-а
        val receiptRequestValuesBindings = receiptRequestFieldNamesToSqlNames.keys
                .joinToString(separator = ", ") { ":" + it }

        //Позиции для Insert-a
        val receiptRequestStatementPositions = receiptRequestFieldNamesToSqlNames.values
                .joinToString(separator = ", ")

        //Строка для update-а SaleReceiptRequest
        val updateValuesBindings = receiptRequestFieldNamesToSqlNames.entries
                .joinToString(separator = ", ") { "${it.value} = :${it.key}" }

        //========== Ниже - подготовленные строки для Line ==========

        //Название всех полей класса Line ассоциированные с соответствующими SQL названиями
        val lineFieldNamesToSqlNames: Map<String, String> = Line::class.primaryConstructor!!
                .parameters
                .zip(Line::class.primaryConstructor!!.findAnnotation<ConstructorProperties>()!!.value) { field, sqlName ->
                    field.name!! to sqlName
                }
                .toMap()

        //Названия полей класса Line преобразованные в биндящую строку для Insert-а
        val lineValuesBindings = lineFieldNamesToSqlNames.keys
                .joinToString(separator = ", ") { ":" + it }

        //Позиции для Insert-а
        val lineStatementPositions = lineFieldNamesToSqlNames.values
                .joinToString(separator = ", ")

        //========= Ниже - подготовленные строки для Result ==========

        val resultFieldNamesToSqlNames: Map<String, String> = Result::class.primaryConstructor!!
                .parameters
                .zip(Result::class.primaryConstructor!!
                .findAnnotation<ConstructorProperties>()!!.value) { field, sqlName ->
                    field.name!! to sqlName
                }
                .toMap()

        val resultValuesBinding = resultFieldNamesToSqlNames.keys
                .joinToString(separator = ", ") { ":" + it }

        val resultStatementPosition = resultFieldNamesToSqlNames.values
                .joinToString(separator = ", ")

        //=========================================================
                        
        val preparedJoinAliases: String =
                lineFieldNamesToSqlNames.map { entry ->
                    "L.${entry.value} L_${entry.key}"
                }.union(receiptRequestFieldNamesToSqlNames.map { entry ->
                    "R.${entry.value} R_${entry.key}"
                }).union(resultFieldNamesToSqlNames.map { (key, value) ->
                    "RE.$value RE_$key"
                }).joinToString(separator = ", ")
    }
}