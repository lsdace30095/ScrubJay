import scala.util.Random
import scala.collection.immutable.Map

import gov.llnl.scrubjay._
import gov.llnl.scrubjay.localDataSource._
import gov.llnl.scrubjay.expandedNodeList._
import gov.llnl.scrubjay.naturalJoin._
import gov.llnl.scrubjay.datasource._
import gov.llnl.scrubjay.cassandraDataSource._

import com.datastax.spark.connector._


object TestScrubJay {

  def createLocalJobQueue(sjs: ScrubJaySession): DataSource = {

    val testData = Array(
      Map("jobid"     -> 123, 
          "nodelist"  -> List(1,2,3)),
      Map("jobid"     -> 456, 
          "nodelist"  -> List(4,5,6)))

    val testMeta = Map(
      (MetaEntry(sjs.metaOntology.VALUE_JOB_ID,     sjs.metaOntology.UNITS_ID)      -> "jobid"),
      (MetaEntry(sjs.metaOntology.VALUE_NODE_LIST,  sjs.metaOntology.UNITS_ID_LIST) -> "nodelist"))

    sjs.createLocalDataSource(testMeta, testData)
  }

  def createLocalCabLayout(sjs: ScrubJaySession): DataSource = {

    val testData = Array(
      Map("node"     -> 1, 
          "rack"     -> 1),
      Map("node"     -> 2, 
          "rack"     -> 1),
      Map("node"     -> 3, 
          "rack"     -> 1),
      Map("node"     -> 4, 
          "rack"     -> 2),
      Map("node"     -> 5, 
          "rack"     -> 2),
      Map("node"     -> 6, 
          "rack"     -> 2))

    val testMeta = Map(
      (MetaEntry(sjs.metaOntology.VALUE_NODE, sjs.metaOntology.UNITS_ID) -> "node"),
      (MetaEntry(sjs.metaOntology.VALUE_RACK, sjs.metaOntology.UNITS_ID) -> "rack"))

    sjs.createLocalDataSource(testMeta, testData)
  }

  def createCassandraJobQueue(sjs: ScrubJaySession): DataSource = {

    val jobQueueMeta = Map(
      (MetaEntry(sjs.metaOntology.VALUE_JOB_ID,     sjs.metaOntology.UNITS_ID)        -> "job_id"),
      (MetaEntry(sjs.metaOntology.VALUE_START_TIME, sjs.metaOntology.UNITS_TIMESTAMP) -> "start_time"),
      (MetaEntry(sjs.metaOntology.VALUE_DURATION,   sjs.metaOntology.UNITS_SECONDS)   -> "elapsed_time"),
      (MetaEntry(sjs.metaOntology.VALUE_JOB_NAME,   sjs.metaOntology.UNITS_ID)        -> "job_name"),
      (MetaEntry(sjs.metaOntology.VALUE_NODE_LIST,  sjs.metaOntology.UNITS_ID_LIST)   -> "node_list"),
      (MetaEntry(sjs.metaOntology.VALUE_NUM_NODES,  sjs.metaOntology.UNITS_QUANTITY)  -> "num_nodes"),
      (MetaEntry(sjs.metaOntology.VALUE_PARTITION,  sjs.metaOntology.UNITS_ID)        -> "partition"),
      (MetaEntry(sjs.metaOntology.VALUE_STATE,      sjs.metaOntology.UNITS_ID)        -> "state"),
      (MetaEntry(sjs.metaOntology.VALUE_USER_NAME,  sjs.metaOntology.UNITS_ID)        -> "user_name"))

    sjs.createCassandraDataSource(jobQueueMeta, "cab_dat_2015_08_05", "job_queue")
  }

  def createCassandraCabLayout(sjs: ScrubJaySession): DataSource = {

    val cabLayoutMeta = Map(
      (MetaEntry(sjs.metaOntology.VALUE_NODE,        sjs.metaOntology.UNITS_ID) -> "node"),
      (MetaEntry(sjs.metaOntology.VALUE_RACK,        sjs.metaOntology.UNITS_ID) -> "rack"),
      (MetaEntry(sjs.metaOntology.VALUE_RACK_ROW,    sjs.metaOntology.UNITS_ID) -> "height"),
      (MetaEntry(sjs.metaOntology.VALUE_RACK_COLUMN, sjs.metaOntology.UNITS_ID) -> "row"))

    sjs.createCassandraDataSource(cabLayoutMeta, "cab_dat_2015_08_05", "cab_layout")

  }

  def main(args: Array[String]) {

    // Change the following to test locally or using Cassandra
    val local = false

    val sjs = new ScrubJaySession(
      cassandra_connection = Some(CassandraConnection(hostname = "sonar10")))

    // Create DataSources
    val jobQueue = if (local) createLocalJobQueue(sjs) else createCassandraJobQueue(sjs)
    val cabLayout = if (local) createLocalCabLayout(sjs) else createCassandraCabLayout(sjs)

    val jobQueueExpanded = sjs.deriveExpandedNodeList(jobQueue)

    val jobQueueRackInfo = sjs.deriveNaturalJoin(jobQueueExpanded, cabLayout)

    if (jobQueueRackInfo.defined) {
      jobQueueRackInfo.rdd.foreach(println)
    }
    else {
      println("UNDEFINED")
    }
  }
}
