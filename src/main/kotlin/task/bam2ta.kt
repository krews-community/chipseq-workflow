package task

import krews.core.WorkflowBuilder
import krews.file.File
import krews.file.OutputFile
import org.reactivestreams.Publisher

data class Bam2taParams(
        val disableTn5Shift: Boolean = true,
        val regexGrepVTA: String = "chrM",
        val subsample: Int = 0
)

data class Bam2taInput(
        val bam: File,
        val repName: String,
        val pairedEnd: Boolean
)

data class Bam2taOutput(
        val ta: File,
        val repName: String,
        val pairedEnd: Boolean
)

fun WorkflowBuilder.bam2taTask(name: String,i: Publisher<Bam2taInput>) = this.task<Bam2taInput, Bam2taOutput>(name, i) {
    val params = taskParams<Bam2taParams>()

    dockerImage = "genomealmanac/chipseq-bam2ta:v1.0.0"
    output =
            Bam2taOutput(
                    ta = OutputFile("bam2ta/${input.repName}.tagAlign.gz"),
                    repName = input.repName,
                    pairedEnd = input.pairedEnd
            )

    command =
            """
            java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -jar /app/chipseq.jar \
               -bam ${input.bam.dockerPath} \
               -outputDir ${outputsDir}/bam2ta \
                -outputPrefix ${input.repName} \
                ${if (input.pairedEnd) "-pairedEnd" else ""} \
                -disable-tn5-shift \
                -parallelism 16
            """
}