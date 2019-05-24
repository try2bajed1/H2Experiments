package iot.example.h2experiments.storage

import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper
import org.jdbi.v3.core.statement.Query
import iot.example.h2experiments.storage.domain.CorrectionDB
import iot.example.h2experiments.storage.domain.Result
import java.beans.ConstructorProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

open class CorrectionRepository(private val jdbi: Jdbi) {
    init {
        jdbi.registerRowMapper(ConstructorMapper.factory(CorrectionDB::class.java))
        jdbi.registerRowMapper(ConstructorMapper.factory(Result::class.java))
    }


    private val insertSQL = "INSERT INTO CORRECTION_RECEIPTS VALUES ($correctionValuesBindings)"
    fun add(request: CorrectionDB) {
        jdbi.useTransaction<Exception> { h ->
            h.createUpdate(insertSQL)
                    .bindBean(request)
                    .execute()
            if (request.result != null) {
                insertResult(h, request.result)
            }
        }
    }


    private val insertResultSql = "INSERT INTO CORRECTION_RESULTS VALUES ($resultValuesBinding)"
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

    fun addAll(requests: Iterable<CorrectionDB>) {
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
    private val deleteSQL = "DELETE FROM CORRECTION_RECEIPTS WHERE id = ?"

    fun remove(id: Long): Boolean =
            jdbi.inTransaction<Boolean, Exception> { h ->
                h.createUpdate(deleteSQL)
                        .bind(0, id)
                        .execute() == 1
            }

    private val updateSQL = "UPDATE CORRECTION_RECEIPTS SET $correctionUpdateValuesBindings WHERE ID = :id"
    private val deleteResult = "DELETE FROM CORRECTION_RESULTS WHERE RECEIPT_ID = ?"
    fun update(request: CorrectionDB): Boolean =
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

    private fun Query.handleJoinQuery(): Set<CorrectionDB> =
            this
                    .registerRowMapper(ConstructorMapper.factory(CorrectionDB::class.java, "C"))
                    .registerRowMapper(ConstructorMapper.factory(Result::class.java, "R"))
                    .reduceRows(HashSet()) { acc, row ->
                        val rowRequest = row.getRow(CorrectionDB::class.java)
                        val result =
                                if (row.getNullableColumn<Long?>("r_receiptId") != null) {
                                    rowRequest.copy(result = row.getRow(Result::class.java))
                                } else {
                                    rowRequest
                                }
                        acc.also { it.add(result) }
                    }

    private val selectAllSQL = "SELECT $preparedJoinAliases FROM CORRECTION_RECEIPTS C " +
            "LEFT JOIN CORRECTION_RESULTS R ON C.ID = R.RECEIPT_ID"

    fun getAll(): Set<CorrectionDB> =
            jdbi.inTransaction<Set<CorrectionDB>, Exception> { h ->
                h.createQuery(selectAllSQL)
                        .handleJoinQuery()
            }


    private val selectSQLPrefix = "SELECT $preparedJoinAliases FROM CORRECTION_RECEIPTS C " +
            "LEFT JOIN CORRECTION_RESULTS R ON C.ID = R.RECEIPT_ID WHERE "

    fun <T> get(specification: CorrectionSpecification<T>): T =
            jdbi.inTransaction<T, Exception> { h ->
                h.createQuery(selectSQLPrefix + specification.toSqlClauses())
                        .also(specification::bindParams)
                        .handleJoinQuery()
                        .let(specification::transform)
            }

    private companion object {

        //=============================correction=============================
        val correctionFieldNamesToSqlNames: Map<String, String> = CorrectionDB::class.constructors
                .first { it.findAnnotation<ConstructorProperties>() != null }
                .let { constr ->
                    constr.parameters.map { it.name!! } zip constr.findAnnotation<ConstructorProperties>()!!.value
                }
                .toMap()

        val correctionValuesBindings = correctionFieldNamesToSqlNames
                .keys
                .joinToString(separator = ", ") { ":$it" }


        val correctionUpdateValuesBindings = correctionFieldNamesToSqlNames
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
                correctionFieldNamesToSqlNames.map { entry ->
                    "C.${entry.value} C_${entry.key}"
                }
                        .union(resultFieldNamesToSqlNames.map { (key, value) ->
                            "R.$value R_$key"
                        })
                        .joinToString(separator = ", ")
    }
}