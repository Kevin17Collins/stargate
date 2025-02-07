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
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.stargate.sgv2.docsapi.service.query.search.db.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.stargate.bridge.grpc.Values;
import io.stargate.bridge.proto.QueryOuterClass;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentProperties;
import io.stargate.sgv2.docsapi.testprofiles.MaxDepth4TestProfile;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(MaxDepth4TestProfile.class)
class SubDocumentSearchQueryBuilderTest {

  private static final String KEYSPACE_NAME = RandomStringUtils.randomAlphanumeric(16);
  private static final String COLLECTION_NAME = RandomStringUtils.randomAlphanumeric(16);

  @Inject DocumentProperties documentProperties;

  @Nested
  class BuildQuery {

    @Test
    public void happyPath() {
      List<String> path = Arrays.asList("path", "to", "something");
      String docId = "d1234";

      SubDocumentSearchQueryBuilder builder =
          new SubDocumentSearchQueryBuilder(documentProperties, docId, path);
      QueryOuterClass.Query query =
          builder.bind(builder.buildQuery(KEYSPACE_NAME, COLLECTION_NAME));

      String expected =
          String.format(
              "SELECT WRITETIME(leaf) FROM \"%s\".\"%s\" WHERE p0 = ? AND p1 = ? AND p2 = ? AND key = ? ALLOW FILTERING",
              KEYSPACE_NAME, COLLECTION_NAME);
      assertThat(query.getCql()).isEqualTo(expected);
      assertThat(query.getValues().getValuesList())
          .containsExactly(
              Values.of("path"), Values.of("to"), Values.of("something"), Values.of("d1234"));
    }
  }
}
