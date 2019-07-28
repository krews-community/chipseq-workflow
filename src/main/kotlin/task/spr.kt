package task

import krews.core.WorkflowBuilder
import krews.file.File
import krews.file.OutputFile
import org.reactivestreams.Publisher


data class sprInput(
        val taFile: File,
        val repName: String,
        val pairedEnd: Boolean
)

data class sprOutput(
        val repName: String,
        val psr1: File,
        val psr2: File,
        val pairedEnd: Boolean
)


fun WorkflowBuilder.sprTask(tag: String, i: Publisher<sprInput>) = this.task<sprInput, sprOutput>(tag, i) {

    dockerImage = "genomealmanac/chipseq-spr:v1.0.0"


    val prefix =  "spr/${input.repName}"

    output =
            task.sprOutput(
                    repName = input.repName,
                    psr1 = OutputFile("$prefix.pr1.tagAlign.gz"),
                    psr2 = OutputFile("$prefix.pr2.tagAlign.gz"),
                    pairedEnd = input.pairedEnd
            )

    command =
            """
               java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -jar /app/chipseq.jar \
               -taFile ${input.taFile.dockerPath} \
               -outputDir ${outputsDir}/spr \
               -outputPrefix ${input.repName} \
               ${if (input.pairedEnd) "-pairedEnd" else ""}
              """

}

