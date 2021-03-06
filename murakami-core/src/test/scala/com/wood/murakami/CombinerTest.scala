package com.wood.murakami

import com.wood.murakami.directory.Fields
import com.wood.murakami.query._
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CombinerTest extends Specification {
  import Fields._

  def basicCombiner(selector: Seq[Selector],
                    filter: Option[Filter],
                    order: Option[Seq[Field]]): SelectionCombiner = {
    val combiner = SelectionCombiner(selector, filter, order)
    addToCombiner(combiner)
    combiner
  }

  def groupCombiner(selector: Seq[Selector],
                    filter: Option[Filter],
                    group: Field,
                    order: Option[Seq[Field]]): AggregateCombiner = {
    val combiner = AggregateCombiner(selector, filter, group, order)
    addToCombiner(combiner)
    combiner
  }

  def addToCombiner(combo: Combiner): Unit = {
    combo += "stb1|the matrix|2014-04-01|warner bros|4.00|1:30"
    combo += "stb2|the hobbit|2014-04-02|warner bros|8.00|9:30"
    combo += "stb2|the matrix|2014-04-02|netflix|4.50|10:30"
  }

  "Combiner" should {
    "select lines" in {
      val fieldsSelector = Seq(Selector(STB, None), Selector(TITLE, None))
      val combiner = basicCombiner(fieldsSelector, None, None)
      combiner.outputString mustEqual
        "stb1,the matrix\nstb2,the hobbit\nstb2,the matrix\n"
    }

    "filter lines" in {
      val fieldsSelector = Seq(Selector(STB, None), Selector(TITLE, None))
      val filter = Some(Filter(Equality(Fields.TITLE, "the matrix")))
      val combiner = basicCombiner(fieldsSelector, filter, None)
      combiner.outputString mustEqual
        "stb1,the matrix\nstb2,the matrix\n"
    }

    "group lines" in {
      val fieldsSelector = Seq(Selector(STB, Some(Collect())), Selector(TITLE, None),
        Selector(REV, Some(Sum())), Selector(PROVIDER, Some(Count())))
      val combiner = groupCombiner(fieldsSelector, None, TITLE, None)
      combiner.outputString mustEqual
        "[stb1,stb2],the matrix,8.5,2\n[stb2],the hobbit,8.0,1\n"
    }

    "sort select" in {
      val fieldsSelector = Seq(Selector(STB, None), Selector(DATE, None), Selector(VIEW_TIME, None))
      val combiner = basicCombiner(fieldsSelector, None, Some(Seq(DATE, VIEW_TIME)))
      combiner.sort
      combiner.outputString mustEqual
        "stb2,2014-04-02,10:30\nstb2,2014-04-02,9:30\nstb1,2014-04-01,1:30\n"
    }

    "sort group" in {
      val fieldsSelector = Seq(Selector(TITLE, Some(Collect())), Selector(STB, None))
      val combiner = groupCombiner(fieldsSelector, None, STB, Some(Seq(TITLE)))
      combiner.sort
      combiner.outputString mustEqual
        "[the matrix,the hobbit],stb2\n[the matrix],stb1\n"
    }

    "return multiple aggs" in {
      val fieldsSelector = Seq(Selector(STB, Some(Collect())), Selector(TITLE, None),
        Selector(REV, Some(Max())), Selector(PROVIDER, Some(Count())), Selector(REV, Some(Sum())))
      val combiner = groupCombiner(fieldsSelector, None, TITLE, Some(Seq(REV)))
      combiner.sort
      combiner.outputString mustEqual
        "[stb2],the hobbit,8.0,1,8.0\n[stb1,stb2],the matrix,4.5,2,8.5\n"
    }

    "do all the things" in {
      val fieldsSelector = Seq(Selector(STB, Some(Collect())), Selector(TITLE, None),
        Selector(REV, Some(Max())), Selector(PROVIDER, Some(Count())), Selector(REV, Some(Sum())))
      val filter = Some(Filter(Equality(STB, "stb2")))
      val combiner = groupCombiner(fieldsSelector, filter, TITLE, Some(Seq(REV)))
      combiner.sort
      combiner.outputString mustEqual
        "[stb2],the hobbit,8.0,1,8.0\n[stb2],the matrix,4.5,1,4.5\n"
    }
  }
}