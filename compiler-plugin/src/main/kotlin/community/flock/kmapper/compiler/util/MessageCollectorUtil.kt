package community.flock.kmapper.compiler.util

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.ERROR
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.INFO
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.WARNING
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

object MessageCollectorUtil {
    fun MessageCollector.info(message: String) = report(INFO, message)
    fun MessageCollector.warn(message: String) = report(WARNING, message)
    fun MessageCollector.error(message: String) = report(ERROR, message)
}