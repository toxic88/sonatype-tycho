package org.sonatype.tycho.test.tycho012;

import java.io.File;

import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.Test;
import org.sonatype.tycho.test.AbstractTychoIntegrationTest;

public class LocalMavenRepositoryTest extends AbstractTychoIntegrationTest {

	@Test
	public void testLocalMavenRepository() throws Exception {
        Verifier v01 = getVerifier("tycho164/build01", false);
    	v01.getCliOptions().add( "-DtargetPlatform=" + getTargetPlatforn() );
        v01.executeGoal("install");
        v01.verifyErrorFreeLog();

        Verifier v02 = getVerifier("tycho164/build02", false);
    	v02.getCliOptions().add( "-DtargetPlatform=" + getTargetPlatforn() );
        v02.executeGoal("install");
        v02.verifyErrorFreeLog();

        File site = new File(v02.getBasedir(), "build02.site01/target/site");

        Assert.assertEquals(2, new File(site, "features").listFiles().length);
        Assert.assertEquals(3, new File(site, "plugins").listFiles().length);
	}

	@Test
	public void testP2LocalMavenRepository() throws Exception {
        Verifier v01 = getVerifier("tycho0209/build01", false);
        v01.getCliOptions().add( "-Dp2.repo=file://" + new File("repositories/e342").getCanonicalPath());
        v01.getCliOptions().add( "-Dmaven.test.skip=true" );
        v01.executeGoal("install");
        v01.verifyErrorFreeLog();

        Verifier v02 = getVerifier("tycho0209/build02", false);
        v02.getCliOptions().add( "-Dp2.repo=file://" + new File("repositories/e342").getCanonicalPath());
        v02.executeGoal("install");
        v02.verifyErrorFreeLog();

        File site = new File(v02.getBasedir(), "build02.site01/target/site");

        Assert.assertEquals(2, new File(site, "features").listFiles().length);
        Assert.assertEquals(3, new File(site, "plugins").listFiles().length);
	    
	}
}
