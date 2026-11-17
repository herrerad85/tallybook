package com.oriondev.moneywallet.ui.drawable;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Reads the shipped asset and the drawable folder straight from the module, so a grid that goes
 * missing or out of shape fails here and not as a classic icon or a crash on a phone.
 */
public class DotMatrixGridsTest {

    private static Map<String, String[]> sGrids;

    @BeforeClass
    public static void readAsset() throws IOException {
        File asset = new File("src/main/assets", DotMatrixDrawable.ASSET);
        try (BufferedReader reader = Files.newBufferedReader(asset.toPath(), StandardCharsets.UTF_8)) {
            sGrids = DotMatrixDrawable.parse(reader);
        }
    }

    @Test
    public void everyShippedIconHasAGrid() {
        File[] drawables = new File("src/main/res/drawable").listFiles();
        assertTrue("drawable folder not found", drawables != null && drawables.length > 0);
        List<String> missing = new ArrayList<>();
        int icons = 0;
        for (File file : drawables) {
            String name = file.getName();
            if (!name.startsWith(DotMatrixDrawable.RESOURCE_PREFIX)) {
                continue;
            }
            icons++;
            String key = name.substring(DotMatrixDrawable.RESOURCE_PREFIX.length(), name.lastIndexOf('.'));
            if (!sGrids.containsKey(key)) {
                missing.add(key);
            }
        }
        assertTrue("no ic_icon_ drawables found", icons > 0);
        assertTrue("no grid for " + missing, missing.isEmpty());
    }

    @Test
    public void everyGridIsThirteenRowsOfThirteenDots() {
        for (Map.Entry<String, String[]> entry : sGrids.entrySet()) {
            String[] rows = entry.getValue();
            assertEquals(entry.getKey(), DotMatrixDrawable.GRID, rows.length);
            for (int r = 0; r < rows.length; r++) {
                String row = rows[r];
                if (row == null || !row.matches("[#.]{" + DotMatrixDrawable.GRID + "}")) {
                    fail(entry.getKey() + " row " + r + " is \"" + row + "\"");
                }
            }
        }
    }
}
