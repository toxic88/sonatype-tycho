package org.sonatype.tycho.p2.facade;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.ProjectDependenciesResolver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.Authentication;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Server;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.codehaus.tycho.DefaultTargetPlatform;
import org.codehaus.tycho.ExecutionEnvironmentUtils;
import org.codehaus.tycho.PlatformPropertiesUtils;
import org.codehaus.tycho.ProjectType;
import org.codehaus.tycho.TargetEnvironment;
import org.codehaus.tycho.TargetPlatform;
import org.codehaus.tycho.TargetPlatformConfiguration;
import org.codehaus.tycho.TargetPlatformResolver;
import org.codehaus.tycho.TychoConstants;
import org.codehaus.tycho.model.Target;
import org.codehaus.tycho.model.Target.Location;
import org.codehaus.tycho.osgitools.targetplatform.AbstractTargetPlatformResolver;
import org.codehaus.tycho.p2.P2ArtifactRepositoryLayout;
import org.sonatype.tycho.osgi.EquinoxEmbedder;
import org.sonatype.tycho.p2.facade.internal.DefaultTychoRepositoryIndex;
import org.sonatype.tycho.p2.facade.internal.MavenRepositoryReader;
import org.sonatype.tycho.p2.facade.internal.P2Logger;
import org.sonatype.tycho.p2.facade.internal.P2RepositoryCache;
import org.sonatype.tycho.p2.facade.internal.P2ResolutionResult;
import org.sonatype.tycho.p2.facade.internal.P2Resolver;
import org.sonatype.tycho.p2.facade.internal.P2ResolverFactory;
import org.sonatype.tycho.p2.facade.internal.TychoRepositoryIndex;

