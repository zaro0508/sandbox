package org.sagebionetworks.bridge.services.backfill;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.List;

import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.models.BackfillTask;
import org.sagebionetworks.bridge.models.HealthId;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AccountEncryptionService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.stormpath.StormpathAccountIterator;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;

/**
 * Backfills study IDs to the health code table.
 */
public class StudyIdBackfill extends AsyncBackfillTemplate  {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyIdBackfill.class);

    private BackfillRecordFactory backfillRecordFactory;
    private StudyService studyService;
    private Client stormpathClient;
    private AccountEncryptionService accountEncryptionService;
    private HealthCodeDao healthCodeDao;

    @Autowired
    public void setBackfillRecordFactory(BackfillRecordFactory backfillRecordFactory) {
        this.backfillRecordFactory = backfillRecordFactory;
    }

    @Autowired
    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    @Autowired
    public void setStormpathClient(Client client) {
        this.stormpathClient = client;
    }

    @Autowired
    public void setAccountEncryptionService(AccountEncryptionService accountEncryptionService) {
        this.accountEncryptionService = accountEncryptionService;
    }

    @Autowired
    public void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
    }

    @Override
    int getLockExpireInSeconds() {
        return 30 * 60;
    }

    @Override
    void doBackfill(final BackfillTask task, final BackfillCallback callback) {
        final List<Study> studies = studyService.getStudies();
        Application application = StormpathFactory.getStormpathApplication(stormpathClient);
        StormpathAccountIterator iterator = new StormpathAccountIterator(application);
        while (iterator.hasNext()) {
            List<Account> accountList = iterator.next();
            for (final Account account : accountList) {
                for (final Study study : studies) {
                    HealthId healthId = accountEncryptionService.getHealthCode(study, account);
                    if (healthId != null) {
                        try {
                            String healthCode = healthId.getCode();
                            if (healthCode != null) {
                                final String studyId = healthCodeDao.getStudyIdentifier(healthCode);
                                if (isBlank(studyId)) {
                                    String msg = "Backfill needed as study ID is blank.";
                                    callback.newRecords(backfillRecordFactory.createOnly(task, study, account, msg));
                                } else {
                                    String msg = "Study ID already exists.";
                                    callback.newRecords(backfillRecordFactory.createOnly(task, study, account, msg));
                                }
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.error(e.getMessage(), e);
                            String msg = e.getClass().getName() + " " + e.getMessage();
                            callback.newRecords(backfillRecordFactory.createOnly(task, study, account, msg));
                        }
                    }
                }
            }
        }
    }
}