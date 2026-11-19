package com.example.ioslauncher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Edit-mode state shared by workspace surfaces; selection IDs remain stable. */
internal class WorkspaceEditState {
    var enabled by mutableStateOf(false)
    var selectedIds by mutableStateOf(setOf<String>())
    var gridColumns by mutableIntStateOf(4)
    var gridRows by mutableIntStateOf(6)
    var transition by mutableStateOf(WorkspaceTransition.SLIDE)
    fun toggleSelection(id: String) { selectedIds = if (id in selectedIds) selectedIds-id else selectedIds+id }
    fun clearSelection() { selectedIds = emptySet() }
    fun exit() { enabled=false; clearSelection() }
}
internal enum class WorkspaceTransition { SLIDE, CROSSFADE, CUBE }

/** Reusable edit-mode toolbar. Caller wires picker and grid-setting actions. */
@Composable
internal fun WorkspaceEditToolbar(
    state: WorkspaceEditState,
    pageCount: Int,
    onWallpaper: () -> Unit,
    onWidgets: () -> Unit,
    onGrid: () -> Unit,
    onBatchMove: () -> Unit,
    onCreateFolder: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier=modifier, color=MaterialTheme.colorScheme.surface.copy(alpha=.96f),
        shape=RoundedCornerShape(26.dp), shadowElevation=12.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Edit Home Screen", style=MaterialTheme.typography.titleMedium, modifier=Modifier.weight(1f))
                Text(pageCount.toString()+" pages", style=MaterialTheme.typography.labelMedium)
                TextButton(onClick=onDone) { Text("Done") }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick=onGrid, modifier=Modifier.weight(1f)) { Icon(Icons.Default.GridView,null); Spacer(Modifier.width(4.dp));Text("Grid") }
                FilledTonalButton(onClick=onWidgets, modifier=Modifier.weight(1f)) { Icon(Icons.Default.Widgets,null); Spacer(Modifier.width(4.dp));Text("Widgets") }
                FilledTonalButton(onClick=onWallpaper, modifier=Modifier.weight(1f)) { Icon(Icons.Default.Wallpaper,null); Spacer(Modifier.width(4.dp));Text("Wallpaper") }
            }
            if(state.selectedIds.isNotEmpty()) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
                    Button(onClick=onBatchMove,modifier=Modifier.weight(1f)) { Text("Move "+state.selectedIds.size) }
                    Button(onClick=onCreateFolder,modifier=Modifier.weight(1f)) { Text("Create folder") }
                    TextButton(onClick={state.clearSelection()}) { Text("Clear") }
                }
            }
            Text("Tap icons to select them for batch actions.", style=MaterialTheme.typography.bodySmall)
        }
    }
}

/** Preview transform for edit-mode workspace thumbnails with spring settling. */
@Composable
internal fun WorkspaceEditScale(enabled:Boolean, modifier:Modifier=Modifier, content:@Composable BoxScope.()->Unit) {
    val scale by animateFloatAsState(if(enabled) .88f else 1f,
        animationSpec=spring(dampingRatio=.78f,stiffness=420f),label="workspace-edit-scale")
    Box(modifier.graphicsLayer { scaleX=scale;scaleY=scale }, content=content)
}
