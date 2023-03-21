package plugins;

import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.test.acceptance.Matchers;
import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.docker.fixtures.SshAgentContainer;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.ant.AntBuildStep;
import org.jenkinsci.test.acceptance.plugins.ant.AntInstallation;
import org.jenkinsci.test.acceptance.plugins.ssh_slaves.SshSlaveLauncher;
import org.jenkinsci.test.acceptance.po.*;
import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;
import org.openqa.selenium.By;

@SuppressWarnings("CdiInjectionPointsInspection")
@WithPlugins({"ant", "ssh-slaves"})
public class AntPluginTest extends AbstractJUnitTest {

    private static final String INSTALL_VERSION_1_8 = "1.8.4";
    private static final String INSTALL_NAME_1_8 = "ant_" + INSTALL_VERSION_1_8;

    private static final String INSTALL_VERSION_1_10 = "1.10.5";
    private static final String INSTALL_NAME_1_10 = "ant_" + INSTALL_VERSION_1_10;

    private static final String NATIVE_ANT_NAME = "native_ant";

    private static final String OK_PROP1 = "okPROP1=foo_bar_ok_1";
    private static final String OK_PROP2 = "okPROP2=foo_bar_ok_2";
    private static final String NOK_PROP1 = "nokPROP1=foo_bar_nok_1";
    private static final String NOK_PROP2 = "nokPROP2=foo_bar_nok_2";
    private static final String PROPERTIES = OK_PROP1+"\n"+OK_PROP2+"\n"+NOK_PROP1+"\n"+NOK_PROP2;
    private static final String OPTS = "-showversion";
    private static final String BUILD_FILE = "custom-build-file.xml";
    private static final String FAKE_BUILD_FILE = "fake.xml";
    private static final String SCRIPT_PIPELINE_ANT = "node {\n" +
            "    withAnt(installation: '" + NATIVE_ANT_NAME + "') {\n" +
            "        if (isUnix()) {\n" +
            "            sh \"ant -version\"\n" +
            "        } else {\n" +
            "            bat \"ant -version\"\n" +
            "        }\n" +
            "    }\n" +
            "}";
    FreeStyleJob job;
    private AntBuildStep step;

    public static final String REMOTE_FS = "/tmp";

    @Inject private DockerContainerHolder<SshAgentContainer> docker;

    private SshAgentContainer sshd;
    private DumbSlave slave;

    @Before
    public void setUp() {
        job = jenkins.jobs.create(FreeStyleJob.class);
        sshd = docker.get();

    }

    private SshSlaveLauncher configureDefaultSSHSlaveLauncher() {
        return configureSSHSlaveLauncher(sshd.ipBound(22), sshd.port(22));
    }

    private SshSlaveLauncher configureSSHSlaveLauncher(String host, int port) {
        SshSlaveLauncher launcher = slave.setLauncher(SshSlaveLauncher.class);
        launcher.host.set(host);
        launcher.port(port);
        launcher.setSshHostKeyVerificationStrategy(SshSlaveLauncher.NonVerifyingKeyVerificationStrategy.class);
        return launcher;
    }

    private void useSlave() {
        slave = jenkins.slaves.create(DumbSlave.class);
        slave.setExecutors(1);
        slave.remoteFS.set(REMOTE_FS);

        configureDefaultSSHSlaveLauncher().pwdCredentials("test", "test");
        slave.save();

        slave.waitUntilOnline();
        assertTrue(slave.isOnline());

        job.configure();
        job.setLabelExpression(slave.getName());

        job.save();
    }

    @Test
    public void use_default_ant_installation() {
        useSlave();
        buildHelloWorld(null);
    }

    @Test
    public void autoInstallAnt() {
        AntInstallation.install(jenkins, INSTALL_NAME_1_8, INSTALL_VERSION_1_8);

        buildHelloWorld(INSTALL_NAME_1_8).shouldContainsConsoleOutput(
            "Unpacking (http|https)://archive.apache.org/dist/ant/binaries/apache-ant-" + INSTALL_VERSION_1_8 + "-bin.zip"
        );
    }
    
    @Test
    public void autoInstallMultipleAnt() {
        AntInstallation.install(jenkins, INSTALL_NAME_1_8, INSTALL_VERSION_1_8);
        AntInstallation.install(jenkins, INSTALL_NAME_1_10, INSTALL_VERSION_1_10);

        buildHelloWorld(INSTALL_NAME_1_10).shouldContainsConsoleOutput(
            "Unpacking (http|https)://archive.apache.org/dist/ant/binaries/apache-ant-" + INSTALL_VERSION_1_10 + "-bin.zip"
        );
    }

