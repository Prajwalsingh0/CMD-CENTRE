package com.aicommandcenter.config;

import com.aicommandcenter.common.enums.Priority;
import com.aicommandcenter.goal.dto.GoalRequest;
import com.aicommandcenter.goal.service.GoalService;
import com.aicommandcenter.task.dto.TaskRequest;
import com.aicommandcenter.task.entity.TaskStatus;
import com.aicommandcenter.task.service.TaskService;
import com.aicommandcenter.user.dto.RegisterRequest;
import com.aicommandcenter.user.dto.SkillRequest;
import com.aicommandcenter.user.repository.UserRepository;
import com.aicommandcenter.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Opt-in demo dataset, for a screenshot or a first-run walkthrough.
 *
 * <p>Guard rails, because seeding is the one place where this application creates rows the user
 * did not ask for:</p>
 * <ul>
 *   <li>off by default in every profile — it only runs with <code>APP_SEED_DEMO=true</code>;</li>
 *   <li>it does nothing at all if any user already exists, so it can never touch real data;</li>
 *   <li>it writes through the ordinary services, so the demo rows are indistinguishable from rows
 *       a user would have created themselves.</li>
 * </ul>
 *
 * <p>Nothing in the UI or the API ever falls back to this data — if it is absent, the workspace is
 * simply empty.</p>
 */
@Component
@ConditionalOnProperty(name = "app.seed.demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String DEMO_EMAIL = "demo@aicommandcenter.local";
    private static final String DEMO_PASSWORD = "DemoPassw0rd";

    private final UserRepository userRepository;
    private final UserService userService;
    private final GoalService goalService;
    private final TaskService taskService;

    public DemoDataSeeder(UserRepository userRepository,
                          UserService userService,
                          GoalService goalService,
                          TaskService taskService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.goalService = goalService;
        this.taskService = taskService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("Demo data skipped: the database already contains users");
            return;
        }
        Long userId = userService.register(new RegisterRequest(DEMO_EMAIL, DEMO_PASSWORD, "Demo User")).user().id();
        log.info("Seeding demo workspace for {}", DEMO_EMAIL);

        List.of(
                new SkillRequest("Java", "ADVANCED", true),
                new SkillRequest("Spring Boot", "ADVANCED", true),
                new SkillRequest("PostgreSQL", "INTERMEDIATE", true),
                new SkillRequest("Docker", "INTERMEDIATE", false),
                new SkillRequest("JUnit", "ADVANCED", true),
                new SkillRequest("Kafka", "BEGINNER", false))
                .forEach(skill -> userService.addSkill(userId, skill));

        Long goalId = goalService.create(userId, new GoalRequest(
                "Get a Java Backend Job",
                "Land a backend role within a month.",
                LocalDate.now().plusDays(30),
                Priority.HIGH,
                null,
                null)).id();

        taskService.create(userId, new TaskRequest("Revise Spring Boot auto-configuration",
                null, TaskStatus.COMPLETED, Priority.HIGH, LocalDate.now().minusDays(2), goalId, List.of("spring")));
        taskService.create(userId, new TaskRequest("Practise SQL joins and indexes",
                null, TaskStatus.IN_PROGRESS, Priority.HIGH, LocalDate.now(), goalId, List.of("sql")));
        taskService.create(userId, new TaskRequest("Write the system-design walkthrough",
                null, TaskStatus.TODO, Priority.MEDIUM, LocalDate.now().plusDays(3), goalId, List.of("design")));
        taskService.create(userId, new TaskRequest("Review the security checklist",
                null, TaskStatus.TODO, Priority.URGENT, LocalDate.now().minusDays(1), goalId, List.of("security")));
        taskService.create(userId, new TaskRequest("Draft the take-home project README",
                null, TaskStatus.TODO, Priority.LOW, LocalDate.now().plusDays(7), null, List.of("docs")));

        log.info("Demo workspace ready. Sign in as {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
    }
}
