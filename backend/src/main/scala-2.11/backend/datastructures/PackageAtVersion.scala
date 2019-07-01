package backend.datastructures

case class PackageAtVersion(packageName: String, packageVersion: String) {
  override def toString = s"$packageName@$packageVersion"
}
