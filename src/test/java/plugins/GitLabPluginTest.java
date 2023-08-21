package plugins;

import jakarta.inject.Inject;
import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.docker.fixtures.GitLabContainer;
import org.jenkinsci.test.acceptance.junit.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import okhttp3.Response;

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

    public String getPrivateToken() {
        return privateToken;
    }

    @Before
    public void init() {
        container = gitLabServer.get();
        repoUrl = container.getRepoUrl();
        host = container.host();
        port = container.port();
        container.waitForReady(this);
        
        try {
            privateToken = container.createUserToken();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void dummy_test() {
        assertNotNull(container.getRepoUrl());
        assertTrue(container.getRepoUrl().contains("ssh://git@"));
        assertNotNull(container.host());
    }

    @Test
    public void createRepo() {
        //This sends a request to make a new repo in the gitlab server with the name "testrepo")
        try {
            Response response = container.createRepo("testrepo", getPrivateToken());
            assertEquals(201, response.code()); // 201 means the repo was created successfully
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            // delete the repo when finished
            container.deleteRepo(getPrivateToken());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
