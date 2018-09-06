package org.globalnames
package index
package nameresolver

import scala.collection.mutable.ArrayBuffer
import akka.http.impl.util._

@SuppressWarnings(Array("org.wartremover.warts.Var",
                        "org.wartremover.warts.NonUnitStatements"))
object ContextFinder {
  private final case class Tree(value: String, nodes: ArrayBuffer[Tree], var hits: Int)
  private val separator: Char = '|'

  private[ContextFinder] def updateTree(path: String, tree: Tree): Unit = {
    val pathChunks = path.fastSplit(separator)
    var treeCursor: Tree = tree
    for (pathChunk <- pathChunks) {
      treeCursor.nodes.find { n => n.value == pathChunk } match {
        case Some(t) =>
          t.hits += 1
          treeCursor = t
        case None =>
          val node = Tree(pathChunk, ArrayBuffer.empty[Tree], 1)
          treeCursor.nodes += node
          treeCursor = node
      }
    }
  }

  private[ContextFinder] def findContext(threshold: Double,
                                         path: StringBuilder,
                                         treeCursor: Tree): String = {
    val candidates = treeCursor.nodes.filter { n => n.hits >= threshold }
    if (candidates.isEmpty) {
      path.toString
    } else {
      val candidateMax = candidates.maxBy { _.hits }
      if (path.nonEmpty) path.append(separator)
      findContext(threshold, path.append(candidateMax.value), candidateMax)
    }
  }

  def find(paths: Seq[String]): String = {
    val tree = Tree("", ArrayBuffer.empty[Tree], 0)
    val threshold: Double = 0.8 * paths.size

    for (path <- paths) {
      updateTree(path, tree)
    }
    val contextPath = findContext(threshold, new StringBuilder, tree)
    contextPath
  }
}
