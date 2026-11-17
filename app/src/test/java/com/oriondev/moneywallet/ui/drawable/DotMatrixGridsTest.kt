package com.oriondev.moneywallet.ui.drawable

import org.junit.BeforeClass
import org.junit.Test

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.ArrayList

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/**
 * Reads the shipped asset and the drawable folder straight from the module, so a grid that goes
 * missing or out of shape fails here and not as a classic icon or a crash on a phone.
 */
class DotMatrixGridsTest {

    companion object {

        private lateinit var sGrids: Map<String, Array<String?>>

        @BeforeClass
        @JvmStatic
        @Throws(IOException::class)
        fun readAsset() {
            val asset = File("src/main/assets", DotMatrixDrawable.ASSET)
            Files.newBufferedReader(asset.toPath(), StandardCharsets.UTF_8).use { reader ->
                sGrids = DotMatrixDrawable.parse(reader)
            }
        }
    }

    @Test
    fun everyShippedIconHasAGrid() {
        val drawables = File("src/main/res/drawable").listFiles()
        assertTrue("drawable folder not found", drawables != null && drawables.size > 0)
        val missing: MutableList<String> = ArrayList()
        var icons = 0
        for (file in drawables) {
            val name = file.name
            if (!name.startsWith(DotMatrixDrawable.RESOURCE_PREFIX)) {
                continue
            }
            icons++
            val key = name.substring(DotMatrixDrawable.RESOURCE_PREFIX.length, name.lastIndexOf('.'))
            if (!sGrids.containsKey(key)) {
                missing.add(key)
            }
        }
        assertTrue("no ic_icon_ drawables found", icons > 0)
        assertTrue("no grid for " + missing, missing.isEmpty())
    }

    @Test
    fun everyGridIsThirteenRowsOfThirteenDots() {
        for (entry in sGrids.entries) {
            val rows = entry.value
            assertEquals(entry.key, DotMatrixDrawable.GRID, rows.size)
            for (r in 0 until rows.size) {
                val row = rows[r]
                if (row == null || !row.matches(Regex("[#.]{" + DotMatrixDrawable.GRID + "}"))) {
                    fail(entry.key + " row " + r + " is \"" + row + "\"")
                }
            }
        }
    }
}
