package backend.presentation

object Dot {

  /**
    * Generator for fresh node IDs.
    */
  object IDGenerator {
    private var current: Int = 0

    def getNewId: Int = {
      current += 1
      current
    }
  }

  /**
    * Super-class for elements of a Graphviz dot file.
    */
  trait DotElement {

    /**
      * Produces a dot string representation of this element.
      */
    def toDotString: String
  }

  /**
    * Represents a node in a Graphviz dot file.
    */
  class DotNode(val id: String,
                val label: String,
                val additionalParams: Map[String, String])
      extends DotElement {

    def this(label: String, additionalParams: Map[String, String] = Map()) =
      this("n" + IDGenerator.getNewId, label, additionalParams)

    def this() = this("")

    def equals(other: DotNode) = toDotString.equals(other.toDotString)

    override def toString: String = toDotString

    def toDotString: String =
      id + "[label=\"" + escape(label) + "\"" +
        additionalParams.map(p => s"${p._1} = ${p._2}").mkString(", ") + "]"

  }

  /**
    * Represents an edge between two nodes in a Graphviz dot file.
    */
  class DotArrow(val fromNode: DotNode,
                 arrow: String,
                 val toNode: DotNode,
                 val label: String)
      extends DotElement {

    def equals(other: DotArrow) = toDotString.equals(other.toDotString)

    def toDotString: String =
      fromNode.id + " " + arrow + " " + toNode.id + "[label=\"" + escape(label) + "\"]"
  }

  /**
    * Represents a directed edge between two nodes in a Graphviz dot file.
    */
  class DotDirArrow(fromNode: DotNode, toNode: DotNode, label: String)
      extends DotArrow(fromNode, "->", toNode, label) {
    def this(fromNode: DotNode, toNode: DotNode) = this(fromNode, toNode, "")
  }

  /**
    * Represents a Graphviz dot graph.
    */
  class DotGraph(val title: String,
                 val nodes: Iterable[DotNode],
                 val edges: Iterable[DotArrow])
      extends DotElement {

    def this(nodes: List[DotNode], edges: List[DotArrow]) = this("", nodes, edges)

    def this(title: String) = this(title, List(), List())

    def this() = this(List(), List())

    def addGraph(g: DotGraph): DotGraph = {
      val ng = g.nodes.foldLeft(this)((g, n) => g.addNode(n))
      g.edges.foldLeft(ng)((g, e) => g.addEdge(e))
    }

    def addNode(n: DotNode): DotGraph =
      if (nodes.exists((a) => n.equals(a))) this
      else new DotGraph(title, nodes ++ List(n), edges)

    def addEdge(e: DotArrow): DotGraph =
      if (edges.exists((a) => e.equals(a))) this
      else new DotGraph(title, nodes, edges ++ List(e))

    override def toString: String = toDotString

    def toDotString =
      "digraph " + title + "{" + (nodes ++ edges).foldLeft("")(
        (str, elm) => str + elm.toDotString + "\n"
      ) + "}"
  }

  /**
    * Escapes special characters in the given string.
    * Special characters are all Unicode chars except 0x20-0x7e but including \, ", {, and }.
    */
  def escape(s: String): String = {
    if (s == null)
      return null
    val b = new StringBuilder()
    for (i <- 0 until s.length) {
      val c = s.charAt(i)
      c match {
        case '"' =>
          b.append("\\\"")
        case '\\' =>
          b.append("\\\\")
        case '\b' =>
          b.append("\\b")
        case '\t' =>
          b.append("\\t")
        case '\n' =>
          b.append("\\n")
        case '\r' =>
          b.append("\\r")
        case '\f' =>
          b.append("\\f")
        case '<' =>
          b.append("\\<")
        case '>' =>
          b.append("\\>")
        case '{' =>
          b.append("\\{")
        case '}' =>
          b.append("\\}")
        case _ =>
          if (c >= 0x20 && c <= 0x7e)
            b.append(c)
          else {
            b.append("\\%04X".format(c.toInt))
          }
      }
    }
    b.toString()
  }
}
