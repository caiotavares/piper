package proxy

import java.net.{InetSocketAddress, SocketAddress}

import com.twitter.finagle.client.{StackClient, StdStackClient, Transporter}
import com.twitter.finagle.dispatch.{SerialClientDispatcher, SerialServerDispatcher}
import com.twitter.finagle.netty4.{Netty4Listener, Netty4Transporter}
import com.twitter.finagle.server.{Listener, StackServer, StdStackServer}
import com.twitter.finagle.stats.{NullStatsReceiver, StatsReceiver}
import com.twitter.finagle.transport.{Transport, TransportContext}
import com.twitter.finagle.{Service, ServiceFactory, Stack}
import com.twitter.util.{Await, Closable}
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.string.{StringDecoder, StringEncoder}
import io.netty.util.CharsetUtil

object Piper extends App {

  type Request = String
  type Response = String

  class Server extends StdStackServer[Request, Response, Server] {

    override def stack: Stack[ServiceFactory[Request, Response]] = StackServer.newStack[Request, Response]

    override def params: Stack.Params = StackServer.defaultParams

    override type In = Request
    override type Out = Response
    override type Context = TransportContext

    private def pipelineInitializer(pipeline: ChannelPipeline): Unit = {
      pipeline.addLast("in:stringDecoder", new StringDecoder(CharsetUtil.UTF_8))
      pipeline.addLast("out:stringEncoder", new StringEncoder(CharsetUtil.UTF_8))
    }

    override protected def newListener(): Listener[In, Out, Context] = {
      Netty4Listener(pipelineInitializer, params)
    }

    override protected def newDispatcher(transport: Transport[Request, Response] {type Context <: TransportContext}, service: Service[Request, Response]): Closable = {
      new SerialServerDispatcher(transport, service)
    }

    override protected def copy1(newStack: Stack[ServiceFactory[In, Out]], newParams: Stack.Params): Server = {
      new Server() {
        override def stack: Stack[ServiceFactory[Request, Response]] = newStack

        override def params: Stack.Params = newParams
      }
    }
  }

  class Client extends StdStackClient[Request, Response, Client] {

    override def stack: Stack[ServiceFactory[Request, Response]] = StackClient.newStack[Request, Response]

    override def params: Stack.Params = StackClient.defaultParams

    override type In = Request
    override type Out = Response
    override type Context = TransportContext

    protected val statsReceiver: StatsReceiver = NullStatsReceiver

    private def pipelineInitializer(pipeline: ChannelPipeline): Unit = {
      pipeline.addLast("in:stringEncoder", new StringEncoder(CharsetUtil.UTF_8))
      pipeline.addLast("out:stringDecoder", new StringDecoder(CharsetUtil.UTF_8))
    }

    override protected def newTransporter(addr: SocketAddress): Transporter[In, Out, Context] = {
      Netty4Transporter.raw[In, Out](pipelineInitializer, addr, params)
    }

    override protected def newDispatcher(transport: Transport[In, Out] {type Context <: TransportContext}): Service[Request, Response] = {
      new SerialClientDispatcher(transport, statsReceiver)
    }

    protected override def copy1(newStack: Stack[ServiceFactory[Request, Response]], newParams: Stack.Params): Client = {
      new Client() {
        override def stack: Stack[ServiceFactory[Request, Response]] = newStack

        override def params: Stack.Params = newParams
      }
    }
  }

  def serve(port: Int): Unit = {
    val client = new Client().newService(":9001")
    val server = new Server().serve(new InetSocketAddress(port), client)
    Await.ready(server)
    println(s"Serving on $port...")
  }

  serve(9000)
}
