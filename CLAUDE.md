# CLAUDE.md - quartz-plus

This file describes project conventions, coding standards, and workflow rules for this codebase.
Follow every section precisely. When in doubt, read existing code before writing new code.

**Quartz-Plus** is a [Spring Boot](https://spring.io/projects/spring-boot) auto-configuration library that enriches the
standard [Quartz Scheduler](https://quartz-scheduler.org/) with declarative annotation-driven job registration,
automatic execution logging, retry-on-error support, Micrometer metrics, and a built-in web administration UI - all
without boilerplate.

Do not commit any code or create any branches unless explicitly asked.

Do not add details about which proposal was worked on for a specific class/method/attribute into javadoc - keep javadoc 
describing the functionality.

---

## Java Coding Style

### Method Parameters and Local Variables

- **All method parameters must be `final`.**

```java
// Correct
public Optional<CountryAndLanguage> lookup(final String ipAddress) {
    final var ip = InetAddress.getByName(ipAddress);
    final var result = reader.get().tryCity(ip);
    ...
}

// Wrong - missing final, missing var
public Optional<CountryAndLanguage> lookup(String ipAddress) {
    InetAddress ip = InetAddress.getByName(ipAddress);
    ...
}
```

### Dependency Injection

Always use **constructor injection**. Never use `@Autowired` on fields. Use `@RequiredArgsConstructor` with `final` fields.

```java
// Correct
@Service
public class SomeServiceImpl implements SomeService {
    private final SomeRepository someRepository;
    private final ChatProperties chatProperties;
    public SomeServiceImpl(final SomeRepository someRepository, final ChatProperties chatProperties) {
        this.someRepository = someRepository;
        this.chatProperties = chatProperties;
    }
}

// Wrong
@Service
public class SomeServiceImpl implements SomeService {
    @Autowired
    private SomeRepository someRepository;
}
```

---

## Service Pattern

Every service must be split into an **interface** and an **implementation**:

- `SomeService` - interface in the appropriate module's `service` package
- `SomeServiceImpl` - implementation, same package, annotated with `@Service`

Inner helper classes (e.g., result types) belong on the interface, not the implementation.

---

## Javadoc

### Rules

- **Every public class and interface must have a class-level Javadoc comment.**
- **Every method on an interface must have a Javadoc comment** explaining its contract, parameters, return value, and exceptions.
- **Implementation classes** must have a class-level Javadoc (can be shorter) but do not need to repeat method Javadoc already present on the interface (use `{@inheritDoc}` on overrides if adding value, otherwise omit).
- Do not write Javadoc on private methods unless the logic is genuinely non-obvious.
- Javadoc should describe *why* and *what contract*, not narrate the implementation line by line.

### @since Tag for Services and Entities/DTOs

When creating a **new service interface or implementation or entities**, add `@since APP_VERSION_GOES_HERE` to the class-level Javadoc
on **both** the interface and the implementation. Replace `APP_VERSION_GOES_HERE` with the current version from `pom.xml`.

```java
/**
 * Service responsible for resolving geographic location from IP addresses.
 *
 * @since 1.0.16
 */
public interface GeoLocationService {

    /**
     * Looks up the country and primary language for the given IP address.
     *
     * @param ipAddress the IPv4 or IPv6 address to look up
     * @return an {@link Optional} containing country and language if found, empty otherwise
     */
    Optional<CountryAndLanguage> lookupCountryAndLanguage(String ipAddress);
}

/**
 * MaxMind GeoIP2-backed implementation of {@link GeoLocationService}.
 *
 * @since 1.0.16
 */
@Service
public class GeoLocationServiceImpl implements GeoLocationService {
    ...
}
```

---

## Testing

### Framework and Assertions

- **JUnit Jupiter** (JUnit 5) only - no JUnit 4.
- **AssertJ** for all assertions - never use `assertEquals`, `assertTrue`, or any JUnit assertion methods.
- Import: `import static org.assertj.core.api.Assertions.assertThat;`

### Test Method Rules

- Method names **must start with `test`** and be descriptive enough to understand the scenario without reading the body.
- All method parameters must be `final`.
- Use `final var` wherever possible.
- **No `@DisplayName` annotations** - the method name is the display name.
- **No inline comments** inside test methods - the code should be self-explanatory.

```java
// Correct
@Test
void testUploadMessageAttachment_Success_Image() {
    final var user = userRepository.findById(TestConstants.USER_1.getId()).orElseThrow();
    final var room = createPublicRoom(UserDto.fromEntity(user));
    ...
    assertThat(attachments).hasSize(1);
    assertThat(attachment.getId()).isNotNull().hasSize(27);
}

// Wrong - no test prefix, has comment, explicit types
@Test
@DisplayName("Upload image attachment")
void uploadImageAttachment() {
    // create user
    UserDto user = ...;
}
```

### Integration vs Unit Tests

**Prefer integration tests with a real Spring context** over mocked unit tests. The project provides `IntegratedTestBase` in `chat-api` for full-stack Spring Boot + Testcontainers (Kafka + Redis) integration tests.

- Use `IntegratedTestBase` for any test that touches a repository, service, controller, or Kafka.
- Use plain JUnit tests only for pure utility/helper classes with no Spring dependencies.
- Use **WireMock** (already wired in `IntegratedTestBase`) for stubbing external HTTP services.
- Never mock repositories or services when a real container can be used.

---------

## Proposals
Developer usually adds change marked with (TODO) in title proposals to `./PROPOSALS.md`.
Only work on those when requested.
While working on a proposal change (TODO) to (IN PROGRESS)
Once complete the proposal should be moved to `./PROPOSALS_COMPLETE.md` and marked (DONE)
Make sure to update the table on top of `PROPOSALS_COMPLETE.md` and make sure it is ordered by proposal number
When creating a flowchart or diagram when possible use mermaid (prefer top-to-bottom vs left-to-right).
When creating comments we can specify the proposal number in the comments, but let's not add "proposal #123" anywhere where our customer might see it.
When creating a new major feature (proposal) and version (project version in pom.xml files) was not updated - ask to update
When running the full build / test suite at the end of the development process which takes time, when appropriate, ask owner to commit / deploy.

When working on proposals ALWAYS follow this directive:
```text
Since there will be questions, and decision - create a visual (HTML in dark mode style) explanation, and each question should be displayed with
recommendation, risk, cost, complexity, competitor choice(s) (when applicable) and ability for me to select options or provide "Other" answer, at the end of the page
we should have a button to copy all the answers to clipboard so we can paste it for further use. 
When showing options provide each name (A,B,C,...) and weights for each options (e.g. 68% 22% 10%) - assuming highest for your recommendation.
When creating an HTML questions page about an option and if that makes sense - create UI mockups of how the app/functionality would look like.
If we are using Fable model for planning - please let me know before you start implementing so i can switch to Opus model.
```


## Changelog

**Every code change must add an entry to `CHANGES.md`** in the root of the project. New entries go at the **top**,
under the current version heading. Entries should be short but descriptive, in form of bullet-points

Format (match existing entries exactly):

```markdown
### Version 1.0.16 (2026-06-21 16:30:00)
- Short descriptions of what changed and why
- Another entry
```

- Use the current version from `pom.xml`.
- One bullet per logical change; keep descriptions concise.
- If the version heading already exists, append bullets to it. Do not create duplicate version headings.

---

## Feature Planning

**For all major new features, create a written plan and review it with the user before writing any code.**

A "major feature" is anything that:
- Adds a new service, controller, or module
- Changes the database schema
- Introduces a new external dependency
- Affects authentication, security, or encryption
- Changes the Kafka topic structure or message format

Minor changes (bug fixes, adding a field to an existing DTO, new utility method, logback/infra config) do not require a plan.

------------

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.