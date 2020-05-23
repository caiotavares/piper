package proxy

import com.twitter.finagle.{Client, Server}

class Piper {

  val client: Client
  val server: Server

}
