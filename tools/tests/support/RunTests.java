package support;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

import java.io.PrintWriter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

public final class RunTests {

    private RunTests() {
    }

    public static void main(String[] args) {
        String packageName = args.length > 0 ? args[0] : "";
        LauncherDiscoveryRequest order = LauncherDiscoveryRequestBuilder.request()
                .selectors(packageName.isEmpty()
                        ? new org.junit.platform.engine.DiscoverySelector[] {
                            selectPackage("controllers"), selectPackage("database"), selectPackage("models"),
                            selectPackage("security"), selectPackage("utils")}
                        : new org.junit.platform.engine.DiscoverySelector[] {selectPackage(packageName)})
                .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener summary = new SummaryGeneratingListener();
        launcher.execute(order, summary);
        TestExecutionSummary r = summary.getSummary();
        PrintWriter out = new PrintWriter(System.out, true);
        r.printFailuresTo(out, 25);
        out.printf("%nTests: %d run, %d passed, %d failed, %d skipped (%d ms)%n",
                r.getTestsStartedCount(), r.getTestsSucceededCount(), r.getTotalFailureCount(),
                r.getTestsSkippedCount(), r.getTimeFinished() - r.getTimeStarted());
        System.exit(r.getTotalFailureCount() == 0 ? 0 : 1);
    }
}
