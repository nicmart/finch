package io.finch.playgorund

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.Httpx
import com.twitter.util.Await

import io.finch.{Endpoint => _, _}
import io.finch.micro._
import io.finch.request._
import io.finch.route.{Endpoint => _, _}
import io.finch.jackson._

/**
 * GET /user/groups        -> Seq[Group]
 * POST /groups?name=foo   -> Group
 * PUT /user/groups/:group -> User
 */
object Main extends App {

  // enable finch-jackson magic
  implicit val objectMapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  // model
  case class Group(name: String, ownerId: Int = 0)
  case class User(id: Int, groups: Seq[Group])

  case class UnknownUser(id: Int) extends Exception(s"Unknown user with id=$id")

  val currentUser: Micro[Int] = OptionalHeader("X-User-Id") ~~> { id =>
    id.getOrElse("0").toInt match {
      case i @ (1 | 2 | 3) => i.toFuture
      case i => UnknownUser(i).toFutureException
    }
  }

  // GET /user/groups -> Seq[Group]
  val getUserGroups: Micro[Seq[Group]] =
    currentUser ~> { userId => Seq(Group("foo", userId), Group("bar", userId)) }

  // POST /groups?name=foo -> Group
  val postGroup: Micro[Group] =
    RequiredParam("name") ~ currentUser ~> Group

  // PUT /user/groups/:group -> User
  def putUserGroup(group: String): Micro[User] =
    currentUser ~> { User(_, Seq.empty[Group]) }

  // an API endpoint
  val api: Endpoint =
    Get / "user" / "groups" /> getUserGroups |
    Post / "groups" /> postGroup |
    Put / "user" / "groups" / string /> putUserGroup

  Await.ready(Httpx.serve(":8081", api))
}
