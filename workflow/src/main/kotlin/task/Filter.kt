package task

import krews.core.WorkflowBuilder
import krews.file.File
import krews.file.LocalInputFile
import krews.file.OutputFile
import org.reactivestreams.Publisher


data class FilterParams(
        val sponge: File?,
        val multimapping: Int = 0,
        val mapqThresh: Int = 20,
        val dupMarker: String = "PICARD",
        val noDupRemoval: Boolean = false
)

data class FilterInput(
        val bam: File,
        val repName: String,
        val pairedEnd: Boolean
)

data class FilterOutput(
        val repName: String,
        val pairedEnd: Boolean,
        val bam: File,
        val bai: File,
      /*  val bed: File?,
        val tagAlign: File?,
        val tagAlignSubsample: File?,
        val ccScore: File?,
        val ccPlot: File?,*/
        val flagstateQC: File,
        val dupQC: File?,
        val pbcQC: File?,
        val mitoDupLog: File?
)


fun WorkflowBuilder.filterTask(name: String,  i: Publisher<FilterInput>) = this.task<FilterInput, FilterOutput>(name, i) {
    val params = taskParams<FilterParams>()

    dockerImage = "genomealmanac/chipseq-filter:v1.0.9"

    val prefix = "filter/${input.repName}"
  //  val prefixBed = "bed/${input.repName}"
  //  val prefixQc = "qc/${input.repName}"
    val noDupRemoval = params.noDupRemoval
    output =
            FilterOutput(
                    repName = input.repName,
                    pairedEnd = input.pairedEnd,
                    bam = if (noDupRemoval) OutputFile("$prefix.filt.bam") else OutputFile("$prefix.nodup.bam"),
                    bai = if (noDupRemoval) OutputFile("$prefix.filt.bam.bai") else OutputFile("$prefix.nodup.bam.bai"),
                 //   bed = OutputFile("$prefixBed.bed.gz"),
                //    tagAlign = OutputFile("$prefixQc.SE.tagAlign.gz"),
                //    tagAlignSubsample = OutputFile("$prefixQc.SE.sample.tagAlign.gz"),
                 //   ccScore = OutputFile("$prefixQc.SE.sample.tagAlign.cc.qc"),
                //    ccPlot = OutputFile("$prefixQc.SE.sample.tagAlign.cc.plot.pdf"),
                    flagstateQC = if (noDupRemoval) OutputFile("$prefix.filt.flagstat.qc") else OutputFile("$prefix.nodup.flagstat.qc"),
                    dupQC = if (noDupRemoval) null else OutputFile("$prefix.dup.qc"),
                    pbcQC = if (noDupRemoval) null else OutputFile("$prefix.pbc.qc"),
                    mitoDupLog = if (noDupRemoval) null else OutputFile("$prefix.mito_dup.txt")
            )


    command =
            """
          java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1  -jar /app/chipseq.jar \
                -bam ${input.bam.dockerPath} \
                -outputDir ${outputsDir}/filter \
                -outputPrefix  ${input.repName} \
                -parallelism 32 \
                -mapqThresh ${params.mapqThresh} \
                ${if (params.sponge != null) "-sponge ${params.sponge.dockerPath} " else ""} \
                ${if (input.pairedEnd) "-pairedEnd" else ""}
            """

}

