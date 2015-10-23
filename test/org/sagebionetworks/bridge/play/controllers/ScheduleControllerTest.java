package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.SchedulePlanService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

import play.mvc.Result;
import play.test.Helpers;

public class ScheduleControllerTest {

    private ScheduleController controller;
    
    private StudyIdentifier studyId;
    
    @Before
    public void before() {
        studyId = new StudyIdentifierImpl(TestUtils.randomName(ScheduleControllerTest.class));
        ClientInfo clientInfo = ClientInfo.fromUserAgentCache("app name/9");
        
        // This filer is done in the bowels of the DAO; tested elsewhere
        List<SchedulePlan> plans = Lists.newArrayList();
        for (SchedulePlan plan : TestUtils.getSchedulePlans(studyId)) {
            if (clientInfo.isTargetedAppVersion(plan.getMinAppVersion(), plan.getMaxAppVersion())) {
                plans.add(plan);
            }
        }
        
        SchedulePlanService schedulePlanService = mock(SchedulePlanService.class);
        when(schedulePlanService.getSchedulePlans(clientInfo, studyId)).thenReturn(plans);
        
        controller = spy(new ScheduleController());
        controller.setSchedulePlanService(schedulePlanService);
        
        User user = new User();
        
        UserSession session = mock(UserSession.class);
        when(session.getStudyIdentifier()).thenReturn(studyId);
        when(session.getUser()).thenReturn(user);
        
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(clientInfo).when(controller).getClientInfoFromUserAgentHeader();
    }
    
    @Test
    public void getSchedules() throws Exception {
        Result result = controller.getSchedules();
        String content = Helpers.contentAsString(result);
        
        JsonNode node = BridgeObjectMapper.get().readTree(content);
        
        assertEquals(1, node.get("total").asInt());
    }
    
    @Test
    public void getSchedulesV3AdjustsScheduleTypes() throws Exception {
        List<SchedulePlan> plans = Lists.newArrayList();

        Schedule schedule = new Schedule();
        schedule.addActivity(new Activity.Builder().withLabel("Label").withTask("foo").build());
        schedule.setEventId("activity:"+schedule.getActivities().get(0).getGuid()+":finished");
        schedule.setScheduleType(ScheduleType.ONCE);
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("Label");
        plan.setGuid("BBB");
        plan.setStudyKey("study-key");
        plan.setStrategy(strategy);
        plans.add(plan);

        schedule = new Schedule();
        schedule.addActivity(new Activity.Builder().withLabel("Label").withTask("foo").build());
        schedule.setEventId("activity:"+schedule.getActivities().get(0).getGuid()+":finished");
        schedule.setScheduleType(ScheduleType.ONCE);
        strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(schedule);
        
        plan = new DynamoSchedulePlan();
        plan.setLabel("Label");
        plan.setGuid("BBB");
        plan.setStudyKey("study-key");
        plan.setStrategy(strategy);
        plans.add(plan);
        
        SchedulePlanService schedulePlanService = mock(SchedulePlanService.class);
        when(schedulePlanService.getSchedulePlans(any(), any())).thenReturn(plans);
        controller.setSchedulePlanService(schedulePlanService);
        
        Result result = controller.getSchedulesV3();
        
        String content = Helpers.contentAsString(result);
        JsonNode node = BridgeObjectMapper.get().readTree(content);
        ArrayNode array = (ArrayNode)node.get("items");
        
        // Verify that both objects have been adjusted so that despite the fact that they are 
        // marked as persistent, they are also marked as recurring.
        ObjectNode schedule1 = (ObjectNode)array.get(0);
        assertTrue(schedule1.get("persistent").asBoolean());
        assertEquals("recurring", schedule1.get("scheduleType").asText());
        
        ObjectNode schedule2 = (ObjectNode)array.get(1);
        assertTrue(schedule2.get("persistent").asBoolean());
        assertEquals("recurring", schedule2.get("scheduleType").asText());
    }
}
