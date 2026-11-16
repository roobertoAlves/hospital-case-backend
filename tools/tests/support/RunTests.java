package support;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

import java.io.PrintWriter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * Roda todos os testes JUnit 5 sem Maven nem IDE (usado por testar.bat / testar.sh). Fica em
 * ferramentas/testes, fora das pastas de codigo da IDE: depende do junit-platform-launcher.
 * No Eclipse e no IntelliJ, prefira o botao direito em src/test/java > Run As > JUnit Test.
 *
 * Uso: java -cp "build/classes;build/test-classes;lib/test/*;lib/compile/*;src/main/webapp/WEB-INF/lib/*"
 *      support.RodarTestes [pacote]
 */
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
        out.printf("%nTestes: %d executados, %d ok, %d falharam, %d ignorados (%d ms)%n",
                r.getTestsStartedCount(), r.getTestsSucceededCount(), r.getTotalFailureCount(),
                r.getTestsSkippedCount(), r.getTimeFinished() - r.getTimeStarted());
        System.exit(r.getTotalFailureCount() == 0 ? 0 : 1);
    }
}
