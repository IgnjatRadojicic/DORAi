package dorai.server.core

import dorai.server.export.RoiPackage
import dorai.shared.model.Finding
import dorai.shared.model.Register
import dorai.shared.model.Resource
import kotlinx.datetime.LocalDate

/** Turns the register into files for the supervisor and for people. */
interface RegisterExporter {
    /** The RoI xBRL-CSV report package for [referenceDate]; [draft] marks a package built while readiness errors remain. */
    suspend fun reportPackage(register: Register, resources: List<Resource>, referenceDate: LocalDate, draft: Boolean): RoiPackage.Written

    /** PDF report of the register, what is missing before export, and the current findings. */
    suspend fun pdf(register: Register, resources: List<Resource>, referenceDate: LocalDate, findings: List<Finding>): ByteArray
}
