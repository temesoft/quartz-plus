package org.quartzplus;

import io.github.temesoft.testpojo.TestPojo;
import org.junit.jupiter.api.Test;
import org.quartzplus.domain.JobExecutionLog;

class PojoTest {

    @Test
    void testAllDomainModels() {
        TestPojo.processPackage(JobExecutionLog.class.getPackageName())
                .testAll();
    }
}
