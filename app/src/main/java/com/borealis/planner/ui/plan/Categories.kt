package com.borealis.planner.ui.plan

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.borealis.planner.R
import com.borealis.planner.data.Category
import com.borealis.planner.data.CategoryPalette
import com.borealis.planner.data.PlannerLogic
import com.borealis.planner.data.PlannerState
import com.borealis.planner.data.PlannerStore
import com.borealis.planner.ui.components.Eyebrow
import com.borealis.planner.ui.components.GlassChip
import com.borealis.planner.ui.components.PillButton
import com.borealis.planner.ui.components.Tone
import com.borealis.planner.ui.components.glass
import com.borealis.planner.ui.components.rememberHaptics
import com.borealis.planner.ui.theme.Aurora

fun categoryColor(index: Int): Color = Color(CategoryPalette.argb(index))

fun categoryColor(state: PlannerState, categoryId: String?): Color? =
    PlannerLogic.colorOf(state, categoryId)?.let { categoryColor(it) }

@Composable
fun CategoryDot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Composable
private fun CatChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** A one-line text box with Add / Cancel, for naming a new category or subcategory. */
@Composable
fun InlineNameField(
    placeholder: String,
    initial: String = "",
    actionLabel: String = "Add",
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp), fillAlpha = 0.05f, veil = 0.3f)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.bodyLarge.copy(color = Aurora.Frost)
        BasicTextField(
            value = value,
            onValueChange = { if (it.text.length <= 40 && '\n' !in it.text) value = it },
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Aurora.Borealis),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (value.text.isNotBlank()) onSubmit(value.text.trim()) }),
            modifier = Modifier.weight(1f).padding(vertical = 8.dp).focusRequester(focus),
            decorationBox = { inner ->
                Box {
                    if (value.text.isEmpty()) Text(placeholder, style = style.copy(color = Aurora.Haze))
                    inner()
                }
            },
        )
        PillButton(actionLabel, { if (value.text.isNotBlank()) onSubmit(value.text.trim()) }, tone = Tone.Accent)
        Spacer(Modifier.width(6.dp))
        PillButton("Cancel", onCancel)
    }
}

/**
 * Two rows of chips: categories, then (once one is picked) its subcategories.
 * New categories and subcategories can be created on the spot.
 */
@Composable
fun CategoryPicker(state: PlannerState, store: PlannerStore, selected: String?, onSelect: (String?) -> Unit) {
    val tops = PlannerLogic.topCategories(state)
    val root = PlannerLogic.rootOf(state, selected)
    var adding by remember { mutableStateOf<String?>(null) } // "" = new category, otherwise parent id
    Column {
        CatChipRow {
            GlassChip("None", selected == null, { onSelect(null) })
            tops.forEach { c ->
                GlassChip(c.name, root?.id == c.id, { onSelect(c.id) }, icon = R.drawable.ic_folder, accent = categoryColor(c.color))
            }
            GlassChip("New category", false, { adding = "" }, icon = R.drawable.ic_add)
        }
        if (root != null) {
            val subs = PlannerLogic.subcategories(state, root.id)
            val accent = categoryColor(root.color)
            Spacer(Modifier.height(8.dp))
            CatChipRow {
                GlassChip(if (subs.isEmpty()) root.name else "Just ${root.name}", selected == root.id, { onSelect(root.id) }, accent = accent)
                subs.forEach { sub ->
                    GlassChip(sub.name, selected == sub.id, { onSelect(sub.id) }, icon = R.drawable.ic_layers, accent = accent)
                }
                GlassChip("Subcategory", false, { adding = root.id }, icon = R.drawable.ic_add)
            }
        }
        val parent = adding
        if (parent != null) {
            Spacer(Modifier.height(8.dp))
            InlineNameField(
                placeholder = if (parent.isEmpty()) "Category name, e.g. Uni" else "Subcategory in ${root?.name ?: ""}, e.g. BIO101",
                onSubmit = { name ->
                    val c = store.addCategory(name, parent.ifEmpty { null })
                    if (c != null) onSelect(c.id)
                    adding = null
                },
                onCancel = { adding = null },
            )
        }
    }
}

@Composable
private fun SmallIcon(@DrawableRes icon: Int, label: String, enabled: Boolean = true, rotate: Boolean = false, tint: Color = Aurora.Mist, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { haptics.tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            label,
            tint = if (enabled) tint else Aurora.Haze.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp).then(if (rotate) Modifier.rotate(180f) else Modifier),
        )
    }
}

