package blocks.health;

import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import blocks.service.BlockStatus;
import blocks.testkit.BlockTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static blocks.testkit.MockitoHelper.exactlyOnce;
import static blocks.testkit.MockitoHelper.exactlyTwice;
import static blocks.testkit.TestConstants.IN_AT_MOST_THREE_SECONDS;
import static blocks.testkit.TestConstants.NOW;
import static blocks.testkit.TestConstants.ZONED_NOW;
import static blocks.testkit.TestConstants.ZONE_ID;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthActorTest extends BlockTestBase {

    private static final Instant START_TIME = NOW.minusSeconds(10L);
    private static final ZonedDateTime ZONED_START_TIME = ZonedDateTime.ofInstant(START_TIME, ZONE_ID);
    private static final Map<String, JsonNode> STATIC_PROPERTIES = new HashMap<String, JsonNode>() {
        {
            this.put("serviceName", TextNode.valueOf("test-service"));
        }
    };

    private static final HealthProtocol.Health EXPECTED_INITIAL_HEALTH = new HealthProtocol.Health(
        new ServiceHealth(true, true, emptyMap(), emptyList(), ZONED_START_TIME, ZONED_NOW, STATIC_PROPERTIES)
    );

    private TestProbe<HealthProtocol.Health> testProbe;
    private ActorRef<HealthProtocol.Message> healthActor;

    @Mock
    private Clock clock;

    @BeforeEach
    public void beforeEach() {
        testProbe = actorTestKit.createTestProbe();

        MockitoAnnotations.openMocks(this);
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZONE_ID);

        healthActor = spawnActor(HealthActor.behavior(START_TIME, clock, STATIC_PROPERTIES), "HealthActor");
    }

    @Test
    public void shouldReturnHealthyAndInitializedIfNoBlocksAreRegistered() {
        healthActor.tell(new HealthProtocol.GetHealth(testProbe.ref()));

        testProbe.expectMessage(IN_AT_MOST_THREE_SECONDS, EXPECTED_INITIAL_HEALTH);
        verify(clock).instant();
        verify(clock).getZone();
    }

    @Test
    public void shouldRegisterNewMandatoryBlockAndUpdateHealthAndInitializedStatusAccordingly() {
        final HealthProtocol.Health expectedUpdatedHealth = new HealthProtocol.Health(
            new ServiceHealth(false, true, singletonMap(
                "TestBlock", new BlockHealthInfo(BlockStatus.NOT_INITIALIZED, true)
            ), emptyList(), ZONED_START_TIME, ZONED_NOW, STATIC_PROPERTIES)
        );

        healthActor.tell(new HealthProtocol.GetHealth(testProbe.ref()));
        testProbe.expectMessage(IN_AT_MOST_THREE_SECONDS, EXPECTED_INITIAL_HEALTH);

        healthActor.tell(new HealthProtocol.RegisterBlock("TestBlock", true));
        healthActor.tell(new HealthProtocol.GetHealth(testProbe.ref()));

        testProbe.expectMessage(IN_AT_MOST_THREE_SECONDS, expectedUpdatedHealth);
        verify(clock, exactlyTwice()).instant();
        verify(clock, exactlyTwice()).getZone();
    }

    @Test
    public void shouldReportHealthyWhenRegisteredMandatoryBlockIsInitialized() {
        checkHealthWhenBlockIsInState(true, true, BlockStatus.INITIALIZED);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"INITIALIZED"})
    public void shouldReportUnhealthyWhenRegisteredMandatoryBlockIsNotInitialized(final BlockStatus status) {
        checkHealthWhenBlockIsInState(false, true, status);
    }

    @ParameterizedTest
    @EnumSource(BlockStatus.class)
    public void shouldReportHealthyRegardlessOfTheStatusOfNotMandatoryBlock(final BlockStatus status) {
        checkHealthWhenBlockIsInState(true, false, status);
    }

    @Test
    public void shouldReportUnhealthyWithNewUninitializedComponentRegistered() {
        final HealthProtocol.Health testComponent = new HealthProtocol.Health(
            new ServiceHealth(false, false, emptyMap(),
                singletonList(new ComponentHealth("TestComponent", false, false, Optional.empty(), emptyList(), ZONED_NOW, OptionalLong.empty())),
                ZONED_START_TIME, ZONED_NOW, STATIC_PROPERTIES));

        healthActor.tell(new HealthProtocol.GetHealth(testProbe.ref()));
        testProbe.expectMessage(IN_AT_MOST_THREE_SECONDS, EXPECTED_INITIAL_HEALTH);

        healthActor.tell(new HealthProtocol.RegisterComponent("TestComponent"));
        healthActor.tell(new HealthProtocol.GetHealth(testProbe.ref()));

        testProbe.expectMessage(IN_AT_MOST_THREE_SECONDS, testComponent);
        verify(clock, times(3)).instant();
        verify(clock, times(3)).getZone();
    }

    private void checkHealthWhenBlockIsInState(final boolean expectedHealthyValue,
                                               final boolean mandatory,
                                               final BlockStatus blockStatus) {
        final HealthProtocol.Health expectedUpdatedHealth = new HealthProtocol.Health(
            new ServiceHealth(expectedHealthyValue, true, singletonMap(
                "TestBlock", new BlockHealthInfo(blockStatus, mandatory)
            ), emptyList(), ZONED_START_TIME, ZONED_NOW, STATIC_PROPERTIES)
        );

        healthActor.tell(new HealthProtocol.RegisterBlock("TestBlock", mandatory));
        healthActor.tell(new HealthProtocol.UpdateBlockStatus("TestBlock", blockStatus));
        healthActor.tell(new HealthProtocol.GetHealth(testProbe.ref()));

        testProbe.expectMessage(IN_AT_MOST_THREE_SECONDS, expectedUpdatedHealth);
        verify(clock, exactlyOnce()).instant();
        verify(clock, exactlyOnce()).getZone();
    }
}