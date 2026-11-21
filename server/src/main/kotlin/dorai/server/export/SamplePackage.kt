package dorai.server.export

import dorai.server.fixtures.FixtureData
import dorai.shared.roi.RoiTables
import kotlinx.datetime.LocalDate
import java.io.File

/**
 * Writes the FinPay fixture's RoI package (a draft, as the fixture is not complete) to the folder in
 * args[0], for the offline taxonomy check in CI (`scripts/validate-roi.sh`). Fixed dates, so the bytes
 * only change when the code or the fixture does.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/roi-sample").apply { mkdirs() }
    val data = FixtureData.load()
    val written = RoiPackage.write(
        RoiTables.build(data.register, data.resources, LocalDate(2026, 12, 31)),
        data.register.entity.lei, data.register.entity.country, "EUR", "20270115120000000", draft = true,
    )
    File(out, written.fileName).writeBytes(written.bytes)
    println(File(out, written.fileName).path)
}
