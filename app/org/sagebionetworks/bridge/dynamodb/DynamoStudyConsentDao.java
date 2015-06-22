package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;

@Component
public class DynamoStudyConsentDao implements StudyConsentDao {

    private DynamoDBMapper mapper;

    @Autowired
    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoStudyConsent1.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public StudyConsent addConsent(StudyIdentifier studyIdentifier, String storagePath, DateTime createdOn) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyIdentifier.getIdentifier());
        consent.setCreatedOn(createdOn.getMillis());
        consent.setStoragePath(storagePath);
        mapper.save(consent);
        return consent;
    }

    @Override
    public StudyConsent activate(StudyConsent studyConsent) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setStudyKey(studyConsent.getStudyKey());
        hashKey.setCreatedOn(studyConsent.getCreatedOn());
        DynamoStudyConsent1 consent = mapper.load(hashKey);
        
        StudyIdentifier studyId = new StudyIdentifierImpl(studyConsent.getStudyKey());
        StudyConsent activeConsent = getConsent(studyId);
        
        consent.setActive(true);
        mapper.save(consent);
        
        if (activeConsent != null && activeConsent.getCreatedOn() != consent.getCreatedOn()) {
            ((DynamoStudyConsent1)activeConsent).setActive(false);
            mapper.save(activeConsent);
        }
        return consent;
    }

    @Override
    public StudyConsent getConsent(StudyIdentifier studyIdentifier) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setStudyKey(studyIdentifier.getIdentifier());
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey)
                .withScanIndexForward(false)
                .withQueryFilterEntry("active", new Condition()
                        .withComparisonOperator(ComparisonOperator.EQ)
                        .withAttributeValueList(new AttributeValue().withN("1")));
        PaginatedQueryList<DynamoStudyConsent1> page = mapper.query(DynamoStudyConsent1.class, queryExpression);
        if (page.isEmpty()) {
            return null;
        }
        return page.iterator().next();
    }

    @Override
    public StudyConsent getConsent(StudyIdentifier studyIdentifier, long timestamp) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyIdentifier.getIdentifier());
        consent.setCreatedOn(timestamp);
        return mapper.load(consent);
    }

    @Override
    public List<StudyConsent> getConsents(StudyIdentifier studyIdentifier) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setStudyKey(studyIdentifier.getIdentifier());
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey)
                .withScanIndexForward(false);
        PaginatedQueryList<DynamoStudyConsent1> consents = mapper.query(DynamoStudyConsent1.class, queryExpression);
        List<StudyConsent> results = new ArrayList<StudyConsent>();
        for (DynamoStudyConsent1 consent : consents) {
            results.add(consent);
        }
        return results;
    }
    
    @Override
    public void deleteAllConsents(StudyIdentifier studyIdentifier) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setStudyKey(studyIdentifier.getIdentifier());
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey)
                .withScanIndexForward(false);
        
        PaginatedQueryList<DynamoStudyConsent1> consents = mapper.query(DynamoStudyConsent1.class, queryExpression);
        List<StudyConsent> consentsToDelete = new ArrayList<StudyConsent>();
        for (DynamoStudyConsent1 consent : consents) {
            consentsToDelete.add(consent);
        }
        if (!consentsToDelete.isEmpty()) {
            List<FailedBatch> failures = mapper.batchDelete(consentsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
}
