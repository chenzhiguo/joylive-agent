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
package com.jd.live.agent.demo.grpc.consumer.controller;

import com.google.common.collect.Sets;
import com.jd.live.agent.demo.grpc.service.api.*;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Set;

@RestController
public class UserServiceController {

    @GrpcClient("grpc-provider")
    private UserServiceGrpc.UserServiceBlockingStub userServiceGrpc;

    private final Set<String> excludeHeaders = Sets.newHashSet(
            "host",
            "content-length",
            "connection",
            "accept-encoding",
            "user-agent",
            "content-type",
            "accept"
    );

    @GetMapping("/get")
    public String get(@RequestParam("id") Integer id,  HttpServletRequest servletRequest) {
        UserGetRequest request = UserGetRequest.newBuilder().setId(id).build();

        Metadata metadata = getMetadata(servletRequest);
        // 使用 stub.withInterceptors() 方法
        UserGetResponse response = userServiceGrpc.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .get(request);

        return response.toString();
    }

    @GetMapping("/create")
    public String create(@RequestParam("name") String name,
                          @RequestParam("gender") Integer gender) {
        UserCreateRequest request = UserCreateRequest.newBuilder()
                .setName(name)
                .setGender(gender)
                .build();
        UserCreateResponse response = userServiceGrpc.create(request);
        return response.toString();
    }

    private Metadata getMetadata(HttpServletRequest servletRequest) {
        // 创建 Metadata 对象来存储 header 和 cookie
        Metadata metadata = new Metadata();

        // 获取并转移所有 header
        Enumeration<String> headerNames = servletRequest.getHeaderNames();
        // 在添加 header 时进行过滤
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!excludeHeaders.contains(headerName.toLowerCase())) {
                String headerValue = servletRequest.getHeader(headerName);
                metadata.put(
                        Metadata.Key.of(headerName.toLowerCase(), Metadata.ASCII_STRING_MARSHALLER),
                        headerValue
                );
            }
        }

        // 获取并转移所有 cookie
        Cookie[] cookies = servletRequest.getCookies();
        if (cookies != null) {
            StringBuilder cookieString = new StringBuilder();
            for (Cookie cookie : cookies) {
                if (cookieString.length() > 0) {
                    cookieString.append("; ");
                }
                cookieString.append(cookie.getName()).append("=").append(cookie.getValue());
            }
            metadata.put(
                    Metadata.Key.of("cookie", Metadata.ASCII_STRING_MARSHALLER),
                    cookieString.toString()
            );
        }
        return metadata;
    }
}
