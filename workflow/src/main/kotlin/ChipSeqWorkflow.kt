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
    val tasks: List<String> = listOf("mergefastq-ip","mergefastq-ctl","align-ips","align-controls","filter-ips","filter-controls","bam2ta-ips","bam2ta-controls","xcor-ta","bam2ta-ips-nofilt","macs2","spr","macs2-pooled","idr","overlap","overlap-pr","overlap-ppr","macs2-pr1","macs2-pr2","macs2-ppr1","macs2-ppr2","spp","spp-pooled","pooled-ta-ips","pooled-ta-control","roundedmean",
    "pooled-spr-ta-pr1","pooled-spr-ta-pr2","idr-spp")

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

    val pooledIPTaInput = bam2taTaskIP.buffer().map { bam2taOut ->  PoolTaInput(bam2taOut.map { it.ta }, "pooled_ta_ips",bam2taOut.first().pairedEnd)}
    val pooledIPTaTask = pooledtaTask("pooled-ta-ips", pooledIPTaInput)
    
    val pooledCtlTaInput = bam2taTaskCtl.buffer().filter { params.tasks.contains("pooled-ta-control") }.map { ctlbam2taOut ->  PoolTaInput(ctlbam2taOut.map { it.ta }, "pooled_ta_ctl",ctlbam2taOut.first().pairedEnd)}
    val pooledCtlTaTask = pooledtaTask("pooled-ta-control", pooledCtlTaInput)

    val sppInput = bam2taTaskIP.filter { params.tasks.contains("spp") }.map { bit ->  SppInput(bit.ta,
        pooledCtlTaTask.buffer().toIterable().flatten().first().pooledTa ,
        bit.repName+"_"+"spp"+"_"+pooledCtlTaTask.buffer().toIterable().flatten().first().repName,
        bit.pairedEnd,
        xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen)
    }
    val sppTask = SppTask("spp",sppInput)


    val macs2Input = bam2taTaskIP.filter { params.tasks.contains("macs2") }.map { bit ->  Macs2Input(bit.ta,
        pooledCtlTaTask.buffer().toIterable().flatten().first().pooledTa ,
        bit.repName+"_"+pooledCtlTaTask.buffer().toIterable().flatten().first().repName,
        bit.pairedEnd,
        xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen)
        }
    val macs2Task = macs2Task("macs2",macs2Input)

    val roundedMeanInput  =  xcorTask.buffer().filter { params.tasks.contains("roundedmean") }.map { xcorOutput -> roundedmeanInput(xcorOutput.map {it.Fraglen},"roundedmean")}
    val roundedMeanTask =  roundedmeanTask("roundedmean", roundedMeanInput)

    val macspooledInput = pooledIPTaTask.filter { params.tasks.contains("macs2-pooled") }.map { bit -> Macs2Input(bit.pooledTa,
    pooledCtlTaTask.buffer().map { co -> co.first() }.toIterable().first().pooledTa,
    bit.repName+"_macs2_pooled",bit.pairedEnd,
    roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen) }
    val macs2pooledTask = macs2Task("macs2-pooled",macspooledInput)

    val spppooledInput = pooledIPTaTask.filter { params.tasks.contains("spp-pooled") }.map { bit -> SppInput(bit.pooledTa,pooledCtlTaTask.buffer().map { co -> co.first() }.toIterable().first().pooledTa,
    bit.repName+"_spp_pooled",bit.pairedEnd,
    roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen) }
    val spppooledTask = SppTask("spp-pooled",spppooledInput)

    val p = mutableListOf<Pair<String,String>>()

    val replicatesIP =  params.experiments.flatMap {
        it.replicates
            .filter { (it is FastqReplicatePE || it is FastqReplicateSE || it is MergedFastqReplicateSE || it is MergedFastqReplicatePE ||  it is FilteredBamReplicate || it is BamReplicate) 
			&& (if(it is FastqReplicatePE) (  !((it as FastqReplicatePE).control!!) ) 
			else if(it is MergedFastqReplicateSE) (  !((it as MergedFastqReplicateSE).control!!) )
			else if(it is MergedFastqReplicatePE) (  !((it as MergedFastqReplicatePE).control!!) )
			else if(it is BamReplicate) (  !((it as BamReplicate).control!!) )			
			else if(it is FilteredBamReplicate) (  !((it as FilteredBamReplicate).control!!) )			
			else !(it as FastqReplicateSE).control!!)  }
            .map { it }
        }
    replicatesIP.forEach { r1 ->
            replicatesIP.forEach { r2 ->
                if(r1.name!==r2.name && !p.contains(Pair(r1.name ,r2.name )) &&  !p.contains(Pair(r2.name ,r1.name ))) {
                    p.add(Pair(r1.name ,r2.name ))
                }
            }
    }

    p.forEach { pr ->
        //   val peak1 = macs2Task.buffer().map { xit ->  xit.find { it -> "ENCFF000ASP".contains(pr.first) } }.toIterable().first()!!.npeak
        //   val peak2 = macs2Task.buffer().map { xit ->  xit.find { it -> "ENCFF000ASU".contains(pr.second) } }.toIterable().first()!!.npeak
        // val pooled_peak = macs2pooledTask.buffer().map { xit ->  xit.find { it -> it.repName.contains("pooled_inputs"+"macs2_pooled") } }.toIterable().first()!!.npeak
        //    val fraglen = roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen
        //   val pooled_ta = pooledTaTask.buffer().map { co -> co.first() }.toIterable().first().pooledTa
        log.info { pr.second }
        log.info { pr.first }
        val idrInput = pooledIPTaTask.filter { params.tasks.contains("idr") }.map { bit -> IdrInput(macs2Task.buffer().map { xit ->  xit.find { it -> it.repName.contains(pr.first) } }.toIterable().first()!!.npeak,
                macs2Task.buffer().map { xit ->  xit.find { it -> it.repName.contains(pr.second) } }.toIterable().first()!!.npeak,
                macs2pooledTask.buffer().map { co -> co.first() }.toIterable().first()!!.npeak,
                roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen,bit.pooledTa,"${pr.first}_${pr.second}") }
        val idrTask = IdrTask("idr",idrInput)

        //idr -spp
        val idrSppInput = pooledIPTaTask.filter { params.tasks.contains("idr-spp") }.map { bit -> IdrInput(sppTask.buffer().map { xit ->  xit.find { it -> it.repName.contains(pr.first) } }.toIterable().first()!!.npeak,
        sppTask.buffer().map { xit ->  xit.find { it -> it.repName.contains(pr.second) } }.toIterable().first()!!.npeak,
        spppooledTask.buffer().map { co -> co.first() }.toIterable().first()!!.npeak,
        roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen,bit.pooledTa,"${pr.first}_${pr.second}_spp") }
        val idrSppTask = IdrTask("idr-spp",idrSppInput)


        val overlapInput = pooledIPTaTask.filter { params.tasks.contains("overlap") }.map { bit -> OverlapInput(macs2Task.buffer().map { xit ->  xit.find { it.repName.contains(pr.first) } }.toIterable().first()!!.npeak,
        macs2Task.buffer().map { xit ->  xit.find { it.repName.contains(pr.second) } }.toIterable().first()!!.npeak,
        macs2pooledTask.buffer().map { co -> co.first() }.toIterable().first()!!.npeak,
        //macs2pooledTask.buffer().map { xit ->  xit.find { it.repName.contains("pooled_ta_ips_motif"+"_macs2_pooled") } }.toIterable().first()!!.npeak,
        roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen,bit.pooledTa,"${pr.first}_${pr.second}") }
        val overlapTask = OverlapTask("overlap",overlapInput)
      }

    
      val sprTaskInput = params.experiments.flatMap {
        it.replicates
            .filter { it is TagAlignReplicate && it.ta !== null && !it.control!! }
            .map { xcorInput(it as TagAlignReplicate) }
        }.toFlux()        

    val sprInput =  sprTaskInput.concatWith(bam2taTaskIP).filter { params.tasks.contains("spr") }.map { sprInput(it.ta,it.repName,it.pairedEnd)}
    val sprTask = sprTask("spr",sprInput)

    val pooledsprTaPr1Input = sprTask.buffer().filter { params.tasks.contains("pooled-spr-ta-pr1") }.map { sprOut ->  PoolTaInput(sprOut.map { it.psr1 }, "pooled_spr_ta_pr1",sprOut.first().pairedEnd)}
    val pooledsprTaPr1Task = pooledtaTask("pooled-spr-ta-pr1",pooledsprTaPr1Input)

    val pooledsprTaPr2Input = sprTask.buffer().filter { params.tasks.contains("pooled-spr-ta-pr2") }.map { sprOut ->  PoolTaInput(sprOut.map { it.psr2 }, "pooled_spr_ta_pr2",sprOut.first().pairedEnd)}
    val pooledsprTaPr2Task = pooledtaTask("pooled-spr-ta-pr2",pooledsprTaPr2Input)

    val macs2pr1Input = sprTask.filter { params.tasks.contains("macs2-pr1") }.map { bit ->  Macs2Input(bit.psr1,
            pooledCtlTaTask.buffer().map { co -> co.first() }.toIterable().first().pooledTa,
            //choosectlTask.buffer().toIterable().flatten().first().ctls.find { xit -> xit.repName.contains(bit.repName)}!!.ctlFile ,
            pooledCtlTaTask.buffer().map { co -> co.first() }.toIterable().first().repName+"_pr1",
            //choosectlTask.buffer().toIterable().flatten().first().ctls.find { xit -> xit.repName.contains(bit.repName)}!!.repName+"_pr1",
            bit.pairedEnd,
            xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen)
    }
    val macs2pr1Task = macs2Task("macs2-pr1",macs2pr1Input)

    val macs2pr2Input = sprTask.filter { params.tasks.contains("macs2-pr2") }.map { bit ->  Macs2Input(bit.psr2,
         //   choosectlTask.buffer().toIterable().flatten().first().ctls.find { xit -> xit.repName.contains(bit.repName)}!!.ctlFile,
         pooledCtlTaTask.buffer().map { co -> co.first() }.toIterable().first().pooledTa,
         pooledCtlTaTask.buffer().map { co -> co.first() }.toIterable().first().repName+"_pr2",
        //choosectlTask.buffer().toIterable().flatten().first().ctls.find { xit -> xit.repName.contains(bit.repName)}!!.repName+"_pr2",
        bit.pairedEnd,
        xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen)
    }
    val macs2pr2Task = macs2Task("macs2-pr2",macs2pr2Input)

    val macspooledpr1Input = pooledsprTaPr1Task.filter { params.tasks.contains("macs2-ppr1") }.map { bit -> Macs2Input(bit.pooledTa,
        pooledCtlTaTask.buffer().map { co -> co.first() }.toIterable().first().pooledTa,
            bit.repName+"_macs2_pooled_ppr1",bit.pairedEnd,
            roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen) }
    val macs2pooledpr1Task = macs2Task("macs2-ppr1",macspooledpr1Input)

    val macspooledpr2Input = pooledsprTaPr2Task.filter { params.tasks.contains("macs2-ppr2") }.map { bit -> Macs2Input(bit.pooledTa,pooledCtlTaTask.buffer().map { co -> co.first() }.toIterable().first().pooledTa,
            bit.repName+"_macs2_pooled_ppr2",bit.pairedEnd,
            roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen) }
    val macs2pooledpr2Task = macs2Task("macs2-ppr2",macspooledpr2Input)

    //overlap_pr
    val overlap_pr_Input = bam2taTaskIP.filter { params.tasks.contains("overlap-pr") }.map { bit -> OverlapInput(macs2pr1Task.buffer().map { xit -> xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.npeak,
            macs2pr2Task.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.npeak,
            macs2Task.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.npeak,
            xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen,bit.ta,
            "rep_${bit.repName}_pr") }
    val overlap_pr_Task = OverlapTask("overlap-pr",overlap_pr_Input)

    //overlap_ppr
    val overlap_ppr_Input = pooledIPTaTask.filter { params.tasks.contains("overlap-ppr") }.map { bit -> OverlapInput(
            macs2pooledpr1Task.buffer().map { co -> co.first() }.toIterable().first()!!.npeak,
            macs2pooledpr2Task.buffer().map { co -> co.first() }.toIterable().first()!!.npeak,
            macs2pooledTask.buffer().map { co -> co.first() }.toIterable().first()!!.npeak,
            roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first()!!.Fraglen,
            bit.pooledTa, "overlap-ppr") }
    val overlap_ppr_Task = OverlapTask("overlap-ppr",overlap_ppr_Input)

}
