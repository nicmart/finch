package io.finch

import com.twitter.finagle.Service
import com.twitter.util.Future

import io.finch.route.{Endpoint => _, _}
import io.finch.response._
import io.finch.request._

/**
 * An experimental package that enables `micro`-services support in Finch.
 */
package object micro {

  /**
   * `RequestReader` is a composable microservice or just `Micro`.
   */
  type Micro[A] = RequestReader[A]

  /**
   * A companion object for `Micro`.
   */
  val Micro = RequestReader

  /**
   *
   */
  type Endpoint = Router[Micro[HttpResponse]]

  implicit class MicroRouterOps[A](r: Router[Micro[A]]) {
    def |[B](that: Router[Micro[B]])(implicit eA: EncodeResponse[A], eB: EncodeResponse[B]): Endpoint =
      r.map(_.map(Ok(_))) orElse that.map(_.map(Ok(_)))
  }

  implicit def microToHttpMicro[A](m: Micro[A])(
    implicit e: EncodeResponse[A]
  ): Micro[HttpResponse] = m.map(Ok(_))

  implicit def microRouterToEndpoint[M](r: Router[M])(
    implicit ev: M => Micro[HttpResponse]
  ): Endpoint = r.map(ev)

  implicit def endpointToFinagleService[M](r: Router[M])(
    implicit ev: M => Micro[HttpResponse]
  ): Service[HttpRequest, HttpResponse] = new Service[HttpRequest, HttpResponse] {
    def apply(req: HttpRequest): Future[HttpResponse] = r.map(ev)(requestToRoute(req)) match {
      case Some((Nil, micro)) => micro(req)
      case _ => RouteNotFound(s"${req.method.toString.toUpperCase} ${req.path}").toFutureException
    }
  }
}
