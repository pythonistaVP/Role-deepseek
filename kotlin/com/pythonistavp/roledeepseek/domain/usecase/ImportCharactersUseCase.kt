package com.pythonistavp.roledeepseek.domain.usecase

import com.pythonistavp.roledeepseek.data.files.AvatarInfo
import com.pythonistavp.roledeepseek.data.files.AvatarStore
import com.pythonistavp.roledeepseek.data.model.BackupFile
import com.pythonistavp.roledeepseek.data.model.Character
import com.pythonistavp.roledeepseek.data.model.CharacterAiCard
import com.pythonistavp.roledeepseek.data.model.CharacterFile
import com.pythonistavp.roledeepseek.data.model.DraftCard
import com.pythonistavp.roledeepseek.data.model.Folder
import com.pythonistavp.roledeepseek.data.model.FolderIcon
import com.pythonistavp.roledeepseek.data.model.ImportFailure
import com.pythonistavp.roledeepseek.data.model.ImportReport
import com.pythonistavp.roledeepseek.data.model.TavernCardV1
import com.pythonistavp.roledeepseek.data.model.TavernCardV2
import com.pythonistavp.roledeepseek.data.model.UsageAction
import com.pythonistavp.roledeepseek.data.model.toDomain
import com.pythonistavp.roledeepseek.data.repository.CharacterRepository
import com.pythonistavp.roledeepseek.data.repository.FolderRepository
import com.pythonistavp.roledeepseek.data.repository.UsageRepository
import com.pythonistavp.roledeepseek.util.Ids
import com.pythonistavp.roledeepseek.util.QrUtil
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Импорт карточек из JSON. Понимает три формата и определяет их сам:
 *  • Role DeepSeek (role_deepseek_v1);
 *  • Tavern AI / SillyTavern (chara_card_v2 и «плоскую» v1);
 *  • Character.AI (экспорт .json).
 * Дополнительно принимает полезную нагрузку из QR-кода приложения.
 */
