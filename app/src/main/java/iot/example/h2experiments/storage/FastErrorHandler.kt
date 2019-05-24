package iot.example.h2experiments.storage

import android.content.Context
import org.flywaydb.core.api.errorhandler.Context
import org.flywaydb.core.api.errorhandler.ErrorHandler
import org.xml.sax.ErrorHandler

/**
 * Created with IntelliJ IDEA.
 * User: nsenchurin
 * Date: 26.03.18
 * Time: 11:50
 */
class FastErrorHandler : ErrorHandler {
    override fun handle(context: Context?): Boolean {
        return true
    }

}