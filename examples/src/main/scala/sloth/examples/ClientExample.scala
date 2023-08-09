package sloth.examples

import chameleon.*
import chameleon.ext.zioJson.given
import sloth.*
import zio.*
import zio.json.*

case class Response(items: List[Int], total: Int, page: Int, limit: Int)

trait Api {
  def list(page: Int, limit: Int): Task[Response]
}

object ClientExample:
  given JsonCodec[Response] = ???
  given ClientHandler[Task] = ???
  val transport: RequestTransport[String, Task] = ???
  val client = Client[String, Task](transport)
  client.wire[Api] 