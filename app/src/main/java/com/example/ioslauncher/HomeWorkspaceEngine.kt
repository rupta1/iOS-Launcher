package com.example.ioslauncher

/**
 * Deterministic placement engine for the launcher workspace.
 *
 * The UI owns gestures and animation; this class owns only the grid model.
 * It is deliberately allocation-light and has no Compose dependencies so a
 * drag preview and a settled page use the same collision rules.
 */
internal object HomeWorkspaceEngine {
    fun layout(
        tiles: List<HomeGridTile>,
        columns: Int,
        rows: Int,
        compact: Boolean
    ): Map<String, HomeGridCell> {
        val cols = columns.coerceAtLeast(1)
        val rowCount = rows.coerceAtLeast(1)
        val capacity = cols * rowCount
        val occupied = Array(rowCount) { BooleanArray(cols) }
        val result = LinkedHashMap<String, HomeGridCell>()

        fun canPlace(index: Int, width: Int, height: Int): Boolean {
            if (index < 0 || index >= capacity) return false
            val left = index % cols
            val top = index / cols
            if (left + width > cols || top + height > rowCount) return false
            for (y in top until top + height) {
                for (x in left until left + width) {
                    if (occupied[y][x]) return false
                }
            }
            return true
        }

        fun place(index: Int, width: Int, height: Int) {
            val left = index % cols
            val top = index / cols
            for (y in top until top + height) {
                for (x in left until left + width) occupied[y][x] = true
            }
        }

        val ordered = if (compact) {
            tiles.sortedWith(compareBy<HomeGridTile> { it.slot }.thenBy { it.key })
        } else {
            tiles.sortedWith(compareBy<HomeGridTile> { it.slot.coerceAtLeast(0) }.thenBy { it.key })
        }

        for (tile in ordered) {
            val width = tile.spanX.coerceIn(1, cols)
            val height = tile.spanY.coerceIn(1, rowCount)
            val requested = if (compact) 0 else tile.slot.coerceIn(0, capacity - 1)
            var chosen = -1

            // Compact pages pack from the beginning. Free-placement pages keep
            // each tile at its requested cell unless its footprint collides.
            val start = if (compact) 0 else requested
            for (index in start until capacity) {
                if (canPlace(index, width, height)) {
                    chosen = index
                    break
                }
            }
            if (chosen < 0 && !compact) {
                for (index in 0 until start) {
                    if (canPlace(index, width, height)) {
                        chosen = index
                        break
                    }
                }
            }

            if (chosen >= 0) {
                place(chosen, width, height)
                result[tile.key] = HomeGridCell(chosen % cols, chosen / cols)
            }
        }
        return result
    }
}

/**
 * Canonical workspace model used by the rebuilt home surface.
 * Coordinates are grid cells; pages are independent and IDs remain stable
 * while items move between pages.
 */
internal data class WorkspaceItem(
    val id: String,
    val page: Int,
    val slot: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val kind: WorkspaceItemKind = WorkspaceItemKind.APP
)

internal enum class WorkspaceItemKind { APP, FOLDER, WIDGET }

internal data class WorkspacePlacement(
    val itemId: String,
    val page: Int,
    val slot: Int,
    val column: Int,
    val row: Int,
    val spanX: Int,
    val spanY: Int
)

internal data class WorkspacePlan(
    val placements: List<WorkspacePlacement>,
    val overflow: List<WorkspaceItem>,
    val pageCount: Int
)

internal data class WorkspaceMove(
    val items: List<WorkspaceItem>,
    val selectedPage: Int
)

internal fun HomeWorkspaceEngine.plan(
    items: List<WorkspaceItem>,
    columns: Int,
    rows: Int,
    compact: Boolean = false
): WorkspacePlan {
    val cols = columns.coerceAtLeast(1)
    val rowCount = rows.coerceAtLeast(1)
    val normalized = items.distinctBy { it.id }.map {
        it.copy(
            page = it.page.coerceAtLeast(0),
            slot = it.slot.coerceAtLeast(0),
            spanX = it.spanX.coerceIn(1, cols),
            spanY = it.spanY.coerceIn(1, rowCount)
        )
    }
    val placements = mutableListOf<WorkspacePlacement>()
    val overflow = mutableListOf<WorkspaceItem>()
    val maxPage = normalized.maxOfOrNull { it.page } ?: 0

    for (page in 0..maxPage) {
        val pageItems = normalized.filter { it.page == page }
        val cells = layout(
            pageItems.map { HomeGridTile(it.id, it.slot, it.spanX, it.spanY) },
            cols,
            rowCount,
            compact
        )
        pageItems.forEach { item ->
            val cell = cells[item.id]
            if (cell == null) {
                overflow += item
            } else {
                placements += WorkspacePlacement(
                    itemId = item.id,
                    page = page,
                    slot = cell.row * cols + cell.column,
                    column = cell.column,
                    row = cell.row,
                    spanX = item.spanX,
                    spanY = item.spanY
                )
            }
        }
    }
    return WorkspacePlan(
        placements = placements,
        overflow = overflow,
        pageCount = (placements.maxOfOrNull { it.page } ?: 0) + 1
    )
}

/** Move an item to a page/slot and deterministically reflow only that page. */
internal fun HomeWorkspaceEngine.move(
    items: List<WorkspaceItem>,
    itemId: String,
    destinationPage: Int,
    destinationSlot: Int,
    columns: Int,
    rows: Int
): WorkspaceMove {
    val moving = items.firstOrNull { it.id == itemId } ?: return WorkspaceMove(items, 0)
    val targetPage = destinationPage.coerceAtLeast(0)
    val targetSlot = destinationSlot.coerceAtLeast(0)
    val updated = items.filterNot { it.id == itemId } +
        moving.copy(page = targetPage, slot = targetSlot)
    val plan = plan(updated, columns, rows, compact = false)
    val resolved = plan.placements.associateBy { it.itemId }
    val committed = updated.map { item ->
        val placement = resolved[item.id]
        if (placement == null) item else item.copy(page = placement.page, slot = placement.slot)
    }
    return WorkspaceMove(committed, targetPage)
}

/** Compact page indices without changing the relative order of populated pages. */
internal fun HomeWorkspaceEngine.compactPages(items: List<WorkspaceItem>): List<WorkspaceItem> {
    val pageMap = items.map { it.page.coerceAtLeast(0) }.distinct().sorted()
        .mapIndexed { index, old -> old to index }.toMap()
    return items.map { it.copy(page = pageMap[it.page.coerceAtLeast(0)] ?: 0) }
}

