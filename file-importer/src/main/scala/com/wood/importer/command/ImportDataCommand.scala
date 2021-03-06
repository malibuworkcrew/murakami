package com.wood.importer.command

import akka.actor.ActorRef
import akka.pattern.ask
import com.webtrends.harness.command._
import com.webtrends.harness.component.spray.route.SprayGet
import com.wood.importer.FileRequest

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

object ImportDataCommand {
  val commandName = "ImportDataCommand"
}

class ImportDataCommand(importLeaders: ActorRef) extends Command with SprayGet {
  override def commandName: String = ImportDataCommand.commandName
  override def path: String = "/v1/import"

  override def execute[T](bean: Option[CommandBean])(implicit evidence$1: Manifest[T]): Future[BaseCommandResponse[T]] = {
    bean match {
      case Some(b) =>
        b.get("path") match {
          case Some(path) =>
            val p = Promise[BaseCommandResponse[T]]()
            importLeaders ? FileRequest(path.toString) onComplete {
              case Success(resp) => p success CommandResponse[T](Some(resp.asInstanceOf[T]), "text/plain")
              case Failure(f) => p failure CommandException(commandName, f)
            }
            p.future
          case None => Future.failed(CommandException(commandName, "Must set 'path'"))
        }
      case None =>
        Future.failed(CommandException(commandName, "Bean required"))
    }
  }
}
