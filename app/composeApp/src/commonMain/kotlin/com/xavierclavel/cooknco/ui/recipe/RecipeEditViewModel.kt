package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.data.RecipeScan
import com.xavierclavel.cooknco.data.ScannedRecipe
import com.xavierclavel.cooknco.data.StepDurations
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.AppUnitSystem
import com.xavierclavel.cooknco.data.AppUnits
import com.xavierclavel.cooknco.data.UnitRepository
import com.xavierclavel.cooknco.di.AppGraph
import com.xavierclavel.cooknco.network.dto.RecipeStepIngredientInfo
import com.xavierclavel.cooknco.network.dto.RecipeStepInfo
import com.xavierclavel.cooknco.network.dto.CooklangImportDto
import com.xavierclavel.cooknco.network.ApiException
import com.xavierclavel.cooknco.network.dto.IngredientSummary
import com.xavierclavel.cooknco.network.dto.RecipeIngredientSaveDto
import com.xavierclavel.cooknco.network.dto.RecipeSaveDto
import com.xavierclavel.cooknco.network.dto.UnitInfo
import com.xavierclavel.cooknco.network.dto.displayName
import com.xavierclavel.cooknco.platform.PickedImage
import com.xavierclavel.cooknco.platform.ScanResult
import com.xavierclavel.cooknco.ui.i18n.Strings
import com.xavierclavel.cooknco.ui.i18n.stringsFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A single row of the ingredient list: a referenced ingredient when [ingredientId] is set, a free-text one when [customName] is. */
data class EditIngredient(
    val ingredientId: Long? = null,
    val customName: String? = null,
    val ingredientName: String = "",
    val type: String = "",
    val query: String = "",
    // A fresh row carries no amount yet, and the server rejects a unit without one.
    val unit: String = "NONE",
    val amount: Float? = null,
    val complement: String = "",
    val allowedTypes: List<String> = emptyList(),
    val searchResults: List<IngredientSummary> = emptyList(),
    val showDropdown: Boolean = false,
)

/**
 * One step being written, and the timer the cook may want on it.
 *
 * [durationSeconds] is null for a step with no timer, which is most of them.
 *
 * [durationTouched] is the whole of the auto-detection rule. A step's wording usually says
 * how long it takes — "laisser reposer 30 mn" — so the duration is read out of the text as
 * it is typed ([StepDurations]) and offered without being asked for. The moment the cook
 * sets or clears it by hand, that stops: an author who typed "simmer for 20 min" and then
 * set the timer to 25 is not to be argued with on the next keystroke.
 *
 * It is not persisted — a step loaded for editing arrives with whatever was saved, and is
 * touched by definition.
 */
/**
 * Something a cook can attach to a step besides its words.
 *
 * One case so far. It is an enum rather than a boolean on [StepItem] because it is not going
 * to stay one case, and because everything that has to be generic over "what can a step
 * carry" - the menu behind the add button, what it offers, what it hides once it is added -
 * is then generic over `entries` instead of over a list somebody has to remember to extend.
 *
 * Adding one is: a case here, a field on [StepItem] for whatever it holds, a row in the step
 * card that shows it, and a branch in [RecipeEditViewModel.attachToStep].
 */
enum class StepAttachment {
    /** How long the step takes, which cook mode counts down. Held in [StepItem.durationSeconds]. */
    TIMER,

    /** Which of the recipe's ingredients the step uses. Held in [StepItem.ingredients]. */
    INGREDIENTS,

    /**
     * A picture of what the step should look like. Held in [StepItem.pendingImage] until it
     * is saved, and in [StepItem.imageVersion] once it is.
     *
     * The only attachment that is not saved with the recipe: a picture is posted to the
     * step's own row, which does not exist until the save that creates it has answered.
     */
    PHOTO,
}

/**
 * One of the recipe's ingredients, used by a step being written.
 *
 * [index] is the ingredient's position in the recipe, which is how the server matches the two
 * lists up — see `RecipeStepIngredientInfo`. It moves when the ingredients are reordered, so
 * it is resolved against the list as it stands rather than remembered.
 *
 * [amount] is text while it is being typed, and blank is meaningful: it says "the rest of it",
 * which is the whole line when no other step names an amount. The server works out what that
 * comes to, so nothing here has to do the subtraction.
 */
data class StepIngredientDraft(
    val index: Int,
    val amount: String = "",
)

