package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher

data class Macs2Params(
        val chrsz: File,
        val blacklist: File,
        val gensz: String? = "hs",
        val capNumPeak: Int = 500_000,
        val pvalThresh: Double = 0.01,
        val makeSignal: Boolean = true
)

data class Macs2Input(
        val ta: File,
        val cta: File?,
        val repName: String,
        val pairedEnd: Boolean,
        val fragLen: File
)

data class Macs2Output(
        val npeak: File,
        val bfiltNpeak: File,
        val bfiltNpeakBB: File,
        val sigPval: File?,
        val sigFc: File?,
        val fripQc: File,
        val  repName:String
)

fun WorkflowBuilder.macs2Task(name:String,i: Publisher<Macs2Input>) = this.task<Macs2Input, Macs2Output>(name, i) {
    val params = taskParams<Macs2Params>()

   // dockerImage = "genomealmanac/atacseq-macs2:1.0.4"
    dockerImage = "genomealmanac/chipseq-macs2:v1.0.6"

    val prefix = "macs2/${input.repName}"
    val npPrefix = "$prefix.pval${params.pvalThresh}.${capNumPeakFilePrefix(params.capNumPeak)}"
 //   val fg = readFraglen("${outputsDir}/${input.fragLen.path}")
    output =
            Macs2Output(
                    npeak = OutputFile("$npPrefix.narrowPeak.gz"),
                    bfiltNpeak = OutputFile("$npPrefix.bfilt.narrowPeak.gz"),
                    bfiltNpeakBB = OutputFile("$npPrefix.bfilt.narrowPeak.bb"),
                    fripQc = OutputFile("$npPrefix.bfilt.frip.qc"),
                    sigPval = if (params.makeSignal) OutputFile("$prefix.pval.signal.bigWig") else null,
                    sigFc = if (params.makeSignal) OutputFile("$prefix.fc.signal.bigWig") else null,
                    repName = input.repName
            )

    command =
            """
             java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -jar /app/chipseq.jar \
                -ta ${input.ta.dockerPath} \
                 ${if (input.cta != null) "-cta ${input.cta!!.dockerPath}" else ""} \
                 -outputDir ${outputsDir}/macs2 \
                -outputPrefix ${input.repName} \
                ${if (params.gensz != null) "-gensz ${params.gensz}" else ""} \
                -chrsz ${params.chrsz.dockerPath} \
                -pvalthresh ${params.pvalThresh} \
                 -cap-num-peak ${params.capNumPeak} \
                -fraglen ${input.fragLen.dockerPath} \
                -blacklist ${params.blacklist.dockerPath} \
                ${if (params.makeSignal) "-make-signal" else ""} \
                ${if (input.pairedEnd) "-pairedEnd" else ""}
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
