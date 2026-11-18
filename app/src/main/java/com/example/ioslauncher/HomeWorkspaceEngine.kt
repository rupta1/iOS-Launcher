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
