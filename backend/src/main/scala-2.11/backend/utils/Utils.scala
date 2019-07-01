package backend.utils

import java.io._
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.Base64

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.stats.CacheStats

import scala.collection.parallel.{ParMap, ParSeq}
import scala.concurrent._
import scala.concurrent.duration._
import scala.io.Source
import scala.runtime.ScalaRunTime
import scala.util._

object Utils {
  private val log = Logger(this.getClass.getSimpleName, Log.Level.Info)

  /**
    * Takes the sets A and B and computes the product
    *
    * { a.b | a \in A, b \in B }
    *
    */
  def cross[X](xs: Set[X], ys: Set[X], comp: (X, X) => X): Set[X] = {
    if (xs.isEmpty || ys.isEmpty) Set()
    else for { x <- xs; y <- ys } yield comp(x, y)
  }

  def crossStream[X, Y](xs: Stream[X], ys: Stream[X], comp: (X, X) => Y): Stream[Y] = {
    if (xs.isEmpty || ys.isEmpty) Stream()
    else for { x <- xs; y <- ys } yield comp(x, y)
  }

  def retry[T](op: => Future[T], msg: Throwable => Unit)(
    implicit ec: ExecutionContext): Future[T] = {
    op recoverWith {
      case x =>
        msg(x)
        retry(op, msg)
    }
  }

  implicit class ExtendedBoolean(a: Boolean) {
    def implies(b: => Boolean) = {
      !a || b
    }
  }

  def readResource(path: String): String = {
    TryWithResource(getClass.getResourceAsStream(path)) { stream =>
      val lines = scala.io.Source.fromInputStream(stream).getLines
      lines.mkString("\n")
    }.get
  }

  def readFile(file: Path): String = {
    TryWithResource(Source.fromFile(file.toAbsolutePath.toFile)) { bs =>
      bs.getLines().mkString("\n")
    }.get
  }

  def writeToFile(file: Path,
                  str: String,
                  silent: Boolean = true,
                  append: Boolean = false): Unit =
    _writeToFile(file.toFile, str, silent, append)

  private def _writeToFile(file: File,
                           str: String,
                           silent: Boolean = true,
                           append: Boolean = false): Unit = {
    TryWithResource(new BufferedWriter(new FileWriter(file.getAbsoluteFile, append))) {
      bw =>
        bw.write(str)
        if (!silent) log.info(s"Written ${file.getAbsolutePath}")
    }.get
  }

  def truncate(s: String, n: Int): String = {
    if (s.length <= n) {
      s
    } else {
      s.take(s.lastIndexWhere(_.isSpaceChar, n + 1)).trim
    }
  }

  /**
    * Converts strange git/ssh repository address to https
    */
  def sshtohttps(s: String): String = {
    val gitre =
      "((?:git|git\\+ssh|https|git\\+https)://)?(git@)?([^:/]+)[:/]([^/]+)/(.*)"
    s.replaceFirst(gitre, "https://$3/$4/$5")
  }

  /**
    * Convert a base64 string in the Github format to a string
    */
  def fromGithubBase64(base64: String): String = {
    var cleans = base64.replace("\n", "")
    cleans = cleans.replace(" ", "")
    cleans = cleans.replace("\r", "")
    new String(Base64.getDecoder.decode(cleans))
  }

  class CachedMemoize[T <: Object, R <: Object](f: T => R) extends (T => R) {
    private val cache = Caffeine.newBuilder().build[T, R]()

    def apply(x: T): R = {
      val v = cache.getIfPresent(x)
      if (v != null) {
        v
      } else {
        val y = f(x)
        cache.put(x, y)
        y
      }
    }

    def stats: CacheStats = cache.stats()
  }

  def recursiveList(dir: File): Stream[File] = {
    Option(dir.listFiles).map(_.toList.sortBy(_.getName).toStream).map { files =>
      files.append(files.filter(_.isDirectory).flatMap(recursiveList))
    } getOrElse {
      println("exception: dir cannot be listed: " + dir.getPath)
      Stream.empty
    }
  }

  object CachedMemoize {
    def apply[T <: Object, R <: Object](f: (T) => R) =
      new CachedMemoize(f)

    def build2[T1 <: Object, T2 <: Object, T3 <: Object, R <: Object](f: (T1, T2) => R) =
      new CachedMemoize(f.tupled)

    def build3[T1 <: Object, T2 <: Object, T3 <: Object, R <: Object](
      f: (T1, T2, T3) => R) = new CachedMemoize(f.tupled)

    def build3[T1 <: Object, T2 <: Object, T3 <: Object, T4 <: Object, R <: Object](
      f: (T1, T2, T3, T4) => R) = new CachedMemoize(f.tupled)
  }

  def copyDirectoryRecursively(from: Path, to: Path): Unit = {
    if (!from.isAbsolute || !to.isAbsolute) {
      // sanity checking
      throw new IllegalArgumentException(
        "Can only copy with absolute paths: " + from + " -> " + to)
    }
    try {
      Files.walkFileTree(
        from,
        new SimpleFileVisitor[Path]() {

          override def preVisitDirectory(dir: Path,
                                         attrs: BasicFileAttributes): FileVisitResult = {
            Files.createDirectories(resolve(dir))
            FileVisitResult.CONTINUE
          }

          override def visitFile(file: Path, attrs: BasicFileAttributes) = {
            Files.copy(file, resolve(file))
            FileVisitResult.CONTINUE
          }

          def resolve(file: Path): Path = {
            to.resolve(from.relativize(file)).normalize()
          }
        })
    } catch {
      case e: IOException =>
        throw new RuntimeException(e)
    }
  }

  def retry[X](times: Int, procedure: () => X, wait: Duration = 0.seconds): X = {
    val tries = (0 until times).toStream map (n =>
      try Left(procedure())
      catch {
        case e: Exception =>
          if (wait.gt(0.seconds)) {
            Thread.sleep(wait.toMillis)
          }
          log.info(s"Retrying ${procedure.toString()}")
          Right(e)
      })

    tries find (_.isLeft) match {
      case Some(Left(result)) => result
      case _                  => throw tries.reverse.head.right.get
    }
  }

  trait SavedHashCode extends Product {
    override val hashCode = ScalaRunTime._hashCode(this)
  }

  implicit class ParSeqWithMapBy[K](l: ParSeq[K]) {
    def mapBy[V](vFunc: K => V): ParMap[K, V] = {
      l.map(a => a -> vFunc(a)).toMap
    }
  }

  implicit class SeqWithMapBy[K](l: Seq[K]) {
    def mapBy[V](vFunc: K => V): Map[K, V] = {
      l.map(a => a -> vFunc(a)).toMap
    }
  }

  implicit class MapWithMapValuesWithKey[K, V](l: Map[K, V]) {
    def mapValuesWithKey[W](vFunc: (K, V) => W): Map[K, W] = {
      l.map(p => p._1 -> vFunc(p._1, p._2))
    }
  }

  implicit class MapOfMapOps[K1, K2, V](m: Map[K1, Map[K2, V]]) {
    def swapKeys(): Map[K2, Map[K1, V]] = {
      m.flatMap {
          case (k1, m1) =>
            m1.map {
              case (k2, v) =>
                (k1, k2, v)
            }
        }
        .groupBy(_._2)
        .mapValues(_.groupBy(_._1).mapValues(vs => vs.head._3))
    }
  }
}
