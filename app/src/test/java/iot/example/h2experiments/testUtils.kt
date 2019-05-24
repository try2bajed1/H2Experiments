package iot.example.h2experiments

import org.joda.time.DateTime
import iot.example.h2experiments.domain.*
import iot.example.h2experiments.storage.domain.*
import java.math.BigDecimal
import java.util.*
import kotlin.math.absoluteValue

val random = Random()

fun randomInt() = random.nextInt()
fun randomLong() = random.nextLong()
fun randomMoneyDecimal() = BigDecimal(random.nextInt().toString() + "." + 99 /*random.nextInt(100)*/)
fun randomBoolean() = random.nextBoolean()
fun randomDate() = DateTime(random.nextLong().absoluteValue)
private const val source = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_+=-abcdefghijklmnopqrstuvwxyz[]{};:'"
fun randomString(length: Long) =
        (0 until length).asSequence()
                .map { source[random.nextInt(source.length)] }
                .joinToString(separator = "")

fun createRandomError(receiptRequestId: Long) =
        OperationRequestError(
                receiptRequestId = receiptRequestId,
                errorCode = randomInt(),
                errorDescription = randomString(256),
                recoverable = randomBoolean()
        )

fun createReceiptRequestWithoutNullFields(id: Long, linesNumber: Int, lineIdBasValue: Long = id) =
        OperationRequest(id = id,
                lines = (0 until linesNumber).map { createLinesWithoutNulls(lineIdBasValue + it, id) },
                accountId = randomInt(),
                fiscalcopy = false,
                webCashboxId = randomInt(),
                type = randomType(),
                status = randomString(128),
                kktReceiptId = randomLong(),
                amount = randomMoneyDecimal(),
                cashAmount = randomMoneyDecimal(),
                electronAmount = randomMoneyDecimal(),
                prepaidAmount = randomMoneyDecimal(),
                postpaidAmount = randomMoneyDecimal(),
                counterOfferAmount = randomMoneyDecimal(),
                serverNum = UUID.randomUUID().toString(),
                cashierName = randomString(128),
                email = randomString(128),
                phoneNumber = randomString(20),
                shouldPrint = randomBoolean(),
                orderId = randomString(12),
                orderNumber = randomString(12),
                createdAt = randomDate(),
                updatedAt = randomDate(),
                archivedAt = randomDate(),
                lockedPreviously = randomBoolean(),
                result = createRandomResult(id),
                transactionAddress = randomString(64),
                cashierInn = randomString(64),
                cashierRole = randomString(64))

fun createRandomResult(id: Long) =
        Result(id)

fun createLinesWithoutNulls(id: Long, receiptRequestId: Long) =
        Line(id = id,
                receiptId = receiptRequestId,
                title = randomString(256),
                quantity = randomMoneyDecimal(),
                totalPrice = randomMoneyDecimal(),
                price = randomMoneyDecimal(),
                vatRate = TaxCode.values()[random.nextInt(4)],
                vatAmount = randomMoneyDecimal(),
                createdAt = randomDate(),
                updatedAt = randomDate(),
                paymentCase = 4)

fun createReceiptRequestWithNullFields(id: Long, linesNumber: Int, lineIdBasValue: Long = id) =
        OperationRequest(id = id,
                lines = (0 until linesNumber).map { createLinesWithNulls(lineIdBasValue + it, id) },
                accountId = randomInt(),
                webCashboxId = null,
                fiscalcopy = false,
                type = randomType(),
                status = randomString(12),
                kktReceiptId = null,
                amount = randomMoneyDecimal(),
                cashAmount = randomMoneyDecimal(),
                electronAmount = randomMoneyDecimal(),
                prepaidAmount = randomMoneyDecimal(),
                postpaidAmount = randomMoneyDecimal(),
                counterOfferAmount = randomMoneyDecimal(),
                serverNum = UUID.randomUUID().toString(),
                cashierName = null,
                email = null,
                phoneNumber = null,
                shouldPrint = randomBoolean(),
                orderId = null,
                orderNumber = null,
                createdAt = null,
                updatedAt = null,
                archivedAt = null,
                lockedPreviously = randomBoolean(),
                cashierRole = randomString(64),
                cashierInn = randomString(64),
                transactionAddress = randomString(64),
                result = null
                )

fun createLinesWithNulls(id: Long, receiptRequestId: Long) =
        Line(id = id,
                receiptId = receiptRequestId,
                title = randomString(256),
                quantity = randomMoneyDecimal(),
                totalPrice = randomMoneyDecimal(),
                price = null,
                vatRate = TaxCode.values()[random.nextInt(4)],
                vatAmount = null,
                createdAt = null,
                updatedAt = null,
                paymentCase = 4)

fun createRandomCloseRetailShiftRequestWithoutNulls(id: Long) =
        CloseRequest(id = id,
                accountId = randomInt(),
                webCashboxId = randomInt(),
                status = randomString(128),
                shouldPrint = randomBoolean(),
                createdAt = randomDate(),
                updatedAt = randomDate(),
                archivedAt = randomDate(),
                lockedPreviously = randomBoolean(),
                serverNum = UUID.randomUUID().toString(),
                result = createRandomResult(id))


fun createRandomCloseRetailShiftRequestWithNulls(id: Long) =
        CloseRequest(id = id,
                accountId = randomInt(),
                webCashboxId = null,
                status = null,
                shouldPrint = randomBoolean(),
                createdAt = null,
                updatedAt = null,
                archivedAt = null,
                lockedPreviously = randomBoolean(),
                serverNum = UUID.randomUUID().toString())


fun createCorrectionWithoutNulls(id: Long) =
        CorrectionDB(id = id,
                    amount = randomMoneyDecimal(),
                    cashAmount = randomMoneyDecimal(),
                    electronAmount = randomMoneyDecimal(),
                    shouldPrint = true,
                    cashierName = randomString(20),
                    vatRate = TaxCode.EIGHTEEN,
                    correctionDescription = randomString(20),
                    correctionType = "self_correction",
                    documentNumber = "1",
                    documentDate = randomDate(),
                    status = "pending",
                    orderId = "10",
                    orderNumber ="10" ,
                    createdAt = randomDate() ,
                    updatedAt = randomDate(),
                    archivedAt = randomDate(),
                    lockedPreviously = false,
                    serverNum = UUID.randomUUID().toString()
                )



fun createCorrectionWithNulls(id: Long) =
        CorrectionDB(id = id,
                    amount = randomMoneyDecimal(),
                    cashAmount = randomMoneyDecimal(),
                    electronAmount = randomMoneyDecimal(),
                    shouldPrint = true,
                    cashierName = randomString(10),
                    vatRate = TaxCode.EIGHTEEN,
                    correctionDescription = null,
                    correctionType = "self_correction",
                    documentNumber = "1",
                    documentDate = randomDate(),
                    status = "pending",
                    orderId = "10",
                    orderNumber ="10" ,
                    createdAt = randomDate() ,
                    updatedAt = randomDate(),
                    archivedAt = randomDate(),
                    lockedPreviously = false,
                    serverNum = UUID.randomUUID().toString()
                )


































