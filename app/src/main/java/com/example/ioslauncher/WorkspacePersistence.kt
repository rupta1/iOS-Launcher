package com.example.ioslauncher

import org.json.JSONArray
import org.json.JSONObject

/** Versioned JSON codec stored in LauncherDb's existing key/value table. */
internal object WorkspaceCodec {
    private const val VERSION = 1
    fun encode(items: List<WorkspaceRecord>): String = JSONObject().apply {
        put("version", VERSION)
        put("items", JSONArray().apply {
            items.distinctBy { it.id }.forEach { item ->
                put(JSONObject().apply {
                    put("id",item.id);put("kind",item.kind.name);put("page",item.page.coerceAtLeast(0))
                    put("cellX",item.cellX.coerceAtLeast(0));put("cellY",item.cellY.coerceAtLeast(0))
                    put("spanX",item.spanX.coerceAtLeast(1));put("spanY",item.spanY.coerceAtLeast(1));put("title",item.title)
                })
            }
        })
    }.toString()
    fun decode(raw: String): List<WorkspaceRecord> = runCatching {
        val root=JSONObject(raw)
        if(root.optInt("version",0)>VERSION) return emptyList()
        val array=root.optJSONArray("items")?:JSONArray()
        (0 until array.length()).mapNotNull { index ->
            val o=array.optJSONObject(index)?:return@mapNotNull null
            val kind=runCatching { WorkspaceItemKind.valueOf(o.optString("kind")) }.getOrDefault(WorkspaceItemKind.APP)
            WorkspaceRecord(o.optString("id"),kind,o.optInt("page").coerceAtLeast(0),
                o.optInt("cellX").coerceAtLeast(0),o.optInt("cellY").coerceAtLeast(0),
                o.optInt("spanX",1).coerceAtLeast(1),o.optInt("spanY",1).coerceAtLeast(1),o.optString("title"))
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())
}

internal fun resolveWorkspacePlacement(
    items: List<WorkspaceRecord>,
    grid: WorkspaceGridSpec
): List<WorkspaceRecord> {
    val normalized=items.distinctBy{it.id}.map{it.copy(page=it.page.coerceAtLeast(0),
        cellX=it.cellX.coerceIn(0,grid.safeColumns-1),cellY=it.cellY.coerceIn(0,grid.safeRows-1),
        spanX=it.spanX.coerceIn(1,grid.safeColumns),spanY=it.spanY.coerceIn(1,grid.safeRows))}
    val output=mutableListOf<WorkspaceRecord>()
    val maxPage=normalized.maxOfOrNull{it.page}?:0
    for(page in 0..maxPage){
        val group=normalized.filter{it.page==page}
        val plan=HomeWorkspaceEngine.layout(group.map{HomeGridTile(it.id,grid.slot(it.cellX,it.cellY),it.spanX,it.spanY)},grid.safeColumns,grid.safeRows,false)
        group.forEach{item->plan[item.id]?.let{cell->output+=item.copy(cellX=cell.column,cellY=cell.row)}}
    }
    return output
}
