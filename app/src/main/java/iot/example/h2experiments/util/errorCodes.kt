package iot.example.h2experiments.util

const val VALIDATION_ERROR: Int = 10006
const val SERVICE_NOT_AVAIBLE: Int = 10007
const val RETAIL_SHIFT_NOT_CLOSED_ERROR: Int = 3822

fun buildErrorCode(type: ErrorType, code: Int) = type.prefix + code