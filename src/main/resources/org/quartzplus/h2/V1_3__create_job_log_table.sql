--
-- Table structure for table JobExecutionLog
--

DROP TABLE IF EXISTS ${executionLogTablePrefix}JOB_EXECUTION_LOG;

CREATE TABLE ${executionLogTablePrefix}JOB_EXECUTION_LOG (
  id varchar(255) NOT NULL,
  createTime TIMESTAMP NOT NULL,
  duration NUMERIC(12) NOT NULL,
  errorMessage varchar(10240),
  stackTrace varchar(10240),
  fireInstanceId varchar(255) DEFAULT NULL,
  groupName varchar(255) NOT NULL,
  instanceClass varchar(255) NOT NULL,
  jobName varchar(255) NOT NULL,
  priority integer NOT NULL,
  success BOOLEAN NOT NULL,
  triggerName varchar(255) NOT NULL,
  jsonData CLOB,
  PRIMARY KEY (id)
);
