package wormhole;

import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import com.google.protobuf.Message
import scala.collection.JavaConversions
import akka.actor.Props
import akka.actor.Actor
import wormhole.game.network.GameProto
import java.util.Arrays
import akka.actor.ActorLogging

/**
 * Class implementing thread-safe writes to an OutputStream.  This class allows atomic writing of a list of protobuf messages, such
 * that all the messages in a list of messages will be written in order, without any messages from other threads being written
 * in between. The writer writes messages to output using writeDelimitedTo.
 */
class ThreadsafeMessageWriter(output:OutputStream){
	/**
	 * The backend actor that performs the actual writing to the output stream.
	 */
	val ref = WormholeSystem.actorOf(Props(new ThreadsafeMessageWriterImpl(output)))
	/**
	 * Writes messages atomically.  Takes a variable number of messages.
	 */
	def write(messages:Message*){
		val msgList = messages.toList
		write(msgList)
	}
	/**
	 * Writes a list of messages atomically.
	 */
	def write(messages:List[Message]){
		ref ! messages
	}
	/**
	 * Shuts down this ThreadsafeMessageWriter, closing its OutputStream
	 */
	def close(){
		ref ! 'Close
	}
}

/**
 * Backend actor class, performing actual operations on the output stream.
 */
private class ThreadsafeMessageWriterImpl(val output:OutputStream) extends Actor with ActorLogging{
	
	def receive = {
		case messages:List[Message] =>
			log.debug("Writing " + messages.length + " messages to ThreadsafeMessageWriter")
			try{
				//write all messages
				messages foreach {_.writeDelimitedTo(output)}
			}catch{
				case e:IOException =>
					log.warning("ThreadsafeMessageWriter failed.  Message: " + e.getMessage())
					context.stop(self)
			}
		case 'Close =>
			log.info("Closing ThreadsafeMessageWriter")
			//close output and shut down this actor
			output.close()
			context.stop(self)
	}
}
