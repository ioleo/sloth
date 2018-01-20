package test.sloth.mycelium

import org.scalatest._
import scala.concurrent.Future

import sloth.core._
import sloth.boopickle._
import sloth.mycelium._
import mycelium.client._
import mycelium.server._

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.actor.ActorSystem
import cats.implicits._
import boopickle.Default._, java.nio.ByteBuffer
import scala.util.{Success, Failure}

//shared
trait Api[Result[_]] {
  def fun(a: Int): Result[Int]
}

object TypeHelper {
  type Event = String
  type State = String

  case class ApiResult[T](state: Future[State], events: Future[Seq[Event]], result: Future[T])
  type ApiResultFun[T] = Future[State] => ApiResult[T]

  sealed trait ApiError
  case class SlothError(msg: String) extends ApiError
  implicit class ApiException(error: ApiError) extends Exception(error.toString)
}
import TypeHelper._
//server
object ApiImpl extends Api[ApiResultFun] {
  def fun(a: Int): ApiResultFun[Int] =
    state => ApiResult(state, Future.successful(Seq.empty), Future.successful(a))
}

class MyceliumSpec extends AsyncFreeSpec with MustMatchers {

  implicit val apiResultFunctor = cats.derive.functor[ApiResult]
  implicit val apiResultFunFunctor = cats.derive.functor[ApiResultFun]

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val port = 9999

 "run" in {
    object Backend {
      import sloth.server._

      val config = ServerConfig(bufferSize = 5, overflowStrategy = OverflowStrategy.fail)

      val server = Server[ByteBuffer, ApiResultFun]
      val router1 = server.route[Api[ApiResultFun]](ApiImpl)
      val router2 = server.route[Api[ApiResultFun]](ApiImpl)
      val router = router1 or router2

      val handler = new SimpleRequestHandler[ByteBuffer, Event, ApiError, State] {
        def initialReaction = Reaction(Future.successful("empty"))
        def onRequest(state: Future[State], path: List[String], payload: ByteBuffer) = {
          router(Request(path, payload)) match {
            case Right(fun) =>
              val res = fun(state)
              Response(res.result.map(Right(_)), Reaction(res.state, res.events))
            case Left(err) =>
              Response(Future.successful(Left(SlothError(err.toString))), Reaction(state))
          }
        }
      }

      val mycelium = WebsocketServerFlow(config, handler)

      //TODO this test of actually running belong into mycelium project
      def run() = {
        import akka.http.scaladsl.server.RouteResult._
        import akka.http.scaladsl.server.Directives._
        import akka.http.scaladsl.Http

        val route = (path("ws") & get) {
          handleWebSocketMessages(mycelium)
        }

        Http().bindAndHandle(route, interface = "0.0.0.0", port = port).onComplete {
          case Success(binding) => println(s"Server online at ${binding.localAddress}")
          case Failure(err) => println(s"Cannot start server: $err")
        }
      }
    }

    object Frontend {
      import sloth.client._

      val config = ClientConfig(requestTimeoutMillis = 5 * 1000)
      val akkaConfig = AkkaWebsocketConfig(bufferSize = 5, overflowStrategy = OverflowStrategy.fail)

      val handler = new IncidentHandler[Event] {
        def onConnect(reconnect: Boolean): Unit = {}
        def onEvents(events: Seq[Event]): Unit = {}
      }

      val mycelium = WebsocketClient[ByteBuffer, Event, ApiError](
        AkkaWebsocketConnection(akkaConfig), config, handler)
      val client = Client[ByteBuffer, Future](mycelium)

      val api = client.wire[Api[Future]]

      def run() = {
        mycelium.run(s"ws://localhost:$port/ws")
      }
    }

    Backend.run()
    Frontend.run()

    Frontend.api.fun(1).map(_ mustEqual 1)
  }
}
