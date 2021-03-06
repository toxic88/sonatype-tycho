package org.codehaus.tycho.eclipsepackaging;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.tycho.MavenSessionUtils;
import org.codehaus.tycho.TychoConstants;
import org.codehaus.tycho.buildversion.VersioningHelper;
import org.codehaus.tycho.eclipsepackaging.product.Plugin;
import org.codehaus.tycho.model.Feature;
import org.codehaus.tycho.model.PluginRef;
import org.codehaus.tycho.osgitools.features.FeatureDescription;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.osgi.framework.Version;

/**
 * @phase package
 * @goal package-feature
 * @requiresProject
 * @requiresDependencyResolution runtime
 * 
 */
public class PackageFeatureMojo extends AbstractTychoPackagingMojo {

	private static final int KBYTE = 1024;

	private static final String GENERATE_FEATURE = "generate.feature@";

	/**
	 * The maven archiver to use.
	 * 
	 * @parameter
	 */
	private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

	/**
	 * @parameter expression="${project.build.directory}"
	 */
	private File outputDirectory;

	/**
	 * @parameter expression="${project.basedir}"
	 */
	private File basedir;

	/**
	 * Name of the generated JAR.
	 * 
	 * @parameter alias="jarName" expression="${project.build.finalName}"
	 * @required
	 */
	private String finalName;

	/**
	 * Build qualifier. Recommended way to set this parameter is using
	 * build-qualifier goal. 
	 * 
	 * @parameter expression="${buildQualifier}"
	 */
	protected String qualifier;

