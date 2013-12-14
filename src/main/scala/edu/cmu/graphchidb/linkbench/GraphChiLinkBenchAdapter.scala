package edu.cmu.graphchidb.linkbench

import com.facebook.LinkBench._
import java.util.Properties
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import edu.cmu.graphchidb.{GraphChiDatabase, GraphChiDatabaseAdmin}
import edu.cmu.graphchidb.storage.{VarDataColumn, Column}
import edu.cmu.graphchidb.storage.ByteConverter
import java.nio.ByteBuffer
import edu.cmu.graphchidb.compute.Pagerank


class GraphChiLinkBenchAdapter extends GraphStore {
  def close() = GraphChiLinkBenchAdapter.close()

  def clearErrors(p1: Int) = GraphChiLinkBenchAdapter.clearErrors(p1)

  def resetNodeStore(p1: String, p2: Long) = GraphChiLinkBenchAdapter.resetNodeStore(p1, p2)

  def initialize(p1: Properties, p2: Phase, p3: Int) = GraphChiLinkBenchAdapter.initialize(p1, p2, p3)

  def addNode(p1: String, p2: Node) = GraphChiLinkBenchAdapter.addNode(p1, p2)

  def getNode(p1: String, p2: Int, p3: Long) = GraphChiLinkBenchAdapter.getNode(p1, p2, p3)

  def updateNode(p1: String, p2: Node) = GraphChiLinkBenchAdapter.updateNode(p1, p2)

  def deleteNode(p1: String, p2: Int, p3: Long) = GraphChiLinkBenchAdapter.deleteNode(p1, p2, p3)

  def addLink(p1: String, p2: Link, p3: Boolean) = GraphChiLinkBenchAdapter.addLink(p1, p2, p3)

  def deleteLink(p1: String, p2: Long, p3: Long, p4: Long, p5: Boolean, p6: Boolean) = GraphChiLinkBenchAdapter.deleteLink(p1, p2, p3, p4, p5, p6)

  def updateLink(p1: String, p2: Link, p3: Boolean) =
    GraphChiLinkBenchAdapter.updateLink(p1, p2, p3)

  def getLink(p1: String, p2: Long, p3: Long, p4: Long) = GraphChiLinkBenchAdapter.getLink(p1, p2, p3, p4)

  def getLinkList(p1: String, p2: Long, p3: Long) = GraphChiLinkBenchAdapter.getLinkList(p1, p2, p3)

  def getLinkList(p1: String, p2: Long, p3: Long, p4: Long, p5: Long, p6: Int, p7: Int) =
    GraphChiLinkBenchAdapter.getLinkList(p1, p2, p3, p4, p5, p6, p7)

  def countLinks(p1: String, p2: Long, p3: Long) = GraphChiLinkBenchAdapter.countLinks(p1, p2, p3)
}
/**
 * @author Aapo Kyrola
 */
object GraphChiLinkBenchAdapter {


  var currentPhase: Phase = null

  var idSequence = new AtomicLong()

  /**** NODE STORE **/


  val baseFilename = "/Users/akyrola/graphs/DB/linkbench/linkbench"
  var DB : GraphChiDatabase = null


  // TODO!
  def edgeType(typeValue: Long) = (typeValue % 2).toByte

  case class LinkContainer(version: Byte, timestamp: Int, payloadId: Long)
  case class NodeContainer(version: Byte, timestamp: Int, payloadId: Long)

  /* Edge columns */
  var edgeDataColumn: Column[LinkContainer] = null
  var vertexDataColumn: Column[NodeContainer] = null

  var type0Counters : Column[Int]  = null
  var type1Counters : Column[Int]  = null

  var edgePayloadColumn : VarDataColumn = null
  var vertexPayloadColumn : VarDataColumn = null
  // payload data
  // var nodePaylaod

  var initialized = false

  val workerCounter = new AtomicInteger()

  def close() = {
    println("GraphChiLinkBenchAdapter: close")
    // Last one flushes the buffers
    if (workerCounter.decrementAndGet() == 0) {
      println("Last one -- flushing buffers")
      DB.flushAllBuffers
      DB.close()
      DB = null
      println("Done.")
      initialized = false
    }
  }

  def clearErrors(threadId: Int) = {
    println("GraphChiLinkBenchAdapter: clear errors")
  }


  object LinkToBytesConverter extends ByteConverter[LinkContainer] {
    def fromBytes(bb: ByteBuffer) : LinkContainer = {
         LinkContainer(bb.get, bb.getInt, bb.getLong)
    }

    def toBytes(v: LinkContainer, out: ByteBuffer) = {
        out.put(v.version)
        out.putInt(v.timestamp)
        out.putLong(v.payloadId)
    }

    def sizeOf = 13
  }

  object NodeToBytesConverter extends ByteConverter[NodeContainer] {
    def fromBytes(bb: ByteBuffer) : NodeContainer = {
      NodeContainer(bb.get, bb.getInt, bb.getLong)
    }

