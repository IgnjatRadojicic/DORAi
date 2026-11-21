package dorai.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import dorai.shared.Dorai
import kotlin.system.exitProcess

class DoraiCommand : CliktCommand(name = "dorai") {
    init {
        versionOption("0.1.0")
        subcommands(CheckCommand())
    }

    override fun help(context: Context) = "${Dorai.NAME} command line."

    override fun run() = Unit

    /** Runs the command line and returns the exit code; a usage error counts as bad input (2). */
    fun execute(args: List<String>): Int = try {
        parse(args)
        ExitCodes.CLEAN
    } catch (e: CliktError) {
        echoFormattedHelp(e)
        if (e is UsageError) ExitCodes.INVALID_INPUT else e.statusCode
    }
}

fun main(args: Array<String>) {
    exitProcess(DoraiCommand().execute(args.toList()))
}