	public void execute() throws MojoExecutionException, MojoFailureException {
	    initializeProjectContext();

		Properties props = new Properties();
		try {
			FileInputStream is = new FileInputStream(new File(basedir, "build.properties"));
			try {
				props.load(is);
			} finally {
				is.close();
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Error reading build properties", e);
		}

		outputDirectory.mkdirs();

		File featureXml = new File(outputDirectory, Feature.FEATURE_XML);
		try {
			updateFeatureXml(featureXml);
		} catch (IOException e) {
			throw new MojoExecutionException("Error updating feature.xml", e);
		}

		File outputJar = new File(outputDirectory, finalName + ".jar");
		outputJar.getParentFile().mkdirs();
		List<String> binIncludes = toFilePattern(props.getProperty("bin.includes"));
		List<String> binExcludes = toFilePattern(props.getProperty("bin.excludes"));
		binExcludes.add(Feature.FEATURE_XML); // we'll include updated feature.xml

		MavenArchiver archiver = new MavenArchiver();
		JarArchiver jarArchiver = getJarArchiver();
		archiver.setArchiver(jarArchiver);
		archiver.setOutputFile(outputJar);
		jarArchiver.setDestFile(outputJar);

		try {
		    archiver.getArchiver().addFileSet( getFileSet( basedir, binIncludes, binExcludes ) );
            archiver.getArchiver().addFile( featureXml, Feature.FEATURE_XML );
			archiver.createArchive(project, archive);
		} catch (Exception e) {
			throw new MojoExecutionException("Error creating feature package",
					e);
		}

		project.getArtifact().setFile(outputJar);

		if(false) {
			//TODO generate source not supported yet
			generateSources(props);
		}
	}

	private void updateFeatureXml(File featureXml) throws MojoExecutionException, IOException {
		FeatureDescription featureDesc = featureResolutionState.getFeatureByLocation( project.getBasedir() );
		Feature feature = new Feature(featureDesc.getFeature());

		// expand version if necessary
		if (VersioningHelper.isSnapshotVersion(featureDesc.getVersion())) {
			Version version = VersioningHelper.expandVersion(featureDesc.getVersion(), qualifier);
			feature.setVersion(version.toString());
			VersioningHelper.setExpandedVersion(session, featureDesc.getLocation(), version.toString());
		}

		// update included/referenced plugins
		for (PluginRef pluginRef : feature.getPlugins()) {
			String bundleId = pluginRef.getId();
			String bundleVersion = pluginRef.getVersion();

			if ("0.0.0".equals(bundleVersion)) {
				bundleVersion = TychoConstants.HIGHEST_VERSION;
			}

			BundleDescription bundle = bundleResolutionState.getBundle(bundleId, bundleVersion);
			if (bundle == null) {
				getLog().warn(project.getId() + " referenced unknown bundle " + bundleId + ":" + bundleVersion);
				continue;
			}

            MavenProject bundleProject = MavenSessionUtils.getMavenProject( session, bundle.getLocation() );

			pluginRef.setVersion(VersioningHelper.getExpandedVersion(session, bundle));

			File file;
			if (bundleProject != null) {
				file = bundleProject.getArtifact().getFile();
			} else {
				file = new File(bundle.getLocation());
			}

			long downloadSize = 0;
			long installSize = 0;
			if (file.isFile()) {
				JarFile jar = new JarFile(file);
				try {
					Enumeration<JarEntry> entries = jar.entries();
					while (entries.hasMoreElements()) {
						JarEntry entry = (JarEntry) entries.nextElement();
						long entrySize = entry.getSize();
						if (entrySize > 0) {
							installSize += entrySize;
						}
					}
				} finally {
					jar.close();
				}
				downloadSize = file.length();
			} else {
				getLog().info("Download/install size is not calculated for directory based bundle " + bundleId);
			}

			pluginRef.setDownloadSide(downloadSize / KBYTE);
			pluginRef.setInstallSize(installSize / KBYTE);
		}

		// update included/referenced features
		for (Feature.FeatureRef ref : feature.getIncludedFeatures()) {
			String refVersion = ref.getVersion();
			String refId = ref.getId();

			FeatureDescription refDescription = featureResolutionState.getFeature(refId, refVersion);
			if (refDescription == null) {
				getLog().warn(project.getId() + " referenced unknown feature " + refId + ":" + refVersion);
				continue;
			}
		}

		Feature.write(feature, featureXml);
	}

	private void generateSources(Properties props)
			throws MojoExecutionException {
		boolean individualSourceBundle = Boolean.parseBoolean(props
				.getProperty("individualSourceBundles"));

		Set<Object> keys = props.keySet();
		for (Object keyObj : keys) {
			String key = keyObj.toString();
			if (key.toString().startsWith(GENERATE_FEATURE)) {
				String baseFeature = props.getProperty(key);
				String sourceFeature = key.substring(GENERATE_FEATURE.length());

				if (individualSourceBundle) {

				} else {
					generateSourceFeature(baseFeature, sourceFeature);
				}
			}
		}

	}

	private JarArchiver getJarArchiver() throws MojoExecutionException {
		try {
			JarArchiver jarArchiver = (JarArchiver) plexus.lookup(
					JarArchiver.ROLE, "jar");
			return jarArchiver;
		} catch (ComponentLookupException e) {
			throw new MojoExecutionException("Unable to get JarArchiver", e);
		}
	}

	private void generateSourceFeature(String baseFeatureId, String sourceFeature) throws MojoExecutionException {
		FeatureDescription baseFeature = featureResolutionState.getFeature(baseFeatureId,	TychoConstants.HIGHEST_VERSION);
		if (baseFeature == null) {
			getLog().warn("Base feature not found: " + baseFeatureId);
			return;
		}

		String version = baseFeature.getVersion().toString();

		List<Plugin> plugins = new ArrayList<Plugin>();
		plugins.add(new Plugin(sourceFeature, version));
		generateSourceFeature(sourceFeature, version, plugins);
	}

	private void generateSourceFeature(String featureId, String featureVersion,
			List<Plugin> plugins) throws MojoExecutionException {
		File target = new File(outputDirectory, featureId);
		target.mkdirs();

		File featureFile = new File(target, Feature.FEATURE_XML);

		// TODO check if tycho already has something to write a new feature
		try {
			FileWriter fw = new FileWriter(featureFile);
			fw.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
					.append('\n');
			fw.append(
					"<feature id=\"" + featureId + "\" version=\""
							+ featureVersion + "\" primary=\"false\" >")
					.append('\n');
			for (Plugin plugin : plugins) {
				fw.append(
						"\t<plugin id=\"" + plugin.getId() + "\" version=\""
								+ plugin.getVersion() + "\" />").append('\n');
			}
			fw.append("</feature>").append('\n');
			fw.flush();
			fw.close();
		} catch (IOException e) {
			throw new MojoExecutionException("Error creating feature.xml for "
					+ featureId + "_" + featureVersion, e);
		}

		File outputJar = new File(outputDirectory, featureId + "-"
				+ featureVersion + ".jar");

		JarArchiver jarArchiver = getJarArchiver();
		jarArchiver.setDestFile(outputJar);
		try {
			jarArchiver.addFile(featureFile, "feature.xml");
			jarArchiver.createArchive();
		} catch (Exception e) {
			throw new MojoExecutionException("Error packing source feature "
					+ featureId + "_" + featureVersion, e);
		}
	}

}