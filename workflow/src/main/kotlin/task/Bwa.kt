package task

import krews.core.WorkflowBuilder
import krews.core.*
import krews.file.File
import krews.file.OutputFile
import model.*
import org.reactivestreams.Publisher


data class BwaParams(
        val idxTar: File,
        val bwaIndexprefix:String?="hg19_with_sponges.fa",
        val multimapping: Int? = 4,
        val scoreMin: String? = null
)

data class BwaInput(
        val repFile1: File,
        val repFile2: File? = null,
        val repName: String,
        val pairedEnd: Boolean
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

    dockerImage = "genomealmanac/chipseq-bwa:v1.0.2"

    val prefix = "bwa/${input.repName}"
    output =
            BwaOutput(
                    repName = input.repName,
                    pairedEnd = input.pairedEnd,
                    bam = OutputFile("$prefix.bam"),
                    bai = OutputFile("$prefix.bam.bai"),
                    flagstatQC = OutputFile("$prefix.flagstat.qc")
            )

   // val mergedRep = input.mergedRep
    command =
    """
    idxFile="${params.idxTar.dockerPath}"
    if [ `echo "${params.idxTar.dockerPath}" | awk -F"." '{ print ${"$"}NF }'` = "gz" ]
    then
      cp ${params.idxTar.dockerPath} $outputsDir/idxTar.tar.gz
      gunzip $outputsDir/idxTar.tar.gz
      idxFile="$outputsDir/idxTar.tar"
    fi
    java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -jar /app/chipseq.jar \
        -indexFile ${"$"}idxFile \
        -outputDir ${outputsDir}/bwa \
        -name ${input.repName} \
        -parallelism 64 \
        ${if (!input.pairedEnd)"-repFile1  ${input.repFile1.dockerPath}" else ""} \
        ${if (input.pairedEnd)"-repFile1  ${input.repFile1.dockerPath}" else ""} \
        ${if (input.pairedEnd) "-repFile2  ${input.repFile2!!.dockerPath}" else ""} \
        ${if (input.pairedEnd) "-pairedEnd" else ""}
    """
}