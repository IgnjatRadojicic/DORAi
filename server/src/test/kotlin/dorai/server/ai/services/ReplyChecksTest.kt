package dorai.server.ai.services

import dorai.server.ai.chat.ChatModel
import dorai.server.ai.chat.FakeChatModel
import dorai.server.core.AiAnswer
import dorai.server.core.DraftText
import dorai.server.fixtures.FixtureData
import dorai.shared.model.ChangeAction
import dorai.shared.model.Finding
import dorai.shared.model.Redundancy
import dorai.shared.model.Resource
import dorai.shared.model.ResourceSource
import dorai.shared.model.ServiceType
import dorai.shared.model.Severity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Every check a reply must pass before it reaches a reviewer, and how outages and cancellation travel. */
class ReplyChecksTest {
    private val fixtures = FixtureData.load()
    private val functions = fixtures.register.functions
    private val resource = Resource(
        id = "/subscriptions/x/app",
        name = "app",
        type = "microsoft.web/sites",
        provider = "Azure",
        location = "westeurope",
        source = ResourceSource.Live,
        changeAction = ChangeAction.None,
        redundancy = Redundancy.Unknown,
    )
    private val finding = Finding(
        ruleId = "DORA-LOC-001",
        severity = Severity.High,
        title = "Critical function moves to a new country",
        resourceAddress = "azurerm_postgresql_flexible_server.payments",
        templates = listOf("B_02.02"),
        detail = "Moves to eastus.",
    )
    private val s18 = annexS18.id
    private val longText = "x".repeat(ReplyChecks.MAX_TEXT + 1)

    private fun classify(chat: ChatModel) = runTestAnswer { AiResourceClassifier(chat, StubSearch(annexS18, annexS17)).classify(resource) }

    private fun suggest(chat: ChatModel) = runTestAnswer { AiFunctionSuggester(chat, StubSearch(functionsColumn)).suggest(resource, functions) }

    private fun explain(chat: ChatModel) = runTestAnswer { AiFindingExplainer(chat, StubSearch(doraArticle)).explain(finding) }

    private fun draft(chat: ChatModel, findings: List<Finding> = listOf(finding)) =
        runTestAnswer { AiReportDrafter(chat, StubSearch(doraArticle)).draft(fixtures.register, findings) }

    private fun <T> runTestAnswer(block: suspend () -> AiAnswer<T>): AiAnswer<T> {
        var answer: AiAnswer<T>? = null
        runTest { answer = block() }
        return answer!!
    }

    /** The same bad reply twice: the retry does not rescue it. */
    private fun twice(reply: String) = ScriptedChat(reply, reply)

    private fun assertRejected(answer: AiAnswer<*>) = assertEquals(GroundedAnswer.INVALID_REPLY, assertIs<AiAnswer.Failed>(answer).reason)

    private fun classification(type: String = "S18", reason: String = "PaaS.", confidence: String = "0.8", ids: String = "\"$s18\"") =
        """{"serviceType":"$type","reason":"$reason","confidence":$confidence,"citationIds":[$ids]}"""

    private fun function(id: String = "F1", reason: String = "Tagged.", confidence: String = "0.7", ids: String = "\"${functionsColumn.id}\"") =
        """{"functionId":"$id","reason":"$reason","confidence":$confidence,"citationIds":[$ids]}"""

    private fun draftReply(summary: String = "One change.", text: String = "Matters.", ids: String = "\"${doraArticle.id}\"", vararg notes: Finding = arrayOf(finding)) =
        """{"summary":"$summary","findings":[""" +
            notes.joinToString(",") { """{"ruleId":"${it.ruleId}","resourceAddress":"${it.resourceAddress}","text":"$text"}""" } +
            """],"citationIds":[$ids]}"""

    // Shared flow

    @Test
    fun anUnexpectedModelErrorIsAFailureNotACrash() {
        assertEquals(GroundedAnswer.MODEL_UNAVAILABLE, assertIs<AiAnswer.Failed>(classify(ScriptedChat(IllegalStateException("bug")))).reason)
    }

    @Test
    fun twoUnparseableRepliesAreAFailure() {
        val chat = ScriptedChat("not json", "still not json")

        assertRejected(classify(chat))
        assertEquals(2, chat.requests.size)
    }

    @Test
    fun cancellationFromTheModelOrTheSearchIsNeverSwallowed() = runTest {
        assertFailsWith<CancellationException> {
            AiResourceClassifier(ScriptedChat(CancellationException("stop")), StubSearch(annexS18)).classify(resource)
        }
        assertFailsWith<CancellationException> {
            AiResourceClassifier(ScriptedChat(), StubSearch(failure = CancellationException("stop"))).classify(resource)
        }
    }

    @Test
    fun theModelSeesBoundedExcerptsWithTheirSource() {
        val long = citation("DORA/Art-28/3", article = "Article 28", excerpt = "y".repeat(5000))
        val chat = ScriptedChat("""{"explanation":"It matters.","citationIds":["${long.id}"]}""")
        runTest { AiFindingExplainer(chat, StubSearch(long)).explain(finding) }

        val message = chat.requests.single().userMessage()
        assertTrue("\"source\":\"DORA, Article 28\"" in message)
        assertTrue("y".repeat(800) in message)
        assertFalse("y".repeat(801) in message)
    }

    @Test
    fun textAndConfidenceLimits() {
        assertFalse(ReplyChecks.text(" "))
        assertFalse(ReplyChecks.text(longText))
        assertTrue(ReplyChecks.text("ok"))
        assertFalse(ReplyChecks.confidence(Double.NaN))
        assertFalse(ReplyChecks.confidence(-0.1))
        assertTrue(ReplyChecks.confidence(0.0))
        assertTrue(ReplyChecks.confidence(1.0))
    }

