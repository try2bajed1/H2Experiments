package iot.example.h2experiments.storage.domain

import org.joda.time.DateTime
import java.beans.ConstructorProperties
import java.math.BigDecimal

/**
 * Created with IntelliJ IDEA.
 * User: nsenchurin
 * Date: 20.08.18
 * Time: 17:45
 */
data class CorrectionDB constructor(val id:Long,
                                    val type: OperationType,
                                    val amount: BigDecimal,
                                    val cashAmount: BigDecimal,
                                    val electronAmount: BigDecimal,
                                    val shouldPrint: Boolean,
                                    val cashierName: String?,
                                    val vatRate: TaxCode,
                                    val correctionDescription: String?,
                                    val correctionType: String,
                                    val documentNumber: String?,
                                    val documentDate: DateTime?,
                                    val status:String,
                                    val orderId:String,
                                    val orderNumber: String,
                                    val createdAt:DateTime,
                                    val updatedAt:DateTime,
                                    val archivedAt:DateTime,
                                    val lockedPreviously:Boolean,
                                    val serverNum: String,
                                    val result: Result?) {
    @ConstructorProperties(
            "id",
            "type",
            "amount",
            "cash_amount",
            "electron_amount",
            "should_print",
            "cashier_name",
            "vat_rate",
            "correction_description",
            "correction_type",
            "document_number",
            "document_date",
            "status",
            "order_id",
            "order_number",
            "created_at",
            "updated_at",
            "archived_at",
            "locked_previously",
            "server_num") constructor(id: Long,
                                      type: OperationType,
                                      amount: BigDecimal,
                                      cashAmount: BigDecimal,
                                      electronAmount: BigDecimal,
                                      shouldPrint: Boolean,
                                      cashierName: String?,
                                      vatRate: TaxCode,
                                      correctionDescription: String?,
                                      correctionType: String,
                                      documentNumber: String?,
                                      documentDate: DateTime?,
                                      status: String,
                                      orderId:String,
                                      orderNumber: String,
                                      createdAt:DateTime,
                                      updatedAt:DateTime,
                                      archivedAt:DateTime,
                                      lockedPreviously:Boolean,
                                      serverNum: String
    ) :this(id=id,
                type = type,
                amount = amount,
                cashAmount = cashAmount,
                electronAmount = electronAmount,
                shouldPrint = shouldPrint,
                cashierName = cashierName,
                vatRate = vatRate,
                correctionDescription = correctionDescription,
                correctionType = correctionType,
                documentNumber = documentNumber,
                documentDate = documentDate,
                status=status,
                orderId=orderId,
                orderNumber=orderNumber,
                createdAt = createdAt,
                updatedAt = updatedAt,
                archivedAt = archivedAt,
                lockedPreviously = lockedPreviously,
                serverNum = serverNum,
                result = null)
}
