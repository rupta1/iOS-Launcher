package com.example.ioslauncher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Persistable workspace record. Slots are row-major and spans are grid dimensions. */
internal data class WorkspaceRecord(
    val id: String,
    val kind: WorkspaceItemKind,
    val page: Int,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val title: String = ""
) {
    val slot: Int get() = cellY * 4 + cellX
}

internal data class WorkspaceGridSpec(val columns: Int = 4, val rows: Int = 6) {
    val safeColumns get() = columns.coerceIn(1, 8)
    val safeRows get() = rows.coerceIn(1, 12)
    fun slot(x: Int, y: Int) = y.coerceIn(0, safeRows - 1) * safeColumns + x.coerceIn(0, safeColumns - 1)
    fun cell(slot: Int) = HomeGridCell(slot.coerceAtLeast(0) % safeColumns, slot.coerceAtLeast(0) / safeColumns)
}

internal sealed interface WorkspaceDragPayload {
    data class App(val packageName: String, val label: String) : WorkspaceDragPayload
    data class Widget(val widgetId: Int) : WorkspaceDragPayload
    data class Folder(val folderId: String) : WorkspaceDragPayload
}

internal data class WorkspaceDragState(
    val payload: WorkspaceDragPayload,
    val originPage: Int,
    val pointer: Offset,
    val pointerOffset: Offset = Offset.Zero,
    val hoveredPage: Int = originPage,
    val hoveredSlot: Int? = null,
    val folderTargetId: String? = null,
    val overDelete: Boolean = false,
    val startedAtMillis: Long = 0L
)

/** UI-independent gesture coordinator. State changes are immutable and no View is retained. */
internal class WorkspaceDragController {
    var state by mutableStateOf<WorkspaceDragState?>(null)
        private set
    var edgeDirection by mutableIntStateOf(0)
        private set
    private var edgeJob: Job? = null
    private var folderJob: Job? = null

    fun start(payload: WorkspaceDragPayload, page: Int, pointer: Offset, offset: Offset = Offset.Zero) {
        cancelTimers()
        state = WorkspaceDragState(payload, page, pointer, offset, startedAtMillis = System.currentTimeMillis())
    }
    fun update(pointer: Offset, page: Int, slot: Int?, folderTarget: String? = null, delete: Boolean = false) {
        state = state?.copy(pointer = pointer, hoveredPage = page, hoveredSlot = slot, folderTargetId = folderTarget, overDelete = delete)
    }
    fun scheduleEdgeFlip(direction: Int, onFlip: (Int) -> Unit) {
        if (direction == 0 || direction == edgeDirection) return
        edgeJob?.cancel()
        edgeDirection = direction
        edgeJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            delay(400)
            if (edgeDirection == direction && state != null) {
                onFlip(direction)
                state = state?.copy(hoveredPage = (state?.hoveredPage ?: 0) + direction)
            }
        }
    }
    fun scheduleFolderHover(targetId: String, onReady: (String) -> Unit) {
        folderJob?.cancel()
        folderJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            delay(550)
            if (state != null) { state = state?.copy(folderTargetId = targetId); onReady(targetId) }
        }
    }
    fun finish(): WorkspaceDragState? { val result = state; cancelTimers(); state = null; return result }
    fun cancel() { cancelTimers(); state = null }
    private fun cancelTimers() { edgeJob?.cancel(); folderJob?.cancel(); edgeJob=null; folderJob=null; edgeDirection=0 }
}

internal data class WorkspaceDropPreview(
    val page: Int,
    val slot: Int,
    val displacedItemIds: List<String> = emptyList(),
    val folderTargetId: String? = null
)

/** Safe hit testing helpers for workspace drop targets and edge zones. */
internal object WorkspaceHitTest {
    fun slot(bounds: Rect, pointer: Offset, columns: Int, rows: Int): Int? {
        if (!bounds.contains(pointer) || bounds.width <= 0f || bounds.height <= 0f) return null
        val x = ((pointer.x - bounds.left) / bounds.width * columns).toInt().coerceIn(0, columns - 1)
        val y = ((pointer.y - bounds.top) / bounds.height * rows).toInt().coerceIn(0, rows - 1)
        return y * columns + x
    }
    fun edgeDirection(pointer: Offset, width: Float, thresholdFraction: Float = .10f): Int =
        when { pointer.x <= width * thresholdFraction -> -1; pointer.x >= width * (1f-thresholdFraction) -> 1; else -> 0 }
}

/** Reflow preview uses the same deterministic grid engine as settled workspace placement. */
internal fun previewGridReflow(
    items: List<HomeGridTile>,
    columns: Int,
    rows: Int,
    movingId: String,
    destinationSlot: Int
): Map<String, HomeGridCell> {
    val reordered = items.filterNot { it.key == movingId } + items.firstOrNull { it.key == movingId }?.copy(slot=destinationSlot).let { listOfNotNull(it) }
    return HomeWorkspaceEngine.layout(reordered, columns, rows, compact=false)
}
