package com.windrunner.server.tools.chat;

import com.windrunner.server.project.domain.Project;
import com.windrunner.server.project.persistence.ProjectRepository;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.user.domain.AppUser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FindProjectsToolTest {

    @Test
    void searchesProjectsVisibleToTheAuthenticatedUserWithABoundedLimit() throws Exception {
        Project project = project("proj-1", "Roadmap");
        RepositoryProbe probe = new RepositoryProbe(project);
        AppUser actor = actor("user-1", "USER");

        FindProjectsTool tool = new FindProjectsTool(probe.repository());
        var llmTool = tool.forContext(new ToolExecutionContext(actor, "session-1", List.of()));
        FindProjectsTool.Result result = (FindProjectsTool.Result) llmTool.handler().execute(
                new FindProjectsTool.Parameters("  Roadmap  ", 100));

        assertThat(result.projects()).extracting(FindProjectsTool.ProjectMatch::id).containsExactly("proj-1");
        assertThat(result.projects()).extracting(FindProjectsTool.ProjectMatch::name).containsExactly("Roadmap");
        assertThat(result.count()).isEqualTo(1);
        assertThat(result.limit()).isEqualTo(50);
        assertThat(probe.userId).isEqualTo("user-1");
        assertThat(probe.query).isEqualTo("Roadmap");
        assertThat(probe.limit).isEqualTo(50);
    }

    @Test
    void usesTheAllProjectsQueryOnlyForSuperAdmins() throws Exception {
        Project project = project("proj-1", "Roadmap");
        RepositoryProbe probe = new RepositoryProbe(project);
        AppUser actor = actor("admin-1", "SUPERADMIN");

        FindProjectsTool tool = new FindProjectsTool(probe.repository());
        FindProjectsTool.Result result = (FindProjectsTool.Result) tool.forContext(
                new ToolExecutionContext(actor, "session-1", List.of())).handler().execute(
                new FindProjectsTool.Parameters(null, null));

        assertThat(result.projects()).extracting(FindProjectsTool.ProjectMatch::id).containsExactly("proj-1");
        assertThat(probe.allProjectsQueryUsed).isTrue();
        assertThat(probe.userId).isNull();
        assertThat(probe.query).isNull();
        assertThat(probe.limit).isEqualTo(20);
    }

    private AppUser actor(String id, String globalRole) {
        AppUser actor = new AppUser();
        actor.setId(id);
        actor.setGlobalRole(globalRole);
        return actor;
    }

    private Project project(String id, String name) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        return project;
    }

    private static final class RepositoryProbe {
        private final Project project;
        private String userId;
        private String query;
        private int limit;
        private boolean allProjectsQueryUsed;

        private RepositoryProbe(Project project) {
            this.project = project;
        }

        private ProjectRepository repository() {
            return (ProjectRepository) Proxy.newProxyInstance(
                    ProjectRepository.class.getClassLoader(),
                    new Class<?>[]{ProjectRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findVisibleToUserByQuery" -> {
                            userId = (String) args[0];
                            query = (String) args[1];
                            limit = (int) args[2];
                            yield List.of(project);
                        }
                        case "findAllByQuery" -> {
                            allProjectsQueryUsed = true;
                            query = (String) args[0];
                            limit = (int) args[1];
                            yield List.of(project);
                        }
                        case "toString" -> "ProjectRepositoryProbe";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.toString());
                    });
        }
    }
}
