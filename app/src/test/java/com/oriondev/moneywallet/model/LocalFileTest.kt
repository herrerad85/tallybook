package com.oriondev.moneywallet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalFileTest {

    @Test
    fun aDotfileNameIsItsOwnExtension() {
        assertEquals(".nomedia", LocalFile("/storage/emulated/0/MoneyWallet/.nomedia").getExtension())
    }

    @Test
    fun aBackupFileHasItsMwbxExtension() {
        assertEquals(".mwbx", LocalFile("/storage/emulated/0/MoneyWallet/backup.mwbx").getExtension())
    }

    @Test
    fun aNameWithSeveralDotsKeepsOnlyTheLastExtension() {
        assertEquals(".gz", LocalFile("/sdcard/archive.tar.gz").getExtension())
    }

    @Test
    fun aNameWithoutADotHasNoExtension() {
        assertNull(LocalFile("/storage/emulated/0/MoneyWallet/README").getExtension())
    }
}
