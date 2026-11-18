package com.example.ioslauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWorkspaceEngineTest {
    @Test
    fun compactLayoutPacksTilesInSlotOrder() {
        val result = HomeWorkspaceEngine.layout(
            listOf(
                HomeGridTile("third", 8),
                HomeGridTile("first", 2),
                HomeGridTile("second", 5)
            ),
            columns = 4,
            rows = 2,
            compact = true
        )

        assertEquals(HomeGridCell(0, 0), result["first"])
        assertEquals(HomeGridCell(1, 0), result["second"])
        assertEquals(HomeGridCell(2, 0), result["third"])
    }

    @Test
    fun freePlacementPreservesRequestedSlotsWhenAvailable() {
        val result = HomeWorkspaceEngine.layout(
            listOf(HomeGridTile("a", 5), HomeGridTile("b", 1)),
            columns = 4,
            rows = 2,
            compact = false
        )

        assertEquals(HomeGridCell(1, 0), result["b"])
        assertEquals(HomeGridCell(1, 1), result["a"])
    }

    @Test
    fun collidingTileMovesToNextAvailableCellWithoutOverlap() {
        val result = HomeWorkspaceEngine.layout(
            listOf(HomeGridTile("a", 0), HomeGridTile("b", 0)),
            columns = 3,
            rows = 2,
            compact = false
        )

        assertEquals(HomeGridCell(0, 0), result["a"])
        assertEquals(HomeGridCell(1, 0), result["b"])
    }

    @Test
    fun multiCellWidgetReservesItsFullFootprint() {
        val result = HomeWorkspaceEngine.layout(
            listOf(
                HomeGridTile("widget", 0, spanX = 2, spanY = 2),
                HomeGridTile("app", 1)
            ),
            columns = 3,
            rows = 2,
            compact = false
        )

        assertEquals(HomeGridCell(0, 0), result["widget"])
        assertEquals(HomeGridCell(2, 0), result["app"])
    }

    @Test
    fun tileThatCannotFitIsNotAssignedAnOverlappingCell() {
        val result = HomeWorkspaceEngine.layout(
            listOf(
                HomeGridTile("large", 0, spanX = 2, spanY = 2),
                HomeGridTile("small", 0)
            ),
            columns = 2,
            rows = 2,
            compact = false
        )

        assertTrue(result.containsKey("large"))
        assertFalse(result.containsKey("small"))
    }

    @Test
    fun canonicalPlanKeepsEveryItemAndReportsOverflowExplicitly() {
        val items = listOf(
            WorkspaceItem("wide", 0, 0, spanX = 2, spanY = 2, kind = WorkspaceItemKind.WIDGET),
            WorkspaceItem("app-a", 0, 4),
            WorkspaceItem("app-b", 0, 5),
            WorkspaceItem("app-c", 0, 6),
            WorkspaceItem("app-d", 0, 7)
        )
        val plan = HomeWorkspaceEngine.plan(items, columns = 2, rows = 2)
        assertEquals(1, plan.placements.size)
        assertEquals(listOf("app-a", "app-b", "app-c", "app-d"), plan.overflow.map { it.id })
        assertEquals(1, plan.pageCount)
    }

    @Test
    fun canonicalPlanReservesWidgetFootprintWithoutOverlap() {
        val plan = HomeWorkspaceEngine.plan(
            listOf(
                WorkspaceItem("widget", 0, 0, 2, 2, WorkspaceItemKind.WIDGET),
                WorkspaceItem("app", 0, 0)
            ),
            columns = 3,
            rows = 2
        )
        val widget = plan.placements.first { it.itemId == "widget" }
        val app = plan.placements.first { it.itemId == "app" }
        assertEquals(0, widget.slot)
        assertEquals(2, app.slot)
    }

    @Test
    fun moveChangesPageAndSlotWithoutChangingStableIdentity() {
        val moved = HomeWorkspaceEngine.move(
            items = listOf(
                WorkspaceItem("a", 0, 0),
                WorkspaceItem("b", 0, 1),
                WorkspaceItem("c", 1, 0)
            ),
            itemId = "b",
            destinationPage = 1,
            destinationSlot = 3,
            columns = 4,
            rows = 4
        )
        val item = moved.items.first { it.id == "b" }
        assertEquals(1, item.page)
        assertEquals(3, item.slot)
        assertEquals(setOf("a", "b", "c"), moved.items.map { it.id }.toSet())
    }

    @Test
    fun compactPagesRemovesGapsAndPreservesPageOrder() {
        val compacted = HomeWorkspaceEngine.compactPages(
            listOf(
                WorkspaceItem("a", 0, 0),
                WorkspaceItem("b", 3, 0),
                WorkspaceItem("c", 7, 0)
            )
        )
        assertEquals(listOf(0, 1, 2), compacted.map { it.page })
    }

    @Test
    fun plannerIsDeterministicForRepeatedRuns() {
        val items = listOf(
            WorkspaceItem("folder", 0, 4, kind = WorkspaceItemKind.FOLDER),
            WorkspaceItem("widget", 0, 0, 2, 1, WorkspaceItemKind.WIDGET),
            WorkspaceItem("app", 0, 1)
        )
        assertEquals(
            HomeWorkspaceEngine.plan(items, 4, 5),
            HomeWorkspaceEngine.plan(items, 4, 5)
        )
    }

}
