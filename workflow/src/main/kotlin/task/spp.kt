package task

import krews.core.*
import krews.file.*

import mu.KotlinLogging
import org.reactivestreams.Publisher

data class SppParams(
        val chrsz: File,
        val blacklist: File,
        //val gensz: String? = "hs",
        val capNumPeak: Int = 300_000
      //  val pvalThresh: Double = 0.01,
     //   val makeSignal: Boolean = true
)

data class SppInput(
        val ta: File,
        val cta: File?,
        val repName: String,
        val pairedEnd: Boolean,
        val fragLen: File
)

data class SppOutput(
        val npeak: File,
        val bfiltNpeak: File,
        val bfiltNpeakBB: File,
     //   val sigPval: File?,
     //   val sigFc: File?,
        val fripQc: File,
        val  repName:String
)

fun WorkflowBuilder.SppTask(name:String,i: Publisher<SppInput>) = this.task<SppInput, SppOutput>(name, i) {
    val params = taskParams<SppParams>()

    dockerImage = "genomealmanac/chipseq-spp:v1.0.7"

    val prefix = "spp/${input.repName}"
    val npPrefix = "$prefix.${capNumPeakFilePrefix(params.capNumPeak)}"

    output =
            SppOutput(
                    npeak = OutputFile("$npPrefix.regionPeak.gz"),
                    bfiltNpeak = OutputFile("$npPrefix.bfilt.regionPeak.gz"),
                    bfiltNpeakBB = OutputFile("$npPrefix.bfilt.regionPeak.bb"),
                    fripQc = OutputFile("$npPrefix.bfilt.frip.qc"),
                    repName = input.repName
            )

    command =
            """
             export TMPDIR="${outputsDir}"
             java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -jar /app/chipseq.jar \
                -ta ${input.ta.dockerPath} \
                 ${if (input.cta != null) "-cta ${input.cta!!.dockerPath}" else ""} \
                 -outputDir ${outputsDir}/spp \
                -outputPrefix ${input.repName} \
                -chrsz ${params.chrsz.dockerPath} \
                 -cap-num-peak ${params.capNumPeak} \
                -fraglen ${input.fragLen.dockerPath} \
                -blacklist ${params.blacklist.dockerPath} \
               -parallelism 2
            """
}

private fun capNumPeakFilePrefix(num:Int):String {
    var number = num
    val units:Array<String> = arrayOf("","K","M","G","T","P")
    for(d in units){
        if(Math.abs(number) < 1000)
        {
            return "${number}${d}"
        }
        number = number /1000
    }
    return "${number}E"
}