class ImportCharactersUseCase @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val folderRepository: FolderRepository,
    private val usageRepository: UsageRepository,
    private val avatarStore: AvatarStore,
    private val json: Json,
) {

    sealed interface ParseResult {
        data class Cards(val cards: List<DraftCard>) : ParseResult
        data class Failure(val reason: ImportFailure) : ParseResult
    }

    /** Разбирает текст, сохраняет карточки и возвращает отчёт. */
    suspend operator fun invoke(payload: String, targetFolderId: String? = null): ImportReport {
        val cards = when (val parsed = parse(payload)) {
            is ParseResult.Failure -> return ImportReport(failure = parsed.reason)
            is ParseResult.Cards -> parsed.cards
        }

        val saved = mutableListOf<Character>()
        val folderCache = mutableMapOf<String, String>()
        var skipped = 0
        var foldersCreated = 0
        val now = System.currentTimeMillis()

        for (card in cards) {
            val draft = card.draft
            if (draft.name.isBlank() && draft.prompt.isBlank()) {
                skipped++
                continue
            }
            val id = if (characterRepository.getById(draft.id) == null) draft.id else Ids.newId()
            var character = draft.copy(id = id, createdAt = now, updatedAt = now)

            val folderId = resolveFolder(card.folderName, targetFolderId, folderCache) { foldersCreated++ }
            character = character.copy(folderId = folderId)

            importAvatar(card, id)?.let { avatar ->
                character = character.copy(avatarPath = avatar.path, avatarBlurHash = avatar.blurHash)
            }

            characterRepository.upsert(character)
            usageRepository.record(character.id, UsageAction.IMPORT)
            saved += character
        }

        if (saved.isEmpty()) {
            return ImportReport(imported = 0, skipped = skipped, failure = ImportFailure.EMPTY)
        }
        return ImportReport(
            imported = saved.size,
            skipped = skipped,
            foldersCreated = foldersCreated,
            characters = saved,
        )
    }

    /** Разбор без сохранения — используется для превью и тестов. */
    fun parse(payload: String): ParseResult {
        val raw = payload.trim()
        val text = if (QrUtil.isQrPayload(raw)) QrUtil.decompress(raw) ?: raw else raw
        if (text.isBlank()) return ParseResult.Failure(ImportFailure.EMPTY)

        val root = runCatching { json.parseToJsonElement(text) }
            .getOrElse { return ParseResult.Failure(ImportFailure.NOT_JSON) }

        val cards = mutableListOf<DraftCard>()
        collect(root, cards)
        return if (cards.isEmpty()) {
            ParseResult.Failure(ImportFailure.UNKNOWN_FORMAT)
        } else {
            ParseResult.Cards(cards)
        }
    }

    private fun collect(element: JsonElement, out: MutableList<DraftCard>) {
        when (element) {
            is JsonArray -> element.forEach { collect(it, out) }
            is JsonObject -> {
                val card = classify(element)
                if (card != null) {
                    out += card
                } else {
                    // Внутри может лежать резервная копия — читаем её список персонажей.
                    (element["characters"] as? JsonArray)?.forEach { collect(it, out) }
                }
            }
            else -> Unit
        }
    }

    private fun classify(obj: JsonObject): DraftCard? {
        val spec = obj.str("spec")
        val data = obj["data"] as? JsonObject
        return when {
            spec == CharacterFile.SPEC -> nativeCard(obj)
            spec == BackupFile.SPEC -> null
            spec != null && spec.startsWith("chara_card") -> tavernV2(obj)
            data != null && (data["first_mes"] != null || data["description"] != null) -> tavernV2(obj)
            obj["first_mes"] != null -> tavernV1(obj)
            obj["greeting"] != null || obj["example_dialogs"] != null ||
                obj["example_dialogue"] != null -> characterAi(obj)
            obj["name"] != null || obj["title"] != null -> generic(obj)
            else -> null
        }
    }

    private fun nativeCard(obj: JsonObject): DraftCard? {
        val file = runCatching { json.decodeFromJsonElement(CharacterFile.serializer(), obj) }
            .getOrNull() ?: return null
        if (file.name.isBlank() && file.prompt.isBlank()) return null
        return DraftCard(
            draft = file.toDomain(Ids.newId()),
            folderName = file.folder,
            avatarBase64 = file.avatarBase64,
        )
    }

    private fun tavernV2(obj: JsonObject): DraftCard? {
        val card = runCatching { json.decodeFromJsonElement(TavernCardV2.serializer(), obj) }
            .getOrNull() ?: return null
        val data = card.data ?: return null
        return composeCard(
            name = data.name,
            parts = listOf(
                data.description,
                data.personality,
                data.scenario,
                data.mesExample,
                data.systemPrompt,
                data.postHistoryInstructions,
            ),
            greeting = data.firstMes ?: data.alternateGreetings?.firstOrNull(),
            tags = data.tags,
            note = data.creatorNotes,
            avatarUrl = data.avatar,
        )
    }

    private fun tavernV1(obj: JsonObject): DraftCard? {
        val card = runCatching { json.decodeFromJsonElement(TavernCardV1.serializer(), obj) }
            .getOrNull() ?: return null
        return composeCard(
            name = card.name,
            parts = listOf(card.description, card.personality, card.scenario, card.mesExample),
            greeting = card.firstMes,
            tags = card.tags,
            note = null,
            avatarUrl = card.avatar,
        )
    }

    private fun characterAi(obj: JsonObject): DraftCard? {
        val card = runCatching { json.decodeFromJsonElement(CharacterAiCard.serializer(), obj) }
            .getOrNull() ?: return null
        return composeCard(
            name = card.name ?: card.title,
            parts = listOf(
                card.description,
                card.personality,
                card.scenario,
                card.exampleDialogs ?: card.exampleDialogue,
            ),
            greeting = card.greeting,
            tags = card.tags,
            note = card.creator,
            avatarUrl = card.avatar,
        )
    }

    private fun generic(obj: JsonObject): DraftCard? = composeCard(
        name = obj.str("name") ?: obj.str("title"),
        parts = listOf(
            obj.str("prompt"),
            obj.str("description"),
            obj.str("personality"),
            obj.str("scenario"),
            obj.str("example_dialogs"),
        ),
        greeting = obj.str("greeting") ?: obj.str("first_mes"),
        tags = (obj["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content },
        note = obj.str("note") ?: obj.str("creator_notes"),
        avatarUrl = obj.str("avatar"),
    )

    private fun composeCard(
        name: String?,
        parts: List<String?>,
        greeting: String?,
        tags: List<String>?,
        note: String?,
        avatarUrl: String?,
    ): DraftCard? {
        val cleanName = name?.trim().orEmpty()
        val prompt = parts.mapNotNull { it?.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
        if (cleanName.isEmpty() && prompt.isEmpty()) return null
        return DraftCard(
            draft = Character(
                id = Ids.newId(),
                name = cleanName,
                prompt = prompt,
                greeting = greeting?.trim()?.takeIf { it.isNotEmpty() },
                tags = (tags ?: emptyList())
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct(),
                note = note?.trim()?.takeIf { it.isNotEmpty() },
            ),
            avatarUrl = avatarUrl,
        )
    }

    private suspend fun importAvatar(card: DraftCard, id: String): AvatarInfo? {
        card.avatarBase64?.takeIf { it.isNotBlank() }?.let { return avatarStore.importFromBase64(it, id) }
        val url = card.avatarUrl?.trim().orEmpty()
        return when {
            url.startsWith("data:image") -> avatarStore.importFromBase64(url, id)
            url.startsWith("http://") || url.startsWith("https://") -> avatarStore.importFromUrl(url, id)
            else -> null
        }
    }

    private suspend fun resolveFolder(
        name: String?,
        fallback: String?,
        cache: MutableMap<String, String>,
        onCreated: () -> Unit,
    ): String? {
        val clean = name?.trim().orEmpty()
        if (clean.isEmpty()) return fallback
        cache[clean.lowercase()]?.let { return it }
        folderRepository.getAll().firstOrNull { it.name.equals(clean, true) }?.let {
            cache[clean.lowercase()] = it.id
            return it.id
        }
        val created = folderRepository.create(clean, Folder.DEFAULT_COLOR, FolderIcon.STAR.key)
        if (created == null) return fallback
        onCreated()
        cache[clean.lowercase()] = created.id
        return created.id
    }

    private fun JsonObject.str(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
