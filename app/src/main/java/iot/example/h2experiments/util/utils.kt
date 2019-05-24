package iot.example.h2experiments.util

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import com.typesafe.config.Config
import org.jdbi.v3.core.result.RowView

fun Kodein.getConfig(): Config = this.instance()

inline fun  <reified T> RowView.getNullableColumn(column: String): T? = getColumn(column, T::class.java)