@Component( role = TargetPlatformResolver.class, hint = P2TargetPlatformResolver.ROLE_HINT, instantiationStrategy = "per-lookup" )
public class P2TargetPlatformResolver
    extends AbstractTargetPlatformResolver
    implements TargetPlatformResolver, Initializable
{

    public static final String ROLE_HINT = "p2";

    public static final String POM_DEPENDENCIES_CONSIDER = "consider";

    @Requirement
    private EquinoxEmbedder equinox;

    @Requirement
    private PlexusContainer plexus;

    @Requirement
    private RepositorySystem repositorySystem;

    private P2ResolverFactory resolverFactory;

    @Requirement( hint = "p2" )
    private ArtifactRepositoryLayout p2layout;

    @Requirement
    private P2RepositoryCache repositoryCache;

    @Requirement
    private ProjectDependenciesResolver projectDependenciesResolver;

    private static final ArtifactRepositoryPolicy P2_REPOSITORY_POLICY =
        new ArtifactRepositoryPolicy( true, ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER,
                                      ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE );

    public TargetPlatform resolvePlatform( MavenSession session, MavenProject project, List<Dependency> dependencies )
    {
        TargetPlatformConfiguration configuration = (TargetPlatformConfiguration) project
            .getContextValue( TychoConstants.CTX_TARGET_PLATFORM_CONFIGURATION );

        P2Resolver resolver = resolverFactory.createResolver();

        resolver.setRepositoryCache( repositoryCache );

        resolver.setLogger( new P2Logger()
        {
            public void debug( String message )
            {
                if ( message.length() > 0 )
                {
                    getLogger().info( message ); // TODO
                }
            }

            public void info( String message )
            {
                getLogger().info( message );
            }
        } );

        for ( MavenProject otherProject : session.getProjects() )
        {
            if ( getLogger().isDebugEnabled() )
            {
                getLogger().debug( "P2resolver.addMavenProject " + otherProject.toString() );
            }
            resolver.addMavenProject(
                otherProject.getBasedir(),
                otherProject.getPackaging(),
                otherProject.getGroupId(),
                otherProject.getArtifactId(),
                otherProject.getVersion() );
        }

        resolver.setLocalRepositoryLocation( new File( session.getLocalRepository().getBasedir() ) );

        Properties properties = new Properties();
        TargetEnvironment environment = configuration.getEnvironment();
        properties.put( PlatformPropertiesUtils.OSGI_OS, environment.getOs() );
        properties.put( PlatformPropertiesUtils.OSGI_WS, environment.getWs() );
        properties.put( PlatformPropertiesUtils.OSGI_ARCH, environment.getArch() );
        ExecutionEnvironmentUtils.loadVMProfile( properties );

        properties.put( "org.eclipse.update.install.features", "true" );
        resolver.setProperties( properties );

        if ( dependencies != null )
        {
            for ( Dependency dependency : dependencies )
            {
                resolver.addDependency( dependency.getType(), dependency.getArtifactId(), dependency.getVersion() );
            }
        }

        if ( POM_DEPENDENCIES_CONSIDER.equals( configuration.getPomDependencies() ) )
        {
            ArrayList<String> scopes = new ArrayList<String>();
            scopes.add( Artifact.SCOPE_COMPILE );
            try
            {
                for ( Artifact a : projectDependenciesResolver.resolve( project, scopes, session ) )
                {
                    if ( getLogger().isDebugEnabled() )
                    {
                        getLogger().debug( "P2resolver.addMavenArtifact " + a.toString() );
                    }
                    resolver.addMavenArtifact( a.getFile(), a.getType(), a.getGroupId(), a.getArtifactId(), a.getVersion() );
                }
            }
            catch ( AbstractArtifactResolutionException e )
            {
                throw new RuntimeException( "Could not resolve project dependencies", e );
            }
        }

        P2ResolutionResult result;

        for ( ArtifactRepository repository : project.getRemoteArtifactRepositories() )
        {
            try
            {
                URI uri = new URL( repository.getUrl() ).toURI();

                if ( repository.getLayout() instanceof P2ArtifactRepositoryLayout )
                {
                    Authentication auth = repository.getAuthentication();
                    if ( auth != null )
                    {
                        resolver.setCredentials( uri, auth.getUsername(), auth.getPassword() );
                    }

                    resolver.addP2Repository( uri );

                    getLogger().debug("Added p2 repository " + repository.getId() + " (" + repository.getUrl() + ")" );
                }
                else
                {
                    try
                    {
                        MavenRepositoryReader reader = plexus.lookup( MavenRepositoryReader.class );
                        reader.setArtifactRepository( repository );
                        reader.setLocalRepository( session.getLocalRepository() );

                        String repositoryKey = getRepositoryKey( repository );
                        TychoRepositoryIndex index = repositoryCache.getRepositoryIndex( repositoryKey );
                        if ( index == null )
                        {
                            index = new DefaultTychoRepositoryIndex( reader );
                            
                            repositoryCache.putRepositoryIndex( repositoryKey, index );
                        }

                        resolver.addMavenRepository( uri, index, reader );
                        getLogger().debug("Added Maven repository " + repository.getId() + " (" + repository.getUrl() + ")" );
                    }
                    catch ( FileNotFoundException e )
                    {
                        // it happens
                    }
                    catch ( Exception e )
                    {
                        getLogger().debug( "Unable to initialize remote Tycho repository", e );
                    }
                }
            }
            catch ( MalformedURLException e )
            {
                getLogger().warn( "Could not parse repository URL", e );
            }
            catch ( URISyntaxException e )
            {
                getLogger().warn( "Could not parse repository URL", e );
            }
        }

        Target target = configuration.getTarget();

        if ( target != null )
        {
            Set<URI> uris = new HashSet<URI>();

            for ( Target.Location location : target.getLocations() )
            {
                try
                {
                    URI uri = new URI( getMirror( location, session.getRequest().getMirrors() ) );
                    if ( uris.add( uri ) )
                    {
                        String id = location.getRepositoryId();
                        if ( id != null )
                        {
                            Server server = session.getSettings().getServer( id );

                            if ( server != null )
                            {
                                resolver.setCredentials( uri, server.getUsername(), server.getPassword() );
                            }
                            else
                            {
                                getLogger().info( "Unknown server id=" + id + " for repository location=" + location.getRepositoryLocation() );
                            }
                        }

                        resolver.addP2Repository( uri );
                    }
                }
                catch ( URISyntaxException e )
                {
                    getLogger().debug( "Could not parse repository URL", e );
                }

                for ( Target.Unit unit : location.getUnits() )
                {
                    resolver.addDependency( P2Resolver.TYPE_INSTALLABLE_UNIT, unit.getId(), unit.getVersion() );
                }
            }
        }

        result = resolver.resolveProject( project.getBasedir() );

        DefaultTargetPlatform platform = createPlatform();

        platform.addSite( new File( session.getLocalRepository().getBasedir() ) );

        for ( File bundle : result.getBundles() )
        {
            platform.addArtifactFile( ProjectType.ECLIPSE_PLUGIN, bundle );
        }

        for ( File feature : result.getFeatures() )
        {
            platform.addArtifactFile( ProjectType.ECLIPSE_FEATURE, feature );
        }

        addProjects( session, platform );

        return platform;
    }

    private String getRepositoryKey( ArtifactRepository repository )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( repository.getId() );
        sb.append( '|' ).append( repository.getUrl() );
        return sb.toString();
    }

    private String getMirror( Location location, List<Mirror> mirrors )
    {
        String url = location.getRepositoryLocation();
        String id = location.getRepositoryId();
        if ( id == null )
        {
            id = url;
        }

        ArtifactRepository repository = repositorySystem.createArtifactRepository( id, url, p2layout, P2_REPOSITORY_POLICY, P2_REPOSITORY_POLICY );

        Mirror mirror = repositorySystem.getMirror( repository, mirrors );
        
        return mirror != null ? mirror.getUrl() : url;
    }

	public void initialize()
        throws InitializationException
    {
        this.resolverFactory = equinox.getService( P2ResolverFactory.class );
    }
}
