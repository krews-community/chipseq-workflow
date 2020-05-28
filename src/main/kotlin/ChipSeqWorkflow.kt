import krews.core.*
import krews.run
import model.MergedFastqReplicatePE
import model.*
import reactor.core.publisher.toFlux
import task.*
import mu.KotlinLogging
private val log = KotlinLogging.logger {}
fun main(args: Array<String>) = run(chipSeqWorkflow, args)

data class ChipSeqParams(
        val replicatesIP: FastqSamples,
        val replicatesCTRL: FastqSamples
)

val chipSeqWorkflow = workflow("chipseq-workflow") {

    val params = params<ChipSeqParams>()

    val pooledCtl = mutableListOf<String>()
    params.replicatesCTRL.replicates.forEach { it->
        pooledCtl.add(it.name)
    }
    val pooledIp = mutableListOf<String>()
    params.replicatesIP.replicates.forEach { it->
        pooledIp.add(it.name)
    }

    val mergeFastqIpInput = params.replicatesIP.replicates.map {MergeFastqInput(it)}.toFlux()
    val mergeFastqIpTask = MergeFastqTask("mergeFastq-rep-ip",mergeFastqIpInput)

    val mergeFastqCtlInput = params.replicatesCTRL.replicates.map {MergeFastqInput(it)}.toFlux()
    val mergeFastqCtlTask = MergeFastqTask("mergeFastq-rep-ctl",mergeFastqCtlInput)


    val bwaInputIps = mergeFastqIpTask
          .map { BwaInput(it.mergedFileR1,it.mergedFileR2,it.repName,it.pairedEnd) }
            .toFlux()
    val bwaTaskIps = bwaTask("align-ips", bwaInputIps)

    val bwaInputControls = mergeFastqCtlTask
    .map { BwaInput(it.mergedFileR1,it.mergedFileR2,it.repName,it.pairedEnd) }
    .toFlux()
    val bwaTaskControl = bwaTask("align-controls", bwaInputControls)

    val filterInputIps = bwaTaskIps
            .map { FilterInput(it.bam, it.repName, it.pairedEnd) }
    val filterTaskIps = filterTask("filter-ips", filterInputIps)
   val filterInputControl = bwaTaskControl
            .map { FilterInput(it.bam, it.repName, it.pairedEnd) }
    val filterTaskControls = filterTask("filter-controls", filterInputControl)

    val bam2taInput = filterTaskIps
            .map { Bam2taInput(it.bam, it.repName,  it.pairedEnd ) }
    val bam2taTask = bam2taTask("bam2ta-ips",bam2taInput)

    val controlbam2taInput = filterTaskControls
            .map { Bam2taInput(it.bam, it.repName, it.pairedEnd ) }
    val controlbam2taTask = bam2taTask("bam2ta-controls",controlbam2taInput)

    val bam2taNoFiltInput = bwaTaskIps
            .map { Bam2taInput(it.bam,  it.repName+"_nofilt",  false ) }
    val bam2tanofiltTask = bam2taTask("bam2ta-ips-nofilt",bam2taNoFiltInput)

    val xcorInput = bam2tanofiltTask.map { XcorInput(it.ta, it.repName, it.pairedEnd)}
    val xcorTask = XcorTask("xcor-ta",xcorInput)

    
    val pooledTaInput = bam2taTask.buffer().map { bam2taOut ->  PoolTaInput(bam2taOut.map { it.ta }, "${pooledIp.joinToString("_" )}_pooled_ta_ips",bam2taOut.first().pairedEnd)}
    val pooledTaTask = pooledtaTask("pooled-ta-ips",pooledTaInput)

    val pooledCtlTaInput = controlbam2taTask.buffer().map { ctlbam2taOut ->  PoolTaInput(ctlbam2taOut.map { it.ta }, "${pooledCtl.joinToString("_" )}_pooled_ta_ips",ctlbam2taOut.first().pairedEnd)}
    val pooledCtlTaTask = pooledtaTask("pooled-ta-control",pooledCtlTaInput)

    val sprInput =  bam2taTask.map { sprInput(it.ta,it.repName,it.pairedEnd)}
    val sprTask = sprTask("spr",sprInput)
   
    val macs2Input = bam2taTask.map { bit ->  Macs2Input(bit.ta,
        pooledCtlTaTask.buffer().toIterable().flatten().first().pooledTa ,
        bit.repName,
        bit.pairedEnd,
         xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen)
        }
    val macs2Task = macs2Task("macs2",macs2Input)

    val macs2pr1Input = sprTask.map { bit ->  Macs2Input(bit.psr1,
            pooledCtlTaTask.buffer().toIterable().flatten().first().pooledTa,
            bit.repName+"_pr1",
            bit.pairedEnd,
            xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen)
    }
    val macs2pr1Task = macs2Task("macs2-pr1",macs2pr1Input)

    val macs2pr2Input = sprTask.map { bit ->  Macs2Input(bit.psr2,
            pooledCtlTaTask.buffer().toIterable().flatten().first().pooledTa,
            bit.repName+"_pr2",
            bit.pairedEnd,
            xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen)
    }
    val macs2pr2Task = macs2Task("macs2-pr2",macs2pr2Input)

    
    val overlap_pr_Input = bam2taTask.map { bit -> OverlapInput(macs2pr1Task.buffer().map { xit -> xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.npeak,
            macs2pr2Task.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.npeak,
            macs2Task.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.npeak,
            xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen,bit.ta,
            "${bit.repName}_psr") }
    val overlap_pr_Task = OverlapTask("overlap-pr",overlap_pr_Input)
}
