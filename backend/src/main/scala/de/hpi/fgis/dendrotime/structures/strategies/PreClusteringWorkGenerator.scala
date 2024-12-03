package de.hpi.fgis.dendrotime.structures.strategies

import java.io.File

trait PreClusteringWorkGenerator[T] extends WorkGenerator[T] {

  def getPreClustersForMedoids(medoid1: T, medoid2: T): Option[(Array[T], Array[T], T)]

  def storeDebugMessages(debugFile: File): Unit
}
