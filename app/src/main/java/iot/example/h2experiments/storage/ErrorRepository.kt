package iot.example.h2experiments.storage

import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper
import iot.example.h2experiments.storage.domain.OperationRequestError
import java.beans.ConstructorProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

open class ErrorRepository(private val jdbi: Jdbi) {

    init {
        jdbi.registerRowMapper(ConstructorMapper.factory(OperationRequestError::class.java))
    }

    private val insertSQL = "INSERT INTO ERRORS ($statementPositions) VALUES ($valueBindings)"
    fun add(receiptRequestError: OperationRequestError) {
        jdbi.useTransaction<Exception> { h ->
            h.createUpdate(insertSQL)
                    .bindBean(receiptRequestError)
                    .execute()
        }
    }

    fun addAll(receiptRequestErrors: List<OperationRequestError>) {
        if (receiptRequestErrors.isEmpty()) {
            return
        }
        jdbi.useTransaction<Exception> { h ->
            h.prepareBatch(insertSQL)
                    .also { b ->
                        receiptRequestErrors.forEach { item -> b.bindBean(item).add() }
                    }
                    .execute()
        }
    }


    private val deleteSQL = "DELETE FROM ERRORS WHERE RECEIPT_ID = ?"
    fun remove(receiptRequestId: Long): Boolean =
            jdbi.inTransaction<Boolean, Exception> { h ->
                h.createUpdate(deleteSQL)
                        .bind(0, receiptRequestId)
                        .execute() == 1
            }


    private val getAllSql = "SELECT * FROM ERRORS"
    fun getAll(): Set<OperationRequestError> =
            jdbi.inTransaction<Set<OperationRequestError>, Exception> { h ->
                h.createQuery(getAllSql)
                        .mapTo(OperationRequestError::class.java)
                        .toSet()
            }


    private companion object {
        val fieldToSqlNames: Map<String, String> = OperationRequestError::class.primaryConstructor!!
                .let { constructor ->
                    constructor.parameters.map { it.name!! }
                            .zip(constructor.findAnnotation<ConstructorProperties>()!!.value.map { it }) { f, s ->
                                f to s
                            }
                            .toMap()
                }
        val valueBindings = fieldToSqlNames.keys.joinToString(separator = ", ") { ":$it" }
        val statementPositions = fieldToSqlNames.values.joinToString(separator = ", ")
    }
}