package org.quartzplus;

import io.github.temesoft.testpojo.TestPojo;
import org.junit.jupiter.api.Test;
import org.quartzplus.domain.JobExecutionLog;

public class PojoTest {

    @Test
    public void testAllDomainModels() {
        TestPojo.processPackage(JobExecutionLog.class.getPackageName())
                .testAll();
    }
}
