/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.pivotal.spring.cloud.service.config.client.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpClientErrorException;

import io.pivotal.spring.cloud.service.config.ConfigClientOAuth2ResourceDetails;
import io.pivotal.spring.cloud.service.config.ConfigResourceClient;
import io.pivotal.spring.cloud.service.config.ConfigResourceClientAutoConfiguration;

/**
 * @author Daniel Lavoie
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ConfigServerTestApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"spring.profiles.active=plaintext,native", "spring.cloud.config.enabled=true", "eureka.client.enabled=false",
		"spring.cloud.config.client.oauth2.client-id=acme"})
public class OAuth2ConfigResourceClientTest {
	// @formatter:off
	private static final String nginxConfig = "server {\n"
			+ "    listen              80;\n"
			+ "    server_name         example.com;\n"
			+ "}";

	private static final String devNginxConfig = "server {\n"
			+ "    listen              80;\n"
			+ "    server_name         dev.example.com;\n" 
			+ "}";

	private static final String testNginxConfig = "server {\n"
			+ "    listen              80;\n"
			+ "    server_name         test.example.com;\n"
			+ "}";
	// @formatter:on 

	@LocalServerPort
	private int port;

	@Autowired
	private ConfigClientOAuth2ResourceDetails resource;

	@Autowired
	private ConfigClientProperties configClientProperties;

	private ConfigResourceClient configClient;

	@Before
	public void setup() {
		resource.setAccessTokenUri("http://localhost:" + port + "/oauth/token");
		configClientProperties.setName("app");
		configClientProperties.setProfile(null);
		configClientProperties.setUri(new String[] {"http://localhost:" + port});
		configClient = new ConfigResourceClientAutoConfiguration()
				.configResourceClient(resource, configClientProperties);
	}

	@Test
	public void shouldFindSimplePlainFile() {
		Assert.assertEquals(nginxConfig, read(configClient.getPlainTextResource(null, "master", "nginx.conf")));

		Assert.assertEquals(devNginxConfig, read(configClient.getPlainTextResource("dev", "master", "nginx.conf")));

		configClientProperties.setProfile("test");
		Assert.assertEquals(testNginxConfig, read(configClient.getPlainTextResource(null, "master", "nginx.conf")));
	}

	@Test
	public void shouldFindBinaryFile() throws IOException {
		byte[] sourceImageBytes = StreamUtils.copyToByteArray(this.getClass().getClassLoader()
				.getResourceAsStream("config/image.png"));
		byte[] imageFromConfigServer = StreamUtils
				.copyToByteArray(configClient.getBinaryResource("dev", "master", "image.png")
						.getInputStream());
		Assert.assertArrayEquals(sourceImageBytes, imageFromConfigServer);
	}

	@Test(expected = HttpClientErrorException.class)
	public void missingConfigFileShouldReturnHttpError() {
		configClient.getPlainTextResource(null, "master", "missing-config.xml");
	}

	@Test(expected = IllegalArgumentException.class)
	public void missingApplicationNameShouldCrash() {
		configClientProperties.setName("");
		configClient.getPlainTextResource(null, "master", "nginx.conf");
	}

	@Test(expected = IllegalArgumentException.class)
	public void missingConfigServerUrlShouldCrash() {
		configClientProperties.setUri(new String[] { "" });
		configClient.getPlainTextResource(null, "master", "nginx.conf");
	}

	public String read(Resource resource) {
		try (BufferedReader buffer = new BufferedReader(
				new InputStreamReader(resource.getInputStream()))) {
			return buffer.lines().collect(Collectors.joining("\n"));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
