package dorai.server.ai.services

import dorai.server.ai.chat.AiTask

/** Task instructions from `ai/prompts/<task id>.txt`; `{{serviceTypes}}` becomes the Annex III list. */
object Prompts {
    private val cache = AiTask.entries.associateWith(::load)

    fun instructions(task: AiTask): String = cache.getValue(task)

    private fun load(task: AiTask): String {
        val path = "ai/prompts/${task.id}.txt"
        val text = Prompts::class.java.classLoader.getResource(path)?.readText()
            ?: error("Prompt $path is missing from the server resources")
        return text.trim().replace("{{serviceTypes}}", ServiceTypes.promptList())
    }
}
