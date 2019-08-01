package task

import krews.core.WorkflowBuilder
import krews.core.*
import krews.file.File
import krews.file.OutputFile
import model.MergedFastqReplicate
import model.MergedFastqReplicatePE
import model.MergedFastqReplicateSE
import org.reactivestreams.Publisher


data class BwaParams(
        val idxTar: File,
        val multimapping: Int? = 4,
        val scoreMin: String? = null
)

data class BwaInput(
        val mergedRep: MergedFastqReplicate
)

data class BwaOutput(
        val repName: String,
        val pairedEnd: Boolean,
        val bam: File,
        val bai: File,
        //val alignLog: File, missing from bwa
        val flagstatQC: File
        //val readLenLog: File
)

fun WorkflowBuilder.bwaTask(name: String, i: Publisher<BwaInput>) = this.task<BwaInput, BwaOutput>(name, i) {
    val params = taskParams<BwaParams>()

    dockerImage = "genomealmanac/chipseq-bwa:v1.0.1"

    val prefix = "bwa/${input.mergedRep.name}"
    output =
            BwaOutput(
                    repName = input.mergedRep.name,
                    pairedEnd = input.mergedRep is MergedFastqReplicatePE,
                    bam = OutputFile("$prefix.bam"),
                    bai = OutputFile("$prefix.bam.bai"),
                    flagstatQC = OutputFile("$prefix.flagstat.qc")
            )

    val mergedRep = input.mergedRep
    command =
            """
          java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -jar /app/chipseq.jar \
                -indexFile ${params.idxTar.dockerPath} \
                -outputDir ${outputsDir}/bwa \
                -name ${mergedRep.name} \
                -parallelism 64 \
                ${if (mergedRep is MergedFastqReplicateSE) "-repFile1  ${mergedRep.merged.dockerPath}" else ""} \
                 ${if (mergedRep is MergedFastqReplicatePE) "-repFile1  ${mergedRep.mergedR1.dockerPath}" else ""} \
                   ${if (mergedRep is MergedFastqReplicatePE) "-repFile2  ${mergedRep.mergedR2.dockerPath}" else ""} \
                     ${if (mergedRep is MergedFastqReplicatePE) "-pairedEnd" else ""}

            """
}