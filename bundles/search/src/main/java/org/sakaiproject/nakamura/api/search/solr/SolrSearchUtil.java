/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.api.search.solr;

import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.DEFAULT_PAGED_ITEMS;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.INFINITY;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_ITEMS_PER_PAGE;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_PAGE;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.TIDY;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import javax.jcr.query.Query;

/**
 *
 */
public class SolrSearchUtil {

  public static final Logger LOGGER = LoggerFactory.getLogger(SolrSearchUtil.class);


  public static long[] getOffsetAndSize(SlingHttpServletRequest request,
      final Map<String, String> options) {
    long nitems;
    if (options != null && options.get(PARAMS_ITEMS_PER_PAGE) != null) {
      nitems = Long.valueOf(options.get(PARAMS_ITEMS_PER_PAGE));
    } else {
      nitems = SolrSearchUtil.longRequestParameter(request, PARAMS_ITEMS_PER_PAGE,
          DEFAULT_PAGED_ITEMS);
    }
    long page;
    if (options != null && options.get(PARAMS_PAGE) != null) {
      page = Long.valueOf(options.get(PARAMS_PAGE));
    } else {
      page = SolrSearchUtil.longRequestParameter(request, PARAMS_PAGE, 0);
    }
    long offset = page * nitems;
    long resultSize = Math.max(nitems, offset);
    return new long[]{offset, resultSize };
  }


  /**
   * Check for an integer value in the request.
   *
   * @param request
   *          The request to look in.
   * @param paramName
   *          The name of the parameter that holds the integer value.
   * @param defaultVal
   *          The default value in case the parameter is not found or is not an integer
   * @return The long value.
   */
  public static long longRequestParameter(SlingHttpServletRequest request,
      String paramName, long defaultVal) {
    RequestParameter param = request.getRequestParameter(paramName);
    if (param != null) {
      try {
        return Integer.parseInt(param.getString());
      } catch (NumberFormatException e) {
        LOGGER.warn(paramName + " parameter (" + param.getString()
            + ") is invalid; defaulting to " + defaultVal);
      }
    }
    return defaultVal;
  }

  /**
   * Get the starting point.
   *
   * @param request
   * @param total
   * @return
   */
  public static long getPaging(SlingHttpServletRequest request) {

    long nitems = longRequestParameter(request, PARAMS_ITEMS_PER_PAGE,
        DEFAULT_PAGED_ITEMS);
    long offset = longRequestParameter(request, PARAMS_PAGE, 0) * nitems;

    return offset;
  }

  /**
   * Assumes value is the value of a parameter in a where constraint and escapes it
   * according to the spec.
   *
   * @param value
   * @param queryLanguage
   *          The language to escape for. This can be XPATH, SQL, JCR_SQL2 or JCR_JQOM.
   *          Look at {@link Query Query}.
   * @return
   */
  @SuppressWarnings("deprecation") // Suppressed because we need to check depreciated methods just in case.
  public static String escapeString(String value, String queryLanguage) {
    String escaped = null;
    if (value != null) {
      if (queryLanguage.equals(Query.XPATH) || queryLanguage.equals(Query.SQL)
          || queryLanguage.equals(Query.JCR_SQL2) || queryLanguage.equals(Query.JCR_JQOM)) {
        // See JSR-170 spec v1.0, Sec. 6.6.4.9 and 6.6.5.2
        escaped = value.replaceAll("\\\\(?![-\"])", "\\\\\\\\").replaceAll("'", "\\\\'")
            .replaceAll("'", "''").replaceAll("\"", "\\\\\"");
      } else {
        LOGGER.error("Unknown query language: " + queryLanguage);
      }
    }
    return escaped;
  }

  public static int getTraversalDepth(SlingHttpServletRequest req) {
    int maxRecursionLevels = 0;
    final String[] selectors = req.getRequestPathInfo().getSelectors();
    if (selectors != null && selectors.length > 0) {
      final String level = selectors[selectors.length - 1];
      if (!TIDY.equals(level)) {
        if (INFINITY.equals(level)) {
          maxRecursionLevels = -1;
        } else {
          try {
            maxRecursionLevels = Integer.parseInt(level);
          } catch (NumberFormatException nfe) {
            LOGGER.warn("Invalid recursion selector value '" + level
                + "'; defaulting to 0");
          }
        }
      }
    }
    return maxRecursionLevels;
  }

}
