package iot.example.h2experiments.storage.domain

import org.joda.time.DateTime
import java.beans.ConstructorProperties


data class CloseRequest constructor(val id: Long,
                                    val accountId: Int,
                                    val webCashboxId: Int?,
                                    val status: String?,
                                    val shouldPrint: Boolean,
                                    val createdAt: DateTime?,
                                    val updatedAt: DateTime?,
                                    val archivedAt: DateTime?,
                                    val lockedPreviously: Boolean,
                                    val serverNum: String,
                                    val result: Result?) {

    @ConstructorProperties("id", "account_id", "web_cashbox_id", "status", "should_print",
            "created_at", "updated_at", "archived_at","locked_previously","server_num")
    constructor(id: Long,
                accountId: Int,
                webCashboxId: Int?,
                status: String?,
                shouldPrint: Boolean,
                createdAt: DateTime?,
                updatedAt: DateTime?,
                archivedAt: DateTime?,
                lockedPreviously: Boolean,
                serverNum: String) :
            this(id = id,
                    accountId = accountId,
                    webCashboxId = webCashboxId,
                    status = status,
                    shouldPrint = shouldPrint,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    archivedAt = archivedAt,
                    lockedPreviously = lockedPreviously,
                    serverNum = serverNum,
                    result = null)

    companion object {
        val type: OperationType = OperationType.SHIFTCLOSE
    }
}