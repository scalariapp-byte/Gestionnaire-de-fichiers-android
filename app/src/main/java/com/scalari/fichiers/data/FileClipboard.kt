package com.scalari.fichiers.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class Clip(val files: List<File>, val cut: Boolean)

object FileClipboard {
    private val _clip = MutableStateFlow<Clip?>(null)
    val clip: StateFlow<Clip?> = _clip.asStateFlow()

    fun set(files: List<File>, cut: Boolean) {
        _clip.value = if (files.isEmpty()) null else Clip(files, cut)
    }

    fun clear() {
        _clip.value = null
    }
}
