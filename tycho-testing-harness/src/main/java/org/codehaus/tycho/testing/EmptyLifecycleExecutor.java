package org.codehaus.tycho.testing;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.RepositoryRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * A stub implementation that assumes an empty lifecycle to bypass interaction with the plugin manager and to avoid
 * plugin artifact resolution from repositories.
 * 
 * @author Benjamin Bentmann
 */
public class EmptyLifecycleExecutor
    implements LifecycleExecutor
{

    public MavenExecutionPlan calculateExecutionPlan( MavenSession session, String... tasks )
    {
        return new MavenExecutionPlan( Collections.<MojoExecution> emptyList(), null, null );
    }

    public void execute( MavenSession session )
    {
    }

    public Xpp3Dom getDefaultPluginConfiguration( String groupId, String artifactId, String version, String goal,
                                                  MavenProject project, ArtifactRepository localRepository )
    {
        return null;
    }

    public List<String> getLifecyclePhases()
    {
        return Collections.emptyList();
    }

    public Set<Plugin> getPluginsBoundByDefaultToAllLifecycles( String packaging )
    {
        return Collections.emptySet();
    }

    public void populateDefaultConfigurationForPlugins( Collection<Plugin> plugins, RepositoryRequest repositoryRequest )
    {
    }

    public void populateDefaultConfigurationForPlugin( Plugin plugin, RepositoryRequest repositoryRequest )
    {
    }

    public void resolvePluginVersion( Plugin plugin, RepositoryRequest repositoryRequest )
    {
    }

    public void calculateForkedExecutions( MojoExecution mojoExecution, MavenSession session )
    {
    }

    public List<MavenProject> executeForkedExecutions( MojoExecution mojoExecution, MavenSession session )
    {
        return Collections.emptyList();
    }
}