    def toBytes(v: NodeContainer, out: ByteBuffer) = {
      out.put(v.version)
      out.putInt(v.timestamp)
      out.putLong(v.payloadId)
    }

    def sizeOf = 13
  }

  def initialize(p1: Properties, phase: Phase, threadId: Int) = {
    if (threadId == 0) {
      println("Initialize: %s, %s, %d".format(p1, currentPhase, threadId))
      currentPhase =  phase


      if (currentPhase.ordinal() == Phase.LOAD.ordinal()) {
        println("GraphChiLinkBenchAdapter: reset node store, startId: " + 0)
        GraphChiDatabaseAdmin.createDatabase(baseFilename)
        idSequence.set(0)
      }

      println("Initializing, curphase = " + currentPhase.ordinal())

      DB = new GraphChiDatabase(baseFilename, disableDegree = true)
      /* Create columns */
      edgeDataColumn = DB.createCustomTypeColumn[LinkContainer]("linkdata", DB.edgeIndexing, LinkToBytesConverter)
      vertexDataColumn = DB.createCustomTypeColumn[NodeContainer]("nodedata", DB.vertexIndexing, NodeToBytesConverter)

      edgePayloadColumn = DB.createVarDataColumn("payload", DB.edgeIndexing)
      vertexPayloadColumn = DB.createVarDataColumn("payload", DB.vertexIndexing)

      type0Counters = DB.createIntegerColumn("type0cnt", DB.vertexIndexing)
      type1Counters = DB.createIntegerColumn("type1cnt", DB.vertexIndexing)
    //  val pagerankComputation = new Pagerank(DB)

      DB.initialize()
      initialized = true
      println("Thread " + threadId + " initialized")

      /* Run pagerank */
      //DB.runIteration(pagerankComputation, continuous = true)
    }
    println("Thread " + threadId + " waiting, this=" + this)

    while (!initialized) Thread.sleep(50)
    println("Thread " + threadId + " starting, this=" + this)
    workerCounter.incrementAndGet()
  }


  /**
   * Reset node storage to a clean state in shard:
   *   deletes all stored nodes
   *   resets id allocation, with new IDs to be allocated starting from startID
   */
  def resetNodeStore(p1: String, startId: Long) : Unit = {
    idSequence.set(startId)

  }



  def addNode(databaseId: String, node: Node) : Long = {
    /* Just insert. Note: nodetype is ignored. */
    val newId = idSequence.getAndIncrement
    val newInternalId = DB.originalToInternalId(newId)
    DB.updateVertexRecords(newInternalId)
    val payloadId = vertexPayloadColumn.insert(node.data)
    vertexDataColumn.set(newInternalId, NodeContainer(node.version.toByte, node.time, payloadId))

    newId
  }

  def getNode(databaseId: String, nodeType: Int, id: Long) : Node = {
    val internalId = DB.originalToInternalId(id)

    val vertexData = vertexDataColumn.get(internalId).getOrElse(NodeContainer(0,0,0))
     if (vertexData.timestamp > 0) {
       val payloadData = vertexPayloadColumn.get(vertexData.payloadId)
       new Node(id, 0, vertexData.version, vertexData.timestamp, payloadData)
    } else {
      null
    }
  }

  def updateNode(databaseId: String, node: Node) = {
    // TODO: payload
    val internalId = DB.originalToInternalId(node.id)
    val vertexData = vertexDataColumn.get(internalId).getOrElse(NodeContainer(0,0,0))

    if (vertexData.timestamp > 0) {
      val payloadId = vertexData.payloadId
      val oldPayload = vertexPayloadColumn.get(payloadId)
      val updatedPayloadId = if (!new String(oldPayload).equals(node.data)) {
        // Create new
        val newPayloadId = vertexPayloadColumn.insert(node.data)
         vertexPayloadColumn.delete(payloadId)
        newPayloadId
      } else { payloadId }

      vertexDataColumn.set(internalId, NodeContainer(node.version.toByte, node.time, updatedPayloadId))
      true
    } else {
      false
    }
  }

  def deleteNode(databaseId: String, nodeType: Int, id: Long) = {
    val internalId = DB.originalToInternalId(id)
    /*type0Counters.set(internalId, 0)
    type1Counters.set(internalId, 0)
    DB.deleteVertexOrigId(internalId) */
    // NOTE: as per the mysql link bench, only invalidate the node
    vertexDataColumn.set(internalId, NodeContainer(0,0,0))
    true
  }

  /**** LINK STORE ****/


  def addLinkImpl(edge: Link) = {
    val edgeTypeByte = edgeType(edge.link_type)
    /* Payload */
    val payloadId = edgePayloadColumn.insert(edge.data)
    DB.addEdgeOrigId(edgeTypeByte, edge.id1, edge.id2, LinkContainer(edge.version.toByte, edge.time.toInt, payloadId))

    /* Adjust counters */
    // NOTE: hard-coded only two types
    val countColumn = if (edgeTypeByte == 0) type0Counters else type1Counters
    countColumn.update(DB.originalToInternalId(edge.id1), c => c.getOrElse(0) + 1)
  }

