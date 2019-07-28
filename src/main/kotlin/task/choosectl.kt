package task

import krews.core.WorkflowBuilder
import krews.file.File
import krews.file.OutputFile
import org.reactivestreams.Publisher


data class ChooseCtlParams(
        val ctlDepthRatio: Double = 1.2,
        val alwaysUsePooledCtl: Boolean = false
)
data class ctls(
        val repName :String,
        val ctlFile : File
)
data class ChooseCtlInput(
        val taFiles: List<File>,
        val ctaFiles: List<File>,
        val ctaPooledFile: File,
        val repNames: List<String>
)

data class ChooseCtlOutput(
        val ctls: List<ctls>
)


fun WorkflowBuilder.choosectlTask(tag: String, i: Publisher<ChooseCtlInput>) = this.task<ChooseCtlInput, ChooseCtlOutput>(tag, i) {

    // dockerImage = "genomealmanac/psychencode-chipseq-pseudoreps:0.0.1"
    dockerImage = "genomealmanac/chipseq-choose-ctl:v1.0.1"
    val params = taskParams<ChooseCtlParams>()
    val oFiles = mutableListOf<ctls>()

    input.repNames.forEach {
        oFiles.add(ctls(it,OutputFile("choosectl/${it}.tagAlign.gz")))
    }

    output =
            ChooseCtlOutput(
                    ctls = oFiles
            )

    command =
            """
               java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1  -jar /app/chipseq.jar \
              ${input.taFiles.joinToString(" ") { it -> " -taFiles  ${it.dockerPath}" }} \
              ${input.ctaFiles.joinToString(" ") { it -> " -ctaFiles  ${it.dockerPath}" }} \
              -ctlpooledta ${input.ctaPooledFile.dockerPath}  \
               ${input.repNames.joinToString(" ") { it -> " -outputPrefix  ${it}" }} \
               -outputDir ${outputsDir}/choosectl \
                 ${if (params.alwaysUsePooledCtl) " -always-use-pooled-ctl" else ""} \
               -ctl-depth-ratio ${params.ctlDepthRatio}
              """

}

