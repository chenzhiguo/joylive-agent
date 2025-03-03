/*
 * Copyright © ${year} ${owner} (${email})
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jd.live.agent.plugin.router.springcloud.v4.response;

import com.jd.live.agent.bootstrap.logger.Logger;
import com.jd.live.agent.bootstrap.logger.LoggerFactory;
import com.jd.live.agent.core.util.CollectionUtils;
import com.jd.live.agent.core.util.IOUtils;
import com.jd.live.agent.core.util.http.HttpUtils;
import com.jd.live.agent.governance.exception.ErrorPredicate;
import com.jd.live.agent.governance.exception.ServiceError;
import com.jd.live.agent.governance.response.AbstractHttpResponse.AbstractHttpOutboundResponse;
import feign.Response;
import org.springframework.http.HttpHeaders;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.jd.live.agent.core.util.map.MultiLinkedMap.caseInsensitive;

/**
 * FeignOutboundResponse
 *
 * @since 1.0.0
 */
public class FeignClusterResponse extends AbstractHttpOutboundResponse<Response> {

    private static final Logger logger = LoggerFactory.getLogger(BlockingClusterResponse.class);

    private byte[] body;

    public FeignClusterResponse(Response response) {
        super(response);
    }

    public FeignClusterResponse(ServiceError error, ErrorPredicate predicate) {
        super(error, predicate);
    }

    @Override
    public String getCode() {
        return response == null ? null : String.valueOf(response.status());
    }

    @Override
    public Object getResult() {
        if (body == null) {
            Response.Body bodied = response == null ? null : response.body();
            if (bodied == null) {
                body = new byte[0];
            } else {
                try {
                    InputStream in = bodied.asInputStream();
                    body = IOUtils.read(in);
                    response.close();
                    // create new
                    response = Response.builder()
                            .body(body)
                            .headers(response.headers())
                            .protocolVersion(response.protocolVersion())
                            .reason(response.reason())
                            .request(response.request())
                            .status(response.status())
                            .build();
                } catch (Throwable e) {
                    logger.error(e.getMessage(), e);
                    body = new byte[0];
                }
            }
        }
        return body;
    }

    @Override
    public String getHeader(String key) {
        if (key == null || response == null) {
            return null;
        }
        Map<String, Collection<String>> headers = response.headers();
        Collection<String> values = headers == null ? null : headers.get(key);
        return values == null ? null : values.iterator().next();
    }

    @Override
    public List<String> getHeaders(String key) {
        if (key == null || response == null) {
            return null;
        }
        Map<String, Collection<String>> headers = response.headers();
        Collection<String> values = headers == null ? null : headers.get(key);
        return values == null ? null : CollectionUtils.toList(values);
    }

    @Override
    protected Map<String, List<String>> parseCookies() {
        return HttpUtils.parseCookie(getHeaders(HttpHeaders.COOKIE));
    }

    @Override
    protected Map<String, List<String>> parseHeaders() {
        Map<String, Collection<String>> headers = response == null ? null : response.headers();
        if (headers == null) {
            return null;
        }
        return caseInsensitive(headers, true);
    }

}
