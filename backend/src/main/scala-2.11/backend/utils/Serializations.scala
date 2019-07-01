package backend.utils

import java.io._
import java.nio.file.Path

import backend.datastructures.SerializerFormats
import org.json4s.Formats
import org.json4s.native.Serialization._
import org.nustaq.serialization._

trait Serializer {
  def serialize[T <: AnyRef](obj: T, to: Path, pretty: Boolean = true)(
    implicit m: Manifest[T]): Unit

  def deserialize[T <: AnyRef](file: Path)(implicit m: Manifest[T]): T

  val extension: String
}

case class JsonSerializer()(
  implicit val jsonSerializationFormats: Formats =
    SerializerFormats.commonSerializationFormats)
    extends Serializer {
  val extension = ".json"

  def serialize[T <: AnyRef](obj: T, to: Path, pretty: Boolean = true)(
    implicit m: Manifest[T]): Unit = {
    if (!to.getFileName.toString.endsWith(extension))
      throw new RuntimeException(
        s"Please preserve extension ${extension} for json serialized objects: ${to}")
    if (pretty) {
      Utils.writeToFile(to, writePretty[T](obj))
    } else {
      Utils.writeToFile(to, write[T](obj))
    }
  }

  def deserialize[T <: AnyRef](path: Path)(implicit m: Manifest[T]): T = {
    val content = Utils.readFile(path)
    if (content.trim.isEmpty)
      throw new RuntimeException(s"Empty file at ${path}")
    read[T](content)
  }
}

case object FastJavaSerializer extends Serializer {
  val extension = ".bin"

  def serialize[T <: AnyRef](obj: T, to: Path, pretty: Boolean)(
    implicit m: Manifest[T]): Unit = {
    if (!to.getFileName.toString.endsWith(extension))
      throw new RuntimeException(
        s"Please preserve extension ${extension} for java serialized objects: ${to}")
    JavaSerialization.fastSerialize(to, obj)
  }

  def deserialize[T <: AnyRef](file: Path)(implicit m: Manifest[T]): T =
    JavaSerialization.fastDeserialize(file)

  private object JavaSerialization {

    def serialize[T](path: Path, obj: T): Unit = {
      val oos = new ObjectOutputStream(new FileOutputStream(path.toAbsolutePath.toString))
      oos.writeObject(obj)
      oos.close()
    }

    def fastSerialize[T](path: Path, obj: T): Unit = {
      val out = new FSTObjectOutput(new FileOutputStream(path.toAbsolutePath.toString))
      out.writeObject(obj, null)
      out.close()
    }

    def deserialize[T](path: Path): T = {
      val ois = new ObjectInputStream(new FileInputStream(path.toAbsolutePath.toString))
      val obj = ois.readObject.asInstanceOf[T]
      ois.close()
      obj
    }

    def fastDeserialize[T](path: Path): T = {
      val in = new FSTObjectInput(new FileInputStream(path.toAbsolutePath.toString))
      val result = in.readObject.asInstanceOf[T]
      in.close()
      result
    }

  }
}