/** Add, rename, recolour, reorder and delete categories and subcategories. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerSheet(state: PlannerState, store: PlannerStore, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var addingTop by remember { mutableStateOf(state.categories.isEmpty()) }
    var addingSubTo by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val tops = PlannerLogic.topCategories(state)
    val openCount: (Category) -> Int = { c -> state.tasks.count { t -> t.isOpen && PlannerLogic.inCategory(state, t, c.id) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = Color(0xF50B1222),
        contentColor = Aurora.Frost,
        scrimColor = Color(0xA6000000),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Eyebrow("Organise", color = Aurora.Borealis)
            Spacer(Modifier.height(6.dp))
            Text("Categories", style = MaterialTheme.typography.headlineMedium, color = Aurora.Frost)
            Spacer(Modifier.height(6.dp))
            Text(
                "Sort tasks into categories, and categories into subcategories, like Uni › BIO101 or Home › Groceries. Tap a colour dot to change it. Tip: type #name in a new task to file it straight away.",
                style = MaterialTheme.typography.bodyMedium,
                color = Aurora.Mist,
            )
            Spacer(Modifier.height(16.dp))
            if (addingTop) {
                InlineNameField("New category, e.g. Uni", onSubmit = { store.addCategory(it); addingTop = false }, onCancel = { addingTop = false })
            } else {
                PillButton("New category", { addingTop = true }, icon = R.drawable.ic_add, tone = Tone.Accent)
            }
            Spacer(Modifier.height(14.dp))
            if (tops.isEmpty()) {
                Text("No categories yet.", style = MaterialTheme.typography.bodyMedium, color = Aurora.Haze)
            }
            tops.forEachIndexed { i, c ->
                val color = categoryColor(c.color)
                val subs = PlannerLogic.subcategories(state, c.id)
                Column(
                    Modifier
                        .padding(vertical = 6.dp)
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(20.dp), tint = color, fillAlpha = 0.05f, veil = 0.35f)
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { store.recolorCategory(c.id, c.color + 1) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(color).border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f).clickable { renaming = c.id }) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium, color = Aurora.Frost, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val n = openCount(c)
                            Text(
                                buildString {
                                    append(if (n == 1) "1 open task" else "$n open tasks")
                                    if (subs.isNotEmpty()) append(" · ${subs.size} sub" + if (subs.size == 1) "category" else "categories")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Aurora.Mist,
                            )
                        }
                        SmallIcon(R.drawable.ic_up, "Move ${c.name} up", enabled = i > 0) { store.moveCategory(c.id, -1) }
                        SmallIcon(R.drawable.ic_up, "Move ${c.name} down", enabled = i < tops.size - 1, rotate = true) { store.moveCategory(c.id, 1) }
                        SmallIcon(R.drawable.ic_trash, "Delete ${c.name}", tint = Aurora.Flare) { confirmDelete = c.id }
                    }
                    if (renaming == c.id) {
                        Spacer(Modifier.height(8.dp))
                        InlineNameField("Category name", initial = c.name, actionLabel = "Save", onSubmit = { store.renameCategory(c.id, it); renaming = null }, onCancel = { renaming = null })
                    }
                    if (confirmDelete == c.id) {
                        Spacer(Modifier.height(8.dp))
                        DeleteConfirm(
                            "Delete ${c.name}?" + if (subs.isNotEmpty()) " Its subcategories go too." else "",
                            "Its tasks stay, without a category.",
                            onDelete = { store.deleteCategory(c.id); confirmDelete = null },
                            onKeep = { confirmDelete = null },
                        )
                    }
                    subs.forEachIndexed { j, sub ->
                        Row(Modifier.padding(start = 30.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_subtask), null, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f).clickable { renaming = sub.id }) {
                                Text(sub.name, style = MaterialTheme.typography.bodyLarge, color = Aurora.Frost, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val n = openCount(sub)
                                Text(if (n == 1) "1 open task" else "$n open tasks", style = MaterialTheme.typography.bodySmall, color = Aurora.Haze)
                            }
                            SmallIcon(R.drawable.ic_up, "Move ${sub.name} up", enabled = j > 0) { store.moveCategory(sub.id, -1) }
                            SmallIcon(R.drawable.ic_up, "Move ${sub.name} down", enabled = j < subs.size - 1, rotate = true) { store.moveCategory(sub.id, 1) }
                            SmallIcon(R.drawable.ic_trash, "Delete ${sub.name}", tint = Aurora.Flare) { confirmDelete = sub.id }
                        }
                        if (renaming == sub.id) {
                            Spacer(Modifier.height(6.dp))
                            InlineNameField("Subcategory name", initial = sub.name, actionLabel = "Save", onSubmit = { store.renameCategory(sub.id, it); renaming = null }, onCancel = { renaming = null })
                        }
                        if (confirmDelete == sub.id) {
                            Spacer(Modifier.height(6.dp))
                            DeleteConfirm("Delete ${sub.name}?", "Its tasks move up into ${c.name}.", onDelete = { store.deleteCategory(sub.id); confirmDelete = null }, onKeep = { confirmDelete = null })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (addingSubTo == c.id) {
                        InlineNameField("Subcategory, e.g. BIO101", onSubmit = { store.addCategory(it, c.id); addingSubTo = null }, onCancel = { addingSubTo = null })
                    } else {
                        Row(Modifier.padding(start = 26.dp)) {
                            PillButton("Add subcategory", { addingSubTo = c.id }, icon = R.drawable.ic_add)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirm(title: String, detail: String, onDelete: () -> Unit, onKeep: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Aurora.Flare.copy(alpha = 0.12f))
            .padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Aurora.Frost)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Aurora.Mist)
        Spacer(Modifier.height(8.dp))
        Row {
            PillButton("Delete", onDelete, icon = R.drawable.ic_trash, tone = Tone.Danger)
            Spacer(Modifier.width(8.dp))
            PillButton("Keep", onKeep)
        }
    }
}
