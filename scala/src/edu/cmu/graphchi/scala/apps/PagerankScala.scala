package edu.cmu.graphchi.scala.apps

import edu.cmu.graphchi.scala._
import edu.cmu.graphchi.datablocks.FloatConverter
import edu.cmu.graphchi.util.IdFloat
import edu.cmu.graphchi.util.Toplist
import java.util.TreeSet
import scala.collection.JavaConversions._

object PagerankScala {
   
    def main(args: Array[String]): Unit = {
        val filename = "/Users/akyrola/graphs/soc-LiveJournal1.txt"
        val niters = 5
        val nshards = 3
        
        val graphchi = new GraphChiScala[java.lang.Float, java.lang.Float, java.lang.Float](filename, nshards)
        graphchi.setEdataConverter(new FloatConverter())
        graphchi.setVertexDataConverter(new FloatConverter())
        
        graphchi.initializeVertices(v => 1.0f)
        graphchi.foreach(niters, 
            gatherDirection = INEDGES(),
            gatherInit = 0.0f,
            gather =  (v, edgeval, iter, gather) => gather + edgeval,
            apply = (gather, v) => 0.15f + 0.85f * gather,
        	scatterDirection = OUTEDGES(),
        	scatter = (v) => v.value() / v.outDegree
        )
        
        /* Print top (this is just Java code)*/
        val top20 = Toplist.topListFloat(filename, 20);
        var i : Int = 0;
        top20.foreach( vertexRank => {
            i = i + 1
            System.out.println(i + ": " + vertexRank.getVertexId() + " = " + vertexRank.getValue());
        } )
    }
}