data class StepItem(
    val id: String,
    val text: String,
    /**
     * The row this step is saved as, or null for one that has never been saved.
     *
     * Distinct from [id], which is made up here and only ever names a row in this list: it
     * is what keeps a card from being rebuilt as the list is edited, and means nothing to
     * the server. [serverId] is what a picture is posted against.
     */
    val serverId: Long? = null,
    /**
     * What this step carries beyond its text, and the one source of truth for whether the
     * card shows it.
     *
     * A timer is *attached* the moment it is added, before a number has been typed into it,
     * which is why this is a set of its own rather than being inferred from
     * [durationSeconds] being non-null. An attached timer left empty is simply no timer by
     * the time it is saved.
     */
    val attachments: Set<StepAttachment> = emptySet(),
    val durationSeconds: Int? = null,
    /** Which of the recipe's ingredients this step uses, and how much of each. */
    val ingredients: List<StepIngredientDraft> = emptyList(),
    /**
     * A picture chosen but not yet sent, or null when there is none waiting.
     *
     * It cannot be sent as it is picked: a step that has never been saved has nothing to
     * post it against. See [RecipeEditViewModel.syncStepImages].
     */
    val pendingImage: PickedImage? = null,
    /** Which version of the saved picture to show. Zero while the step has none. */
    val imageVersion: Long = 0,
    /**
     * Whether the cook has taken the timer over from the text.
     *
     * A step's wording usually says how long it takes - "laisser reposer 30 mn" - so a timer
     * is read out of the text as it is typed and attached without being asked for. The moment
     * the cook edits or removes it by hand, that stops: an author who typed "simmer for 20
     * min" and then set 25, or took the timer off altogether, is not to be argued with on the
     * next keystroke.
     *
     * Not persisted - a step loaded for editing arrives with whatever was saved, and is
     * touched by definition.
     */
    val durationTouched: Boolean = false,
)

data class RecipeEditUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val title: String = "",
    val description: String = "",
    val dishClass: String = "MAIN_DISH",
    val yield: String = "",
    val prepTime: String = "",
    val cookTime: String = "",
    val cookTemp: String = "",
    val ingredients: List<EditIngredient> = emptyList(),
    val units: List<UnitInfo> = emptyList(),
    val steps: List<StepItem> = emptyList(),
    val tips: String = "",
    val pendingImage: PickedImage? = null,
    val error: String? = null,
    val recipeId: Long? = null,
    val recipeVersion: Long? = null,
    /**
     * A scanned page is being turned into a recipe.
     *
     * Its own flag rather than [isLoading], which greys the whole editor out: the fields are
     * still the cook's to type in while the catalogue is being asked about the ingredients
     * that have already been filled in.
     */
    val isScanning: Boolean = false,
    val isImporting: Boolean = false,
    /** What the last scan came to, shown once and dismissed. */
    val scanMessage: String? = null,
    /** What the last Cooklang import did, said once. See [RecipeEditViewModel.importCooklang]. */
    val importMessage: String? = null,
)

