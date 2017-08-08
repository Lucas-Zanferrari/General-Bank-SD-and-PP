package controllers

import javax.inject.Inject

import play.api.Logger
import play.api.libs.json.{JsObject, JsString}
import play.api.mvc._


/**
  * A very small controller that renders a home page.
  */
class HomeController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def lifeStatus = Action { implicit request =>
    Logger.info(s"${getClass.getCanonicalName}: Central Bank asked if this bank is still online. Responding 'Yes'.")
    Ok(JsObject(Seq(
      "message" -> JsString("I'm alive!")
    )))
  }
}
