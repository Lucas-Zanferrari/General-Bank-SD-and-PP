package controllers

import javax.inject.Inject
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
    Ok(JsObject(Seq(
      "message" -> JsString("I'm alive!")
    )))
  }
}
