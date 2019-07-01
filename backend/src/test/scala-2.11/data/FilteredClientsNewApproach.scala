package data

object FilteredClientsNewApproach {

  def SetNoDuplicates[T](elems: T*): Set[T] = {
    val s = Set(elems: _*)
    if (s.size != elems.size)
      throw new RuntimeException(
        s"Duplicates presents in: ${elems.groupBy(identity).values.filter(_.size > 1).map(_.mkString(",")).mkString("\n")}")
    s
  }

  val badClients: Map[String, Set[String]] =
    Map[String, Set[String]](
      "request@2.0.0" -> SetNoDuplicates(
        "html2pdf.it@1.7.0" //Looks like the tests are dependent on some locally spawned server
      ),
      "underscore@1.8.0" -> SetNoDuplicates(),
      "debug@2.0.0" -> SetNoDuplicates(
        "alinex-make@0.3.1", // generating empty observations
        "bittorrent-dht@2.3.1", // not using mocha
        "bittorrent-protocol@1.4.2", // not using mocha
        "bittorrent-tracker@2.6.1" // package.json scripts.test object do not contain a call to mocha
      ),
      "express@3.0.0" → SetNoDuplicates("formage@3.0.29"),
      "lodash@3.0.0" → SetNoDuplicates(
        //Produces a random number of observations for unknown reasons
        elems = "itunes-app-reviews@0.0.1",
        "soap-noendlessloop@0.15.0",
        "soap-extended@0.15.0"),
      "async@2.0.0" → SetNoDuplicates(
        "gulp-inline-ng2-template@2.0.0",
        //Causes TreeAPIModel construction issues sometimes but the issues is not reproducible
        "rigger@1.0.0"),
      "lodash@4.0.0" -> SetNoDuplicates(
        "hubiquitus-core@0.8.19" //trying to use preamble_proxy.js as a test.
      )).withDefaultValue(Set())

}
