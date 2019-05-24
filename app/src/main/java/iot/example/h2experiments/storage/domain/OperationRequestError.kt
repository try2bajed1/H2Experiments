package iot.example.h2experiments.storage.domain

import java.beans.ConstructorProperties

data class OperationRequestError @ConstructorProperties("receipt_id", "error_code",
        "error_type", "error_description", "recoverable") constructor(val receiptRequestId: Long,
                                                                      val errorCode: Int,
                                                                      val errorDescription: String,
                                                                      val recoverable: Boolean)