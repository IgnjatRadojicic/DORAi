package dorai.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.versionOption
import dorai.shared.Dorai

class DoraiCommand : CliktCommand(name = "dorai") {
    init {
        versionOption("0.1.0")
    }

    override fun help(context: Context) = "${Dorai.NAME} command line."

    override fun run() = echo("${Dorai.NAME}: run `dorai --help` for commands.")
}

fun main(args: Array<String>) = DoraiCommand().main(args)
