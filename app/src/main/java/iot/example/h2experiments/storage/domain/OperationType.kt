package iot.example.h2experiments.storage.domain

enum class OperationType(val backendRequestName: String, val boxUrl: String, val typeName: String) {
    SALE("op_1", "1", "Sale"),
    RETURN("op_2", "2", "Return"),
    SHIFTCLOSE("op_3", "3", "CloseReta"),
    CORRECTION_SALE("op_4", "4", "SaleCor"),
    CORRECTION_REFUND("op_5", "5", "ReturnCor");


    companion object {

        private val TYPE_BY_BOX_URL = OperationType.values()
                .associate { it.boxUrl to it }

        private val TYPE_BY_TYPE_NAME = OperationType.values()
                .associate { it.typeName to it }

        private val TYPE_BY_BACKEND_REQUEST_NAME = OperationType.values()
                .associate { it.backendRequestName to it }

        fun findByBoxUrl(boxUrl: String): OperationType? = TYPE_BY_BOX_URL[boxUrl]

        fun findByTypeName(typeName: String) = TYPE_BY_TYPE_NAME[typeName]

        fun findByBackendRequestName(backendRequestName: String) =
                TYPE_BY_BACKEND_REQUEST_NAME[backendRequestName]
    }
}
