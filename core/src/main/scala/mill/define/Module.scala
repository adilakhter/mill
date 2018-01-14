package mill.define

import java.lang.reflect.Modifier

import ammonite.main.Router.{EntryPoint, Overrides}
import ammonite.ops.Path

import scala.language.experimental.macros
import scala.reflect.ClassTag
import scala.reflect.macros.blackbox
/**
  * `Module` is a class meant to be extended by `trait`s *only*, in order to
  * propagate the implicit parameters forward to the final concrete
  * instantiation site so they can capture the enclosing/line information of
  * the concrete instance.
  */
class Module(implicit parentCtx0: mill.define.Ctx) extends mill.moduledefs.Cacher{

  def traverse[T](f: Module => Seq[T]): Seq[T] = {
    def rec(m: Module): Seq[T] = f(m) ++ m.modules.flatMap(rec)
    rec(this)
  }

  lazy val segmentsToModules = traverse{m => Seq(m.parentCtx.segments -> m)}
    .toMap

  lazy val modules = this.reflectNestedObjects[Module]
  lazy val segmentsToTargets = traverse{_.reflect[Target[_]]}
    .map(t => (t.ctx.segments, t))
    .toMap

  lazy val targets = segmentsToTargets.valuesIterator.toSet
  lazy val segmentsToCommands = traverse{
    m => m.reflectNames[Command[_]].map(c => m.parentCtx.segments ++ Seq(Segment.Label(c)))
  }.toSet

  def parentCtx = parentCtx0
  // Ensure we do not propagate the implicit parameters as implicits within
  // the body of any inheriting class/trait/objects, as it would screw up any
  // one else trying to use sourcecode.{Enclosing,Line} to capture debug info
  val millModuleEnclosing = parentCtx.enclosing
  val millModuleLine = parentCtx.lineNum
  def basePath: Path = parentCtx.basePath / (parentCtx.segment match{
    case Segment.Label(s) => List(s)
    case Segment.Cross(vs) => vs.map(_.toString)
  })
  implicit def millModuleBasePath: BasePath = BasePath(basePath)
  implicit def millModuleSegments: Segments = {
    parentCtx.segments0 ++ Seq(parentCtx.segment)
  }
  def reflect[T: ClassTag] = {
    this
      .getClass
      .getMethods
      .filter(!_.getName.contains('$'))
      .filter(_.getParameterCount == 0)
      .filter(x => (x.getModifiers & Modifier.STATIC) == 0)
      .filter(implicitly[ClassTag[T]].runtimeClass isAssignableFrom _.getReturnType)
      .map(_.invoke(this).asInstanceOf[T])
  }
  def reflectNames[T: ClassTag] = {
    this
      .getClass
      .getMethods
      .filter(x => (x.getModifiers & Modifier.STATIC) == 0)
      .filter(implicitly[ClassTag[T]].runtimeClass isAssignableFrom _.getReturnType)
      .map(_.getName)
  }
  // For some reason, this fails to pick up concrete `object`s nested directly within
  // another top-level concrete `object`. This is fine for now, since Mill's Ammonite
  // script/REPL runner always wraps user code in a wrapper object/trait
  def reflectNestedObjects[T: ClassTag] = {
    reflect[T] ++
    this
      .getClass
      .getClasses
      .filter(implicitly[ClassTag[T]].runtimeClass isAssignableFrom _)
      .flatMap(c => c.getFields.find(_.getName == "MODULE$").map(_.get(c).asInstanceOf[T]))
  }
}
trait TaskModule extends Module {
  def defaultCommandName(): String
}

class BaseModule(basePath: Path)
                (implicit millModuleEnclosing0: sourcecode.Enclosing,
                 millModuleLine0: sourcecode.Line,
                 millName0: sourcecode.Name,
                 overrides0: Overrides)
  extends Module()(
    mill.define.Ctx.make(implicitly, implicitly, implicitly, BasePath(basePath), Segments(), implicitly)
  )