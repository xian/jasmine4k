package org.github.xian.jasmine

import org.fest.assertions.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier
import java.util.*
import kotlin.test.assertTrue

class JasmineTest {
  Test public fun specsAreLoaded() {
    val runner = JasmineRunner(javaClass<FakeTest>())
    val description = runner.getDescription()!!

    assertThat(description.getDisplayName())
        .isEqualTo(javaClass<FakeTest>().getName())

    assertThat(description.getChildren().map { it.getDisplayName() })
        .containsExactly("java.lang.Object")

    val objDesc = description.getChildren()[0]
    assertThat(objDesc.getChildren().map { it.getDisplayName() })
        .containsExactly("when A", "when whatever", "when B")
  }

  Test public fun specsAreRun_passing() {
    val runner = JasmineRunner(javaClass<FakeTest>())
    assertThat(runChild("should pass", runner))
        .containsExactly("started: should pass", "finished: should pass")
  }

  Test public fun specsAreRun_failing() {
    val runner = JasmineRunner(javaClass<FakeTest>())
    FakeTest.log.clear()
    assertThat(runChild("should fail", runner)).containsExactly(
        "started: should fail",
        "failed: should fail: fake failure. Expected <true> actual <false>",
        "finished: should fail")

    assertThat(FakeTest.log).containsExactly(
        "outer before 1",
        "outer before 2",
        "inner before 1",
        "inner before 2",
        "should fail run",
        "inner after 1",
        "inner after 2",
        "outer after 1",
        "outer after 2",
        "final after"
    )
  }

  public class FakeTest() {
    companion object {
      val log = arrayListOf<String>()
    }

    init {
      describe(javaClass<Any>()) {
        beforeEach { log.add("outer before 1") }
        beforeEach { log.add("outer before 2") }
        afterEach { log.add("outer after 1") }
        afterEach { log.add("outer after 2") }

        context("when A") {
          beforeEach { log.add("when A before") }
          beforeEach { log.add("when A after") }
        }

        context("when whatever") {
          beforeEach { log.add("inner before 1") }
          afterEach { log.add("inner after 1") }

          it("should pass") {
            log.add("should pass run")
            assertTrue(true)
          }

          it("should fail") {
            log.add("should fail run")
            assertTrue(false, "fake failure")
          }

          beforeEach { log.add("inner before 2") }
          afterEach { log.add("inner after 2") }
        }

        context("when B") {
          beforeEach { log.add("when A before") }
          beforeEach { log.add("when A after") }
        }

        afterEach { log.add("final after") }
      }
    }
  }

  private fun runChild(name: String, runner: JasmineRunner): List<String> {
    val listener = LoggingListener()
    val runNotifier = RunNotifier().let { it.addListener(listener); it }
    runner.runChild(runner.getChildren()!!.find(name)!!, runNotifier)
    return listener.log
  }

  fun List<Node>.find(name: String): Node? {
    forEach {
      if (it.name == name) return it
      val found = it.children.find(name)
      if (found != null) return found
    }
    return null
  }

  class LoggingListener : RunListener() {
    val log = arrayListOf<String>()

    override fun testStarted(description: Description?) {
      log.add("started: ${description}")
    }

    override fun testFailure(failure: Failure?) {
      log.add("failed: ${failure}")
    }

    override fun testFinished(description: Description?) {
      log.add("finished: ${description}")
    }
  }
}
