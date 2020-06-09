package task

import krews.core.WorkflowBuilder
import krews.file.File
import krews.file.OutputFile
import org.reactivestreams.Publisher

data class XcorParams(
        val chipSeqType: String = "histone",
        val exclusion_range_min: Int = -500,
        val xcor_subsample_reads: Int = 15000000
)

data class XcorInput(
        val ta: File,
        val repName: String,
        val pairedEnd: Boolean
)

data class XcorOutput(
        val Fraglen: File,
        val repName: String,
        val pairedEnd: Boolean
)

fun WorkflowBuilder.XcorTask(name: String,i: Publisher<XcorInput>) = this.task<XcorInput, XcorOutput>(name, i) {
    val params = taskParams<XcorParams>()

    dockerImage = "genomealmanac/chipseq-xcor:v1.0.0"
    output =
            XcorOutput(
                    Fraglen = OutputFile("xcor/${input.repName}.cc.fraglen.txt"),
                    repName = input.repName,
                    pairedEnd = input.pairedEnd
            )

    command =
            """
            export TMPDIR="${outputsDir}/xcor"
            java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -jar /app/chipseq.jar \
               -ta ${input.ta.dockerPath} \
               -outputDir ${outputsDir}/xcor \
                -outputPrefix ${input.repName} \
                -chip-seq-type ${params.chipSeqType} \
                -exclusion-range-min ${params.exclusion_range_min} \
                -subsample ${params.xcor_subsample_reads} \
                ${if (input.pairedEnd) "-pairedEnd" else ""} \
                -parallelism 12
            """
}