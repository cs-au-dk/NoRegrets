package distilling.server.utils

import scala.concurrent._

object Futures {

  //Note: taken from foursquare open source code
  def groupedCollect[A, B](xs: Seq[A], par: Int)(f: A => Future[B])(
    implicit ec: ExecutionContext): Future[Seq[B]] = {
    val bsF: Future[Seq[B]] = Future.successful(Seq.empty[B])
    xs.grouped(par).foldLeft(bsF) {
      case (bsF, group) => {
        for {
          bs <- bsF
          xs <- Future.sequence(group.map(f))
        } yield bs ++ xs
      }
    }
  }

  def groupedCollectStream[B](xs: Stream[Future[B]], par: Int)(
    implicit ec: ExecutionContext): Future[Seq[B]] = {
    val bsF: Future[Seq[B]] = Future.successful(Seq.empty[B])
    xs.grouped(par).foldLeft(bsF) {
      case (bsF, group) => {
        for {
          bs <- bsF
          xs <- Future.sequence(group)
        } yield bs ++ xs
      }
    }
  }

}
