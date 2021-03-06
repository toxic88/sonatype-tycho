package org.codehaus.tycho.osgitools.targetplatform;

import java.io.File;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.tycho.DefaultTargetPlatform;
import org.codehaus.tycho.ProjectType;
import org.codehaus.tycho.TargetPlatform;
import org.codehaus.tycho.TargetPlatformResolver;

/**
 * Creates target platform based on local eclipse installation.
 */
@Component( role = TargetPlatformResolver.class, hint = LocalTargetPlatformResolver.ROLE_HINT, instantiationStrategy = "per-lookup" )
public class LocalTargetPlatformResolver
    extends AbstractTargetPlatformResolver
    implements TargetPlatformResolver
{

    public static final String ROLE_HINT = "local";

    @Requirement
    private EclipseInstallationLayout layout;

    public TargetPlatform resolvePlatform( MavenSession session, MavenProject project, List<Dependency> dependencies )
    {
        DefaultTargetPlatform platform = createPlatform();

        for ( File site : layout.getSites() )
        {
            platform.addSite( site );

            for ( File plugin : layout.getPlugins( site ) )
            {
                platform.addArtifactFile( ProjectType.ECLIPSE_PLUGIN, plugin );
            }

            for ( File feature : layout.getFeatures( site ) )
            {
                platform.addArtifactFile( ProjectType.ECLIPSE_FEATURE, feature );
            }
        }

        addProjects( session, platform );

        if ( platform.isEmpty() )
        {
            getLogger().warn( "Could not find any bundles or features in " + layout.getLocation().getAbsolutePath() );
        }

        return platform;
    }

    public void setLocation( File location )
    {
        layout.setLocation( location );
    }
}