    @Test
    public void locallyInstalledAnt() {
        useSlave();
        AntInstallation ant = ToolInstallation.addTool(jenkins, AntInstallation.class);
        ant.name.set("native_ant");
        String antHome = ant.useNative();
        ant.getPage().save();

        job.configure();
        job.copyResource(resource("ant/echo-helloworld.xml"), "build.xml");
        AntBuildStep step = job.addBuildStep(AntBuildStep.class);
        step.antName.select("native_ant");
        step.targets.set("-version");
        job.save();

        String expectedVersion = "1.10.5"; // this is the version installed in the java container by the ubuntu bionic
        job.startBuild().shouldSucceed().shouldContainsConsoleOutput(Pattern.quote(expectedVersion));
    }

    @Test
    @WithPlugins({"workflow-job", "workflow-cps", "workflow-basic-steps", "workflow-durable-task-step"})
    public void testAntWrapper() {
        useSlave();
        String antHome = setUpAntInstallation();

        String expectedVersion = "1.10.5"; // this is the version installed in the java container by the ubuntu bionic;

        WorkflowJob workflowJob = jenkins.jobs.create(WorkflowJob.class);
        workflowJob.script.set(SCRIPT_PIPELINE_ANT);

        workflowJob.save();

        workflowJob.startBuild().shouldSucceed();

        String console = workflowJob.getLastBuild().getConsole();
        assertThat(console, containsString("withAnt"));
        assertThat(console, containsString("ant -version"));
        assertThat(console, containsString(expectedVersion));
    }

    @Test
    public void testAdvancedConfiguration() {
        useSlave();
        setUpAnt();

        antBuildStepAdvancedConfiguration(step, BUILD_FILE, PROPERTIES, OPTS);

        job.save();

        job.startBuild().shouldSucceed();

        String console = job.getLastBuild().getConsole();
        assertThat(console, containsString(System.getProperty("java.version")));
        assertThat(console, containsString("-D" + OK_PROP1));
        assertThat(console, containsString("-D" + OK_PROP2));
        assertThat(console, containsString("-D" + NOK_PROP1));
        assertThat(console, containsString("-D" + NOK_PROP2));
        assertThat(console, containsString("[echoproperties] " + OK_PROP1));
        assertThat(console, containsString("[echoproperties] " + OK_PROP2));
        assertThat(console, not(Matchers.containsRegexp("[echoproperties] " + NOK_PROP1, Pattern.MULTILINE)));
        assertThat(console, not(Matchers.containsRegexp("[echoproperties] " + NOK_PROP2, Pattern.MULTILINE)));
    }

    @Test
    public void testCustomBuildFailDoesNotExist() {
        useSlave();
        setUpAnt();

        antBuildStepAdvancedConfiguration(step, FAKE_BUILD_FILE, null, null);

        job.save();

        job.startBuild().shouldFail();

        String console = job.getLastBuild().getConsole();
        assertThat(console, Matchers.containsRegexp("ERROR: Unable to find build script at .*/" + FAKE_BUILD_FILE, Pattern.MULTILINE));
    }

    private Build buildHelloWorld(final String name) {
        job.configure(() -> {
            job.copyResource(resource("ant/echo-helloworld.xml"), "build.xml");
            AntBuildStep ant = job.addBuildStep(AntBuildStep.class);
            if (name!=null)
                ant.antName.select(name);
            ant.targets.set("hello");
            return null;
        });

        return job.startBuild().shouldSucceed().shouldContainsConsoleOutput("Hello World");
    }

    private void setUpAnt() {
        setUpAntInstallation();

        job.configure();
        job.copyResource(resource("ant/"+BUILD_FILE), BUILD_FILE);
        step = job.addBuildStep(AntBuildStep.class);
        step.antName.select(NATIVE_ANT_NAME);
        step.targets.set("");
    }

    private String setUpAntInstallation() {
        AntInstallation ant = ToolInstallation.addTool(jenkins, AntInstallation.class);
        ant.name.set(NATIVE_ANT_NAME);
        String antHome = ant.useNative();
        ant.getPage().save();

        return antHome;
    }

    private void antBuildStepAdvancedConfiguration(AntBuildStep step, String buildFile, String properties, String antOpts) {
        step.control("advanced-button").click();
        step.control(By.xpath("(//div[contains(@descriptorid, \"Ant\")]//input[@type = \"button\"])[4]")).click();
        step.control("antOpts").set(StringUtils.defaultString(antOpts));
        step.control(By.xpath("(//div[contains(@descriptorid, \"Ant\")]//input[@type = \"button\"])[3]")).click();
        step.control("properties").set(StringUtils.defaultString(properties));
        step.control(By.xpath("(//div[contains(@descriptorid, \"Ant\")]//input[@type = \"button\"])[2]")).click();
        step.control("buildFile").set(StringUtils.defaultString(buildFile));
    }
}
