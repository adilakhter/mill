package forge

import forge.define.Task
import forge.util.{Args, OSet}
import utest.assert

import scala.collection.mutable

object TestUtil {
  def test(inputs: Task[Int]*) = {
    new Test(inputs, pure = inputs.nonEmpty)
  }

  /**
    * A dummy target that takes any number of inputs, and whose output can be
    * controlled externally, so you can construct arbitrary dataflow graphs and
    * test how changes propagate.
    */
  class Test(val inputs: Seq[Task[Int]],
             val pure: Boolean) extends Task[Int]{
    var counter = 0
    def evaluate(args: Args) = {
      counter + args.args.map(_.asInstanceOf[Int]).sum
    }

    override def sideHash = counter
  }
  def checkTopological(targets: OSet[Task[_]]) = {
    val seen = mutable.Set.empty[Task[_]]
    for(t <- targets.items.reverseIterator){
      seen.add(t)
      for(upstream <- t.inputs){
        assert(!seen(upstream))
      }
    }
  }

}
