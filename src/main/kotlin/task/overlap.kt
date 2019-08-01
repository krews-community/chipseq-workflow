package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher

data class OverlapParams(
        val chrsz: File,
        val blacklist: File,
        val peakType: String ="narrowPeak"
)

data class OverlapInput(
        val peak1: File,
        val peak2: File,
        val pooledpeak: File,
        val fragLen:File,
        val taFile:File,
        val repName: String
)

data class OverlapOutput(
        val overlap_peak: File,
        val bfilt_overlap_peak: File,
        val bfilt_overlap_peak_bb: File,
        val fripQc: File,
        val repName: String
)

fun WorkflowBuilder.OverlapTask(name:String,i: Publisher<OverlapInput>) = this.task<OverlapInput, OverlapOutput>(name, i) {
    val params = taskParams<OverlapParams>()

    dockerImage = "genomealmanac/chipseq-overlap:v1.0.1"

    val prefix = "overlap/${input.repName}"
    //   val fg = readFraglen("${outputsDir}/${input.fragLen.path}")
    output =
            OverlapOutput(
                    overlap_peak = OutputFile("$prefix.overlap.${params.peakType}.gz"),
                    bfilt_overlap_peak = OutputFile("$prefix.overlap.bfilt.${params.peakType}.gz"),
                    bfilt_overlap_peak_bb = OutputFile("$prefix.overlap.bfilt.${params.peakType}.bb"),
                    fripQc = OutputFile("$prefix.overlap.bfilt.frip.qc"),
                    repName = input.repName
            )

    command =
            """
             java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -jar /app/chipseq.jar \
                -peak1 ${input.peak1.dockerPath} \
                 -peak2 ${input.peak2.dockerPath} \
                 -pooledPeak ${input.pooledpeak.dockerPath} \
                 -fraglen ${input.fragLen.dockerPath} \
                 -ta ${input.taFile.dockerPath} \
                 -outputDir ${outputsDir}/overlap \
                -outputPrefix ${input.repName} \
                -peakType ${params.peakType} \
                -chrsz ${params.chrsz.dockerPath} \
                -blacklist ${params.blacklist.dockerPath} \
                -nonamecheck
            """
}