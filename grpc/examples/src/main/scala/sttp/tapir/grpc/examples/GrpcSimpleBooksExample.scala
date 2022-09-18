package sttp.tapir.grpc.examples

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import cats.implicits._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import sttp.tapir._
import sttp.tapir.grpc.protobuf._
import sttp.tapir.grpc.protobuf.model._
import sttp.tapir.grpc.protobuf.pbdirect._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkagrpc.AkkaGrpcServerInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.grpc.examples.gen.{SimpleBook => GenSimpleBook, Library => GenLibrary, LibraryClient => GenLibraryClient}

import java.io.ByteArrayOutputStream
import scala.concurrent.{Await, ExecutionContext, Future}

case class SimpleBook(title: String)

/** Descriptions of endpoints used in the example.
  */
object Endpoints {
  val addBook = endpoint
    .in("Library" / "AddBook")
    .in(grpcBody[SimpleBook])
    .out(grpcBody[SimpleBook])

  val es = List(addBook)
}

object SimpleBooksExample extends StrictLogging {

  import Endpoints._

  def booksServerEndpoints: List[ServerEndpoint[Any, Future]] =
    List(
      addBook.serverLogic { book =>
        println(book)
        Future.successful(book.asRight[Unit])
      }
    )

  def main(args: Array[String]): Unit = {
    val conf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    val system = ActorSystem("HelloWorld", conf)

    new TestServer(system).run()
  }
}

class TestServer(system: ActorSystem) {
  def run(): Future[Http.ServerBinding] = {
    // Akka boot up code
    implicit val sys: ActorSystem = system
    implicit val ec: ExecutionContext = sys.dispatcher

    val route = new AkkaGrpcServerInterpreter {
      override implicit def executionContext: ExecutionContext = ec
    }.toRoute(SimpleBooksExample.booksServerEndpoints)

    val binding = Http().newServerAt("127.0.0.1", 8080).bind(route)

    // report successful binding
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    binding
  }
}

object MainGenerator extends ProtoSchemaGenerator {
  val path: String = "/Users/mborek/OSS/tapir/grpc/examples/src/main/protobuf/main.proto"
  val endpoints = Endpoints.es
  val packageName = "sttp.tapir.grpc.examples.gen"

  generate()
}

object ClientMain extends App with StrictLogging {
  import scala.concurrent.duration._

  implicit val sys = ActorSystem("HelloWorldClient")
  implicit val ec = sys.dispatcher

  val client = GenLibraryClient(GrpcClientSettings.connectToServiceAt("localhost", 8080).withTls(false))  
  val result = Await.result(client.addBook(GenSimpleBook("TEST_BOOK")), 10.second)

  println(s"Result: [$result]")
}