  /// NOTE: can apparently ignore noInverse settings!
  def addLink(databaseId: String, edge: Link, noInverse: Boolean) = {

    if (currentPhase == Phase.LOAD) {
      /* Just insert */
      addLinkImpl(edge)
    } else {
      /* Check first if exits, then insert */
      if (!updateLink(databaseId, edge, noInverse)) {
        addLinkImpl(edge)
      }
    }
    true
  }


  /**
   * Delete link identified by parameters from store
   * @param databaseId
   * @param id1
   * @param linkType
   * @param id2
   * @param noInverse
   * @param exPunge if true, delete permanently.  If false, hide instead
   * @return true if row existed. Implementation is optional, for informational
   *         purposes only.
   * @throws Exception
   */
  def deleteLink(databaseId: String, id1: Long, linkType: Long, id2: Long, noInverse: Boolean, exPunge: Boolean) = {
    if (DB.deleteEdgeOrigId(edgeType(linkType), id1, id2)) {
      val countColumn = if (edgeType(linkType) == 0) type0Counters else type1Counters
      countColumn.update(DB.originalToInternalId(id1), c => c.getOrElse(1) - 1)
      true
    } else {
      false
    }
  }

  def updateLink(databaseId: String, edge: Link, noInverse: Boolean) : Boolean = {
    val edgeTypeByte = edgeType(edge.link_type)

    DB.findEdgePointer(edgeTypeByte, DB.originalToInternalId(edge.id1), DB.originalToInternalId(edge.id2)) { ptrOpt => {
      ptrOpt match {
        case Some(ptr) => {

          val edgeData =  DB.getByPointer(edgeDataColumn, ptr).get

          val payloadId = edgeData.payloadId
          val oldPayload = edgePayloadColumn.get(payloadId)
          val updatedPayloadId = if (!new String(oldPayload).equals(new String(edge.data))) {
            val newPayloadId = edgePayloadColumn.insert(edge.data)

            edgePayloadColumn.delete(payloadId)
            newPayloadId
          } else { payloadId }

          DB.setByPointer(edgeDataColumn, ptr, LinkContainer(edge.version.toByte, edge.time.toInt, updatedPayloadId))

          return true
        }
        case None => return false
        // TODO: update payload

      }
    } }
    throw new IllegalArgumentException("Edge does not exist: %s".format(edge))
  }

  private def linkFromPointer(ptr: Long, id1: Long, linkType: Long, id2: Long, timestampFilter: Int => Boolean) : Link = {
    val edgeData  = DB.getByPointer(edgeDataColumn, ptr).get
    if (timestampFilter(edgeData.timestamp)) {
      val payload = edgePayloadColumn.get(edgeData.payloadId)
      // TODO: visibility
      new Link(id1, linkType, id2, LinkStore.VISIBILITY_DEFAULT, payload, edgeData.version, edgeData.timestamp)
    } else {
      null
    }
  }

  def getLink(databaseId: String, id1: Long, linkType: Long, id2: Long) : Link = {
    DB.findEdgePointer(edgeType(linkType), DB.originalToInternalId(id1), DB.originalToInternalId(id2)) {
      ptrOpt => {
        ptrOpt match {
          case Some(ptr) => {
            return linkFromPointer(ptr, id1, linkType, id2, t => true)
          }
          case None =>
            return null
        }
      }
    }

    null
  }

  def getLinkList(databaseId: String, id1: Long, linkType: Long) : Array[Link]    = {
    val results = DB.queryOut(DB.originalToInternalId(id1), edgeType(linkType))
    results.getPointers.zip(results.getInternalIds).
      map( row  => linkFromPointer(row._1.asInstanceOf[Long], id1, linkType, DB.internalToOriginalId(row._2.asInstanceOf[Long]), t=>true)
    ).toArray[Link]
  }

  def getLinkList(databaseId: String, id1: Long, linkType: Long, minTimestamp: Long, maxTimestamp: Long,
                  offset: Int, limit: Int) : Array[Link]   = {
    val results = DB.queryOut(DB.originalToInternalId(id1), edgeType(linkType))
    results.getPointers.zip(results.getInternalIds).
      map( row  => linkFromPointer(row._1.asInstanceOf[Long], id1, linkType, DB.internalToOriginalId(row._2.asInstanceOf[Long]),
      t => (t >= minTimestamp && t <= maxTimestamp))
    ).filter(_ != null).toArray[Link]
  }

  def countLinks(databaseId: String, id1: Long, linkType: Long) = {
    val internalId = DB.originalToInternalId(id1)
    val c = if (edgeType(linkType) == 1) {
      type0Counters.get(internalId).getOrElse(0)
    } else {
      type1Counters.get(internalId).getOrElse(0)
    }
    c.toLong
  }
}