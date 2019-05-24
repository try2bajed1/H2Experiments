package ru.ekam.online

import io.reactivex.Single
import org.funktionale.either.Either
import ru.ekam.base.utils.toLeft
import ru.ekam.base.utils.toRight

fun <T> cases(vararg values: T, execution: (T) -> Unit) =
        values.forEach(execution)

fun <T> Single<T>.eitherBlockingGet(): Either<Exception, T> =
        try {
            this.blockingGet().toRight<Exception, T>()
        } catch (e: Exception) {
            e.toLeft()
        }

