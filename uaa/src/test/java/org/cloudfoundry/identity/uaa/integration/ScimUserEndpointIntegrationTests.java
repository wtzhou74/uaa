package org.cloudfoundry.identity.uaa.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.cloudfoundry.identity.uaa.scim.ScimException;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJacksonHttpMessageConverter;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * @author Luke Taylor
 * @author Dave Syer
 */
public class ScimUserEndpointIntegrationTests {
	ObjectMapper mapper = new ObjectMapper();

	private final String userEndpoint = "/uaa/User";

	private final String usersEndpoint = "/uaa/Users";

	@Rule
	public ServerRunning server = ServerRunning.isRunning();
	
	{
		server.setPort(8001);
	}

	private RestTemplate client;

	@Before
	public void createRestTemplate() {
		client = server.getRestTemplate();
		List<HttpMessageConverter<?>> list = new ArrayList<HttpMessageConverter<?>>();
		list.add(new MappingJacksonHttpMessageConverter());
		list.add(new StringHttpMessageConverter());
		client.setErrorHandler(new ResponseErrorHandler() {
			@Override
			public boolean hasError(ClientHttpResponse response) throws IOException {
				return false;
			}

			@Override
			public void handleError(ClientHttpResponse response) throws IOException {
			}
		});
		client.setMessageConverters(list);
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> response = server.getForObject(usersEndpoint
				+ "?filter=userName eq 'joe' or userName eq 'joel'", Map.class);
		@SuppressWarnings("unchecked")
		List<Map<String, String>> results = (List<Map<String, String>>) response.getBody().get("resources");
		// System.err.println(results);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		for (Map<String, String> map : results) {
			String id = map.get("id");
			deleteUser(id); // ignore errors
		}
	}

	@SuppressWarnings("rawtypes")
	private ResponseEntity<Map> deleteUser(String id) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("If-Match", "*");
		return client.exchange(server.getUrl(userEndpoint + "/{id}"), HttpMethod.DELETE, new HttpEntity<Void>(headers),
				Map.class, id);
	}

	@SuppressWarnings("rawtypes")
	private ResponseEntity<Map> deleteUser(String id, int version) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("If-Match", "\"" + version + "\"");
		return client.exchange(server.getUrl(userEndpoint + "/{id}"), HttpMethod.DELETE, new HttpEntity<Void>(headers),
				Map.class, id);
	}

	// curl -v -H "Content-Type: application/json" -H "Accept: application/json" --data
	// "{\"userName\":\"joe\",\"schemas\":[\"urn:scim:schemas:core:1.0\"]}" http://localhost:8080/uaa/User
	@Test
	public void createUserSucceeds() throws Exception {
		ScimUser user = new ScimUser();
		user.setUserName("joe");
		user.setName(new ScimUser.Name("Joe", "User"));
		user.addEmail("joe@blah.com");

		ResponseEntity<ScimUser> response = client.postForEntity(server.getUrl(userEndpoint), user, ScimUser.class);
		ScimUser joe1 = response.getBody();
		assertEquals("joe", joe1.getUserName());

		// Check we can GET the user
		ScimUser joe2 = client.getForObject(server.getUrl(userEndpoint + "/{id}"), ScimUser.class, joe1.getId());

		assertEquals(joe1.getId(), joe2.getId());
	}

	@Test
	public void getUserHasEtag() throws Exception {
		ScimUser user = new ScimUser();
		user.setUserName("joe");
		user.setName(new ScimUser.Name("Joe", "User"));
		user.addEmail("joe@blah.com");

		ResponseEntity<ScimUser> response = client.postForEntity(server.getUrl(userEndpoint), user, ScimUser.class);
		ScimUser joe = response.getBody();
		assertEquals("joe", joe.getUserName());

		// Check we can GET the user
		ResponseEntity<ScimUser> result = client.getForEntity(server.getUrl(userEndpoint + "/{id}"), ScimUser.class, joe.getId());
		assertEquals("\""+joe.getVersion() + "\"", result.getHeaders().getFirst("ETag"));
	}


	// curl -v -H "Content-Type: application/json" -X PUT -H "Accept: application/json" --data
	// "{\"userName\":\"joe\",\"schemas\":[\"urn:scim:schemas:core:1.0\"]}" http://localhost:8080/uaa/User
	@Test
	public void updateUserSucceeds() throws Exception {

		ScimUser user = new ScimUser();
		user.setUserName("joe");
		user.setName(new ScimUser.Name("Joe", "User"));
		user.addEmail("joe@blah.com");

		ResponseEntity<ScimUser> response = client.postForEntity(server.getUrl(userEndpoint), user, ScimUser.class);

		ScimUser joe = response.getBody();
		assertEquals("joe", joe.getUserName());

		joe.setName(new ScimUser.Name("Joe", "Bloggs"));

		HttpHeaders headers = new HttpHeaders();
		headers.add("If-Match", "\"" + joe.getVersion() + "\"");
		response = client.exchange(server.getUrl(userEndpoint) + "/{id}", HttpMethod.PUT, new HttpEntity<ScimUser>(joe,
				headers), ScimUser.class, joe.getId());
		ScimUser joe1 = response.getBody();
		assertEquals("joe", joe1.getUserName());

		assertEquals(joe.getId(), joe1.getId());

	}

	// curl -v -H "Content-Type: application/json" -H "Accept: application/json" -H 'If-Match: "0"' --data
	// "{\"userName\":\"joe\",\"schemas\":[\"urn:scim:schemas:core:1.0\"]}" http://localhost:8080/uaa/User
	@Test
	public void createUserTwiceFails() throws Exception {
		ScimUser user = new ScimUser();
		user.setUserName("joel");
		user.setName(new ScimUser.Name("Joel", "D'sa"));
		user.addEmail("joel@blah.com");

		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> response = client.postForEntity(server.getUrl(userEndpoint), user, Map.class);
		@SuppressWarnings("unchecked")
		Map<String, String> joe1 = response.getBody();
		assertEquals("joel", joe1.get("userName"));

		response = client.postForEntity(server.getUrl(userEndpoint), user, Map.class);
		@SuppressWarnings("unchecked")
		Map<String, String> error = response.getBody();

		assertEquals(IllegalArgumentException.class.getName(), error.get("error"));

	}

	// curl -v -H "Content-Type: application/json" -H "Accept: application/json" -X DELETE
	// -H "If-Match: 0" http://localhost:8080/uaa/User/joel
	@Test
	public void deleteUserWithWrongIdFails() throws Exception {
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> response = deleteUser("9999", 0);
		@SuppressWarnings("unchecked")
		Map<String, String> error = response.getBody();
		// System.err.println(error);
		assertEquals(ScimException.class.getName(), error.get("error"));
		assertEquals("User 9999 does not exist", error.get("message"));

	}

	// curl -v -H "Content-Type: application/json" -H "Accept: application/json" -X DELETE
	// -H "If-Match: 0" http://localhost:8080/uaa/User/joel
	@Test
	public void deleteUserWithNoEtagFails() throws Exception {
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> response = client.exchange(server.getUrl(userEndpoint + "/{id}"), HttpMethod.DELETE,
				new HttpEntity<Void>((Void) null), Map.class, "joe");
		@SuppressWarnings("unchecked")
		Map<String, String> error = response.getBody();
		// System.err.println(error);
		assertEquals(ScimException.class.getName(), error.get("error"));
		assertEquals("Missing If-Match for DELETE", error.get("message"));

	}

	@Test
	public void getReturnsNotFoundForNonExistentUser() throws Exception {
		ResponseEntity<String> response = server.getForString(userEndpoint + "/9999");
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	public void findUsers() throws Exception {
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> response = server.getForObject(usersEndpoint, Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> results = response.getBody();
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue("There should be more than one user", (Integer) results.get("totalResults") > 1);
	}

}