package backend.commands.benchmarking

class WarningCategorization {

  object Category extends Enumeration {
    val BREAKING, NON_BREAKING, POSSIBLY_BREAKING, NOT_UNDERSTOOD = Value
  }

  val categorization: Map[String, Map[String, Category.Value]] = Map(
    "lodash@3.3.0" -> Map(
      "require(lodash).defaults.(3).arg2.*" -> Category.BREAKING,
      "require(lodash).merge.(4).arg2.*" -> Category.BREAKING),
    "lodash@3.3.1" -> Map(
      "require(lodash).defaults.(3).arg2.*" -> Category.BREAKING,
      "require(lodash).merge.(4).arg2.*" -> Category.BREAKING),
    "lodash@4.0.0" -> Map(
      "require(lodash).assign.(2).arg0.*" -> Category.BREAKING,
      "require(lodash).compose" -> Category.BREAKING,
      "require(lodash).contains" -> Category.BREAKING,
      "require(lodash).foldl" -> Category.BREAKING,
      "require(lodash).pluck" -> Category.BREAKING,
      "require(lodash).unique" -> Category.BREAKING))
}
