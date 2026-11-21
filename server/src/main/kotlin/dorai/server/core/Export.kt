package dorai.server.core

import dorai.shared.model.Finding
import dorai.shared.model.Register

/** Turns the register into files for the supervisor and for people. */
interface RegisterExporter {
    /** EBA register CSV files, zipped. */
    suspend fun csvZip(register: Register): ByteArray

    /** PDF report of the register and the current findings. */
    suspend fun pdf(register: Register, findings: List<Finding>): ByteArray
}
