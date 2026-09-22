package com.aicommandcenter.job.extract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The controlled vocabulary used to turn free text into skills.
 *
 * <p>Extraction against a declared list — rather than "whatever the model says" — is what makes
 * the skill-gap report reproducible and auditable. The same dictionary normalises the user's own
 * skill entries, so "SpringBoot", "spring boot" and "Spring Boot" all collapse to one skill and
 * the comparison is fair.</p>
 */
public final class SkillDictionary {

    /** canonical display name -> alternative spellings that must resolve to it */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    private static final List<String> CANONICAL = List.of(
            "Java", "Kotlin", "Scala", "Python", "JavaScript", "TypeScript", "Go", "Rust", "C", "C++", "C#",
            "PHP", "Ruby", "Swift", "Shell",
            "Spring", "Spring Boot", "Spring Security", "Spring Data JPA", "Hibernate", "JPA", "JUnit",
            "Mockito", "Testcontainers", "Maven", "Gradle", "Quarkus", "Micronaut", "Jakarta EE",
            "Node.js", "Express", "NestJS", "React", "Angular", "Vue", "Next.js", "Redux", "HTML", "CSS",
            "Tailwind CSS", "Vite", "Webpack",
            "REST", "GraphQL", "gRPC", "SOAP", "Microservices", "Event-Driven Architecture",
            "Kafka", "RabbitMQ", "ActiveMQ", "Redis",
            "PostgreSQL", "MySQL", "Oracle", "SQL Server", "MongoDB", "Cassandra", "DynamoDB", "SQLite",
            "Elasticsearch", "Neo4j",
            "SQL", "NoSQL", "PL/SQL", "JPA Criteria", "Flyway", "Liquibase",
            "Docker", "Kubernetes", "Helm", "Terraform", "Ansible", "Jenkins", "GitHub Actions", "GitLab CI",
            "CircleCI", "ArgoCD", "AWS", "Azure", "GCP", "Lambda", "EC2", "S3", "ECS", "EKS", "RDS",
            "CloudFormation", "CloudWatch",
            "Linux", "Bash", "Git", "Nginx", "Apache",
            "OAuth2", "JWT", "SAML", "LDAP", "SSO", "Keycloak", "OWASP",
            "Prometheus", "Grafana", "ELK", "Datadog", "Splunk", "New Relic",
            "Jira", "Confluence", "Agile", "Scrum", "Kanban",
            "System Design", "Distributed Systems", "Design Patterns", "SOLID",
            "Unit Testing", "Integration Testing", "TDD", "BDD", "Selenium", "Cypress", "Playwright",
            "Pandas", "NumPy", "PyTorch", "TensorFlow", "scikit-learn", "LangChain", "LLM",
            "Vector Database", "RAG", "Prompt Engineering", "OpenAI API",
            "Machine Learning", "Deep Learning", "NLP", "Data Engineering", "Spark", "Hadoop", "Airflow",
            "Tableau", "Power BI", "Excel", "Data Structures", "Algorithms", "Multithreading", "Concurrency",
            "Reactive Programming", "WebFlux", "WebSockets", "JSON", "XML", "YAML", "OpenAPI", "Swagger");

