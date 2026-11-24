package io.github.rrezartprebreza.evalbaseline.example

import io.github.rrezartprebreza.evalbaseline.junit.EvalBaseline
import io.github.rrezartprebreza.evalbaseline.junit.EvalTest
import io.github.rrezartprebreza.evalbaseline.springai.EvalMode
import io.github.rrezartprebreza.evalbaseline.springai.RecordingChatModel
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Path

/**
 * The refund check from [SupportBotEvalTest], written in Kotlin. It replays the Java example's
 * recordings, so it needs no model and no new cassettes.
 */
@EvalBaseline(value = "src/test/resources/kotlin-baseline.tsv", maxLatencyFactor = 0.0)
class SupportBotKotlinEvalTest {

    private val bot = SupportBot(model)

    @EvalTest(runs = 10)
    fun refundAnswerMentionsThe30DayLimit() {
        assertTrue(bot.answer("Can I get a refund two weeks after delivery?").contains("30 days"))
    }

    companion object {
        private val model = RecordingChatModel(null, Path.of("src/test/resources/cassettes"), EvalMode.REPLAY)
    }
}
