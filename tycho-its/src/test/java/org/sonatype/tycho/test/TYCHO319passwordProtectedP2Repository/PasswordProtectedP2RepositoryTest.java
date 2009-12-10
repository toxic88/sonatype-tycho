package org.sonatype.tycho.test.TYCHO319passwordProtectedP2Repository;

import java.io.File;
import java.util.Arrays;

import org.apache.maven.it.Verifier;
import org.codehaus.tycho.model.Target;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonatype.tycho.test.AbstractTychoIntegrationTest;
import org.sonatype.tycho.test.util.HttpServer;

@SuppressWarnings( "unchecked" )
public class PasswordProtectedP2RepositoryTest
    extends AbstractTychoIntegrationTest
{

    private HttpServer server;

    @Before
    public void startServer()
        throws Exception
    {
        server = HttpServer.startServer( "test-user", "test-password" );
    }

    @After
    public void stopServer()
        throws Exception
    {
        server.stop();
    }

    @Test
    public void testRepository()
        throws Exception
    {
        String url = server.addServer( "foo", new File( "repositories/e342" ) );

        Verifier verifier =
            getVerifier( "/TYCHO319passwordProtectedP2Repository", false,
                         new File( "projects/TYCHO319passwordProtectedP2Repository/settings.xml" ) );
        verifier.getCliOptions().add( "-P=repository" );
        verifier.executeGoals( Arrays.asList( "package", "-Dp2.repo=" + url ) );
        verifier.verifyErrorFreeLog();
    }

    @Test
    public void testTargetDefinition()
        throws Exception
    {
        String url = server.addServer( "foo", new File( "repositories/e342" ) );

        Verifier verifier =
            getVerifier( "/TYCHO319passwordProtectedP2Repository", false,
                         new File( "projects/TYCHO319passwordProtectedP2Repository/settings.xml" ) );
        
        File platformFile = new File( verifier.getBasedir(), "platform.target" );
        Target platform = Target.read( platformFile );
        platform.getLocations().get( 0 ).setRepositoryLocation( url );
        Target.write( platform, platformFile );

        verifier.getCliOptions().add( "-P=target-definition" );
        verifier.executeGoals( Arrays.asList( "package", "-Dp2.repo=" + url ) );
        verifier.verifyErrorFreeLog();
    }
}