    @Test
    fun citationsComeBackInTheModelsOrderWithoutRepeats() {
        val answer = classify(ScriptedChat(classification(ids = "\"${annexS17.id}\",\"$s18\",\"${annexS17.id}\"")))

        assertEquals(listOf(annexS17, annexS18), assertIs<AiAnswer.Answer<ServiceType>>(answer).citations)
    }

    // Classifier

    @Test
    fun classifierAcceptsALowercaseTypeAndTrimsTheReason() {
        val answer = assertIs<AiAnswer.Answer<ServiceType>>(classify(ScriptedChat(classification(type = "s18", reason = "  PaaS.  "))))

        assertEquals(ServiceType.S18, answer.value)
        assertEquals("PaaS.", answer.reason)
        assertEquals(0.8, answer.confidence)
    }

    @Test
    fun classifierRejectsABlankOrOverlongReason() {
        assertRejected(classify(twice(classification(reason = " "))))
        assertRejected(classify(twice(classification(reason = longText))))
    }

    @Test
    fun classifierRejectsUnknownThatCitesAForeignPassage() {
        assertRejected(classify(twice(classification(type = "Unknown", ids = "\"DORA/Art-99/1\""))))
    }

    @Test
    fun classifierUnknownWithEvidenceIsStillInsufficientEvidence() {
        assertIs<AiAnswer.InsufficientEvidence>(classify(ScriptedChat(classification(type = "unknown", reason = "Unclear."))))
    }

    // Function suggester

    @Test
    fun suggesterWithoutFunctionsNeverAsksTheModel() {
        val chat = ScriptedChat()
        val answer = runTestAnswer { AiFunctionSuggester(chat, StubSearch(functionsColumn)).suggest(resource, emptyList()) }

        assertIs<AiAnswer.InsufficientEvidence>(answer)
        assertTrue(chat.requests.isEmpty())
    }

    @Test
    fun suggesterRejectsAKnownFunctionWithoutCitation() {
        assertRejected(suggest(twice(function(ids = ""))))
    }

    @Test
    fun suggesterRejectsBadConfidenceBlankReasonOrForeignCitation() {
        assertRejected(suggest(twice(function(confidence = "2.0"))))
        assertRejected(suggest(twice(function(reason = " "))))
        assertRejected(suggest(twice(function(ids = "\"DORA/Art-99/1\""))))
    }

    @Test
    fun suggesterTreatsUnknownInAnyCaseAsInsufficientEvidence() {
        assertIs<AiAnswer.InsufficientEvidence>(suggest(ScriptedChat(function(id = "UNKNOWN", ids = ""))))
    }

    @Test
    fun suggesterKeepsTheModelsConfidence() {
        assertEquals(0.7, assertIs<AiAnswer.Answer<String>>(suggest(ScriptedChat(function()))).confidence)
    }

    // Explainer

    @Test
    fun explainerRejectsABlankExplanationOrAForeignCitation() {
        assertRejected(explain(twice("""{"explanation":" ","citationIds":["${doraArticle.id}"]}""")))
        assertRejected(explain(twice("""{"explanation":"It matters.","citationIds":["DORA/Art-99/1"]}""")))
    }

    @Test
    fun explainerTrimsTheExplanation() {
        val answer = explain(ScriptedChat("""{"explanation":"  It matters.  ","citationIds":["${doraArticle.id}"]}"""))

        assertEquals("It matters.", assertIs<AiAnswer.Answer<String>>(answer).value)
    }

    // Report drafter

    @Test
    fun drafterRejectsAnOverlongSummaryOrABlankNote() {
        assertRejected(draft(twice(draftReply(summary = "s".repeat(2001)))))
        assertRejected(draft(twice(draftReply(text = " "))))
    }

    @Test
    fun drafterRejectsAMissingOrForeignCitation() {
        assertRejected(draft(twice(draftReply(ids = ""))))
        assertRejected(draft(twice(draftReply(ids = "\"DORA/Art-99/1\""))))
    }

    @Test
    fun drafterTrimsSummaryAndNotes() {
        val answer = assertIs<AiAnswer.Answer<DraftText>>(draft(ScriptedChat(draftReply(summary = " One change. ", text = " Matters. "))))

        assertEquals("One change.", answer.value.summary)
        assertEquals("Matters.", answer.value.findings.single().text)
    }

    @Test
    fun drafterSendsAtMostTheFindingLimitAndDraftsOneNoteEach() {
        val many = (1..AiReportDrafter.MAX_FINDINGS + 5).map { finding.copy(resourceAddress = "res.$it") }
        val answer = assertIs<AiAnswer.Answer<DraftText>>(draft(FakeChatModel(), many))

        assertEquals(AiReportDrafter.MAX_FINDINGS, answer.value.findings.size)
        assertEquals(many.take(AiReportDrafter.MAX_FINDINGS).map { it.resourceAddress }, answer.value.findings.map { it.resourceAddress })
    }

    @Test
    fun theSameRuleAndResourceTwiceGetsOneNote() {
        val answer = draft(ScriptedChat(draftReply()), listOf(finding, finding.copy(detail = "Second message.")))

        assertEquals(1, assertIs<AiAnswer.Answer<DraftText>>(answer).value.findings.size)
    }

    @Test
    fun drafterSearchesForTheFindingsTemplates() {
        val search = StubSearch(doraArticle)
        runTest { AiReportDrafter(ScriptedChat(draftReply()), search).draft(fixtures.register, listOf(finding)) }

        assertEquals("register of information B_02.02", search.queries.single())
    }
}
