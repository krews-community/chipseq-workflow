
package model

import krews.file.File

data class BamReplicate (override val name: String, val bam: File? = null, val pairedEnd: Boolean, val control: Boolean? =false ) : Replicate

data class FilteredBamReplicate (override val name: String, val bam: File? = null, val pairedEnd: Boolean, val control: Boolean? = false) : Replicate

