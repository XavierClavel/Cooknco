package main.com.xavierclavel.containers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

/**
 * The real renderer, for the tests that assert on what an export contains.
 *
 * A stub would let every layout assertion pass against markup nothing ever printed, and the
 * questions worth asking of the export — does the sheet hold the whole recipe, does the
 * picture survive, does a saved layout actually change the output — are all questions about
 * the PDF that comes back. Docker is already required to run these tests.
 *
 * Started with the flags the deployment uses (`k8s/base/gotenberg.yaml`), so a layout that
 * only works because something was fetched from the network fails here too.
 */
object GotenbergTestContainer {

    val gotenberg: GenericContainer<*> = GenericContainer("gotenberg/gotenberg:8").apply {
        withCommand(
            "gotenberg",
            "--chromium-allow-list=^file:///tmp/.*",
            "--chromium-deny-list=^$",
            "--chromium-deny-private-ips",
            "--chromium-deny-public-ips",
            "--chromium-disable-javascript",
        )
        withExposedPorts(3000)
        waitingFor(Wait.forHttp("/health").forPort(3000))
        start()
    }

    fun getGotenbergUrl(): String = "http://${gotenberg.host}:${gotenberg.firstMappedPort}"
}
