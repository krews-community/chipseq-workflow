package task

import krews.core.WorkflowBuilder
import krews.core.*
import krews.file.File
import krews.file.OutputFile
import model.*
import org.reactivestreams.Publisher
import model.*



data class MergeFastqInput(
    val mergedRep: Replicate
)
data class MergeFastqOutput(
        val repName: String,
        val pairedEnd: Boolean,
        val mergedFileR1: File,
        val mergedFileR2: File?
)

fun WorkflowBuilder.MergeFastqTask(name: String, i: Publisher<MergeFastqInput>) = this.task<MergeFastqInput, MergeFastqOutput>(name, i) {

    dockerImage =  "alpine:latest"

    output =
            MergeFastqOutput(
                    repName = input.mergedRep.name,
                    pairedEnd = input.mergedRep is FastqReplicatePE,
                    mergedFileR1 = OutputFile("${input.mergedRep.name}.merged.r1.fastq.gz"),
                    mergedFileR2 = if(input.mergedRep is FastqReplicatePE) OutputFile("${input.mergedRep.name}.merged.r2.fastq.gz") else null
            )

    val mergedRep = input.mergedRep
    command = if (mergedRep is FastqReplicateSE) """
    zcat ${ mergedRep.fastqs.joinToString(" ") { it.dockerPath } } | gzip > ${outputsDir}/${input.mergedRep.name}.merged.r1.fastq.gz
""" else if (mergedRep is FastqReplicatePE) """
    zcat ${mergedRep.fastqsR1.joinToString(" ") { it.dockerPath } } | gzip > ${outputsDir}/${input.mergedRep.name}.merged.r1.fastq.gz && \
    zcat ${mergedRep.fastqsR2.joinToString(" ") { it.dockerPath} } | gzip > ${outputsDir}/${input.mergedRep.name}.merged.r2.fastq.gz
""" else ""

}
