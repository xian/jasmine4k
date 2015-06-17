package org.github.xian.jasmine

import org.junit.internal.AssumptionViolatedException
import org.junit.internal.runners.model.EachTestNotifier
import org.junit.runner.Description
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.ParentRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import java.util.*
import kotlin.reflect.KClass

var globalEnv: Env = DeclareEnv()

fun describe(clazz: Class<*>, fn: () -> Unit) {
  globalEnv.describe(clazz, fn)
}

fun describe(desc: String, fn: () -> Unit) {
  globalEnv.describe(desc, fn)
}

fun context(desc: String, fn: () -> Unit) {
  globalEnv.context(desc, fn)
}

fun it(desc: String, fn: () -> Unit) {
  globalEnv.it(desc, fn)
}

fun beforeEach(fn: () -> Unit) {
  globalEnv.beforeEach(fn)
}

fun afterEach(fn: () -> Unit) {
  globalEnv.afterEach(fn)
}

open class JasmineRunner(clazz: Class<*>) : ParentRunner<Node>(clazz) {
  var instance: Any? = null

  override public fun getChildren(): MutableList<Node>? {
    val env = DeclareEnv()
    globalEnv = env
    instance = newInstance()
    return env.topLevelNode.children as MutableList<Node>
  }

  override fun describeChild(child: Node): Description? {
    return child.describe()
  }

  override public fun runChild(child: Node, notifier: RunNotifier?) {
    runLeaf(object : Statement() {
      override fun evaluate() {
        val ancestors = child.getAncestors()
        val redeclareEnv = RedeclareEnv(ancestors)
        globalEnv = redeclareEnv
        newInstance()
        println(child.name + ":" + ancestors)

        if (redeclareEnv.foundFn == null) {
          throw RuntimeException("couldn't find node! ${child}")
        } else {
          redeclareEnv.foundFn!!()
        }
      }
    }, child.describe(), notifier)
  }

  private fun newInstance() = getTestClass().getJavaClass().newInstance()
}

interface Env {
  fun describe(clazz: Class<*>, fn: () -> Unit) {
    describe(clazz.getName(), fn)
  }

  fun describe(desc: String, fn: () -> Unit)
  fun context(desc: String, fn: () -> Unit)
  fun it(desc: String, fn: () -> Unit)

  fun beforeEach(fn: () -> Unit)
  fun afterEach(fn: () -> Unit)
}

class RedeclareEnv(val findNodes : List<Node>) : Env {
  var currentIndex = 0
  var currentNode = 1
  var foundFn : (() -> Unit)? = null

  val befores = arrayListOf<() -> Unit>()
  val afters = arrayListOf<() -> Unit>()

  override fun describe(desc: String, fn: () -> Unit) {
    check(desc, fn)
  }

  override fun context(desc: String, fn: () -> Unit) {
    check(desc, fn)
  }

  override fun it(desc: String, fn: () -> Unit) {
    check(desc, fn)
  }

  private fun check(desc: String, fn: () -> Unit) {
    if (foundFn != null) return

    println("${findNodes[currentNode]} == $currentIndex?")
    if (findNodes[currentNode].index == currentIndex) {
      println("Found! ${desc}")

      if (currentNode == findNodes.size() - 1) {
        foundFn = fun() {
          befores.forEach { it.invoke() }

          try {
            fn()
          } finally {
            afters.forEach { it.invoke() }
          }
        }
      } else {
        currentIndex = 0
        currentNode++

        val parentAfters = ArrayList(afters)
        afters.clear()

        fn()

        afters.addAll(parentAfters)
      }
      return
    } else {
      currentIndex++
    }
  }

  override fun beforeEach(fn: () -> Unit) {
    befores.add(fn)
  }

  override fun afterEach(fn: () -> Unit) {
    afters.add(fn)
  }
}

class DeclareEnv : Env {
  var currentContextsList = arrayListOf<Node>()
  val topLevelNode = Node("<top level>", currentContextsList, 0)
  var currentNode = topLevelNode

  override fun describe(desc: String, fn: () -> Unit) {
    pushNode(desc, fn)
  }

  override fun context(desc: String, fn: () -> Unit) {
    pushNode(desc, fn)
  }

  override fun it(desc: String, fn: () -> Unit) {
    pushExample(desc)
  }

  override fun beforeEach(fn: () -> Unit) {
  }

  override fun afterEach(fn: () -> Unit) {
  }

  private fun pushNode(desc: String, fn: () -> Unit) {
    val children = arrayListOf<Node>()
    val newNode = Node(desc, children, currentContextsList.size(), currentNode)
    val priorContextsList = currentContextsList
    val priorNode = currentNode
    currentContextsList = children
    currentNode = newNode

    fn()

    currentContextsList = priorContextsList
    currentNode = priorNode

    currentContextsList.add(newNode)
  }


  private fun pushExample(desc: String) {
    currentContextsList.add(ExampleNode(desc, currentContextsList.size(), currentNode))
  }
}

open class Node(val name: String, val children: List<Node>, val index: Int, val parent: Node? = null) {
  init {
    println("Node: ${name} index ${index}")
  }

  fun describe(): Description? {
    val description = Description.createSuiteDescription(name)
    children.forEach { child -> description.addChild(child.describe()) }
    return description
  }

  fun getAncestors(): List<Node> {
    val parents = arrayListOf<Node>()
    populateAncestors(parents)
    return parents
  }

  private fun populateAncestors(parents: MutableList<Node>) {
    parent?.populateAncestors(parents)
    parents.add(this)
  }

  override fun toString(): String {
    return "Node: ${name} (${index})"
  }
}

class ExampleNode(name: String, index: Int, parent: Node? = null) : Node(name, emptyList(), index, parent)