package model

import krews.file.File

data class TagAlignReplicate (override val name: String, val ta: File? = null, val pairedEnd: Boolean, val control: Boolean? = false, val fragLen: File? = null ) : Replicate