class RecipeEditViewModel(
    private val repo: RecipeRepository,
    private val unitRepo: UnitRepository,
    private val recipeId: Long?,
    private val userId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RecipeEditUiState(recipeId = recipeId, units = UnitRepository.DEFAULT_UNITS),
    )
    val uiState: StateFlow<RecipeEditUiState> = _uiState.asStateFlow()

    private val searchJobs = mutableMapOf<Int, Job>()
    private var nextStepId = 0
    private fun newStepId() = "s${nextStepId++}"

    init {
        loadUnits()
        if (recipeId != null) {
            loadRecipe(recipeId)
        }
    }

    private fun loadUnits() {
        viewModelScope.launch {
            val units = unitRepo.getUnits()
            _uiState.update { it.copy(units = units) }
        }
    }

    private fun loadRecipe(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.getRecipe(id)
                .onSuccess { recipe ->
                    val editIngredients = recipe.ingredients.map { ing ->
                        EditIngredient(
                            ingredientId = ing.id,
                            customName = ing.name.takeIf { ing.id == null },
                            ingredientName = ing.name,
                            type = ing.type ?: "",
                            query = ing.name,
                            unit = ing.unit,
                            amount = ing.amount,
                            complement = ing.complement ?: "",
                            allowedTypes = ing.allowedTypes,
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = recipe.title,
                            description = recipe.description,
                            dishClass = recipe.dishClass,
                            yield = recipe.yield?.toString() ?: "",
                            prepTime = recipe.preparationTime?.toString() ?: "",
                            cookTime = recipe.cookingTime?.toString() ?: "",
                            cookTemp = recipe.cookingTemperature?.toString() ?: "",
                            ingredients = editIngredients,
                            steps = recipe.steps.map { step ->
                                // Recipes written before steps had a duration have none saved.
                                // Reading it out of the wording here is what gives them one, on
                                // the screen where it can be seen and corrected before saving.
                                val duration = step.durationSeconds
                                    ?: StepDurations.parseSeconds(step.text)
                                StepItem(
                                    id = newStepId(),
                                    text = step.text,
                                    serverId = step.id,
                                    imageVersion = step.imageVersion,
                                    attachments = setOfNotNull(
                                        StepAttachment.TIMER.takeIf { duration != null },
                                        StepAttachment.INGREDIENTS.takeIf { step.ingredients.isNotEmpty() },
                                        StepAttachment.PHOTO.takeIf { step.imageVersion > 0 },
                                    ),
                                    ingredients = step.ingredients.map { used ->
                                        StepIngredientDraft(
                                            index = used.index,
                                            // What the author said, not what it works out to.
                                            // Prefilling from the worked-out value would write
                                            // it back on the next save and freeze the blank.
                                            amount = used.amount?.let { formatAmount(it) }.orEmpty(),
                                        )
                                    },
                                    durationSeconds = duration,
                                    // Anything that came off the server is a decision already
                                    // taken; only what is typed from here on is detected.
                                    durationTouched = true,
                                )
                            },
                            tips = recipe.tips,
                            recipeId = recipe.id,
                            recipeVersion = recipe.version,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    // ── Scanning a page ───────────────────────────────────────────────────────

    /** What a finished scan comes to: a recipe read off the page, or a sentence about why not. */
    fun onScanned(result: ScanResult) = when (result) {
        is ScanResult.Failed -> reportScanFailure()
        is ScanResult.Read -> prefillFromScan(RecipeScan.parse(result.lines))
    }

    /**
     * Fills the editor in from a scanned page, then asks the catalogue about what it read.
     *
     * **Nothing already written is overwritten.** A field the cook has filled in keeps what
     * they put there, and ingredients and steps are appended rather than replaced — which is
     * what makes scanning a second page of the same recipe work, and what stops a mis-aimed
     * scan destroying half an hour of typing. The one thing a scan can do to existing content
     * is add to it, and that is undone by deleting a row.
     *
     * The ingredients land as free text first and are matched to the catalogue afterwards, in
     * the background: the match needs a request each, and a cook watching a page turn into a
     * recipe should not wait on the network to see it.
     */
    fun prefillFromScan(scanned: ScannedRecipe) {
        if (scanned.isEmpty) {
            _uiState.update { it.copy(scanMessage = copy().scanFoundNothing) }
            return
        }

        val added = scanned.ingredients.map { ingredient ->
            EditIngredient(
                customName = customName(ingredient.name),
                ingredientName = ingredient.name,
                query = ingredient.name,
                unit = ingredient.unit,
                amount = ingredient.amount,
                complement = ingredient.complement.orEmpty(),
            )
        }

        _uiState.update { state ->
            state.copy(
                title = state.title.ifBlank { scanned.title.orEmpty() },
                description = state.description.ifBlank { scanned.description.orEmpty() },
                yield = state.yield.ifBlank { scanned.yield?.toString().orEmpty() },
                prepTime = state.prepTime.ifBlank { scanned.prepMinutes?.toString().orEmpty() },
                cookTime = state.cookTime.ifBlank { scanned.cookMinutes?.toString().orEmpty() },
                cookTemp = state.cookTemp.ifBlank { scanned.temperatureCelsius?.toString().orEmpty() },
                tips = state.tips.ifBlank { scanned.tips.orEmpty() },
                ingredients = state.ingredients + added,
                steps = state.steps + scanned.steps.map { text ->
                    // The wording is read for a timer exactly as [updateStep] reads it while
                    // it is typed: a step off a page has had no more said about it than one
                    // being written, so it is not "touched" and the detection keeps up with
                    // any edit the cook makes to it.
                    val detected = StepDurations.parseSeconds(text)
                    StepItem(
                        id = newStepId(),
                        text = text,
                        durationSeconds = detected,
                        attachments = setOfNotNull(StepAttachment.TIMER.takeIf { detected != null }),
                    )
                },
                isScanning = added.isNotEmpty(),
                scanMessage = null,
                error = null,
            )
        }

        if (added.isNotEmpty()) matchScannedIngredients(from = _uiState.value.ingredients.size - added.size)
    }

    /**
     * Fills the editor in from a Cooklang file the cook picked.
     *
     * **Nothing already written is overwritten**, exactly as a scan does not: a field that
     * has been filled in keeps what was typed, and ingredients and steps are appended. That
     * is what makes importing a second file work, and what makes picking the wrong one cost
     * a few deleted rows rather than everything typed so far.
     *
     * Unlike a scan, the ingredients arrive already matched: the backend has the catalogue
     * and does the lookup while it parses, so there is no second pass here and no per-row
     * request. A row it could not place comes through as free text, which is a working
     * ingredient rather than a wrong one — [CooklangImportDto.unmatchedIngredients] is how
     * many, and it is worth a sentence rather than a warning.
     */
    fun importCooklang(source: String) {
        if (_uiState.value.isImporting) return
        // Set before the coroutine, not inside it: the card is tappable until this flips, so
        // raising it a dispatch later leaves a window in which a second tap starts a second
        // import of the same file.
        _uiState.update { it.copy(isImporting = true, importMessage = null, error = null) }
        viewModelScope.launch {
            repo.importCooklang(source)
                .onSuccess { imported -> prefillFromImport(imported) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isImporting = false, importMessage = importFailure(error))
                    }
                }
        }
    }

    private fun prefillFromImport(imported: CooklangImportDto) {
        val s = copy()
        val parsed = imported.recipe
        // Positions in the *imported* list, so they shift by whatever the form already held.
        val offset = _uiState.value.ingredients.size

        val added = parsed.ingredients.mapIndexed { index, item ->
            val name = imported.ingredientNames.getOrNull(index) ?: item.customName.orEmpty()
            EditIngredient(
                ingredientId = item.id,
                // Exactly one of the two, which is what the save path insists on.
                customName = if (item.id == null) customName(name) else null,
                ingredientName = name,
                query = name,
                unit = item.unit,
                amount = item.amount,
                complement = item.complement.orEmpty(),
            )
        }

        _uiState.update { state ->
            state.copy(
                title = state.title.ifBlank { parsed.title },
                description = state.description.ifBlank { parsed.description },
                dishClass = state.dishClass,
                yield = state.yield.ifBlank { parsed.yield?.toString().orEmpty() },
                prepTime = state.prepTime.ifBlank { parsed.preparationTime?.toString().orEmpty() },
                cookTime = state.cookTime.ifBlank { parsed.cookingTime?.toString().orEmpty() },
                cookTemp = state.cookTemp.ifBlank { parsed.cookingTemperature?.toString().orEmpty() },
                tips = state.tips.ifBlank { parsed.tips },
                ingredients = state.ingredients + added,
                steps = state.steps + parsed.steps.map { step ->
                    StepItem(
                        id = newStepId(),
                        text = step.text,
                        durationSeconds = step.durationSeconds,
                        // The file said how long, so the timer is the file's rather than
                        // something read back out of the wording - which is why this does
                        // not go through StepDurations as a scanned step does.
                        durationTouched = step.durationSeconds != null,
                        attachments = setOfNotNull(StepAttachment.TIMER.takeIf { step.durationSeconds != null }),
                    )
                },
                isImporting = false,
                importMessage = listOfNotNull(
                    s.importCooklangUnmatched(imported.unmatchedIngredients)
                        .takeIf { imported.unmatchedIngredients > 0 },
                    s.importCooklangSplit.takeIf { imported.stepsWereSplit },
                ).ifEmpty { listOf(s.importCooklangDone) }.joinToString(" "),
                error = null,
            )
        }
    }

    /**
     * What to tell the cook when a file did not import.
     *
     * Only the cause they can act on is named — a file with no recipe in it, which means
     * they picked the wrong one. Everything else is one apology, because "try again" is the
     * only advice there is for it.
     */
    private fun importFailure(throwable: Throwable): String {
        val s = copy()
        val body = (throwable as? ApiException)?.body ?: throwable.message ?: return s.importCooklangFailed
        return if ("cooklang_file_empty" in body) s.importCooklangEmpty else s.importCooklangFailed
    }

    fun dismissImportMessage() = _uiState.update { it.copy(importMessage = null) }

    /** The scanner could not be reached, or read nothing off the page. */
    fun reportScanFailure() = _uiState.update { it.copy(scanMessage = copy().scanFailed) }

    fun dismissScanMessage() = _uiState.update { it.copy(scanMessage = null) }

    /**
     * Turns the free-text ingredients a scan produced into catalogue references, where the
     * catalogue clearly holds the same thing.
     *
     * **Only an unambiguous match is taken.** The search is fuzzy by design — it is there to
     * find "farine" while somebody is still typing "fari" — so its best answer to "sel" is a
     * salt of some sort, not necessarily salt. A row the catalogue does not obviously hold
     * stays free text, which is a working ingredient rather than a wrong one, and one tap
     * from being corrected by hand.
     *
     * [from] is the position the scanned rows start at. Rows are matched against that
     * snapshot and written back by identity rather than by index: the cook is free to type,
     * add and delete while this runs, and a row that moved or went away must not take
     * another's name.
     */
    private fun matchScannedIngredients(from: Int) {
        val pending = _uiState.value.ingredients.drop(from)
        viewModelScope.launch {
            val resolved = pending.map { row ->
                val match = repo.searchIngredients(row.query)
                    .getOrNull()
                    ?.items
                    ?.firstOrNull { sameIngredient(it.displayName(), row.query) }
                row to match
            }
            _uiState.update { current ->
                val units = current.units
                val system = AppUnits.system.value
                val ingredients = current.ingredients.map { row ->
                    val match = resolved.firstOrNull { it.first === row }?.second ?: return@map row
                    row.copy(
                        ingredientId = match.id,
                        customName = null,
                        ingredientName = match.displayName(),
                        type = match.type,
                        query = match.displayName(),
                        // What the page said, when it said anything: the catalogue's default
                        // is for a row being started from nothing, not for one already
                        // carrying "200 g".
                        unit = row.unit.takeIf { it != "NONE" } ?: defaultUnitFor(match, units, system),
                        allowedTypes = match.allowedTypes,
                    )
                }
                current.copy(ingredients = ingredients, isScanning = false)
            }
        }
    }

    /**
     * Whether two names are the same ingredient.
     *
     * Case, accents and a trailing plural aside — a page says "Pommes" where the catalogue
     * says "pomme" — and nothing else. Anything looser starts accepting a near neighbour, and
     * an ingredient silently replaced by a similar one is worse than one left as free text:
     * it is wrong in the nutrition, and it reads as correct.
     */
    private fun sameIngredient(catalogue: String, scanned: String): Boolean {
        fun normalise(value: String) = RecipeScan.fold(value.trim()).removeSuffix("s")
        return normalise(catalogue) == normalise(scanned) && scanned.isNotBlank()
    }

    private fun copy(): Strings = stringsFor(AppLanguage.current.value)

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value, error = null) }
    fun updateDescription(value: String) = _uiState.update { it.copy(description = value) }
    fun updateDishClass(value: String) = _uiState.update { it.copy(dishClass = value) }
    fun updateYield(value: String) = _uiState.update { it.copy(yield = value) }
    fun updatePrepTime(value: String) = _uiState.update { it.copy(prepTime = value) }
    fun updateCookTime(value: String) = _uiState.update { it.copy(cookTime = value) }
    fun updateCookTemp(value: String) = _uiState.update { it.copy(cookTemp = value) }
    fun updateTips(value: String) = _uiState.update { it.copy(tips = value) }
    fun setPendingImage(image: PickedImage?) = _uiState.update { it.copy(pendingImage = image) }

    // Ingredient operations
    fun addIngredient() {
        _uiState.update { it.copy(ingredients = it.ingredients + EditIngredient()) }
    }

    fun removeIngredient(index: Int) {
        searchJobs[index]?.cancel()
        searchJobs.remove(index)
        _uiState.update { state ->
            state.copy(ingredients = state.ingredients.toMutableList().also { it.removeAt(index) })
        }
    }

    fun updateIngredientQuery(index: Int, query: String) {
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) {
                val row = list[index]
                list[index] = row.copy(
                    query = query,
                    ingredientId = null,
                    // A row already known to be custom keeps its name in sync with the field.
                    customName = if (row.customName == null) null else customName(query),
                    ingredientName = query,
                    showDropdown = query.isNotBlank(),
                )
            }
            state.copy(ingredients = list)
        }
        searchJobs[index]?.cancel()
        if (query.isNotBlank()) {
            searchJobs[index] = viewModelScope.launch {
                delay(300)
                repo.searchIngredients(query)
                    .onSuccess { result ->
                        _uiState.update { state ->
                            val list = state.ingredients.toMutableList()
                            if (index < list.size) {
                                list[index] = list[index].copy(searchResults = result.items, showDropdown = true)
                            }
                            state.copy(ingredients = list)
                        }
                    }
            }
        } else {
            _uiState.update { state ->
                val list = state.ingredients.toMutableList()
                if (index < list.size) {
                    list[index] = list[index].copy(searchResults = emptyList(), showDropdown = false)
                }
                state.copy(ingredients = list)
            }
        }
    }

    fun selectIngredient(index: Int, summary: IngredientSummary) {
        val name = summary.displayName()
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) {
                list[index] = list[index].copy(
                    ingredientId = summary.id,
                    customName = null,
                    ingredientName = name,
                    type = summary.type,
                    query = name,
                    unit = defaultUnitFor(summary, state.units, AppUnits.system.value),
                    allowedTypes = summary.allowedTypes,
                    searchResults = emptyList(),
                    showDropdown = false,
                )
            }
            state.copy(ingredients = list)
        }
    }

    fun selectCustomIngredient(index: Int) {
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) {
                val name = customName(list[index].query)
                list[index] = list[index].copy(
                    ingredientId = null,
                    customName = name,
                    ingredientName = name,
                    query = name,
                    allowedTypes = emptyList(),
                    searchResults = emptyList(),
                    showDropdown = false,
                )
            }
            state.copy(ingredients = list)
        }
    }

    fun updateIngredientUnit(index: Int, unit: String) {
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) {
                list[index] = list[index].copy(
                    unit = unit,
                    amount = if (unit == "NONE") null else list[index].amount,
                )
            }
            state.copy(ingredients = list)
        }
    }

    fun updateIngredientAmount(index: Int, amount: String) {
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) list[index] = list[index].copy(amount = amount.toFloatOrNull())
            state.copy(ingredients = list)
        }
    }

    fun updateIngredientComplement(index: Int, complement: String) {
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) list[index] = list[index].copy(complement = complement)
            state.copy(ingredients = list)
        }
    }

    fun dismissDropdown(index: Int) {
        _uiState.update { state ->
            val list = state.ingredients.toMutableList()
            if (index < list.size) list[index] = list[index].copy(showDropdown = false)
            state.copy(ingredients = list)
        }
    }

    // Step operations
    fun addStep() = _uiState.update { it.copy(steps = it.steps + StepItem(newStepId(), "")) }

    fun removeStep(id: String) = _uiState.update { s ->
        s.copy(steps = s.steps.filter { it.id != id })
    }

    /**
     * Rewrites a step, and re-reads its duration from the new text unless the cook has taken
     * that over. See [StepItem.durationTouched].
     */
    fun updateStep(id: String, text: String) = _uiState.update { s ->
        s.copy(
            steps = s.steps.map { step ->
                when {
                    step.id != id -> step
                    step.durationTouched -> step.copy(text = text)
                    else -> {
                        val detected = StepDurations.parseSeconds(text)
                        step.copy(
                            text = text,
                            // Reading a duration out of the words is also what attaches the
                            // timer: until there is one, there is nothing to show.
                            attachments =
                                if (detected == null) step.attachments - StepAttachment.TIMER
                                else step.attachments + StepAttachment.TIMER,
                            durationSeconds = detected,
                        )
                    }
                }
            }
        )
    }

    /** Gives a step something to carry. Adding a timer is what makes its field appear. */
    fun attachToStep(id: String, attachment: StepAttachment) = _uiState.update { s ->
        s.copy(
            steps = s.steps.map { step ->
                if (step.id != id) step
                else when (attachment) {
                    // Empty, rather than a made-up five minutes: the cook asked for a timer,
                    // not for a duration we invented. Touched, because asking for one is a
                    // decision, and the next keystroke must not overwrite what they are
                    // about to type into it.
                    StepAttachment.TIMER -> step.copy(
                        attachments = step.attachments + attachment,
                        durationSeconds = null,
                        durationTouched = true,
                    )
                    // Nothing picked yet: the list of the recipe's ingredients appears and the
                    // cook ticks what the step uses.
                    StepAttachment.INGREDIENTS -> step.copy(attachments = step.attachments + attachment)
                    // Just the empty frame, which opens the picker when tapped. Asking for a
                    // photo and choosing one are two taps in a row, not one.
                    StepAttachment.PHOTO -> step.copy(attachments = step.attachments + attachment)
                }
            },
        )
    }

    /** Takes it away again, and whatever it was holding with it. */
    fun detachFromStep(id: String, attachment: StepAttachment) = _uiState.update { s ->
        s.copy(
            steps = s.steps.map { step ->
                if (step.id != id) step
                else when (attachment) {
                    StepAttachment.TIMER -> step.copy(
                        attachments = step.attachments - attachment,
                        durationSeconds = null,
                        durationTouched = true,
                    )
                    StepAttachment.INGREDIENTS -> step.copy(
                        attachments = step.attachments - attachment,
                        ingredients = emptyList(),
                    )
                    // A picture already on the server is not deleted here: the row is only
                    // removed by the save, so that a photo taken off and put back before
                    // saving costs nothing. [syncStepImages] is what notices it has gone.
                    StepAttachment.PHOTO -> step.copy(
                        attachments = step.attachments - attachment,
                        pendingImage = null,
                    )
                }
            },
        )
    }

    /**
     * Stages a picture for a step. It replaces whatever was chosen before, and whatever is
     * already saved - the old file goes when the new one is posted, at the next save.
     */
    fun setStepImage(id: String, image: PickedImage) = _uiState.update { s ->
        s.copy(
            steps = s.steps.map { step ->
                if (step.id != id) step
                else step.copy(
                    pendingImage = image,
                    // Picking one from the camera roll without having asked for a photo
                    // first is possible from the recipe card; either way it is attached now.
                    attachments = step.attachments + StepAttachment.PHOTO,
                )
            },
        )
    }

    /** Ticks or unticks one of the recipe's ingredients for a step. */
    fun toggleStepIngredient(id: String, index: Int) = _uiState.update { s ->
        s.copy(
            steps = s.steps.map { step ->
                if (step.id != id) step
                else if (step.ingredients.any { it.index == index }) {
                    step.copy(ingredients = step.ingredients.filterNot { it.index == index })
                } else {
                    // Appended rather than sorted: the cook ticks them in the order the step
                    // uses them, and the recipe's own order is what the reader gets anyway.
                    step.copy(ingredients = step.ingredients + StepIngredientDraft(index))
                }
            },
        )
    }

    /** How much of a ticked ingredient this step uses. Blank means the rest of it. */
    fun updateStepIngredientAmount(id: String, index: Int, amount: String) = _uiState.update { s ->
        s.copy(
            steps = s.steps.map { step ->
                if (step.id != id) step
                else step.copy(
                    ingredients = step.ingredients.map {
                        if (it.index == index) it.copy(amount = amount.filter { c -> c.isDigit() || c == '.' })
                        else it
                    },
                )
            },
        )
    }

    /**
     * Sets a step's timer by hand, in minutes, and stops detecting one for it.
     *
     * Null clears it — and still counts as being taken over, so a cook who deliberately
     * removes the timer a step's wording implies does not get it back on the next keystroke.
     */
    fun updateStepDuration(id: String, minutes: Int?) = _uiState.update { s ->
        s.copy(
            steps = s.steps.map { step ->
                if (step.id != id) step
                else step.copy(
                    durationSeconds = minutes?.takeIf { it > 0 }?.times(60),
                    durationTouched = true,
                )
            }
        )
    }

    fun reorderStep(from: Int, to: Int) = _uiState.update { s ->
        s.copy(steps = s.steps.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = "Title is required") }
            return
        }

        // Held onto, because what comes back has to be matched to it: the server answers
        // with the steps it was sent, in the order it was sent them, and that is how a
        // step being written learns the id its picture is posted against.
        val written = state.steps.filter { it.text.isNotBlank() }

        val dto = RecipeSaveDto(
            title = state.title.trim(),
            description = state.description.trim(),
            dishClass = state.dishClass,
            yield = state.yield.toIntOrNull(),
            preparationTime = state.prepTime.toIntOrNull(),
            cookingTime = state.cookTime.toIntOrNull(),
            cookingTemperature = state.cookTemp.toIntOrNull(),
            ingredients = state.ingredients.mapNotNull { ing ->
                val customName = ing.customName?.takeIf { it.isNotBlank() }
                if (ing.ingredientId == null && customName == null) return@mapNotNull null
                RecipeIngredientSaveDto(
                    id = ing.ingredientId,
                    customName = customName.takeIf { ing.ingredientId == null },
                    unit = ing.unit,
                    amount = if (ing.unit == "NONE") null else ing.amount,
                    complement = ing.complement.ifBlank { null },
                )
            },
            steps = written
                .map { step ->
                    RecipeStepInfo(
                        text = step.text.trim(),
                        // What the server matches this step to the row it already has by.
                        // Null for a step written since the last save, which is an insert.
                        id = step.serverId,
                        // A timer that was added and left blank is no timer: nothing is saved
                        // for an attachment holding nothing.
                        durationSeconds = step.durationSeconds
                            ?.takeIf { StepAttachment.TIMER in step.attachments },
                        ingredients = if (StepAttachment.INGREDIENTS !in step.attachments) emptyList()
                        else step.ingredients
                            // An ingredient deleted since it was ticked leaves a position
                            // pointing at nothing. The server drops those, but not sending
                            // them keeps the amounts it checks honest.
                            .filter { it.index in state.ingredients.indices }
                            .map { used ->
                                RecipeStepIngredientInfo(
                                    index = used.index,
                                    amount = used.amount.toFloatOrNull()?.takeIf { it > 0f },
                                )
                            },
                    )
                },
            tips = state.tips.trim(),
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val result = if (state.recipeId != null) {
                repo.updateRecipe(state.recipeId, dto)
            } else {
                repo.createRecipe(dto)
            }
            result
                .onSuccess { recipe ->
                    // The image needs a recipe id to upload against, which a brand-new
                    // recipe only gets from this save — the opposite order from a profile
                    // edit, where the id already exists going in. A failed upload here is
                    // best-effort: the recipe's own content already saved, so it doesn't
                    // block navigating on; the user can just add a photo again from Edit.
                    // Clearing it only on success keeps a later "Save draft" tap from
                    // re-uploading (and re-bumping the image version for) the same picture.
                    val imageUploaded = state.pendingImage?.let { image ->
                        repo.uploadRecipeImage(recipe.id, image.bytes, image.mimeType).isSuccess
                    } ?: false
                    val stepVersions = syncStepImages(written, recipe.steps)
                    _uiState.update { current ->
                        current.copy(
                            isSaving = false,
                            saved = true,
                            recipeId = recipe.id,
                            pendingImage = if (imageUploaded) null else current.pendingImage,
                            // Matched by the card's own id rather than by position: the cook
                            // may have added or reordered steps while the save was in flight.
                            steps = current.steps.map { item ->
                                val row = recipe.steps.getOrNull(written.indexOfFirst { it.id == item.id })
                                    ?: return@map item
                                item.copy(
                                    serverId = row.id,
                                    imageVersion = stepVersions[item.id] ?: row.imageVersion,
                                    pendingImage =
                                        if (item.id in stepVersions) null else item.pendingImage,
                                )
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, error = error.message) }
                }
        }
    }

    /**
     * Puts each step's picture where the save has just made room for it, and returns the
     * version every step that changed one is now on.
     *
     * Two lists arrive: the steps as they were sent, and the rows the server answered with.
     * They line up one for one, because a save returns what it was given in the order it was
     * given it - that is the whole reason a step carries an id of its own now.
     *
     * Best-effort, like the recipe's own photograph: the words are saved by the time this
     * runs, so a failed upload leaves the picture staged rather than failing the save. One
     * that did upload is cleared, so a second Save does not send it - and bump its version -
     * all over again.
     */
    private suspend fun syncStepImages(
        written: List<StepItem>,
        saved: List<RecipeStepInfo>,
    ): Map<String, Long> {
        val versions = mutableMapOf<String, Long>()
        written.forEachIndexed { index, step ->
            val row = saved.getOrNull(index) ?: return@forEachIndexed
            val stepId = row.id ?: return@forEachIndexed
            val picked = step.pendingImage
            when {
                picked != null ->
                    if (repo.uploadStepImage(stepId, picked.bytes, picked.mimeType).isSuccess) {
                        versions[step.id] = row.imageVersion + 1
                    }
                // The cook took the photo off. Saving the words does not touch the file, so
                // it is removed by hand - and the row is still there to remove it from.
                StepAttachment.PHOTO !in step.attachments && row.imageVersion > 0 ->
                    if (repo.deleteStepImage(stepId).isSuccess) versions[step.id] = 0
            }
        }
        return versions
    }

    companion object {
        private const val CUSTOM_NAME_MAX_LENGTH = 50

        private fun customName(query: String) = query.trim().take(CUSTOM_NAME_MAX_LENGTH)

        /**
         * What the unit picker opens on for a freshly chosen ingredient.
         *
         * The catalogue's own default first, then weight, then whatever the ingredient
         * allows — each of them put on the ladder this cook measures on, so an imperial
         * cook adding flour meets ounces rather than grams and a picker to correct. The
         * declared unit is mapped rather than kept because it cannot have been a choice
         * about *this* cook: an operator sets it once for the whole catalogue.
         *
         * Only the preselection. What the cook then picks is what gets saved, as written.
         */
        private fun defaultUnitFor(
            summary: IngredientSummary,
            units: List<UnitInfo>,
            system: AppUnitSystem,
        ): String {
            val unit = summary.defaultUnit
                ?: "GRAM".takeIf { "WEIGHT" in summary.allowedTypes }
                ?: units.firstOrNull { it.name != "NONE" && it.type in summary.allowedTypes }?.name
                ?: "NONE"
            return preferredUnitFor(unit, system, units)
        }

        fun factory(recipeId: Long?, userId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RecipeEditViewModel(AppGraph.recipeRepository, AppGraph.unitRepository, recipeId, userId)
            }
        }
    }
}
