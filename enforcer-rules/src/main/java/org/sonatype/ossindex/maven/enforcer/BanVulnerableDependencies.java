/*
 * Copyright (c) 2018-present Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package org.sonatype.ossindex.maven.enforcer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.sonatype.ossindex.maven.common.ComponentReportAssistant;
import org.sonatype.ossindex.maven.common.ComponentReportRequest;
import org.sonatype.ossindex.maven.common.ComponentReportResult;
import org.sonatype.ossindex.maven.common.MavenCoordinates;
import org.sonatype.ossindex.service.client.OssindexClientConfiguration;

import com.google.common.base.Splitter;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.CumulativeScopeArtifactFilter;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;

/**
 * Enforcer rule to ban vulnerable dependencies.
 *
 * @since ???
 */
public class BanVulnerableDependencies
    extends EnforcerRuleSupport
{
  private OssindexClientConfiguration clientConfiguration = new OssindexClientConfiguration();

  private String scope;

  private boolean transitive = true;

  private Set<MavenCoordinates> excludeCoordinates = new HashSet<>();

  private float cvssScoreThreshold = 0;

  private Set<String> excludeVulnerabilityIds = new HashSet<>();

  public OssindexClientConfiguration getClientConfiguration() {
    return clientConfiguration;
  }

  public void setClientConfiguration(final OssindexClientConfiguration clientConfiguration) {
    this.clientConfiguration = clientConfiguration;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(final String scope) {
    this.scope = scope;
  }

  public boolean isTransitive() {
    return transitive;
  }

  public void setTransitive(final boolean transitive) {
    this.transitive = transitive;
  }

  public Set<MavenCoordinates> getExcludeCoordinates() {
    return excludeCoordinates;
  }

  // TODO: allow setting coordinates from List<String>

  public void setExcludeCoordinates(final Set<MavenCoordinates> excludeCoordinates) {
    this.excludeCoordinates = excludeCoordinates;
  }

  public float getCvssScoreThreshold() {
    return cvssScoreThreshold;
  }

  public void setCvssScoreThreshold(final float cvssScoreThreshold) {
    this.cvssScoreThreshold = cvssScoreThreshold;
  }

  public Set<String> getExcludeVulnerabilityIds() {
    return excludeVulnerabilityIds;
  }

  public void setExcludeVulnerabilityIds(final Set<String> excludeVulnerabilityIds) {
    this.excludeVulnerabilityIds = excludeVulnerabilityIds;
  }

  @Override
  public void execute(@Nonnull final EnforcerRuleHelper helper) throws EnforcerRuleException {
    new Task(helper).run();
  }

  /**
   * Encapsulates state for rule evaluation.
   */
  private class Task
  {
    private final Log log;

    private final MavenSession session;

    private final MavenProject project;

    private final DependencyGraphBuilder graphBuilder;

    private final ComponentReportAssistant reportAssistant;

    public Task(final EnforcerRuleHelper helper) {
      this.log = helper.getLog();
      this.session = lookup(helper, "${session}", MavenSession.class);
      this.project = lookup(helper, "${project}", MavenProject.class);
      this.graphBuilder = lookup(helper, DependencyGraphBuilder.class);
      this.reportAssistant = lookup(helper, ComponentReportAssistant.class);
    }

    // FIXME: adjust all logging to include rule-simple-name; what does this show up as by default?

    public void run() throws EnforcerRuleException {
      // skip if maven is in offline mode
      if (session.isOffline()) {
        log.warn("Skipping " + BanVulnerableDependencies.class.getSimpleName() + "; offline");
        return;
      }

      // skip if packaging is pom
      if ("pom".equals(project.getPackaging())) {
        log.debug("Skipping; POM module");
        return;
      }

      // determine dependencies
      Set<Artifact> dependencies;
      try {
        dependencies = resolveDependencies();
      }
      catch (DependencyGraphBuilderException e) {
        throw new RuntimeException("Failed to resolve dependencies", e);
      }

      // skip if project has no dependencies
      if (dependencies.isEmpty()) {
        log.debug("Skipping; zero dependencies");
        return;
      }

      ComponentReportRequest reportRequest = new ComponentReportRequest();
      reportRequest.setClientConfiguration(clientConfiguration);
      reportRequest.setExcludeCoordinates(excludeCoordinates);
      reportRequest.setExcludeVulnerabilityIds(excludeVulnerabilityIds);
      reportRequest.setCvssScoreThreshold(cvssScoreThreshold);
      reportRequest.setComponents(dependencies);

      ComponentReportResult reportResult = reportAssistant.request(reportRequest);

      if (reportResult.hasVulnerable()) {
        throw new EnforcerRuleException(reportResult.explain());
      }
    }

    /**
     * Resolve dependencies to inspect for vulnerabilities.
     */
    private Set<Artifact> resolveDependencies() throws DependencyGraphBuilderException {
      Set<Artifact> result = new HashSet<>();

      ArtifactFilter artifactFilter = null;
      if (scope != null) {
        List<String> scopes = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(scope);
        artifactFilter = new CumulativeScopeArtifactFilter(scopes);
      }

      DependencyNode node = graphBuilder.buildDependencyGraph(project, artifactFilter);
      collectArtifacts(result, node);

      return result;
    }

    /**
     * Collect artifacts from dependency.
     *
     * Optionally including transitive dependencies if {@link #transitive} is {@code true}.
     */
    @SuppressWarnings("Duplicates")
    private void collectArtifacts(final Set<Artifact> artifacts, final DependencyNode node) {
      if (node.getChildren() != null) {
        for (DependencyNode child : node.getChildren()) {
          artifacts.add(child.getArtifact());

          if (transitive) {
            collectArtifacts(artifacts, child);
          }
        }
      }
    }
  }
}