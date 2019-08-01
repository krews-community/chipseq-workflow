package task

import krews.core.WorkflowBuilder
import krews.file.File
import krews.file.OutputFile
import org.reactivestreams.Publisher


data class PoolTaInput(
        val taFiles: List<File>,
        val repName: String
)

data class PoolTaOutput(
        val repName: String,
        val pooledTa: File
)


fun WorkflowBuilder.pooledtaTask(tag: String, i: Publisher<PoolTaInput>) = this.task<PoolTaInput, PoolTaOutput>(tag, i) {

    dockerImage = "genomealmanac/chipseq-pooledta:v1.0.0"

    output =
            PoolTaOutput(
                    repName = input.repName,
                    pooledTa = OutputFile("pooledta/${input.repName}.pooled.tagAlign.gz")
            )

     command =
              """
               java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1  -jar /app/chipseq.jar \
              ${input.taFiles.joinToString(" ") { it -> " -taFiles  ${it.dockerPath}" }} \
               -outputDir ${outputsDir}/pooledta \
               -outputPrefix ${input.repName}
              """

}

