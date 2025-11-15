package org.ntqqrev.cecilia.commands

import io.ktor.client.HttpClient
import org.ntqqrev.acidify.Bot
import java.time.LocalDateTime

data class CommandParameter(
    val name: String,
    val description: String = "",
    val placeholder: String = "",
    val required: Boolean = true,
    val suggestionsProvider: CommandCompletionContext.(String) -> List<String> = { emptyList() },
)

data class Command(
    val id: String,
    val title: String,
    val description: String = "",
    val parameters: List<CommandParameter> = emptyList(),
    val execute: suspend CommandExecutionContext.() -> Unit
)

class CommandCompletionContext(
    val bot: Bot,
    val httpClient: HttpClient,
)

class CommandExecutionContext(
    val args: Map<String, String>,
    val bot: Bot,
    val httpClient: HttpClient,
)

object CommandCatalog {
    fun demoCommands(): List<Command> {
        val broadcastTargets = listOf("全体成员", "测试小队", "仅自己", "外部通知渠道")
        val cannedMessages = listOf(
            "你好，Cecilia!",
            "命令已经执行。",
            "这是一条示例消息。",
            "敬请期待更多功能。"
        )
        val owners = listOf("Alice", "Bob", "Carol", "David")
        val priorities = listOf("P0", "P1", "P2", "P3")

        return listOf(
            Command(
                id = "welcome",
                title = "打印欢迎语",
                description = "演示没有参数的指令。",
                execute = {
                    println("[Command] 欢迎使用 Command Palette! 当前时间: ${LocalDateTime.now()}")
                }
            ),
            Command(
                id = "broadcast",
                title = "公告广播",
                description = "向不同目标 println 一条公告内容。",
                parameters = listOf(
                    CommandParameter(
                        name = "目标",
                        placeholder = "选择广播对象",
                        suggestionsProvider = { query ->
                            broadcastTargets.filter { it.contains(query, ignoreCase = true) }
                        }
                    ),
                    CommandParameter(
                        name = "内容",
                        placeholder = "输入要广播的内容",
                        suggestionsProvider = { query ->
                            cannedMessages.filter { it.contains(query, ignoreCase = true) }
                        }
                    )
                ),
                execute = {
                    val target = args["目标"].orEmpty()
                    val message = args["内容"].orEmpty()
                    println("[Command] 向 $target 广播：$message")
                }
            ),
            Command(
                id = "plan-drill",
                title = "安排演练任务",
                description = "带多个参数的示例指令。",
                parameters = listOf(
                    CommandParameter(
                        name = "负责人",
                        placeholder = "选择负责人",
                        suggestionsProvider = { query ->
                            owners.filter { it.contains(query, ignoreCase = true) }
                        }
                    ),
                    CommandParameter(
                        name = "优先级",
                        placeholder = "P0 / P1 / P2 / P3",
                        suggestionsProvider = { query ->
                            priorities.filter { it.contains(query, ignoreCase = true) }
                        }
                    ),
                    CommandParameter(
                        name = "备注",
                        required = false,
                        placeholder = "可选备注",
                        suggestionsProvider = { emptyList() }
                    )
                ),
                execute = {
                    val owner = args["负责人"].orEmpty()
                    val priority = args["优先级"].orEmpty()
                    val note = args["备注"].orEmpty().ifBlank { "(无)" }
                    println("[Command] 创建演练任务 -> 负责人: $owner, 优先级: $priority, 备注: $note")
                }
            )
        )
    }
}
