/*
 * Copyright The Stargate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.stargate.it.http.graphql.graphqlfirst;

import com.jayway.jsonpath.JsonPath;
import io.stargate.it.http.RestUtils;
import io.stargate.it.http.graphql.GraphqlClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.Response;

public class GraphqlFirstClient extends GraphqlClient {

  // Note: these constants duplicate the ones from the production code's `ResourcePaths` (which is
  // not accessible from here).
  private static final String ADMIN = "/graphql-admin";
  private static final String KEYSPACES = "/graphql";
  private static final String FILES = "/graphql-files";

  private final String host;
  private final String authToken;
  private final String adminUri;
  private final String cqlDirectivesUri;

  public GraphqlFirstClient(String host, String authToken) {
    this.host = host;
    this.authToken = authToken;
    this.adminUri = String.format("http://%s:8080%s", host, ADMIN);
    this.cqlDirectivesUri = String.format("http://%s:8080%s/cql_directives.graphql", host, FILES);
  }

  /**
   * Deploys new contents to a keyspace, assuming no previous version existed.
   *
   * @return the resulting version.
   */
  public UUID deploySchema(String keyspace, String contents) {
    return deploySchema(keyspace, null, contents);
  }

  public UUID deploySchema(String keyspace, String expectedVersion, String contents) {
    return deploySchema(keyspace, expectedVersion, false, contents);
  }

  public UUID deploySchema(
      String keyspace, String expectedVersion, boolean force, String contents) {
    Map<String, Object> response =
        getGraphqlData(
            authToken,
            adminUri,
            buildDeploySchemaQuery(keyspace, expectedVersion, force, contents));
    String version = JsonPath.read(response, "$.deploySchema.version");
    return UUID.fromString(version);
  }

  /**
   * Deploys new contents to a keyspace, assuming this will produce a single GraphQL error.
   *
   * @return the message of that error.
   */
  public String getDeploySchemaError(String keyspace, String expectedVersion, String contents) {
    return getDeploySchemaError(keyspace, expectedVersion, false, contents);
  }

  public String getDeploySchemaError(
      String keyspace, String expectedVersion, boolean force, String contents) {
    return getGraphqlError(
        authToken, adminUri, buildDeploySchemaQuery(keyspace, expectedVersion, force, contents));
  }

  private String buildDeploySchemaQuery(
      String keyspace, String expectedVersion, boolean force, String contents) {
    StringBuilder query = new StringBuilder("mutation { deploySchema( ");
    query.append(String.format("keyspace: \"%s\" ", keyspace));
    if (expectedVersion != null) {
      query.append(String.format("expectedVersion: \"%s\" ", expectedVersion));
    }
    query.append(String.format("force: %b ", force));
    query.append(String.format("schema: \"\"\"%s\"\"\" ", contents));
    return query.append(") { version } }").toString();
  }

  public void undeploySchema(String keyspace, String expectedVersion, boolean force) {
    getGraphqlData(authToken, adminUri, buildUndeploySchemaQuery(keyspace, expectedVersion, force));
  }

  public void undeploySchema(String keyspace, String expectedVersion) {
    undeploySchema(keyspace, expectedVersion, false);
  }

  public String getUndeploySchemaError(String keyspace, String expectedVersion, boolean force) {
    return getGraphqlError(
        authToken, adminUri, buildUndeploySchemaQuery(keyspace, expectedVersion, force));
  }

  public String getUndeploySchemaError(String keyspace, String expectedVersion) {
    return getUndeploySchemaError(keyspace, expectedVersion, false);
  }

  private String buildUndeploySchemaQuery(String keyspace, String expectedVersion, boolean force) {
    return String.format(
        "mutation { undeploySchema(keyspace: \"%s\", expectedVersion: \"%s\", force: %b) }",
        keyspace, expectedVersion, force);
  }

  /** Returns the contents of the static cql_directives.graphql file. */
  public String getCqlDirectivesFile() {
    try {
      return RestUtils.get(authToken, cqlDirectivesUri, Response.Status.OK.getStatusCode());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public String getSchemaFile(String keyspace) {
    return getSchemaFile(keyspace, null);
  }

  public String getSchemaFile(String keyspace, String version, int expectedStatusCode) {
    try {
      return RestUtils.get(authToken, buildSchemaFileUri(keyspace, version), expectedStatusCode);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public String getSchemaFile(String keyspace, String version) {
    return getSchemaFile(keyspace, version, Response.Status.OK.getStatusCode());
  }

  public void expectSchemaFileStatus(String keyspace, Response.Status expectedStatus) {
    expectSchemaFileStatus(keyspace, null, expectedStatus);
  }

  public void expectSchemaFileStatus(
      String keyspace, String version, Response.Status expectedStatus) {
    try {
      RestUtils.get(
          authToken, buildSchemaFileUri(keyspace, version), expectedStatus.getStatusCode());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Object executeKeyspaceQuery(String keyspace, String graphqlQuery) {
    return getGraphqlData(authToken, buildKeyspaceUri(keyspace), graphqlQuery);
  }

  /** Executes a GraphQL query for a keyspace, expecting a <b>single</b> GraphQL error. */
  public String getKeyspaceError(String keyspace, String graphqlQuery) {
    return getGraphqlError(authToken, buildKeyspaceUri(keyspace), graphqlQuery);
  }

  private String buildSchemaFileUri(String keyspace, String version) {
    String url =
        String.format("http://%s:8080%s/keyspace/%s.graphql", host, FILES, urlEncode(keyspace));
    if (version != null) {
      url = url + "?version=" + urlEncode(version);
    }
    return url;
  }

  private String buildKeyspaceUri(String keyspace) {
    return String.format("http://%s:8080%s/%s", host, KEYSPACES, keyspace);
  }
}
