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
    val experiments: List<Experiment>,
    val tasks: List<String> = listOf("mergefastq-ip","mergefastq-ctl","align-ips","align-controls","filter-ips","filter-controls","bam2ta-ips","bam2ta-controls","spr","xcor-ta","bam2ta-ips-nofilt")

)

fun filterInput(v: BamReplicate): BwaOutput =  BwaOutput( v.name, v.pairedEnd, v.bam!!, v.bam, v.bam)
fun bam2taInput(v: FilteredBamReplicate): FilterOutput = FilterOutput( v.name, v.pairedEnd, v.bam!!, v.bam, v.bam, v.bam, v.bam, v.bam )
fun xcorInput(v: TagAlignReplicate): Bam2taOutput = Bam2taOutput(v.ta!!, v.name, v.pairedEnd)
val chipSeqWorkflow = workflow("chipseq-workflow") {

    val params = params<ChipSeqParams>()
    val forceSingleEnd = true 
    val mergeFastqIpInput =  params.experiments.flatMap {
        it.replicates
            .filter { (it is FastqReplicatePE || it is FastqReplicateSE) && (if(it is FastqReplicatePE) (  !((it as FastqReplicatePE).control!!) )  else !(it as FastqReplicateSE).control!!) && (params.tasks.contains("mergefastq-ip")) }
            .map { MergeFastqInput(it) }
        }.toFlux()
    val mergeFastqIpTask = MergeFastqTask("mergefastq-ip",mergeFastqIpInput)

    
    val mergeFastqCtlInput =  params.experiments.flatMap {
        it.replicates
            .filter { (it is FastqReplicatePE || it is FastqReplicateSE) && (if(it is FastqReplicatePE) ((it as FastqReplicatePE).control!!) else ((it as FastqReplicateSE).control!!) ) && (params.tasks.contains("mergefastq-ctl")) }
            .map { MergeFastqInput(it) }
        }.toFlux()
    val mergeFastqCtlTask = MergeFastqTask("mergefastq-ctl",mergeFastqCtlInput)

    val bwaInputsIp = params.experiments.flatMap {
        it.replicates
            .filter { (it is MergedFastqReplicateSE || it is MergedFastqReplicatePE) && (if(it is MergedFastqReplicatePE) !(it as MergedFastqReplicatePE).control!! else !(it as MergedFastqReplicateSE).control!! )  }
            .map { if(it is MergedFastqReplicateSE) MergeFastqOutput(it.name, false, it.merged, null) else MergeFastqOutput(it.name, true, (it as MergedFastqReplicatePE).mergedR1,(it as MergedFastqReplicatePE).mergedR2) }
        }.toFlux()
    val bwaInputIps = mergeFastqIpTask.concatWith(bwaInputsIp).filter { params.tasks.contains("align-ips") }.map { BwaInput(it.mergedFileR1, it.mergedFileR2, it.repName, it.pairedEnd) }   
    val bwaTaskIps = bwaTask("align-ips", bwaInputIps)   


    val bwaInputsCtl = params.experiments.flatMap {
        it.replicates
            .filter { (it is MergedFastqReplicateSE || it is MergedFastqReplicatePE) && (if(it is MergedFastqReplicatePE) (it as MergedFastqReplicatePE).control!! else (it as MergedFastqReplicateSE).control!! )  }
            .map { if(it is MergedFastqReplicateSE) MergeFastqOutput(it.name, false, it.merged, null) else MergeFastqOutput(it.name, true, (it as MergedFastqReplicatePE).mergedR1,(it as MergedFastqReplicatePE).mergedR2) }
        }.toFlux()
    val bwaInputCtls = mergeFastqCtlTask.concatWith(bwaInputsCtl).filter { params.tasks.contains("align-controls") }.map { BwaInput(it.mergedFileR1, it.mergedFileR2, it.repName, it.pairedEnd) }   
    val bwaTaskControl = bwaTask("align-controls", bwaInputCtls)   
      
    val filterBamInputIP = params.experiments.flatMap {
        it.replicates
            .filter { it is BamReplicate && it.bam !== null && !(it.control!!) }
            .map { filterInput(it as BamReplicate) }
        }.toFlux()
    val filterInputIP = bwaTaskIps.concatWith(filterBamInputIP).filter { params.tasks.contains("filter-ips") }.map { FilterInput(it.bam, it.repName, it.pairedEnd) }
    val filterTaskIps = filterTask("filter-ips", filterInputIP)

    val filterBamInputCtl = params.experiments.flatMap {
        it.replicates
            .filter { it is BamReplicate && it.bam !== null && it.control!! }
            .map { filterInput(it as BamReplicate) }
        }.toFlux()
    val filterInputCtl = bwaTaskControl.concatWith(filterBamInputCtl).filter { params.tasks.contains("filter-controls") }.map { FilterInput(it.bam, it.repName, it.pairedEnd) }
    val filterTaskControls = filterTask("filter-controls", filterInputCtl)   

   val bam2taTaskInputIP = params.experiments.flatMap {
        it.replicates
            .filter { it is FilteredBamReplicate && it.bam !== null && !(it.control!!)  }
            .map { bam2taInput(it as FilteredBamReplicate) }
        }.toFlux()    
    val bam2taInputIP = bam2taTaskInputIP.concatWith(filterTaskIps).filter { params.tasks.contains("bam2ta-ips") }.map { Bam2taInput(it.bam, it.repName,  it.pairedEnd ) }
    val bam2taTaskIP = bam2taTask("bam2ta-ips",bam2taInputIP)

    val bam2taTaskInputCtl = params.experiments.flatMap {
        it.replicates
            .filter { it is FilteredBamReplicate && it.bam !== null && it.control!!  }
            .map { bam2taInput(it as FilteredBamReplicate) }
        }.toFlux()                
    val bam2taInputCtl = bam2taTaskInputCtl.concatWith(filterTaskControls).filter { params.tasks.contains("bam2ta-controls") }.map { Bam2taInput(it.bam, it.repName,  it.pairedEnd ) }
    val bam2taTaskCtl = bam2taTask("bam2ta-controls",bam2taInputCtl)
        
    val bam2taNoFiltInputIP = params.experiments.flatMap {
        it.replicates
            .filter { it is BamReplicate && it.bam !== null && !it.control!! }
            .map { filterInput(it as BamReplicate) }
        }.toFlux()
    
    val bam2taNoFiltInput = bam2taNoFiltInputIP.concatWith(bwaTaskIps).filter { params.tasks.contains("bam2ta-ips-nofilt") }.map { Bam2taInput(it.bam,  it.repName+"_nofilt",   if(forceSingleEnd) false else it.pairedEnd ) }
    val bam2tanofiltTask = bam2taTask("bam2ta-ips-nofilt",bam2taNoFiltInput)
    
    val xcorTaskInput = params.experiments.flatMap {
        it.replicates
            .filter { it is TagAlignReplicate && it.ta !== null && !it.control!! }
            .map { xcorInput(it as TagAlignReplicate) }
        }.toFlux()        

    val xcorInput = xcorTaskInput.concatWith(bam2tanofiltTask).filter { params.tasks.contains("xcor-ta") }.map { XcorInput(it.ta, it.repName, it.pairedEnd)}
    val xcorTask = XcorTask("xcor-ta",xcorInput)

    
    val sprTaskInput = params.experiments.flatMap {
        it.replicates
            .filter { it is TagAlignReplicate && it.ta !== null && !it.control!! }
            .map { xcorInput(it as TagAlignReplicate) }
        }.toFlux()        

    val sprInput =  sprTaskInput.concatWith(bam2taTaskIP).filter { params.tasks.contains("spr") }.map { sprInput(it.ta,it.repName,it.pairedEnd)}
    val sprTask = sprTask("spr",sprInput)

    
    val pooledCtlTaInput = bam2taTaskCtl.buffer().map { ctlbam2taOut ->  PoolTaInput(ctlbam2taOut.map { it.ta }, "pooled_ta_ctl",ctlbam2taOut.first().pairedEnd)}
    val pooledCtlTaTask = pooledtaTask("pooled-ta-control",pooledCtlTaInput)

    val macs2Input = bam2taTaskIP.map { bit ->  Macs2Input(bit.ta,
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

    val overlap_pr_Input = bam2taTaskIP.map { bit -> OverlapInput(macs2pr1Task.buffer().map { xit -> xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.npeak,
            macs2pr2Task.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.npeak,
            macs2Task.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.npeak,
            xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen,bit.ta,
            "${bit.repName}_psr") }
    val overlap_pr_Task = OverlapTask("overlap-pr",overlap_pr_Input)
}
