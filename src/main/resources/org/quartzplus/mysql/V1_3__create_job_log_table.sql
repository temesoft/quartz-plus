--
-- Table structure for table JobExecutionLog
--

DROP TABLE IF EXISTS ${executionLogTablePrefix}JOB_EXECUTION_LOG;
CREATE TABLE ${executionLogTablePrefix}JOB_EXECUTION_LOG (
  id varchar(255) NOT NULL,
  createTime datetime NOT NULL,
  duration bigint(20) NOT NULL,
  errorMessage longtext,
  stackTrace longtext,
  fireInstanceId varchar(255) DEFAULT NULL,
  groupName varchar(255) NOT NULL,
  instanceClass varchar(255) NOT NULL,
  jobName varchar(255) NOT NULL,
  priority int(11) NOT NULL,
  success bit(1) NOT NULL,
  triggerName varchar(255) NOT NULL,
  jsonData longtext,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE ${executionLogTablePrefix}JOB_EXECUTION_LOG ADD INDEX JOB_EXECUTION_LOG_groupName_INDEX (groupName);
ALTER TABLE ${executionLogTablePrefix}JOB_EXECUTION_LOG ADD INDEX JOB_EXECUTION_LOG_instanceClass_INDEX (instanceClass);
ALTER TABLE ${executionLogTablePrefix}JOB_EXECUTION_LOG ADD INDEX JOB_EXECUTION_LOG_jobName_INDEX (jobName);
ALTER TABLE ${executionLogTablePrefix}JOB_EXECUTION_LOG ADD INDEX JOB_EXECUTION_LOG_triggerName_INDEX (triggerName);
ALTER TABLE ${executionLogTablePrefix}JOB_EXECUTION_LOG ADD INDEX JOB_EXECUTION_LOG_success_INDEX (success);
