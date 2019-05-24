package iot.example.h2experiments.storage

import android.app.DownloadManager
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper
import org.jdbi.v3.core.statement.Query
import iot.example.h2experiments.storage.domain.CloseRequest
import iot.example.h2experiments.storage.domain.Result
import java.beans.ConstructorProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

open class CloseRepository(private val jdbi: Jdbi) {
    init {
        jdbi.registerRowMapper(ConstructorMapper.factory(CloseRequest::class.java))
        jdbi.registerRowMapper(ConstructorMapper.factory(Result::class.java))
    }


    private val insertSQL = "INSERT INTO CLOSE_RETAIL_SHIFT_REQUESTS VALUES ($closeValuesBindings)"
    fun add(request: CloseRequest) {
        jdbi.useTransaction<Exception> { h ->
            h.createUpdate(insertSQL)
                    .bindBean(request)
                    .execute()
            if (request.result != null) {
                insertResult(h, request.result)
            }
        }
    }


    private val insertResultSql = "INSERT INTO CLOSE_RESULTS VALUES ($resultValuesBinding)"
    private fun insertResults(h: Handle, results: List<Result>) {
        if (results.isEmpty()) {
            return
        }
        h.prepareBatch(insertResultSql)
                .also { b -> results.forEach { v -> b.bindBean(v).add() } }
                .execute()
    }

    private fun insertResult(h: Handle, result: Result) {
        h.prepareBatch(insertResultSql)
                .bindBean(result)
                .execute()
    }

    fun addAll(requests: Iterable<CloseRequest>) {
        if (requests.firstOrNull() == null) {
            return
        }
        jdbi.useTransaction<Exception> { h ->
            h.prepareBatch(insertSQL)
                    .also { batch ->
                        requests.forEach { v -> batch.bindBean(v).add() }
                    }
                    .execute()
            insertResults(h, requests.mapNotNull { it.result })
        }
    }

    //TODO доработать
    private val deleteSQL = "DELETE FROM CLOSE_RETAIL_SHIFT_REQUESTS WHERE id = ?"

    fun remove(id: Long): Boolean =
            jdbi.inTransaction<Boolean, Exception> { h ->
                h.createUpdate(deleteSQL)
                        .bind(0, id)
                        .execute() == 1
            }

    private val updateSQL = "UPDATE CLOSE_RETAIL_SHIFT_REQUESTS SET $closeUpdateValuesBindings WHERE ID = :id"
    private val deleteResult = "DELETE FROM CLOSE_RESULTS WHERE RECEIPT_ID = ?"
    fun update(request: CloseRequest): Boolean =
            jdbi.inTransaction<Boolean, Exception> { h ->
                val result = h.createUpdate(updateSQL)
                        .bindBean(request)
                        .execute() == 1
                if (result) {
                    h.createUpdate(deleteResult)
                            .bind(0, request.id)
                            .execute()
                    if (request.result != null) {
                        insertResult(h, request.result)
                    }
                }
                return@inTransaction result
            }!!

    private fun DownloadManager.Query.handleJoinQuery(): Set<CloseRequest> =
            this
                    .registerRowMapper(ConstructorMapper.factory(CloseRetailShiftRequest::class.java, "C"))
                    .registerRowMapper(ConstructorMapper.factory(Result::class.java, "R"))
                    .reduceRows(HashSet()) { acc, row ->
                        val rowRequest = row.getRow(CloseRetailShiftRequest::class.java)
                        val result =
                                if (row.getNullableColumn<Long?>("r_receiptId") != null) {
                                    rowRequest.copy(result = row.getRow(Result::class.java))
                                } else {
                                    rowRequest
                                }
                        acc.also { it.add(result) }
                    }

    private val selectAllSQL = "SELECT $preparedJoinAliases FROM CLOSE_RETAIL_SHIFT_REQUESTS C " +
            "LEFT JOIN CLOSE_RESULTS R ON C.ID = R.RECEIPT_ID"

    fun getAll(): Set<CloseRequest> =
            jdbi.inTransaction<Set<CloseRequest>, Exception> { h ->
                h.createQuery(selectAllSQL)
                        .handleJoinQuery()
            }


    private val selectSQLPrefix = "SELECT $preparedJoinAliases FROM CLOSE_RETAIL_SHIFT_REQUESTS C " +
            "LEFT JOIN CLOSE_RESULTS R ON C.ID = R.RECEIPT_ID WHERE "

    fun <T> get(specification: CloseSpecification<T>): T =
            jdbi.inTransaction<T, Exception> { h ->
                h.createQuery(selectSQLPrefix + specification.toSqlClauses())
                        .also(specification::bindParams)
                        .handleJoinQuery()
                        .let(specification::transform)
            }

    private companion object {
        //=============================Close Retail Shift=============================
        val closeFieldNamesToSqlNames: Map<String, String> = CloseRequest::class.constructors
                .first { it.findAnnotation<ConstructorProperties>() != null }
                .let { constr ->
                    constr.parameters.map { it.name!! } zip constr.findAnnotation<ConstructorProperties>()!!.value
                }
                .toMap()

        val closeValuesBindings = closeFieldNamesToSqlNames
                .keys
                .joinToString(separator = ", ") { ":$it" }


        val closeUpdateValuesBindings = closeFieldNamesToSqlNames
                .entries
                .joinToString(separator = ", ") { "${it.value} = :${it.key}" }

        //=============================Result=============================

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

        //==========================================================

        val preparedJoinAliases: String =
                closeFieldNamesToSqlNames.map { entry ->
                    "C.${entry.value} C_${entry.key}"
                }
                        .union(resultFieldNamesToSqlNames.map { (key, value) ->
                            "R.$value R_$key"
                        })
                        .joinToString(separator = ", ")
    }
}