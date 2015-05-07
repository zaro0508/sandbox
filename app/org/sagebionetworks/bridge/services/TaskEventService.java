package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.TaskEventDao;
import org.sagebionetworks.bridge.dynamodb.DynamoTaskEvent;
import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.tasks.TaskEvent;
import org.sagebionetworks.bridge.models.tasks.TaskEventAction;
import org.sagebionetworks.bridge.models.tasks.TaskEventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Joiner;

@Component
public class TaskEventService {

    private TaskEventDao taskEventDao;
    
    @Autowired
    public void setTaskEventDao(TaskEventDao taskEventDao) {
        this.taskEventDao = taskEventDao;
    }
    
    public void publishEvent(String healthCode, SurveyAnswer answer) {
        checkNotNull(answer);
        
        TaskEvent event = new DynamoTaskEvent.Builder()
            .withHealthCode(healthCode)
            .withTimestamp(answer.getAnsweredOn())
            .withType(TaskEventType.QUESTION)
            .withId(answer.getQuestionGuid())
            .withAction(TaskEventAction.ANSWERED)
            .withValue(Joiner.on(",").join(answer.getAnswers())).build();
        taskEventDao.publishEvent(event);
    }
    
    public void publishEvent(SurveyResponse response) {
        checkNotNull(response);
        
        TaskEvent event = new DynamoTaskEvent.Builder()
            .withHealthCode(response.getHealthCode())
            .withTimestamp(response.getCompletedOn())
            .withType(TaskEventType.SURVEY)
            .withId(response.getSurveyGuid())
            .withAction(TaskEventAction.FINISHED)
            .build();
        taskEventDao.publishEvent(event);
    }
    
    public void publishEvent(TaskEvent event) {
        checkNotNull(event);
        taskEventDao.publishEvent(event);
    }

    public Map<String, DateTime> getTaskEventMap(String healthCode) {
        checkNotNull(healthCode);
        return taskEventDao.getTaskEventMap(healthCode);
    }

    public void deleteTaskEvents(String healthCode) {
        checkNotNull(healthCode);
        taskEventDao.deleteTaskEvents(healthCode);
    }

}
