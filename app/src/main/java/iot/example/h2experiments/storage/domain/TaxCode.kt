package iot.example.h2experiments.storage.domain

enum class TaxCode(val backendValue: Int?, val taxTypeId: Int) {
    ZERO(0, 1),
    TEN(10, 2),
    EIGHTEEN(18, 3),
    TWENTY(20, 3),
    NULL(null, 4);


    companion object {

        private val byVal = TaxCode.values().associateBy { it.backendValue }
        fun getByBackendValue(value: Int?) = byVal[value]
    }
}