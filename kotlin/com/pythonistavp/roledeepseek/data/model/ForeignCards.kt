package com.pythonistavp.roledeepseek.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** SillyTavern / Tavern AI, спецификация chara_card_v2. */
@Serializable
data class TavernCardV2(
    val spec: String? = null,
    @SerialName("spec_version") val specVersion: String? = null,
    val data: TavernData? = null,
)

@Serializable
data class TavernData(
    val name: String? = null,
    val description: String? = null,
    val personality: String? = null,
    val scenario: String? = null,
    @SerialName("first_mes") val firstMes: String? = null,
    @SerialName("mes_example") val mesExample: String? = null,
    @SerialName("creator_notes") val creatorNotes: String? = null,
    @SerialName("system_prompt") val systemPrompt: String? = null,
    @SerialName("post_history_instructions") val postHistoryInstructions: String? = null,
    @SerialName("alternate_greetings") val alternateGreetings: List<String>? = null,
    @SerialName("character_version") val characterVersion: String? = null,
    val creator: String? = null,
    val tags: List<String>? = null,
    val avatar: String? = null,
)

/** SillyTavern / Tavern AI, «плоская» карточка v1. */
@Serializable
data class TavernCardV1(
    val name: String? = null,
    val description: String? = null,
    val personality: String? = null,
    val scenario: String? = null,
    @SerialName("first_mes") val firstMes: String? = null,
    @SerialName("mes_example") val mesExample: String? = null,
    val avatar: String? = null,
    val tags: List<String>? = null,
)

/** Экспорт Character.AI. */
@Serializable
data class CharacterAiCard(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val greeting: String? = null,
    val avatar: String? = null,
    val personality: String? = null,
    val scenario: String? = null,
    @SerialName("example_dialogs") val exampleDialogs: String? = null,
    @SerialName("example_dialogue") val exampleDialogue: String? = null,
    val tags: List<String>? = null,
    val creator: String? = null,
)
