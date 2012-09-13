package wormhole.game

import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.pattern.ask
import wormhole.actor._
import wormhole.WormholeSystem
import com.google.protobuf.Message
import wormhole.game.network.GameProto
/**
 * Author: Brandon
 */
class WormholeMap(val width:Int, val height:Int, val players:List[Player]){
	val ref:ActorRef = WormholeSystem.actorOf(Props(new WormholeMapImpl(this)))
	
	var server:WormholeGameServer = null
	
	def player(id:PlayerId) = players find {_.id == id}
	
	def addObject(obj:BaseObject){
		ref ! obj
	}
	def addUnitGroup(group:UnitGroup){
		ref ! group
	}
	def updateAll(){
		ref ! 'Update
	}
	def objectAt(x:Int, y:Int) = {
		fetch ((ref ? ('At, x,y)).mapTo[Option[BaseObject]])
	}
	def objectsFuture = (ref ? 'Objects).mapTo[List[BaseObject]]
	def objects = fetch (objectsFuture)
	def unitGroupsFuture = (ref ? 'Units).mapTo[List[UnitGroup]]
	def unitGroups = fetch (unitGroupsFuture)
	
	def sendToAll(msg:Message*){
		if(server!=null){
			server.connections.foreach {_.out.write(msg.toList)}
		}
	}
}
 
/**
 * Actor backend for WormholeMap.
 * Author: Brandon
 */
private class WormholeMapImpl(main:WormholeMap) extends Actor{
	val MovesPerBaseUpdate = 5
	var ugID = 0
	var objects:List[BaseObject] = Nil
	var unitGroups:List[(Int, UnitGroup)] = Nil
	var count = 0
	def receive = {
		case obj:BaseObject =>
			objects ::= obj
		case group:UnitGroup =>
			val data = (ugID, group)
			ugID += 1
			unitGroups ::= data
			val mType = GameProto.IncomingMessageType.newBuilder().setType(GameProto.MessageType.NEW_UNIT_GROUP).build()
			val builder = GameProto.NewUnitGroup.newBuilder()
			builder.setId(data._1)
			val loc = group.location
			builder.setX(loc.x)
			builder.setY(loc.y)
			val msg = builder.build()
			main.sendToAll(mType, msg)
		case 'Update =>
			count += 1
			if(count==MovesPerBaseUpdate){
				objects foreach {_.update()}
				count = 0
			}
			unitGroups foreach {
				tup =>
					val (id, group) = tup
					group.update()
					val mType = GameProto.IncomingMessageType.newBuilder().setType(GameProto.MessageType.UNIT_GROUP_POSITION).build()
					val builder = GameProto.UnitGroupPosition.newBuilder()
					builder.setId(id)
					if(group.isComplete){
						builder.setComplete(true)
					}else{
						val loc = group.location
						builder.setX(loc.x)
						builder.setY(loc.y)
					}
					main.sendToAll(mType, builder.build())
			}
			unitGroups = unitGroups filterNot {_._2.isComplete}
		case ('At,x:Int,y:Int) =>
			val zipped = objects zip (objects map {_.dataFuture})
			val res = zipped find {
				tup =>
					val loc = fetch(tup._2).location
					loc.x==x && loc.y == y
			}
			sender ! (res map {_._1})
		case 'Objects =>
			sender ! objects
		case 'Units =>
			sender ! unitGroups
	}
}