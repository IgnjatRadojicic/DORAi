package dorai.cli

import com.github.ajalt.clikt.testing.test
import dorai.shared.model.DoraiJson
import dorai.shared.model.Finding
import dorai.shared.model.Severity
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `dorai check` on the FinPay example: the clean plan in `examples/finpay` and the eastus demo plan. */
class CheckCommandTest {
    private val root = Path("..")
    private val register = (root / "examples" / "finpay" / "register.json").absolutePathString()
    private val cleanPlan = (root / "examples" / "finpay" / "plan.json").absolutePathString()
    private val demoPlan = (root / "server" / "src" / "main" / "resources" / "fixtures" / "plan-eastus.json").absolutePathString()

    private fun check(vararg args: String) = DoraiCommand().test(listOf("check", *args))

    @Test
    fun aPlanWithoutChangesPasses() {
        val result = check(cleanPlan, "--register", register)
        assertEquals(ExitCodes.CLEAN, result.statusCode, result.stderr)
        assertTrue(result.stdout.startsWith("DORAi: no register updates required"), result.stdout)
    }

    @Test
    fun theEastusPlanFailsWithHighFindings() {
        val result = check(demoPlan, "-r", register)
        assertEquals(ExitCodes.HIGH, result.statusCode, result.stderr)
        assertTrue(result.stdout.lines().any { it.startsWith("HIGH") }, result.stdout)
        assertTrue("aws_db_instance.payments_replica" in result.stdout)
    }

    @Test
    fun markdownIsThePrComment() {
        val out = Files.createTempFile("dorai-check", ".md")
        val result = check(demoPlan, "-r", register, "--format", "markdown", "--output", out.toString())
        assertEquals(ExitCodes.HIGH, result.statusCode)
        assertEquals("", result.stdout)
        val md = out.readText()
        assertTrue(md.startsWith(CheckReport.STICKY_MARKER + "\n### DORAi: "), md)
        assertTrue("| Severity | Finding | Resource | Function | Register |" in md)
        assertTrue("this check fails" in md)
    }

    @Test
    fun jsonHasASummaryAndEveryFinding() {
        val result = check(demoPlan, "-r", register, "-f", "json")
        val json = DoraiJson.parseToJsonElement(result.stdout).jsonObject
        val findings = DoraiJson.decodeFromJsonElement<List<Finding>>(json.getValue("findings"))
        val summary = json.getValue("summary").jsonObject
        assertEquals(findings.size, summary.getValue("total").jsonPrimitive.int)
        assertEquals(findings.count { it.severity == Severity.High }, summary.getValue("high").jsonPrimitive.int)
        assertTrue(findings.first().severity == Severity.High)
    }

    @Test
    fun badInputExitsWithTwo() {
        val missing = check("nope.json", "-r", register)
        assertEquals(ExitCodes.INVALID_INPUT, missing.statusCode)
        assertTrue("Plan file not found" in missing.stderr, missing.stderr)

        val notAPlan = Files.createTempFile("not-a-plan", ".json").apply { writeText("{\"a\": 1}") }
        val invalid = check(notAPlan.toString(), "-r", register)
        assertEquals(ExitCodes.INVALID_INPUT, invalid.statusCode)
        assertTrue("resource_changes" in invalid.stderr, invalid.stderr)

        val badRegister = Files.createTempFile("register", ".json").apply { writeText("[]") }
        assertEquals(ExitCodes.INVALID_INPUT, check(cleanPlan, "-r", badRegister.toString()).statusCode)

        // Usage errors (a missing --register, an unknown format) are bad input too.
        assertEquals(ExitCodes.INVALID_INPUT, DoraiCommand().execute(listOf("check", cleanPlan)))
        assertEquals(ExitCodes.INVALID_INPUT, DoraiCommand().execute(listOf("check", cleanPlan, "-r", register, "-f", "xml")))
    }
}

class CheckReportTest {
    private fun finding(title: String, address: String = "aws_s3_bucket.x", severity: Severity = Severity.Medium) =
        Finding(ruleId = "R1", severity = severity, title = title, resourceAddress = address, detail = "detail", templates = listOf("B_02.01"))

    @Test
    fun untrustedTextCannotBreakTheComment() {
        val md = CheckReport.format(
            listOf(finding("<img src=x> @team | **bold** [link](http://x)\nnew line", address = "a`|b@c<d>")),
            CheckFormat.Markdown,
        )
        val row = md.lines().single { it.startsWith("| ") && "MEDIUM" in it }
        assertFalse("<img" in md)
        assertFalse("@team" in md)
        assertTrue("&lt;img src=x&gt; &#64;team \\| \\*\\*bold\\*\\* \\[link\\](http://x) new line" in row, row)
        assertTrue("`a'\\|b\uFF20c\u2039d\u203A`" in row, row)
    }

    @Test
    fun theCommentStaysUnderGithubsLimit() {
        val many = List(400) { finding("finding $it " + "x".repeat(300), address = "aws_s3_bucket.b$it") }
        val md = CheckReport.format(many, CheckFormat.Markdown)
        assertTrue(md.encodeToByteArray().size <= CheckReport.MAX_MARKDOWN_BYTES, md.length.toString())
        assertTrue("more finding(s) omitted" in md)
    }

    @Test
    fun orderIsHighFirstThenResource() {
        val text = CheckReport.format(
            listOf(finding("b", "z"), finding("a", "y", Severity.High), finding("c", "a", Severity.Low), finding("d", "a")),
            CheckFormat.Text,
        )
        val severities = text.lines().filter { it.matches(Regex("^(HIGH|MEDIUM|LOW) .*")) }.map { it.substringAfter(" ").trim() }
        assertEquals(listOf("a", "d", "b", "c"), severities)
        assertEquals(ExitCodes.HIGH, CheckReport.exitCode(listOf(finding("a", severity = Severity.High))))
        assertEquals(ExitCodes.CLEAN, CheckReport.exitCode(listOf(finding("a"))))
    }
}
