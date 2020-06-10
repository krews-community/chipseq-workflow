package task

import krews.core.*
import krews.file.*
import org.reactivestreams.Publisher

data class IdrParams(
        val chrsz: File,
        val blacklist: File,
        val idrThresh: Double=0.05,
        val peakType: String ="narrowPeak",
        val idrRank: String="p.value"
)

data class IdrInput(
        val peak1: File,
        val peak2: File,
        val pooledpeak: File,
        val fragLen:File,
        val taFile:File,
        val repName: String
)

data class IdrOutput(
        val idr_peak: File,
        val bfilt_idr_peak: File,
        val bfilt_idr_peak_bb: File,
        val idr_unthresholded_peak: File,
        val idr_log: File,
        val fripQc: File,
        val repName: String
)

fun WorkflowBuilder.IdrTask(name:String,i: Publisher<IdrInput>) = this.task<IdrInput, IdrOutput>(name, i) {
    val params = taskParams<IdrParams>()

    dockerImage = "genomealmanac/chipseq-idr:v1.0.5"

    val prefix = "idr/${input.repName}"
    val npPrefix = "$prefix.idr${params.idrThresh}"
    //   val fg = readFraglen("${outputsDir}/${input.fragLen.path}")
    output =
            IdrOutput(
                    idr_peak = OutputFile("$npPrefix.${params.peakType}.gz"),
                    bfilt_idr_peak = OutputFile("$npPrefix.bfilt.${params.peakType}.gz"),
                    bfilt_idr_peak_bb = OutputFile("$npPrefix.bfilt.${params.peakType}.bb"),
                    fripQc = OutputFile("$npPrefix.bfilt.frip.qc"),
                    idr_unthresholded_peak = OutputFile("$npPrefix.unthresholded-peaks.txt.gz"),
                    idr_log =  OutputFile("$npPrefix.log"),
                    repName = input.repName
            )

    command =
            """
             java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -jar /app/chipseq.jar \
                -peak1 ${input.peak1.dockerPath} \
                -peak2 ${input.peak2.dockerPath} \
                -pooledPeak ${input.pooledpeak.dockerPath} \
                -fraglen ${input.fragLen.dockerPath} \
                -idrRank ${params.idrRank} \
                -idrthresh ${params.idrThresh} \
                -ta ${input.taFile.dockerPath} \
                -outputDir ${outputsDir}/idr \
                -outputPrefix ${input.repName} \
                -peakType ${params.peakType} \
                -chrsz ${params.chrsz.dockerPath} \
                -blacklist ${params.blacklist.dockerPath}
            """
}