    static {
        for (String skill : CANONICAL) {
            ALIASES.put(skill.toLowerCase(Locale.ROOT), skill);
        }
        // Spellings that appear in real job descriptions but are not the canonical label.
        alias("Java", "jdk", "java 8", "java 11", "java 17", "java 21", "core java", "j2ee", "java ee");
        alias("Spring", "spring framework", "spring mvc", "spring core");
        alias("Spring Boot", "springboot", "spring-boot", "spring boot 3");
        alias("Spring Data JPA", "spring data", "spring-data-jpa");
        alias("Hibernate", "hibernate orm", "jpa/hibernate");
        alias("JPA", "jakarta persistence");
        alias("JavaScript", "js", "es6", "ecmascript");
        alias("TypeScript", "ts");
        alias("Node.js", "node", "nodejs", "node js");
        alias("REST", "restful", "rest api", "restful api", "rest apis");
        alias("PostgreSQL", "postgres", "psql", "postgre sql");
        alias("MySQL", "mariadb");
        alias("SQL", "ansi sql", "structured query language");
        alias("Docker", "dockerfile", "containerization", "containerisation");
        alias("Kubernetes", "k8s", "openshift");
        alias("AWS", "amazon web services");
        alias("GCP", "google cloud", "google cloud platform");
        alias("Azure", "microsoft azure");
        alias("CI/CD", "cicd", "ci cd", "continuous integration", "continuous delivery", "continuous deployment");
        alias("GitHub Actions", "gh actions", "github workflow");
        alias("GitLab CI", "gitlab pipelines");
        alias("OAuth2", "oauth 2", "oauth2.0");
        alias("JWT", "json web token", "json web tokens");
        alias("Unit Testing", "unit tests", "unit test");
        alias("Integration Testing", "integration tests", "integration test");
        alias("Microservices", "micro services", "microservice");
        alias("Event-Driven Architecture", "event driven", "event-driven");
        alias("C++", "cpp", "c plus plus");
        alias("C#", "csharp", "c sharp", ".net", "dotnet", "asp.net");
        alias("Vue", "vue.js", "vuejs");
        alias("React", "react.js", "reactjs");
        alias("Next.js", "nextjs", "next js");
        alias("Node.js", "node.js");
        alias("Tailwind CSS", "tailwind");
        alias("Vector Database", "vector db", "pgvector", "pinecone", "weaviate", "chroma", "faiss");
        alias("LLM", "large language model", "large language models", "gpt-4", "gpt4");
        alias("OpenAI API", "openai", "chatgpt api");
        alias("Power BI", "powerbi");
        alias("ELK", "elastic stack", "elasticsearch logstash kibana");
        alias("Data Structures", "dsa", "data structure");
        alias("Algorithms", "algorithm", "algorithmic");
        alias("System Design", "system-design", "high level design", "hld", "low level design", "lld");
        alias("Multithreading", "multi-threading", "multi threading");
        alias("Design Patterns", "design pattern", "gang of four");
        alias("Jakarta EE", "j2ee", "java ee");
        alias("PL/SQL", "plsql", "pl sql");
        alias("Shell", "bash", "shell scripting", "powershell");
        alias("TDD", "test driven development", "test-driven development");
        alias("BDD", "behaviour driven development", "behavior driven development", "cucumber");
        alias("Machine Learning", "ml models", "ml model");
        alias("Deep Learning", "neural networks", "neural network");
        alias("NLP", "natural language processing");
        alias("Prompt Engineering", "prompt design");
    }

    private static void alias(String canonical, String... alternatives) {
        for (String alternative : alternatives) {
            ALIASES.putIfAbsent(alternative.toLowerCase(Locale.ROOT), canonical);
        }
    }

    private SkillDictionary() {
    }

    /**
     * Finds skills in a text, ordered by first appearance (which is a useful proxy for emphasis)
     * and de-duplicated by canonical name.
     */
    public static List<String> findSkills(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> patterns = new ArrayList<>(ALIASES.keySet());
        patterns.sort((left, right) -> Integer.compare(right.length(), left.length()));
        List<String> byPosition = new ArrayList<>();
        Map<String, Integer> position = new LinkedHashMap<>();
        for (String pattern : patterns) {
            Matcher matcher = patternFor(pattern).matcher(haystack);
            if (matcher.find()) {
                String canonical = ALIASES.get(pattern);
                byPosition.add(canonical);
                position.merge(canonical, matcher.start(), Math::min);
            }
        }
        byPosition.sort((left, right) -> Integer.compare(position.get(left), position.get(right)));
        for (String canonical : byPosition) {
            if (seen.add(canonical)) {
                found.add(canonical);
            }
        }
        return found;
    }

    /** Maps any user-entered spelling to its canonical form, or returns a tidy version of it. */
    public static String canonicalise(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String mapped = ALIASES.get(trimmed.toLowerCase(Locale.ROOT));
        return mapped != null ? mapped : trimmed;
    }

    public static List<String> allSkills() {
        return CANONICAL;
    }

    /**
     * Word-boundary match that also respects the punctuation inside names such as {@code C++},
     * {@code C#} and {@code Node.js}, so "java" does not match inside "javascript".
     */
    private static Pattern patternFor(String pattern) {
        String quoted = Pattern.quote(pattern.toLowerCase(Locale.ROOT));
        return Pattern.compile("(?<![\\w+#.])" + quoted + "(?![\\w+#])");
    }
}
