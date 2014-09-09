package controllers;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static org.sagebionetworks.bridge.TestConstants.SURVEYS_URL;
import static org.sagebionetworks.bridge.TestConstants.GET_SURVEY_URL;
import static org.sagebionetworks.bridge.TestConstants.GET_VERSIONS_OF_SURVEY_URL;
import static org.sagebionetworks.bridge.TestConstants.RECENT_SURVEYS_URL;
import static org.sagebionetworks.bridge.TestConstants.RECENT_PUBLISHED_SURVEYS_URL;
import static org.sagebionetworks.bridge.TestConstants.VERSION_SURVEY_URL;
import static org.sagebionetworks.bridge.TestConstants.PUBLISH_SURVEY_URL;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SurveyControllerTest {
    
    private static Logger logger = LoggerFactory.getLogger(SurveyControllerTest.class);

    @Resource
    TestUserAdminHelper helper;
    
    @Resource
    DynamoSurveyDao surveyDao;
    
    @Resource
    StudyControllerService studyControllerSerivce;
    
    private ObjectMapper mapper = new ObjectMapper();
    private Study study;
    private List<String> roles;

    @Before
    public void before() {
        study = studyControllerSerivce.getStudyByHostname("localhost");
        roles = Lists.newArrayList(study.getKey()+"_researcher");
        List<Survey> surveys = surveyDao.getSurveys(study.getKey());
        for (Survey survey : surveys) {
            surveyDao.closeSurvey(survey.getGuid(), survey.getVersionedOn());
            surveyDao.deleteSurvey(survey.getGuid(), survey.getVersionedOn());
        }
    }

    @After
    public void after() {
        helper.deleteOneUser();
    }
    
    private String createSurveyObject(String name) throws Exception {
        Survey survey = new DynamoSurvey();
        survey.setName(name);
        survey.setIdentifier("general");
        
        SurveyQuestion question = new DynamoSurveyQuestion();
        question.setIdentifier("gender");
        survey.getQuestions().add(question);
        
        question = new DynamoSurveyQuestion();
        question.setIdentifier("age");
        ObjectNode node = mapper.createObjectNode();
        node.put("value", 40);
        question.setData(node);
        survey.getQuestions().add(question);
        
        return mapper.writeValueAsString(survey);
    }
    
    @Test
    public void mustSubmitAsAdminOrResearcher() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                try {
                    helper.createOneUser();  
                    
                    String content = createSurveyObject("Name");
                    Response response = TestUtils.getURL(helper.getUserSessionToken(), SURVEYS_URL).post(content)
                            .get(TIMEOUT);
                    assertEquals("HTTP response indicates authorization error", SC_FORBIDDEN, response.getStatus());
                } finally {
                    helper.deleteOneUser();    
                }
            }
        });
    }
    
    @Test
    public void saveAndRetrieveASurvey() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                try {
                    helper.createOneUser(roles);
                    
                    GuidVersionHolder keys = createSurvey("Name");

                    ArrayNode questions = (ArrayNode)keys.node.get("questions");
                    int age = questions.get(1).get("data").get("value").asInt();
                    assertEquals("Age is 40", 40, age);
                } finally {
                    helper.deleteOneUser();    
                }
            }
        });
    }
    
    @Test
    public void createVersionPublish() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                try {
                    helper.createOneUser(roles);

                    GuidVersionHolder keys = createSurvey("Name");

                    GuidVersionHolder laterKeys = versionSurvey(keys);
                    boolean isPublished = laterKeys.node.get("published").asBoolean();
                    assertNotEquals("versionedOn has been updated", keys.versionedOn, laterKeys.versionedOn);
                    assertFalse("New survey is not published", isPublished);

                    isPublished = publishSurvey(laterKeys);
                    assertTrue("New survey is published", isPublished);
                } finally {
                    helper.deleteOneUser();    
                }
            }
        });
    }
    
    @Test
    public void getAllVersionsOfASurvey() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                GuidVersionHolder keys = null;
                try {
                    helper.createOneUser(roles);
                    
                    keys = createSurvey("Name");

                    keys = versionSurvey(keys);

                    int count = getAllVersionsOfSurveysCount(keys);
                    assertEquals("There are two versions for this survey", 2, count);
                } finally {
                    helper.deleteOneUser();    
                }
            }
        });        
    }
    
    @Test
    public void canGetMostRecentOrRecentlyPublishedSurveys() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                try {
                    helper.createOneUser(roles);
                    
                    GuidVersionHolder keys = createSurvey("Name 1");
                    keys = versionSurvey(keys);
                    keys = versionSurvey(keys);

                    GuidVersionHolder keys2 = createSurvey("Name 2");
                    keys2 = versionSurvey(keys2);
                    keys2 = versionSurvey(keys2);
                    
                    GuidVersionHolder keys3 = createSurvey("Name 3");
                    keys3 = versionSurvey(keys3);
                    keys3 = versionSurvey(keys3);

                    List<GuidVersionHolder> versions = getMostRecentSurveys();
                    assertEquals("There are two items", 3, versions.size());
                    assertEquals("Last version in list", keys3.versionedOn, versions.get(0).versionedOn);
                    assertEquals("Last version in list", keys2.versionedOn, versions.get(1).versionedOn);
                    
                    publishSurvey(keys);
                    publishSurvey(keys3);
                    versions = getMostRecentlyPublishedSurveys();
                    assertEquals("One published item", 2, versions.size());
                    assertEquals("Published version", keys3.versionedOn, versions.get(0).versionedOn);
                    assertEquals("Published version", keys.versionedOn, versions.get(1).versionedOn);
                    
                } finally {
                    helper.deleteOneUser();    
                }
            }
        });        
    }
    
    @Test
    public void canUpdateASurvey() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                try {
                    helper.createOneUser(roles);
                    
                    GuidVersionHolder keys = createSurvey("Name");
                    JsonNode node = getSurvey(keys);
                    
                    logger.info(node.toString());
                } finally {
                    helper.deleteOneUser();    
                }
            }
        });          
    }
    
    public class GuidVersionHolder {
        public final String guid;
        public final String versionedOn;
        public final JsonNode node;
        public GuidVersionHolder(String guid, String versionedOn, JsonNode node) {
            this.guid = guid;
            this.versionedOn = versionedOn;
            this.node = node;
        }
        public String toString() {
            return "GuidVersionHolder [guid=" + guid + ", versionedOn=" + versionedOn + "]";
        }
    }
    
    private GuidVersionHolder createSurvey(String name) throws Exception {
        String content = createSurveyObject(name);
        Response response = TestUtils.getURL(helper.getUserSessionToken(), SURVEYS_URL).post(content).get(TIMEOUT);
        assertEquals("200 response [createSurvey]", SC_OK, response.getStatus());
        
        JsonNode node = mapper.readTree(response.getBody());
        String surveyGuid = node.get("guid").asText();
        String timestamp = node.get("versionedOn").asText();
        return new GuidVersionHolder(surveyGuid, timestamp, node);
    }
    
    private GuidVersionHolder versionSurvey(GuidVersionHolder keys) throws Exception {
        String url = String.format(VERSION_SURVEY_URL, keys.guid, keys.versionedOn); 
        Response response = TestUtils.getURL(helper.getUserSessionToken(), url).post("").get(TIMEOUT);
        assertEquals("200 response [versionSurvey]", SC_OK, response.getStatus());

        JsonNode node = mapper.readTree(response.getBody());
        String surveyGuid = node.get("guid").asText();
        String versionedOn = node.get("versionedOn").asText();
        return new GuidVersionHolder(surveyGuid, versionedOn, node);
    }
    
    private boolean publishSurvey(GuidVersionHolder keys) throws Exception {
        String url = String.format(PUBLISH_SURVEY_URL, keys.guid, keys.versionedOn);
        Response response = TestUtils.getURL(helper.getUserSessionToken(), url).post("").get(TIMEOUT);
        assertEquals("200 response [publishSurvey]", SC_OK, response.getStatus());
        
        // Get it. It should be published
        url = String.format(GET_SURVEY_URL, keys.guid, keys.versionedOn);
        response = TestUtils.getURL(helper.getUserSessionToken(), url).get().get(TIMEOUT);
        JsonNode node = mapper.readTree(response.getBody());
        
        return node.get("published").asBoolean();
    }
    
    private int getAllVersionsOfSurveysCount(GuidVersionHolder keys) throws Exception {
        String url = String.format(GET_VERSIONS_OF_SURVEY_URL, keys.guid);
        Response response = TestUtils.getURL(helper.getUserSessionToken(), url).get().get(TIMEOUT);
        assertEquals("200 response [allVersionsOfSurveysCount]", SC_OK, response.getStatus());
        
        JsonNode node = mapper.readTree(response.getBody());
        return node.get("total").asInt();                    
    }
    
    private List<GuidVersionHolder> getMostRecentSurveys() throws Exception {
        Response response = TestUtils.getURL(helper.getUserSessionToken(), RECENT_SURVEYS_URL).get().get(TIMEOUT);
        assertEquals("200 response [mostRecentSurveys]", SC_OK, response.getStatus());
        
        JsonNode node = mapper.readTree(response.getBody());
        List<GuidVersionHolder> list = Lists.newArrayList();
        ArrayNode array = (ArrayNode)node.get("items");
        for (int i=0; i < array.size(); i++) {
            String surveyGuid = array.get(i).get("guid").asText();
            String versionedOn = array.get(i).get("versionedOn").asText();
            list.add(new GuidVersionHolder(surveyGuid, versionedOn, array.get(i)));
        }
        return list;
    }
    
    private JsonNode getSurvey(GuidVersionHolder keys) throws Exception {
        String url = String.format(GET_SURVEY_URL, keys.guid, keys.versionedOn);
        Response response = TestUtils.getURL(helper.getUserSessionToken(), url).get().get(TIMEOUT);
        
        return mapper.readTree(response.getBody());
    }

    private List<GuidVersionHolder> getMostRecentlyPublishedSurveys() throws Exception {
        Response response = TestUtils.getURL(helper.getUserSessionToken(), RECENT_PUBLISHED_SURVEYS_URL).get().get(TIMEOUT);
        assertEquals("200 response [mostRecentSurveys]", SC_OK, response.getStatus());
        
        JsonNode node = mapper.readTree(response.getBody());
        List<GuidVersionHolder> list = Lists.newArrayList();
        ArrayNode array = (ArrayNode)node.get("items");
        for (int i=0; i < array.size(); i++) {
            String surveyGuid = array.get(i).get("guid").asText();
            String versionedOn = array.get(i).get("versionedOn").asText();
            list.add(new GuidVersionHolder(surveyGuid, versionedOn, array.get(i)));
        }
        return list;
    }
}