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
}
