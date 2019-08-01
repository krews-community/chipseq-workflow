package task

import krews.core.WorkflowBuilder
import krews.file.File
import krews.file.OutputFile
import org.reactivestreams.Publisher

data class roundedmeanInput(
        val files: List<File>,
        val repName: String
)

data class roundedmeanOutput(
        val Fraglen: File,
        val repName: String
)

fun WorkflowBuilder.roundedmeanTask(name: String,i: Publisher<roundedmeanInput>) = this.task<roundedmeanInput, roundedmeanOutput>(name, i) {
    val params = taskParams<XcorParams>()

    dockerImage = "genomealmanac/chipseq-roundedmean:v1.0.0"
    output =
            roundedmeanOutput(
                    Fraglen = OutputFile("roundedmean/${input.repName}.txt"),
                    repName = input.repName
            )

    command =
            """
            java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -jar /app/chipseq.jar \
                 ${input.files.joinToString(" ") { it -> " -Files  ${it.dockerPath}" }} \
               -outputDir ${outputsDir}/roundedmean \
                -outputPrefix ${input.repName}
            """
}