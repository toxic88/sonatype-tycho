package org.codehaus.tycho.osgitools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.tycho.ProjectType;
import org.codehaus.tycho.maven.DependenciesReader;
import org.codehaus.tycho.model.ProductConfiguration;

@Component( role = DependenciesReader.class, hint = ProjectType.ECLIPSE_APPLICATION )
public class EclipseApplicationDependenciesReader extends
		AbstractDependenciesReader {

	public List<Dependency> getDependencies(MavenSession session, MavenProject project)
			throws MavenExecutionException {
		// XXX at present time there is no way to get plugin configuration here
		// http://issues.sonatype.org/browse/TYCHO-190
		String productFilename = project.getArtifactId() + ".product";

		File productFile = new File(project.getBasedir(), productFilename);
		if (!productFile.exists()) {
			getLogger().warn("product file not found at " + productFile.getAbsolutePath());
			return NO_DEPENDENCIES;
		}

		ProductConfiguration product;
		try {
			product = ProductConfiguration.read(productFile);
		} catch (Exception e) {
			String m = e.getMessage();
			if (null == m) {
				m = e.getClass().getName();
			}
			MavenExecutionException me = new MavenExecutionException(m, project
					.getFile());
			me.initCause(e);
			throw me;
		}

		ArrayList<Dependency> result = new ArrayList<Dependency>();

		result.addAll(getPluginsDependencies(project, product.getPlugins(), session));
		result.addAll(getFeaturesDependencies(project, product.getFeatures(), session));

		return new ArrayList<Dependency>(result);
	}

}
