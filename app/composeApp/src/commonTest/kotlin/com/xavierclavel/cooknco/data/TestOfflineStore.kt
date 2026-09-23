package com.xavierclavel.cooknco.data

import okio.FileSystem
import okio.Path
import kotlin.random.Random

/** A throwaway offline store, one directory per test. Delete it with [deleteTestStore]. */
fun testOfflineStore(): Pair<OfflineStore, Path> {
    val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "cooknco-offline-${Random.nextLong()}"
    return OfflineStore(root) to root
}

fun deleteTestStore(root: Path) {
    FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
}
