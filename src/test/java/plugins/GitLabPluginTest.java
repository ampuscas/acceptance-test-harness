package plugins;

import jakarta.inject.Inject;
import org.gitlab4j.api.GitLabApiException;
import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.docker.fixtures.GitLabContainer;
import org.jenkinsci.test.acceptance.junit.*;
import org.jenkinsci.test.acceptance.plugins.gitlab_plugin.GitLabServerConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.http.HttpResponse;

import java.io.IOException;

import static org.junit.Assert.*;

@WithDocker
@Category(DockerTest.class)
@WithPlugins("gitlab-plugin")
@WithCredentials(credentialType = WithCredentials.SSH_USERNAME_PRIVATE_KEY, values = {"gitlabplugin", "/org/jenkinsci/test/acceptance/docker/fixtures/GitLabContainer"})
public class GitLabPluginTest extends AbstractJUnitTest {

    @Inject
    DockerContainerHolder<GitLabContainer> gitLabServer;

    private GitLabContainer container;
    private String repoUrl;
    private String host;
    private int port;

    private String privateToken;

    private String password = "arandompassword12#"; // I guess the password can be the same for all users

    public String getPrivateToken() {
        return privateToken;
    }

    @Before
    public void init() throws InterruptedException, IOException {
        container = gitLabServer.get();
        repoUrl = container.getRepoUrl();
        host = container.host();
        port = container.port();
        container.waitForReady(this);

        // create an admin user
        privateToken = container.createUserToken("testadmin", password, "testadmin@gmail.com", "true");

        // create another user
        privateToken = container.createUserToken("testsimple", password, "testsimple@gmail.com", "false");
    }

    @Test
    public void dummy_test() {
        assertNotNull(container.getRepoUrl());
        assertTrue(container.getRepoUrl().contains("ssh://git@"));
        assertNotNull(container.host());
    }

    @Test
    public void createRepo() throws IOException, GitLabApiException {
        String repoName = "testrepo";
        //This sends a request to make a new repo in the gitlab server with the name "testrepo"
        HttpResponse<String> response = container.createRepo(repoName, getPrivateToken());
        assertEquals(201, response.statusCode()); // 201 means the repo was created successfully

        // delete the repo when finished
        container.deleteRepo(getPrivateToken(), repoName);
    }

    @Test
    public void configureGitLabServer() throws IOException {
        jenkins.configure();
        GitLabServerConfig serverConfig = new GitLabServerConfig(jenkins);
        String message = serverConfig.configureServer(container.getHttpUrl().toString(), privateToken);
        assertEquals("Success", message);

    }
}
