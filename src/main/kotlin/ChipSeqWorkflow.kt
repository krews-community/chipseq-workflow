import krews.core.*
import krews.run
import model.MergedFastqReplicatePE
import model.MergedFastqSamples
import reactor.core.publisher.toFlux
import task.*
import mu.KotlinLogging

private val log = KotlinLogging.logger {}
fun main(args: Array<String>) = run(chipSeqWorkflow, args)

data class ChipSeqParams(
        val replicatesIP: MergedFastqSamples,
        val replicatesCTRL: MergedFastqSamples
)

val chipSeqWorkflow = workflow("encode-chipseq-workflow") {

    val params = params<ChipSeqParams>()

/*
    val trimfastqinp = params.replicatesIP.replicates.map {  TrimfastqInput(it)}.toFlux()
    val trimFastqInput = trimfastqTask("trimfastq",trimfastqinp)

    val bwaR1InputIps = trimFastqInput
            .map { MBwaInput(it.fastq,null,it.repName+"bwa_R1",false) }
            .toFlux()
    val bwaR1TaskIps = mbwaTask("align-ips", bwaR1InputIps)

    val bam2taNoFiltR1Input = bwaR1TaskIps
            .map { Bam2taInput(it.bam,  it.repName+"_nofilt_R1",  false ) }
    val bam2tanofiltR1Task = bam2taTask("bam2ta-ips-nofilt",bam2taNoFiltR1Input)
*/
    val pooledCtl = mutableListOf<String>()
    params.replicatesCTRL.replicates.forEach { it->
        pooledCtl.add(it.name)
    }
    val pooledIp = mutableListOf<String>()
    params.replicatesIP.replicates.forEach { it->
        pooledIp.add(it.name)
    }

    val bwaInputIps = params.replicatesIP.replicates
            .map { BwaInput(it) }
            .toFlux()
    val bwaTaskIps = bwaTask("align-ips", bwaInputIps)

    val bwaInputControls = params.replicatesCTRL.replicates
            .map { BwaInput(it) }
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
            .map { Bam2taInput(it.bam, it.repName,  it.pairedEnd ) }
    val controlbam2taTask = bam2taTask("bam2ta-controls",controlbam2taInput)

    val bam2taNoFiltInput = bwaTaskIps
            .map { Bam2taInput(it.bam,  it.repName+"_nofilt",  false ) }
    val bam2tanofiltTask = bam2taTask("bam2ta-ips-nofilt",bam2taNoFiltInput)

    //old

    // bam2tanofiltR1Task
    val xcorInput = bam2tanofiltTask.map { XcorInput(it.ta,it.repName,it.pairedEnd)}
    val xcorTask = XcorTask("xcor-ta",xcorInput)


    val pooledTaInput = bam2taTask.buffer().map { bam2taOut ->  PoolTaInput(bam2taOut.map { it.ta }, "${pooledIp.joinToString("_" )}_pooled_ta_ips",bam2taOut.first().pairedEnd)}
    val pooledTaTask = pooledtaTask("pooled-ta-ips",pooledTaInput)


    val pooledCtlTaInput = controlbam2taTask.buffer().map { ctlbam2taOut ->  PoolTaInput(ctlbam2taOut.map { it.ta }, "psychencode_pooled_ta_ctl",ctlbam2taOut.first().pairedEnd)}
    val pooledCtlTaTask = pooledtaTask("pooled-ta-control",pooledCtlTaInput)


    val rNames = mutableListOf<String>()
       params.replicatesIP.replicates.forEachIndexed { i,it->
           if(params.replicatesCTRL.replicates.size < params.replicatesIP.replicates.size)
           {
               rNames.add(it.name+"_"+params.replicatesCTRL.replicates[0].name)


           } else {
               rNames.add(it.name+"_"+params.replicatesCTRL.replicates[i].name)

       }
    }

    val choosectlInput  =  bam2taTask.buffer().map { bam2taOut -> ChooseCtlInput(
               bam2taOut.map { it.ta },controlbam2taTask.buffer().toIterable().flatten().map { it.ta },
               pooledCtlTaTask.buffer().map { co -> co.first() }.toIterable().first().pooledTa,
               rNames
    )}

    val choosectlTask = choosectlTask("choosectl",choosectlInput)
/*
    val sppInput = bam2taTask.map { bit ->  SppInput(bit.ta,
            choosectlTask.buffer().toIterable().flatten().first().ctls.find { xit -> xit.repName.contains(bit.repName)}!!.ctlFile ,
            choosectlTask.buffer().toIterable().flatten().first().ctls.find { xit -> xit.repName.contains(bit.repName)}!!.repName,false,
            xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen)
    }
    val sppTask = SppTask("spp",sppInput)
*/
    val macs2Input = bam2taTask.map { bit ->  Macs2Input(
        bit.ta,
        //controlbam2taTask.buffer().toIterable().flatten().first().ta,
        pooledCtlTaTask.buffer().toIterable().flatten().first().pooledTa ,
        bit.repName+"_"+pooledCtlTaTask.buffer().toIterable().flatten().first().repName,
        //bit.repName+"_"+ controlbam2taTask.buffer().toIterable().flatten().first().repName,
        bit.pairedEnd,
        xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen)
    }
    val macs2Task = macs2Task("macs2",macs2Input)

    //rounded mean

    val roundedMeanInput  =  xcorTask.buffer().map { xcorOutput -> roundedmeanInput(xcorOutput.map {it.Fraglen},"${pooledIp.joinToString("_" )}_roundedmean")}
     val roundedMeanTask =  roundedmeanTask("roundedmean",roundedMeanInput)

/*
    val spppooledInput = pooledTaTask.map { bit -> SppInput(bit.pooledTa,pooledCtlTaTask.buffer().map { co -> co.first() }.toIterable().first().pooledTa,
            bit.repName+"_spp_pooled",false,
            roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen) }
    val spppooledTask = SppTask("spp-pooled",spppooledInput)
*/

    val macspooledInput = pooledTaTask.map { bit -> Macs2Input(bit.pooledTa,pooledCtlTaTask.buffer().map { co -> co.first() }.toIterable().first().pooledTa,
               bit.repName+"_macs2_pooled",bit.pairedEnd,
               roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen) }
    val macs2pooledTask = macs2Task("macs2-pooled",macspooledInput)

    val p = mutableListOf<Pair<String,String>>()
    params.replicatesIP.replicates.forEach { r1 ->
        params.replicatesIP.replicates.forEach { r2 ->
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
        val idrInput = pooledTaTask.map { bit -> IdrInput(macs2Task.buffer().map { xit ->  xit.find { it -> it.repName.contains(pr.first) } }.toIterable().first()!!.npeak,
                macs2Task.buffer().map { xit ->  xit.find { it -> it.repName.contains(pr.second) } }.toIterable().first()!!.npeak,
                macs2pooledTask.buffer().map { xit ->  xit.find { it -> it.repName.contains("pooled_ta_ips_motif"+"_macs2_pooled") } }.toIterable().first()!!.npeak,
                roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen,bit.pooledTa,"${pr.first}_${pr.second}") }
        val idrTask = IdrTask("idr",idrInput)

/*
        //idr -spp
        val idrInput = pooledTaTask.map { bit -> IdrInput(sppTask.buffer().map { xit ->  xit.find { it -> it.repName.contains(pr.first) } }.toIterable().first()!!.npeak,
        sppTask.buffer().map { xit ->  xit.find { it -> it.repName.contains(pr.second) } }.toIterable().first()!!.npeak,
        spppooledTask.buffer().map { xit ->  xit.find { it -> it.repName.contains("pooled_ta_ips_motif"+"_spp_pooled") } }.toIterable().first()!!.npeak,
        roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen,bit.pooledTa,"${pr.first}_${pr.second}") }
        val idrTask = IdrTask("idr",idrInput)
*/

        val overlapInput = pooledTaTask.map { bit -> OverlapInput(macs2Task.buffer().map { xit ->  xit.find { it.repName.contains(pr.first) } }.toIterable().first()!!.npeak,
        macs2Task.buffer().map { xit ->  xit.find { it.repName.contains(pr.second) } }.toIterable().first()!!.npeak,
        macs2pooledTask.buffer().map { co -> co.first() }.toIterable().first()!!.npeak,
        //macs2pooledTask.buffer().map { xit ->  xit.find { it.repName.contains("pooled_ta_ips_motif"+"_macs2_pooled") } }.toIterable().first()!!.npeak,
        roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen,bit.pooledTa,"${pr.first}_${pr.second}") }
        val overlapTask = OverlapTask("overlap",overlapInput)
      }

      val sprInput =  bam2taTask.map { sprInput(it.ta,it.repName,it.pairedEnd)}
      val sprTask = sprTask("spr",sprInput)

      val pooledsprTaPr1Input = sprTask.buffer().map { sprOut ->  PoolTaInput(sprOut.map { it.psr1 }, "pooled_spr_ta_pr1",sprOut.first().pairedEnd)}
      val pooledsprTaPr1Task = pooledtaTask("pooled-spr-ta-pr1",pooledsprTaPr1Input)

      val pooledsprTaPr2Input = sprTask.buffer().map { sprOut ->  PoolTaInput(sprOut.map { it.psr2 }, "pooled_spr_ta_pr2",sprOut.first().pairedEnd)}
      val pooledsprTaPr2Task = pooledtaTask("pooled-spr-ta-pr2",pooledsprTaPr2Input)

      val macs2pr1Input = sprTask.map { bit ->  Macs2Input(bit.psr1,
              choosectlTask.buffer().toIterable().flatten().first().ctls.find { xit -> xit.repName.contains(bit.repName)}!!.ctlFile ,
              choosectlTask.buffer().toIterable().flatten().first().ctls.find { xit -> xit.repName.contains(bit.repName)}!!.repName+"_pr1",bit.pairedEnd,
              xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen)
      }
      val macs2pr1Task = macs2Task("macs2-pr1",macs2pr1Input)

      val macs2pr2Input = sprTask.map { bit ->  Macs2Input(bit.psr2,
              choosectlTask.buffer().toIterable().flatten().first().ctls.find { xit -> xit.repName.contains(bit.repName)}!!.ctlFile,
              choosectlTask.buffer().toIterable().flatten().first().ctls.find { xit -> xit.repName.contains(bit.repName)}!!.repName+"_pr2",bit.pairedEnd,
              xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen)
      }
      val macs2pr2Task = macs2Task("macs2-pr2",macs2pr2Input)

      val macspooledpr1Input = pooledsprTaPr1Task.map { bit -> Macs2Input(bit.pooledTa,pooledCtlTaTask.buffer().map { co -> co.first() }.toIterable().first().pooledTa,
              bit.repName+"_macs2_pooled_ppr1",bit.pairedEnd,
              roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen) }
      val macs2pooledpr1Task = macs2Task("macs2-ppr1",macspooledpr1Input)

      val macspooledpr2Input = pooledsprTaPr2Task.map { bit -> Macs2Input(bit.pooledTa,pooledCtlTaTask.buffer().map { co -> co.first() }.toIterable().first().pooledTa,
              bit.repName+"_macs2_pooled_ppr2",bit.pairedEnd,
              roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first().Fraglen) }
      val macs2pooledpr2Task = macs2Task("macs2-ppr2",macspooledpr2Input)

      //overlap_pr
      val overlap_pr_Input = bam2taTask.map { bit -> OverlapInput(macs2pr1Task.buffer().map { xit -> xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.npeak,
              macs2pr2Task.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.npeak,
              macs2Task.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.npeak,
              xcorTask.buffer().map { xit ->  xit.find { it.repName.contains(bit.repName) } }.toIterable().first()!!.Fraglen,bit.ta,
              "rep_${bit.repName}_pr") }
      val overlap_pr_Task = OverlapTask("overlap-pr",overlap_pr_Input)

      //overlap_ppr
      val overlap_ppr_Input = pooledTaTask.map { bit -> OverlapInput(
              macs2pooledpr1Task.buffer().map { co -> co.first() }.toIterable().first()!!.npeak,
              macs2pooledpr2Task.buffer().map { co -> co.first() }.toIterable().first()!!.npeak,
              macs2pooledTask.buffer().map { co -> co.first() }.toIterable().first()!!.npeak,
              roundedMeanTask.buffer().map { co -> co.first() }.toIterable().first()!!.Fraglen,
              bit.pooledTa, "ppr") }
      val overlap_ppr_Task = OverlapTask("overlap-ppr",overlap_ppr_Input)

}
