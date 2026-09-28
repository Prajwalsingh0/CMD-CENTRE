package com.aicommandcenter.export;

import com.aicommandcenter.ai.activity.AiActivity;
import com.aicommandcenter.ai.activity.AiActivityRepository;
import com.aicommandcenter.goal.entity.Goal;
import com.aicommandcenter.goal.repository.GoalRepository;
import com.aicommandcenter.notification.entity.Notification;
import com.aicommandcenter.notification.entity.NotificationType;
import com.aicommandcenter.notification.repository.NotificationRepository;
import com.aicommandcenter.support.IntegrationTestBase;
import com.aicommandcenter.task.entity.Task;
import com.aicommandcenter.task.repository.TaskRepository;
import com.aicommandcenter.user.entity.User;
import com.aicommandcenter.user.entity.UserSkill;
import com.aicommandcenter.user.repository.UserRepository;
import com.aicommandcenter.user.repository.UserSkillRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The data export: what it contains, what it must never contain, and whose rows it is allowed to
 * contain.
 */
class UserDataExportIT extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AiActivityRepository aiActivityRepository;

    @Test
    @DisplayName("the export carries every table that belongs to the caller")
    void exportContainsOwnRows() throws Exception {
        String email = uniqueEmail("export-own");
        String token = register(email);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        Goal goal = new Goal();
        goal.setUserId(user.getId());
        goal.setTitle("Ship the export endpoint");
        goalRepository.save(goal);

        Task task = new Task();
        task.setUserId(user.getId());
        task.setGoalId(goal.getId());
        task.setTitle("Write the integration test");
        taskRepository.save(task);

        UserSkill skill = new UserSkill();
        skill.setUserId(user.getId());
        skill.setSkill("Spring Boot");
        skill.setLevel("EXPERT");
        userSkillRepository.save(skill);

        Notification notification = new Notification();
        notification.setUserId(user.getId());
        notification.setEventType(NotificationType.DEADLINE_SOON);
        notification.setTitle("A task is due");
        notification.setMessage("Write the integration test is due soon");
        notification.setDedupeKey("export-test-" + user.getId());
        notificationRepository.save(notification);

        AiActivity activity = new AiActivity();
        activity.setUserId(user.getId());
        activity.setCommand("create goal");
        activity.setIntent("CREATE_GOAL");
        activity.setStatus("SUCCESS");
        activity.setResultSummary("created goal " + goal.getId());
        aiActivityRepository.save(activity);

        String body = mockMvc.perform(MockMvcRequestBuilders.get("/api/users/me/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".json")))
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.account.email").value(email))
                .andExpect(jsonPath("$.goals[0].title").value("Ship the export endpoint"))
                .andExpect(jsonPath("$.tasks[0].title").value("Write the integration test"))
                .andExpect(jsonPath("$.skills[0].skill").value("Spring Boot"))
                .andExpect(jsonPath("$.notifications[0].title").value("A task is due"))
                .andExpect(jsonPath("$.aiActivity[0].intent").value("CREATE_GOAL"))
                .andExpect(jsonPath("$.sessions").isArray())
                .andReturn().getResponse().getContentAsString();

        JsonNode parsed = objectMapper.readTree(body);
        assertThat(parsed.path("sessions").size()).isGreaterThanOrEqualTo(1);
        assertThat(parsed.path("generatedAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("the export never contains credentials or derived vectors")
    void exportCarriesNoSecrets() throws Exception {
        String email = uniqueEmail("export-secrets");
        String token = register(email);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        Goal goal = new Goal();
        goal.setUserId(user.getId());
        goal.setTitle("Something to export");
        goalRepository.save(goal);

        String body = mockMvc.perform(MockMvcRequestBuilders.get("/api/users/me/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The payload *names* what it excludes, so assert on the real values instead
        assertThat(body)
                .doesNotContain(user.getPasswordHash())
                .doesNotContain(PASSWORD)
                .doesNotContain("tokenHash\":")
                .doesNotContain("replacedBy")
                .doesNotContain("\"embedding\"");
        assertThat(body).contains("documentChunkEmbeddings");
        assertThat(user.getPasswordHash()).isNotBlank();
    }

    @Test
    @DisplayName("one account's export never contains another account's rows")
    void exportIsScopedToTheCaller() throws Exception {
        String mine = uniqueEmail("export-mine");
        String token = register(mine);
        User me = userRepository.findByEmailIgnoreCase(mine).orElseThrow();

        String theirs = uniqueEmail("export-theirs");
        register(theirs);
        User other = userRepository.findByEmailIgnoreCase(theirs).orElseThrow();

        Goal myGoal = new Goal();
        myGoal.setUserId(me.getId());
        myGoal.setTitle("My own goal");
        goalRepository.save(myGoal);

        Goal theirGoal = new Goal();
        theirGoal.setUserId(other.getId());
        theirGoal.setTitle("A stranger's private goal");
        goalRepository.save(theirGoal);

        String body = mockMvc.perform(MockMvcRequestBuilders.get("/api/users/me/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("My own goal");
        assertThat(body).doesNotContain("A stranger's private goal");
        assertThat(body).doesNotContain(theirs);
    }

    @Test
    @DisplayName("exporting requires a session")
    void exportRequiresAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/users/me/export"))
                .andExpect(status().isUnauthorized());
    }
}
