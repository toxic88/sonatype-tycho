package org.sonatype.tycho.plugins.p2;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.sonatype.tycho.osgi.EquinoxEmbedder;
import org.sonatype.tycho.p2.facade.P2Generator;
import org.sonatype.tycho.p2.facade.RepositoryLayoutHelper;

/**
 * @goal p2-metadata
 */
public class P2MetadataMojo
    extends AbstractMojo
{
    /** @parameter expression="${project}" */
    protected MavenProject project;

    /** @parameter default-value="true" */
    protected boolean attachP2Metadata;

    /** @component */
    protected MavenProjectHelper projectHelper;

    /** @component */
    private EquinoxEmbedder equinox;

    private P2Generator p2;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        attachP2Metadata();
    }

    protected P2Generator getP2Generator()
    {
        if ( p2 == null )
        {
            p2 = equinox.getService( P2Generator.class );

            if ( p2 == null )
            {
                throw new IllegalStateException( "Could not acquire P2 metadata service" );
            }
        }
        return p2;
    }

    protected void attachP2Metadata()
        throws MojoExecutionException
    {
        if ( !attachP2Metadata )
        {
            return;
        }

        File file = project.getArtifact().getFile();

        if ( file == null || !file.canRead() )
        {
            throw new IllegalStateException();
        }

        File content = new File( project.getBuild().getDirectory(), "p2content.xml" );
        File artifacts = new File( project.getBuild().getDirectory(), "p2artifacts.xml" );

        try
        {
            getP2Generator().generateMetadata( file,
                                               project.getPackaging(),
                                               project.getGroupId(),
                                               project.getArtifactId(),
                                               project.getVersion(),
                                               content,
                                               artifacts );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Could not generate P2 metadata", e );
        }

        projectHelper.attachArtifact( project,
                                      RepositoryLayoutHelper.EXTENSION_P2_METADATA,
                                      RepositoryLayoutHelper.CLASSIFIER_P2_METADATA,
                                      content );

        projectHelper.attachArtifact( project,
                                      RepositoryLayoutHelper.EXTENSION_P2_ARTIFACTS,
                                      RepositoryLayoutHelper.CLASSIFIER_P2_ARTIFACTS,
                                      artifacts );
    }

}
